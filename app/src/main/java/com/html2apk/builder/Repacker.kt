package com.html2apk.builder

import com.reandroid.apk.ApkModule
import com.reandroid.archive.FileInputSource
import com.reandroid.archive.InputSource
import com.reandroid.arsc.chunk.PackageBlock
import java.io.File

/**
 * Mesin repack + edit identitas APK (Level 5).
 *
 * Semua operasi ZIP (ganti HTML, ganti ikon, tulis ulang APK) dilakukan
 * sepenuhnya melalui ARSCLib `ApkModule`, sehingga alignment ZIP tetap
 * terjaga dan APK dapat di-install.
 */
object Repacker {

    private const val WWW_INDEX_PATH = "assets/www/index.html"
    private const val ICON_NAME_FRAGMENT = "ic_launcher"

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
                // Kumpulkan semua nama entry yang mengandung "ic_launcher".
                val iconEntryNames = mutableListOf<String>()
                val iterator = zipEntryMap.iterator()
                while (iterator.hasNext()) {
                    val inputSource = iterator.next()
                    val name = inputSource.alias
                    if (name.contains(ICON_NAME_FRAGMENT, ignoreCase = true) &&
                        (name.endsWith(".png") || name.endsWith(".xml"))
                    ) {
                        iconEntryNames.add(name)
                    }
                }

                // Hapus entry ikon lama.
                iconEntryNames.forEach { zipEntryMap.remove(it) }

                // Tambahkan ikon baru dengan nama yang sama (agar manifest tetap valid).
                iconEntryNames.forEach { entryName ->
                    val iconInputSource = FileInputSource(iconFile, entryName)
                    zipEntryMap.add(iconInputSource)
                }

                // Jika template tidak memiliki entry ic_launcher sama sekali,
                // tambahkan setidaknya satu entry default agar ikon terpasang.
                if (iconEntryNames.isEmpty()) {
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