package com.html2apk.builder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.reandroid.apk.ApkFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Repacker (Level 8) - Menggunakan ARSCLib secara penuh.
 * 
 * Perubahan:
 * - DILARANG menggunakan ZipInputStream/ZipOutputStream untuk menulis APK final.
 * - Menggunakan ApkFile dari ARSCLib untuk memodifikasi manifest, resources, 
 *   menghapus aset lama, dan menambahkan aset baru (HTML/ZIP).
 * - Proses signing diserahkan sepenuhnya ke ViewModel/SignerHelper.
 */
class Repacker(private val context: Context) {

    companion object {
        private const val ASSETS_WWW_PREFIX = "assets/www/"
        private const val LAUNCHER_ICON_SIZE = 192
        private const val DEFAULT_OLD_LABEL = "Template App" // Sesuaikan dengan string di template APK
    }

    /**
     * Melakukan proses repacking.
     * @return File APK hasil repack (belum di-sign).
     */
    fun repack(
        templateApk: File,
        inputFile: File, // Bisa berupa file HTML tunggal ATAU folder hasil ekstrak ZIP
        outputApk: File,
        iconFile: File?,
        newAppName: String? = null,
        newPackageName: String? = null
    ): File {
        require(templateApk.exists()) { "Template APK tidak ditemukan: ${templateApk.absolutePath}" }
        require(inputFile.exists()) { "Input file/folder tidak ditemukan: ${inputFile.absolutePath}" }

        // 1. Validasi index.html jika input berupa Folder
        if (inputFile.isDirectory) {
            if (!File(inputFile, "index.html").exists()) {
                throw IllegalArgumentException("File index.html wajib ada di dalam folder ZIP yang diekstrak!")
            }
        }

        // 2. Load Template APK menggunakan ARSCLib
        val apkFile = ApkFile.load(templateApk.absolutePath)

        try {
            // 3. Modifikasi Manifest (Package Name) via ARSCLib
            if (!newPackageName.isNullOrBlank()) {
                apkFile.androidManifest?.packageName = newPackageName
                // Update juga di manifest XML secara eksplisit jika diperlukan oleh ARSCLib
                apkFile.androidManifest?.refresh()
            }

            // 4. Modifikasi ARSC (App Name) via ARSCLib
            val appLabel = newAppName ?: deriveAppLabel(inputFile)
            if (appLabel.isNotBlank()) {
                val table = apkFile.table
                // Mencari entry string app_name dan menggantinya
                val appNameEntry = table?.getEntry("string", "app_name")
                if (appNameEntry != null) {
                    appNameEntry.valueAsString = appLabel
                } else {
                    // Fallback jika app_name tidak ditemukan via nama, kita bisa
                    // menimpa semua string yang bernilai "Template App"
                    table?.stringPool?.forEach { str ->
                        if (str.value == DEFAULT_OLD_LABEL) {
                            str.value = appLabel
                        }
                    }
                }
            }

            // 5. Hapus entry lama `assets/www/` yang ada di apkFile
            val entriesToRemove = mutableListOf<String>()
            for (entry in apkFile.entries) {
                if (entry.name.startsWith(ASSETS_WWW_PREFIX)) {
                    entriesToRemove.add(entry.name)
                }
            }
            entriesToRemove.forEach { apkFile.removeEntry(it) }

            // 6. Tambahkan konten baru (HTML / Folder)
            if (inputFile.isDirectory) {
                addDirectoryToApk(apkFile, inputFile)
            } else {
                // Input berupa file HTML tunggal
                apkFile.addEntry("${ASSETS_WWW_PREFIX}index.html", inputFile.inputStream())
            }

            // 7. Ganti Ikon
            if (iconFile != null && iconFile.exists()) {
                replaceIcon(apkFile, iconFile)
            }

            // 8. Tulis APK Final (unsigned) menggunakan ARSCLib
            if (outputApk.exists()) outputApk.delete()
            apkFile.writeApk(outputApk)

            return outputApk

        } finally {
            // Selalu tutup ApkFile untuk membebaskan memori
            apkFile.close()
        }
    }

    // ------------------------------------------------------------------
    // Fungsi Bantuan
    // ------------------------------------------------------------------

    private fun deriveAppLabel(inputFile: File): String {
        val base = if (inputFile.isDirectory) inputFile.name else inputFile.nameWithoutExtension
        return base
            .replace(Regex("[^A-Za-z0-9 _\\-]"), "")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }
            ?: "My App"
    }

    private fun addDirectoryToApk(apkFile: ApkFile, rootDir: File) {
        rootDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
                val entryName = ASSETS_WWW_PREFIX + relativePath
                apkFile.addEntry(entryName, file.inputStream())
            }
    }

    private fun replaceIcon(apkFile: ApkFile, iconFile: File) {
        // 1. Siapkan byte array untuk ikon baru
        val iconBytes = loadScaledIcon(iconFile)
        
        // 2. Kumpulkan semua entry yang perlu dihapus (Adaptive Icon XML & PNG Lama)
        val entriesToRemove = mutableListOf<String>()
        for (entry in apkFile.entries) {
            val name = entry.name
            if (name.startsWith("res/") && name.contains("ic_launcher")) {
                // Hapus XML adaptive icon (jika ada)
                if (name.endsWith(".xml")) {
                    entriesToRemove.add(name)
                } 
                // Hapus PNG lama (akan ditimpa)
                else if (name.endsWith(".png")) {
                    entriesToRemove.add(name)
                }
            }
        }
        
        // Hapus file lama dari APK
        entriesToRemove.forEach { apkFile.removeEntry(it) }

        // 3. Tambahkan PNG baru dengan nama yang sama persis
        // Kita iterasi lagi untuk mencari path PNG yang tadinya ada
        for (entry in apkFile.entries.toList()) { // toList() untuk menghindari ConcurrentModificationException
            val name = entry.name
            if (name.startsWith("res/") && name.contains("ic_launcher") && name.endsWith(".png")) {
                apkFile.addEntry(name, ByteArrayInputStream(iconBytes))
            }
        }
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
}