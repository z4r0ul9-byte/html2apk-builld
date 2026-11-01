package com.html2apk.builder

import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.PackageBlock
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Mesin repack + edit identitas APK (Level 4).
 *
 * Alur:
 * 1. Buka template APK dengan ARSCLib (`ApkModule.loadApkFile`).
 * 2. Ganti atribut `package` pada `<manifest>` menjadi [newPackageName].
 * 3. Ganti string resource `app_name` menjadi [newAppName].
 * 4. Tulis APK hasil edit ke file sementara.
 * 5. Repack ZIP dari file sementara: skip `META-INF/` & `assets/www/`,
 *    sisipkan HTML user sebagai `assets/www/index.html`.
 */
object Repacker {

    private const val BUFFER_SIZE = 8192
    private const val WWW_PREFIX = "assets/www/"
    private const val META_INF_PREFIX = "META-INF/"
    private const val INDEX_HTML_ENTRY = "assets/www/index.html"

    fun repack(
        templateApk: File,
        htmlFile: File,
        outputApk: File,
        newAppName: String,
        newPackageName: String
    ) {
        require(templateApk.exists()) {
            "Template APK tidak ditemukan: ${templateApk.absolutePath}"
        }
        require(htmlFile.exists()) {
            "File HTML tidak ditemukan: ${htmlFile.absolutePath}"
        }

        // File sementara hasil edit ARSCLib (manifest + resources.arsc).
        val tempEditedApk = File(templateApk.parentFile, "temp_edited.apk")
        if (tempEditedApk.exists() && !tempEditedApk.delete()) {
            throw IllegalStateException("Gagal menghapus temporary APK lama")
        }

        try {
            // ── LANGKAH 1: Edit manifest & resources dengan ARSCLib ──
            val apkModule = ApkModule.loadApkFile(templateApk)
            try {
                // 1a. Ganti package name pada <manifest package="...">.
                val manifest = apkModule.androidManifestBlock
                    ?: throw IllegalStateException(
                        "AndroidManifest.xml tidak ditemukan di template"
                    )
                manifest.setPackageName(newPackageName)

                // 1b. Ganti string resource `app_name` pada resources.arsc.
                val tableBlock = apkModule.tableBlock
                    ?: throw IllegalStateException(
                        "resources.arsc tidak ditemukan di template"
                    )

                // ARSCLib menyediakan listPackages() yang mengembalikan Iterable,
                // bukan List, sehingga kita ambil package pertama via iterator.
                // Alternatif yang lebih aman jika package ID diketahui:
                //   val packageBlock = tableBlock.getPackageBlockById(0x7f)
                val packageIterator = tableBlock.listPackages().iterator()
                if (!packageIterator.hasNext()) {
                    throw IllegalStateException("Package block kosong di resources.arsc")
                }
                val packageBlock: PackageBlock = packageIterator.next()

                // Method yang benar adalah setValueAsString, bukan setDataAsString.
                val appNameEntry = packageBlock.getOrCreate("", "string", "app_name")
                appNameEntry.setValueAsString(newAppName)

                // 1c. Simpan hasil edit ke file sementara.
                apkModule.writeApk(tempEditedApk)
            } finally {
                // Pastikan resource ARSCLib dilepas.
                apkModule.close()
            }

            // ── LANGKAH 2: Repack ZIP dari file sementara hasil edit ──
            if (outputApk.exists() && !outputApk.delete()) {
                throw IllegalStateException(
                    "Gagal menghapus output lama: ${outputApk.absolutePath}"
                )
            }
            outputApk.parentFile?.mkdirs()

            ZipFile(tempEditedApk).use { zip ->
                ZipOutputStream(outputApk.outputStream().buffered()).use { out ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()

                        // Buang segel lama agar signing baru valid.
                        if (entry.name.startsWith(META_INF_PREFIX)) continue
                        // Ganti seluruh isi web template dengan HTML user.
                        if (entry.name.startsWith(WWW_PREFIX)) continue

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
        } finally {
            // Bersihkan file sementara.
            if (tempEditedApk.exists()) tempEditedApk.delete()
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