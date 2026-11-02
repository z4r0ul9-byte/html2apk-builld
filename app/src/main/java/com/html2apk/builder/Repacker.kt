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

class Repacker(private val context: Context) {

    companion object {
        private const val ASSETS_WWW_PREFIX = "assets/www/"
        private const val LAUNCHER_ICON_SIZE = 192
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
        require(inputFile.exists()) { "Input file/folder tidak ditemukan" }

        if (inputFile.isDirectory) {
            if (!File(inputFile, "index.html").exists()) {
                throw IllegalArgumentException("File index.html wajib ada di dalam ZIP!")
            }
        }

        val iconBytes: ByteArray? = iconFile?.let { loadScaledIcon(it) }

        val unsignedApk = File(outputApk.parentFile ?: File("."), "unsigned_${System.currentTimeMillis()}.apk")

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
                            zipOut.putNextEntry(ZipEntry(name))
                            zipOut.write(iconBytes)
                            zipOut.closeEntry()
                            continue
                        }

                        val newEntry = ZipEntry(name)
                        newEntry.time = entry.time
                        zipOut.putNextEntry(newEntry)
                        if (!entry.isDirectory) {
                            zipIn.getInputStream(entry).use { input -> input.copyTo(zipOut) }
                        }
                        zipOut.closeEntry()
                    }

                    if (inputFile.isDirectory) {
                        addExtractedDirectoryToApk(inputFile, zipOut)
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

    // ... (fungsi addSingleHtmlToApk, addExtractedDirectoryToApk, isOldSignature, loadScaledIcon tetap sama) ...

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

        val signerConfig = ApkSigner.SignerConfig.Builder(keyAlias, privateKey, listOf(cert)).build()

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            // Penyesuaian: Menyesuaikan skema signing dengan minSdkVersion perangkat
            // Ini membantu kompatibilitas pada perangkat yang lebih lama
            .setMinSdkVersion(21) // Anda bisa menyesuaikan ini dengan minSdk proyek Anda
            .build()
            .sign()

        if (!outputApk.exists() || outputApk.length() == 0L) {
            throw IllegalStateException("Signing APK gagal: output tidak valid")
        }
    }
}