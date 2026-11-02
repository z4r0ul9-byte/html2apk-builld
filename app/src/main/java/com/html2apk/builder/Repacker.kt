package com.html2apk.builder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.apksig.ApkSigner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class Repacker(private val context: Context) {

    companion object {
        private const val ASSETS_WWW_PREFIX = "assets/www/"
        private const val LAUNCHER_ICON_SIZE = 192
        private const val DEFAULT_OLD_LABEL = "Template App"   // ganti jika template memakai nama lain
    }

    private val iconEntryPattern = Regex("^res/(mipmap|drawable)[^/]*/ic_launcher\\.png$")

    fun repack(
        templateApk: File,
        inputFile: File,
        outputApk: File,
        iconFile: File?,
        keystoreFile: File,
        keystorePass: String,
        keyAlias: String,
        keyPass: String
    ): File {
        require(templateApk.exists()) { "Template APK tidak ditemukan" }
        require(inputFile.exists()) { "Input tidak ditemukan" }

        val newLabel = deriveAppLabel(inputFile)
        val iconBytes = iconFile?.let { loadScaledIcon(it) }

        val unsignedApk = File(
            outputApk.parentFile ?: File("."),
            "unsigned_${System.currentTimeMillis()}.apk"
        )

        try {
            ZipFile(templateApk).use { zipIn ->
                ZipOutputStream(FileOutputStream(unsignedApk)).use { zipOut ->
                    val entries = zipIn.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name

                        if (name.startsWith(ASSETS_WWW_PREFIX)) continue
                        if (isOldSignature(name)) continue

                        if (iconBytes != null && iconEntryPattern.matches(name)) {
                            writeEntry(zipOut, name, iconBytes, entry.time)
                            continue
                        }

                        // Baca bytes asli
                        val raw = if (entry.isDirectory) null
                                  else zipIn.getInputStream(entry).use { it.readBytes() }

                        // resources.arsc: WAJIB STORED + alignment 4-byte
                        if (name == "resources.arsc" && raw != null) {
                            val patched = if (newLabel != null)
                                replaceLabelInArsc(raw, DEFAULT_OLD_LABEL, newLabel)
                            else raw
                            writeStoredAlignedEntry(zipOut, name, patched, entry.time)
                            continue
                        }

                        // AndroidManifest.xml: byte-level replace string label (jika hardcoded)
                        if (name == "AndroidManifest.xml" && raw != null && newLabel != null) {
                            val patched = replaceLabelInAxml(raw, DEFAULT_OLD_LABEL, newLabel)
                            writeEntry(zipOut, name, patched, entry.time)
                            continue
                        }

                        // Salin apa adanya
                        if (entry.isDirectory) {
                            zipOut.putNextEntry(ZipEntry(name).apply { time = entry.time })
                            zipOut.closeEntry()
                        } else {
                            writeEntry(zipOut, name, raw!!, entry.time)
                        }
                    }

                    if (inputFile.isDirectory) {
                        addDirectoryToApk(inputFile, zipOut)
                    } else {
                        addSingleHtmlToApk(inputFile, zipOut)
                    }
                }
            }

            signApk(unsignedApk, outputApk, keystoreFile, keystorePass, keyAlias, keyPass)
            return outputApk
        } finally {
            if (unsignedApk.exists()) unsignedApk.delete()
        }
    }

    // ------------------------------------------------------------------
    // Penentuan nama aplikasi baru
    // ------------------------------------------------------------------

    private fun deriveAppLabel(inputFile: File): String? {
        val base = if (inputFile.isDirectory) inputFile.name else inputFile.nameWithoutExtension
        return base
            .replace(Regex("[^A-Za-z0-9 _\\-]"), "")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }
            ?: "My App"
    }

    // ------------------------------------------------------------------
    // Byte-level replacement (ARSC & AXML)
    // ------------------------------------------------------------------

    /**
     * Cari pola UTF-16LE "Template App" di resources.arsc dan ganti dengan label baru.
     * Juga coba pola UTF-8 untuk keamanan.
     */
    private fun replaceLabelInArsc(arsc: ByteArray, oldLabel: String, newLabel: String): ByteArray {
        var out = arsc

        // UTF-16LE
        val oldU16 = oldLabel.toByteArray(Charsets.UTF_16LE)
        val newU16 = newLabel.toByteArray(Charsets.UTF_16LE)
        out = replaceBytes(out, oldU16, newU16)

        // UTF-8 fallback
        val oldU8 = oldLabel.toByteArray(Charsets.UTF_8)
        val newU8 = newLabel.toByteArray(Charsets.UTF_8)
        out = replaceBytes(out, oldU8, newU8)

        return out
    }

    private fun replaceLabelInAxml(axml: ByteArray, oldLabel: String, newLabel: String): ByteArray {
        var out = axml
        out = replaceBytes(out, oldLabel.toByteArray(Charsets.UTF_16LE), newLabel.toByteArray(Charsets.UTF_16LE))
        out = replaceBytes(out, oldLabel.toByteArray(Charsets.UTF_8), newLabel.toByteArray(Charsets.UTF_8))
        return out
    }

    private fun replaceBytes(input: ByteArray, old: ByteArray, new: ByteArray): ByteArray {
        if (old.isEmpty() || new.isEmpty()) return input
        var result = input
        var searchFrom = 0
        while (true) {
            val idx = indexOf(result, old, searchFrom) ?: break
            val output = ByteArray(result.size - old.size + new.size)
            System.arraycopy(result, 0, output, 0, idx)
            System.arraycopy(new, 0, output, idx, new.size)
            System.arraycopy(result, idx + old.size, output, idx + new.size, result.size - idx - old.size)
            result = output
            searchFrom = idx + new.size
        }
        return result
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int? {
        if (needle.isEmpty() || haystack.size < needle.size) return null
        outer@ for (i in from..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return null
    }

    // ------------------------------------------------------------------
    // Penulisan ZIP entry (normal & STORED-ALIGNED untuk ARSC)
    // ------------------------------------------------------------------

    private fun writeEntry(out: ZipOutputStream, name: String, data: ByteArray, time: Long) {
        val entry = ZipEntry(name).apply { this.time = time }
        out.putNextEntry(entry)
        out.write(data)
        out.closeEntry()
    }

    /**
     * resources.arsc harus STORED (uncompressed) dan offset data kelipatan 4.
     * Jika offset awal tidak aligned, tambahkan padding di header lokal sebelum data.
     * (ZipOutputStream tidak mendukung padding lokal secara langsung, tetapi
     *  kita bisa memaksa ekstrak field dengan cara manual — di sini digunakan
     *  pendekatan sederhana: set method STORED dan berharap template sudah aligned;
     *  untuk produksi, gunakan library zipalign.)
     */
    private fun writeStoredAlignedEntry(out: ZipOutputStream, name: String, data: ByteArray, time: Long) {
        val crc = CRC32().apply { update(data) }
        val entry = ZipEntry(name).apply {
            this.time = time
            method = ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            this.crc = crc.value
        }
        out.putNextEntry(entry)
        out.write(data)
        out.closeEntry()
    }

    // ------------------------------------------------------------------
    // Assets writer
    // ------------------------------------------------------------------

    private fun addSingleHtmlToApk(htmlFile: File, zipOut: ZipOutputStream) {
        val data = htmlFile.readBytes()
        writeEntry(zipOut, "${ASSETS_WWW_PREFIX}index.html", data, System.currentTimeMillis())
    }

    private fun addDirectoryToApk(rootDir: File, zipOut: ZipOutputStream) {
        if (!File(rootDir, "index.html").exists()) {
            throw IllegalArgumentException("index.html wajib ada")
        }
        rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.relativeTo(rootDir).invariantSeparatorsPath
            writeEntry(zipOut, ASSETS_WWW_PREFIX + rel, file.readBytes(), file.lastModified())
        }
    }

    // ------------------------------------------------------------------
    // Utilitas
    // ------------------------------------------------------------------

    private fun isOldSignature(name: String): Boolean {
        if (!name.startsWith("META-INF/")) return false
        val u = name.uppercase()
        return u == "META-INF/MANIFEST.MF" || u.endsWith(".SF") ||
                u.endsWith(".RSA") || u.endsWith(".DSA") || u.endsWith(".EC")
    }

    private fun loadScaledIcon(iconFile: File): ByteArray {
        val bmp = BitmapFactory.decodeFile(iconFile.absolutePath)
            ?: return iconFile.readBytes()
        val scaled = Bitmap.createScaledBitmap(bmp, LAUNCHER_ICON_SIZE, LAUNCHER_ICON_SIZE, true)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
        if (scaled !== bmp) bmp.recycle()
        scaled.recycle()
        return baos.toByteArray()
    }

    private fun signApk(
        inputApk: File,
        outputApk: File,
        keystoreFile: File,
        keystorePass: String,
        keyAlias: String,
        keyPass: String
    ) {
        val ks = KeyStore.getInstance("PKCS12")
        FileInputStream(keystoreFile).use { fis -> ks.load(fis, keystorePass.toCharArray()) }
        val privateKey = ks.getKey(keyAlias, keyPass.toCharArray()) as PrivateKey
        val cert = ks.getCertificate(keyAlias) as X509Certificate

        val signerConfig = ApkSigner.SignerConfig.Builder(
            keyAlias, privateKey, listOf(cert)
        ).build()

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setMinSdkVersion(21)
            .build()
            .sign()

        if (!outputApk.exists() || outputApk.length() == 0L) {
            throw IllegalStateException("Signing gagal")
        }
    }
}