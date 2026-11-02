package com.html2apk.builder

import com.reandroid.apk.ApkModule
import com.reandroid.archive.FileInputSource
import com.reandroid.arsc.chunk.PackageBlock
import java.io.File

/**
 * Mesin repack + edit identitas APK (Level 6 - Perbaikan Ikon).
 *
 * Menggunakan ARSCLib `ApkModule` secara penuh untuk menjaga alignment ZIP.
 * Logika adaptif icon XML dihapus agar Android terpaksa menggunakan file PNG.
 */
object Repacker {

    private const val WWW_INDEX_PATH = "assets/www/index.html"

    fun repack(
        templateApk: File,
        htmlFile: File,
        iconFile: File?,
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

        // Muat template APK dengan ARSCLib.
        val apkModule = ApkModule.loadApkFile(templateApk)

        try {
            // ── 1. Ganti package name pada <manifest> ──
            val manifest = apkModule.androidManifestBlock
                ?: throw IllegalStateException(
                    "AndroidManifest.xml tidak ditemukan di template"
                )
            manifest.setPackageName(newPackageName)

            // ── 2. Ganti string resource `app_name` pada resources.arsc ──
            val tableBlock = apkModule.tableBlock
                ?: throw IllegalStateException(
                    "resources.arsc tidak ditemukan di template"
                )

            val packageIterator = tableBlock.listPackages().iterator()
            if (!packageIterator.hasNext()) {
                throw IllegalStateException("Package block kosong di resources.arsc")
            }
            val packageBlock: PackageBlock = packageIterator.next()

            val appNameEntry = packageBlock.getOrCreate("", "string", "app_name")
            appNameEntry.setValueAsString(newAppName)

            // ── 3. Ganti HTML: hapus entry lama, tambah entry baru ──
            val zipEntryMap = apkModule.zipEntryMap

            // Hapus entry index.html lama (jika ada).
            zipEntryMap.remove(WWW_INDEX_PATH)

            // Tambahkan HTML baru sebagai assets/www/index.html.
            val htmlInputSource = FileInputSource(htmlFile, WWW_INDEX_PATH)
            zipEntryMap.add(htmlInputSource)

            // ── 4. Ganti ikon (jika iconFile != null) ──
            if (iconFile != null && iconFile.exists()) {
                val entriesToRemove = mutableListOf<String>()
                val pngEntriesToReplace = mutableListOf<String>()

                // Kumpulkan nama entry untuk dihapus atau diganti.
                val iterator = zipEntryMap.iterator()
                while (iterator.hasNext()) {
                    val inputSource = iterator.next()
                    val name = inputSource.alias

                    // 4a. Hapus semua XML Adaptive Icon agar tidak menimpa PNG.
                    if (name.startsWith("res/mipmap-anydpi-v26/") && name.endsWith(".xml")) {
                        entriesToRemove.add(name)
                    }
                    // 4b. Cari semua file PNG ikon di folder mipmap.
                    else if (name.startsWith("res/mipmap-") && 
                             (name.endsWith("/ic_launcher.png") || name.endsWith("/ic_launcher_round.png"))) {
                        pngEntriesToReplace.add(name)
                    }
                }

                // Eksekusi penghapusan XML.
                entriesToRemove.forEach { zipEntryMap.remove(it) }

                // Eksekusi penggantian PNG.
                if (pngEntriesToReplace.isNotEmpty()) {
                    pngEntriesToReplace.forEach { entryName ->
                        zipEntryMap.remove(entryName)
                        // Timpa dengan iconFile baru menggunakan nama entry yang sama.
                        zipEntryMap.add(FileInputSource(iconFile, entryName))
                    }
                } else {
                    // Fallback jika template tidak memiliki PNG (sangat jarang terjadi).
                    // Tambahkan setidaknya satu ikon default.
                    val defaultIconPath = "res/mipmap-hdpi-v4/ic_launcher.png"
                    zipEntryMap.add(FileInputSource(iconFile, defaultIconPath))
                }
            }

            // ── 5. Tulis APK hasil edit langsung ke output ──
            if (outputApk.exists() && !outputApk.delete()) {
                throw IllegalStateException(
                    "Gagal menghapus output lama: ${outputApk.absolutePath}"
                )
            }
            outputApk.parentFile?.mkdirs()

            apkModule.writeApk(outputApk)
        } finally {
            // Pastikan resource ARSCLib dilepas.
            apkModule.close()
        }
    }
}