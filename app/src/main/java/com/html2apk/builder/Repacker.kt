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
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Repacker Level 7.
 *
 * Perubahan:
 * - Parameter htmlFile: File diganti menjadi inputFile: File.
 * - Jika inputFile adalah FOLDER (hasil ekstrak ZIP), semua file rekursif masuk ke assets/www/.
 * - Jika inputFile adalah FILE tunggal (.html), dibungkus sebagai assets/www/index.html.
 * - Validasi index.html wajib ada.
 * - Menghapus tanda tangan lama dan melakukan signing ulang dengan V1, V2, V3.
 */
class Repacker(private val context: Context) {

    companion object {
        private const val ASSETS_WWW_PREFIX = "assets/www/"
        private const val LAUNCHER_ICON_SIZE = 192
    }

    // Pola regex untuk entry ikon launcher standar.
    // Contoh path: res/mipmap-hdpi/ic_launcher.png
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
        require(templateApk.exists()) { "Template APK tidak ditemukan: ${templateApk.absolutePath}" }
        require(inputFile.exists()) { "Input file/folder tidak ditemukan: ${inputFile.absolutePath}" }

        if (inputFile.isDirectory) {
            if (!File(inputFile, "index.html").exists()) {
                throw IllegalArgumentException("File index.html wajib ada di dalam ZIP!")
            }
        }

        val iconBytes: ByteArray? = iconFile?.let { loadScaledIcon(it) }

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

                        // 1) Buang assets/www/ lama.
                        if (name.startsWith(ASSETS_WWW_PREFIX)) continue

                        // 2) Buang signature lama (akan dibuat ulang).
                        if (isOldSignature(name)) continue

                        // 3) Replace ikon launcher bila disediakan.
                        if (iconBytes != null && iconEntryPattern.matches(name)) {
                            zipOut.putNextEntry(ZipEntry(name))
                            zipOut.write(iconBytes)
                            zipOut.closeEntry()
                            continue
                        }

                        // 4) Salin apa adanya.
                        val newEntry = ZipEntry(name)
                        newEntry.time = entry.time
                        zipOut.putNextEntry(newEntry)
                        if (!entry.isDirectory) {
                            zipIn.getInputStream(entry).use { input -> input.copyTo(zipOut) }
                        }
                        zipOut.closeEntry()
                    }

                    // 5) Tambahkan konten baru ke assets/www/.
                    if (inputFile.isDirectory) {
                        addExtractedDirectoryToApk(inputFile, zipOut)
                    } else {
                        addSingleHtmlToApk(inputFile, zipOut)
                    }
                }
            }

            // 6) Sign ulang APK
            signApk(unsignedApk, outputApk, keystoreFile, keystorePass, keyAlias, keyPass)
            return outputApk
        } finally {
            if (unsignedApk.exists()) unsignedApk.delete()
        }
    }

    // ------------------------------------------------------------------
    // Assets writer
    // ------------------------------------------------------------------

    private fun addSingleHtmlToApk(htmlFile: File, zipOut: ZipOutputStream) {
        val entryName = "${ASSETS_WWW_PREFIX}index.html"
        zipOut.putNextEntry(ZipEntry(entryName))
        htmlFile.inputStream().use { it.copyTo(zipOut) }
        zipOut.closeEntry()
    }

    private fun addExtractedDirectoryToApk(rootDir: File, zipOut: ZipOutputStream) {
        if (!File(rootDir, "index.html").exists()) {
            throw IllegalArgumentException("File index.html wajib ada di dalam ZIP!")
        }

        rootDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
                val entryName = ASSETS_WWW_PREFIX + relativePath
                zipOut.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
    }

    // ------------------------------------------------------------------
    // Utilitas
    // ------------------------------------------------------------------

    private fun isOldSignature(entryName: String): Boolean {
        if (!entryName.startsWith("META-INF/")) return false
        val upper = entryName.uppercase()
        return upper == "META-INF/MANIFEST.MF" ||
                upper.endsWith(".SF") ||
                upper.endsWith(".RSA") ||
                upper.endsWith(".DSA") ||
                upper.endsWith(".EC")
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
        FileInputStream(keystoreFile).use { fis ->
            ks.load(fis, keystorePass.toCharArray())
        }
        val privateKey = ks.getKey(keyAlias, keyPass.toCharArray()) as PrivateKey
        val cert = ks.getCertificate(keyAlias) as X509Certificate

        val signerConfig = ApkSigner.SignerConfig.Builder(
            keyAlias,
            privateKey,
            listOf(cert)
        ).build()

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            // Menyesuaikan skema signing dengan minSdkVersion perangkat target
            .setMinSdkVersion(21)
            .build()
            .sign()

        if (!outputApk.exists() || outputApk.length() == 0L) {
            throw IllegalStateException("Signing APK gagal: output tidak valid")
        }
    }
}