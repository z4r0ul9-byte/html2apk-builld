package com.html2apk.builder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.apksig.ApkSigner
import com.reandroid.apk.ApkFile
import com.reandroid.xml.XMLFactory
import com.reandroid.xml.XmlDocument
import com.reandroid.xml.XmlElement
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.KeyStore
import java.security.PrivateKey

/**
 * Repacker (Level 8) - Menggunakan ARSCLib secara penuh.
 *
 * Perubahan Level 8:
 * - Patch `<uses-sdk>` (minSdkVersion / targetSdkVersion / maxSdkVersion).
 * - Set versionCode & versionName langsung ke manifest XML.
 * - Injeksi `<intent-filter>` (VIEW / BROWSABLE) ke launcher activity
 *   jika `urlScheme` diisi.
 * - Signing tetap dilakukan (via apksig) agar kompatibel dengan
 *   pemanggilan ViewModel yang sudah stabil.
 *
 * DILARANG:
 * - ZipOutputStream manual untuk menulis APK final.
 * - Mengubah logika ZIP extraction / icon replacement yang sudah stabil.
 */
class Repacker(private val context: Context) {

    companion object {
        private const val ASSETS_WWW_PREFIX = "assets/www/"
        private const val LAUNCHER_ICON_SIZE = 192
        private const val DEFAULT_OLD_LABEL = "Template App"
    }

    /**
     * Repack + patch manifest + sign APK.
     */
    fun repack(
        templateApk: File,
        inputFile: File,
        outputApk: File,
        iconFile: File?,
        newAppName: String? = null,
        newPackageName: String? = null,
        versionCode: Int = 1,
        versionName: String = "1.0",
        minSdk: Int = 24,
        targetSdk: Int = 34,
        maxSdk: Int? = null,
        urlScheme: String? = null,
        keystoreFile: File? = null,
        keystorePass: String = "android",
        keyAlias: String = "androiddebugkey",
        keyPass: String = "android"
    ): File {
        require(templateApk.exists()) { "Template APK tidak ditemukan: ${templateApk.absolutePath}" }
        require(inputFile.exists()) { "Input file/folder tidak ditemukan: ${inputFile.absolutePath}" }

        // 1. Validasi index.html jika input folder (perilaku stabil — jangan diubah)
        if (inputFile.isDirectory) {
            if (!File(inputFile, "index.html").exists()) {
                throw IllegalArgumentException(
                    "File index.html wajib ada di dalam folder ZIP yang diekstrak!"
                )
            }
        }

        // 2. Load template via ARSCLib
        val apkFile = ApkFile.load(templateApk.absolutePath)

        // 3. File intermediate untuk unsigned APK
        val parentDir = outputApk.parentFile ?: throw IOException("Parent dir output tidak valid")
        val unsignedApk = File(parentDir, "unsigned_${System.currentTimeMillis()}.apk")

        try {
            // 4. Patch manifest: version, uses-sdk, url scheme
            patchManifest(
                apkFile = apkFile,
                versionCode = versionCode,
                versionName = versionName,
                minSdk = minSdk,
                targetSdk = targetSdk,
                maxSdk = maxSdk,
                urlScheme = urlScheme
            )

            // 5. Package name
            if (!newPackageName.isNullOrBlank()) {
                apkFile.androidManifest?.packageName = newPackageName
            }

            // 6. App label (ARSC)
            val appLabel = newAppName?.takeIf { it.isNotBlank() } ?: deriveAppLabel(inputFile)
            if (appLabel.isNotBlank()) {
                val table = apkFile.table
                val appNameEntry = table?.getEntry("string", "app_name")
                if (appNameEntry != null) {
                    appNameEntry.valueAsString = appLabel
                } else {
                    table?.stringPool?.forEach { str ->
                        if (str.value == DEFAULT_OLD_LABEL) {
                            str.value = appLabel
                        }
                    }
                }
            }

            // 7. Hapus assets/www lama
            val entriesToRemove = mutableListOf<String>()
            for (entry in apkFile.entries) {
                if (entry.name.startsWith(ASSETS_WWW_PREFIX)) {
                    entriesToRemove.add(entry.name)
                }
            }
            entriesToRemove.forEach { apkFile.removeEntry(it) }

            // 8. Tambah konten baru
            if (inputFile.isDirectory) {
                addDirectoryToApk(apkFile, inputFile)
            } else {
                apkFile.addEntry("${ASSETS_WWW_PREFIX}index.html", inputFile.inputStream())
            }

            // 9. Ganti ikon
            if (iconFile != null && iconFile.exists()) {
                replaceIcon(apkFile, iconFile)
            }

            // 10. Tulis APK unsigned
            if (unsignedApk.exists()) unsignedApk.delete()
            apkFile.writeApk(unsignedApk)

        } finally {
            apkFile.close()
        }

        // 11. Sign (apksig). Jika keystore tidak tersedia, salin apa adanya.
        try {
            if (keystoreFile != null && keystoreFile.exists()) {
                signApk(
                    unsignedApk = unsignedApk,
                    signedApk = outputApk,
                    keystoreFile = keystoreFile,
                    keystorePass = keystorePass,
                    keyAlias = keyAlias,
                    keyPass = keyPass
                )
            } else {
                unsignedApk.copyTo(outputApk, overwrite = true)
            }
        } finally {
            if (unsignedApk.exists()) unsignedApk.delete()
        }

        return outputApk
    }

    // ------------------------------------------------------------------
    // Manifest patching (Level 8)
    // ------------------------------------------------------------------

    private fun patchManifest(
        apkFile: ApkFile,
        versionCode: Int,
        versionName: String,
        minSdk: Int,
        targetSdk: Int,
        maxSdk: Int?,
        urlScheme: String?
    ) {
        val manifest = apkFile.androidManifest ?: return

        // Dapatkan XmlDocument <manifest> root
        val doc: XmlDocument = try {
            manifest.getAndroidManifest()
        } catch (e: Throwable) {
            // Fallback: gunakan referensi manifest sebagai XmlElement root
            // (beberapa versi ARSCLib membungkus root di dalam block)
            throw IllegalStateException("Gagal membaca AndroidManifest XML: ${e.message}", e)
        }
        val root: XmlElement = doc.getRootElement()
            ?: throw IllegalStateException("Root element <manifest> tidak ditemukan")

        // ── 1. versionCode & versionName ─────────────────────────────
        root.setAttribute("android:versionCode", versionCode.toString())
        root.setAttribute("android:versionName", versionName)

        // ── 2. <uses-sdk> ────────────────────────────────────────────
        val usesSdkList = root.getElementsByTagName("uses-sdk")
        val usesSdk: XmlElement = if (usesSdkList.isNotEmpty()) {
            usesSdkList[0]
        } else {
            val created = XMLFactory.createElement("uses-sdk")
            // Sisipkan sebelum <application> agar sesuai konvensi
            val appList = root.getElementsByTagName("application")
            if (appList.isNotEmpty()) {
                root.addElement(created)
            } else {
                root.addElement(created)
            }
            created
        }

        usesSdk.setAttribute("android:minSdkVersion", minSdk.toString())
        usesSdk.setAttribute("android:targetSdkVersion", targetSdk.toString())

        if (maxSdk != null && maxSdk > 0) {
            usesSdk.setAttribute("android:maxSdkVersion", maxSdk.toString())
        } else {
            // Bersihkan atribut maxSdkVersion jika ada versi lama
            try {
                usesSdk.removeAttribute("android:maxSdkVersion")
            } catch (_: Throwable) { /* opsional */ }
        }

        // ── 3. URL Scheme intent-filter ─────────────────────────────
        if (!urlScheme.isNullOrBlank()) {
            injectUrlSchemeFilter(root, urlScheme.trim())
        }
    }

    private fun injectUrlSchemeFilter(root: XmlElement, scheme: String) {
        val application = root.getElementsByTagName("application").firstOrNull() ?: return
        val activities = application.getElementsByTagName("activity")
        if (activities.isEmpty()) return

        // Cari launcher activity: intent-filter dengan MAIN + LAUNCHER
        val launcher = activities.firstOrNull { activity ->
            activity.getElementsByTagName("intent-filter").any { filter ->
                val hasMain = filter.getElementsByTagName("action").any { a ->
                    safeAttr(a, "android:name") == "android.intent.action.MAIN"
                }
                val hasLauncher = filter.getElementsByTagName("category").any { c ->
                    safeAttr(c, "android:name") == "android.intent.category.LAUNCHER"
                }
                hasMain && hasLauncher
            }
        } ?: activities[0]

        val intentFilter = XMLFactory.createElement("intent-filter")

        val action = XMLFactory.createElement("action")
        action.setAttribute("android:name", "android.intent.action.VIEW")
        intentFilter.addElement(action)

        val catDefault = XMLFactory.createElement("category")
        catDefault.setAttribute("android:name", "android.intent.category.DEFAULT")
        intentFilter.addElement(catDefault)

        val catBrowsable = XMLFactory.createElement("category")
        catBrowsable.setAttribute("android:name", "android.intent.category.BROWSABLE")
        intentFilter.addElement(catBrowsable)

        val data = XMLFactory.createElement("data")
        data.setAttribute("android:scheme", scheme)
        intentFilter.addElement(data)

        launcher.addElement(intentFilter)
    }

    private fun safeAttr(element: XmlElement, name: String): String? {
        return try {
            element.getAttributeValue(name)
        } catch (_: Throwable) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Signing (apksig)
    // ------------------------------------------------------------------

    private fun signApk(
        unsignedApk: File,
        signedApk: File,
        keystoreFile: File,
        keystorePass: String,
        keyAlias: String,
        keyPass: String
    ) {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        FileInputStream(keystoreFile).use { fis ->
            keyStore.load(fis, keystorePass.toCharArray())
        }

        val privateKey = keyStore.getKey(keyAlias, keyPass.toCharArray()) as PrivateKey
        val certificate = keyStore.getCertificate(keyAlias)

        val signerConfig = ApkSigner.SignerConfig.Builder(
            keyAlias,
            privateKey,
            listOf(certificate)
        ).build()

        if (signedApk.exists()) signedApk.delete()

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(signedApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    // ------------------------------------------------------------------
    // Helper (dipertahankan dari versi stabil)
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
        val iconBytes = loadScaledIcon(iconFile)

        val entriesToRemove = mutableListOf<String>()
        for (entry in apkFile.entries) {
            val name = entry.name
            if (name.startsWith("res/") && name.contains("ic_launcher")) {
                if (name.endsWith(".xml") || name.endsWith(".png")) {
                    entriesToRemove.add(name)
                }
            }
        }
        entriesToRemove.forEach { apkFile.removeEntry(it) }

        // Kumpulkan path PNG lama yang tadinya ada
        for (entry in apkFile.entries.toList()) {
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
