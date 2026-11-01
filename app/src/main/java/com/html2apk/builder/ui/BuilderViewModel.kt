package com.html2apk.builder.ui

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.html2apk.builder.Repacker
import com.html2apk.builder.SignerHelper
import com.html2apk.builder.data.AppDatabase
import com.html2apk.builder.data.BuildHistory
import com.html2apk.builder.data.BuildHistoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel utama untuk BuilderScreen.
 *
 * Menyimpan seluruh state form input, state proses build, dan menulis
 * riwayat (SUCCESS / FAILED) ke Room database setelah proses selesai.
 */
class BuilderViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val dao: BuildHistoryDao = AppDatabase.getDatabase(appContext).historyDao()

    /** Flow riwayat build terurut dari terbaru. */
    val historyFlow: Flow<List<BuildHistory>> = dao.getAllHistory()

    // ── State form input (dipegang ViewModel agar persist antar-rotasi) ──
    var selectedHtmlUri by mutableStateOf<Uri?>(null)
        private set
    var selectedHtmlName by mutableStateOf<String?>(null)
        private set
    var selectedIconUri by mutableStateOf<Uri?>(null)
        private set
    var selectedIconName by mutableStateOf<String?>(null)
        private set
    var appName by mutableStateOf("")
    var packageName by mutableStateOf("")

    // ── State proses build ──
    var isBuilding by mutableStateOf(false)
        private set
    var buildResult by mutableStateOf<String?>(null)
        private set

    fun setHtmlUri(uri: Uri?) {
        selectedHtmlUri = uri
        selectedHtmlName = uri?.let { resolveDisplayName(appContext, it) }
    }

    fun setIconUri(uri: Uri?) {
        selectedIconUri = uri
        selectedIconName = uri?.let { resolveDisplayName(appContext, it) }
    }

    /** Konsumsi hasil build agar Toast tidak ditampilkan berulang. */
    fun consumeBuildResult() {
        buildResult = null
    }

    /**
     * Menjalankan pipeline build lengkap:
     * copy template → repack (ARSCLib) → generate keystore → sign → simpan riwayat.
     */
    fun startBuild() {
        val htmlUri = selectedHtmlUri ?: return
        if (isBuilding) return

        isBuilding = true
        buildResult = null

        val finalAppName = appName.ifBlank { "My App" }
        val finalPackageName = packageName.ifBlank { "com.html2apk.generated" }
        val currentIconUri = selectedIconUri

        viewModelScope.launch(Dispatchers.IO) {
            var success = false
            var apkPath = ""
            var errorMessage: String? = null

            try {
                // 1. Salin template.apk dari assets ke cache.
                val templateFile = File(appContext.cacheDir, "template.apk")
                if (templateFile.exists()) templateFile.delete()
                appContext.assets.open("template.apk").use { input ->
                    templateFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 2. Siapkan direktori output.
                val outputDir = appContext.getExternalFilesDir(
                    Environment.DIRECTORY_DOWNLOADS
                ) ?: throw IllegalStateException(
                    "Direktori unduhan eksternal tidak tersedia"
                )
                outputDir.mkdirs()

                // 3. Salin input HTML + ikon ke cache.
                val htmlFile = copyUriToCache(appContext, htmlUri, "input.html")
                val iconFile = currentIconUri?.let {
                    copyUriToCache(appContext, it, "icon.png")
                }

                // 4. Repack via ARSCLib (logika Level 5 tidak diubah).
                val unsignedApk = File(outputDir, "output.apk")
                if (unsignedApk.exists() && !unsignedApk.delete()) {
                    throw IllegalStateException("Gagal menghapus APK unsigned lama")
                }

                Repacker.repack(
                    templateApk = templateFile,
                    htmlFile = htmlFile,
                    iconFile = iconFile,
                    outputApk = unsignedApk,
                    newAppName = finalAppName,
                    newPackageName = finalPackageName
                )

                // 5. Generate keystore + sign.
                val keystoreFile = File(appContext.cacheDir, "keystore.p12")
                SignerHelper.generateKeystore(keystoreFile)

                val signedFile = File(outputDir, "signed_output.apk")
                SignerHelper.signApk(unsignedApk, signedFile, keystoreFile)

                if (unsignedApk.exists()) unsignedApk.delete()

                apkPath = signedFile.absolutePath
                success = true
            } catch (e: Exception) {
                errorMessage = e.message ?: e.javaClass.simpleName
            }

            // 6. Simpan riwayat ke Room (SUCCESS maupun FAILED).
            try {
                dao.insertHistory(
                    BuildHistory(
                        appName = finalAppName,
                        packageName = finalPackageName,
                        apkPath = apkPath,
                        timestamp = System.currentTimeMillis(),
                        status = if (success) "SUCCESS" else "FAILED"
                    )
                )
            } catch (_: Exception) {
                // Kegagalan insert tidak boleh menggagalkan hasil build.
            }

            // 7. Update state UI di main thread.
            withContext(Dispatchers.Main) {
                isBuilding = false
                buildResult = if (success) {
                    "Sukses! APK tersimpan di: $apkPath"
                } else {
                    "Gagal build: ${errorMessage ?: "Unknown error"}"
                }
            }
        }
    }
}

/**
 * Menyalin konten `content://` URI ke file cache dengan nama tertentu.
 */
internal fun copyUriToCache(context: Context, uri: Uri, targetFileName: String): File {
    val cacheFile = File(context.cacheDir, targetFileName)
    if (cacheFile.exists() && !cacheFile.delete()) {
        throw IllegalStateException("Gagal menghapus cache $targetFileName lama")
    }

    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("Gagal membuka file: $uri")

    inputStream.use { input ->
        cacheFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    return cacheFile
}

/**
 * Mengambil nama tampilan file dari Uri.
 */
internal fun resolveDisplayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: uri.toString()

    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = cursor.getString(index) ?: name
                }
            }
        }
    }
    return name
}
