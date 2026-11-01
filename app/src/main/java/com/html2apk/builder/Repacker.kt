package com.html2apk.builder

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Mesin repack APK (Level 2 + Level 3).
 *
 * Perubahan Level 3:
 * - Selain melewati `assets/www/`, sekarang juga MELEWATI seluruh `META-INF/`
 *   dari template. Folder ini berisi segel JAR lama (MANIFEST.MF, CERT.SF, CERT.RSA)
 *   yang sudah tidak valid setelah isi APK diubah, dan akan membuat proses
 *   signing ulang gagal / APK hasil tidak valid.
 */
object Repacker {

    private const val BUFFER_SIZE = 8192
    private const val WWW_PREFIX = "assets/www/"
    private const val META_INF_PREFIX = "META-INF/"
    private const val INDEX_HTML_ENTRY = "assets/www/index.html"

    fun repack(templateApk: File, htmlFile: File, outputApk: File) {
        require(templateApk.exists()) {
            "Template APK tidak ditemukan: ${templateApk.absolutePath}"
        }
        require(htmlFile.exists()) {
            "File HTML tidak ditemukan: ${htmlFile.absolutePath}"
        }

        if (outputApk.exists() && !outputApk.delete()) {
            throw IllegalStateException("Gagal menghapus output lama: ${outputApk.absolutePath}")
        }
        outputApk.parentFile?.mkdirs()

        ZipFile(templateApk).use { zip ->
            ZipOutputStream(outputApk.outputStream().buffered()).use { out ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()

                    // Level 3: buang segel lama agar signing baru valid.
                    if (entry.name.startsWith(META_INF_PREFIX)) {
                        continue
                    }
                    // Ganti seluruh isi web template dengan HTML user.
                    if (entry.name.startsWith(WWW_PREFIX)) {
                        continue
                    }

                    val newEntry = ZipEntry(entry.name)
                    out.putNextEntry(newEntry)
                    if (!entry.isDirectory) {
                        zip.getInputStream(entry).use { input ->
                            copyStream(input, out)
                        }
                    }
                    out.closeEntry()
                }

                // Sisipkan HTML user sebagai assets/www/index.html.
                val indexEntry = ZipEntry(INDEX_HTML_ENTRY)
                out.putNextEntry(indexEntry)
                htmlFile.inputStream().use { input ->
                    copyStream(input, out)
                }
                out.closeEntry()
            }
        }
    }

    private fun copyStream(input: java.io.InputStream, out: java.io.OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int
        while (input.read(buffer).also { read = it } > 0) {
            out.write(buffer, 0, read)
        }
    }
}