package com.html2apk.builder

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Mesin repack APK sederhana (Level 2).
 *
 * Tugas:
 * 1. Membaca template APK (ZipFile).
 * 2. Menyalin seluruh entry KECUALI yang berada di `assets/www/`.
 * 3. Menambahkan entry baru `assets/www/index.html` berisi HTML dari user.
 *
 * Belum ada signing / manifest editing — hanya repack ZIP.
 */
object Repacker {

    private const val BUFFER_SIZE = 8192
    private const val WWW_PREFIX = "assets/www/"
    private const val INDEX_HTML_ENTRY = "assets/www/index.html"

    /**
     * Menghasilkan [outputApk] dari [templateApk] dengan konten [htmlFile]
     * disisipkan sebagai `assets/www/index.html`.
     */
    fun repack(templateApk: File, htmlFile: File, outputApk: File) {
        require(templateApk.exists()) {
            "Template APK tidak ditemukan: ${templateApk.absolutePath}"
        }
        require(htmlFile.exists()) {
            "File HTML tidak ditemukan: ${htmlFile.absolutePath}"
        }

        // Hapus output lama agar tidak korup.
        if (outputApk.exists() && !outputApk.delete()) {
            throw IllegalStateException("Gagal menghapus output lama: ${outputApk.absolutePath}")
        }
        outputApk.parentFile?.mkdirs()

        ZipFile(templateApk).use { zip ->
            ZipOutputStream(outputApk.outputStream().buffered()).use { out ->
                // Salin semua entry template, kecuali `assets/www/`.
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()

                    // Skip seluruh isi assets/www/ dari template —
                    // akan digantikan oleh index.html milik user.
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

                // Sisipkan file HTML user sebagai assets/www/index.html.
                val indexEntry = ZipEntry(INDEX_HTML_ENTRY)
                out.putNextEntry(indexEntry)
                htmlFile.inputStream().use { input ->
                    copyStream(input, out)
                }
                out.closeEntry()
            }
        }
    }

    /**
     * Menyalin stream dengan buffer 8192 byte. Tidak menutup stream (caller `use`).
     */
    private fun copyStream(input: java.io.InputStream, out: java.io.OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int
        while (input.read(buffer).also { read = it } > 0) {
            out.write(buffer, 0, read)
        }
    }
}
