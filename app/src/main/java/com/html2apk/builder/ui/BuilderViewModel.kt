package com.html2apk.builder.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.html2apk.builder.Repacker
import com.html2apk.builder.data.BuildHistory
import com.html2apk.builder.data.BuildHistoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * State UI Builder.
 */
data class BuilderUiState(
    val selectedFileUri: Uri? = null,
    val selectedFileName: String? = null,
    val selectedIconUri: Uri? = null,
    val isBuilding: Boolean = false,
    val progressMessage: String = "",
    val outputApkPath: String? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel Level 7.
 * Perubahan: Menyesuaikan pemanggilan Room DAO dengan Entity BuildHistory yang ada.
 */
class BuilderViewModel(
    application: Application,
    private val historyDao: BuildHistoryDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BuilderUiState())
    val uiState: StateFlow<BuilderUiState> = _uiState.asStateFlow()

    fun onFileSelected(uri: Uri) {
        val name = queryFileName(uri) ?: "unknown"
        _uiState.update {
            it.copy(
                selectedFileUri = uri,
                selectedFileName = name,
                errorMessage = null,
                outputApkPath = null
            )
        }
    }

    fun onIconSelected(uri: Uri) {
        _uiState.update { it.copy(selectedIconUri = uri) }
    }

    fun startBuild() {
        val state = _uiState.value
        val fileUri = state.selectedFileUri ?: run {
            _uiState.update { it.copy(errorMessage = "Pilih file HTML atau ZIP terlebih dahulu") }
            return
        }
        if (state.isBuilding) return

        val fileName = state.selectedFileName.orEmpty()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBuilding = true,
                    errorMessage = null,
                    outputApkPath = null,
                    progressMessage = "Menyiapkan build..."
                )
            }
            try {
                val outputApk = withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()

                    // 1. Tentukan jenis input berdasarkan ekstensi.
                    _uiState.update { it.copy(progressMessage = "Menyiapkan file input...") }
                    val inputFile: File = if (fileName.endsWith(".zip", ignoreCase = true)) {
                        extractZipToCache(ctx, fileUri)
                    } else {
                        copyUriToCache(ctx, fileUri, "input.html")
                    }

                    // 2. Siapkan resource lain.
                    _uiState.update { it.copy(progressMessage = "Menyalin template APK...") }
                    val templateApk = copyAssetToCache(ctx, "template.apk")

                    val iconFile: File? = state.selectedIconUri?.let { iconUri ->
                        copyUriToCache(ctx, iconUri, "launcher_icon.png")
                    }

                    val keystoreFile = copyAssetToCache(ctx, "debug.keystore")

                    // 3. Repack + sign.
                    _uiState.update { it.copy(progressMessage = "Repacking & signing APK...") }
                    val output = File(ctx.cacheDir, "output_${System.currentTimeMillis()}.apk")

                    Repacker(ctx).repack(
                        templateApk = templateApk,
                        inputFile = inputFile,
                        outputApk = output,
                        iconFile = iconFile,
                        keystoreFile = keystoreFile,
                        keystorePass = "android",
                        keyAlias = "androiddebugkey",
                        keyPass = "android"
                    )
                    output
                }

                // 4. Simpan riwayat build (Sesuaikan dengan parameter Entity BuildHistory Anda).
                // Error sebelumnya: Parameter 'fileName', 'outputPath', 'sizeBytes' tidak ada.
                // Parameter yang valid: appName, packageName, apkPath, status.
                val record = BuildHistory(
                    appName = fileName.substringBeforeLast("."),
                    packageName = "com.html2apk.generated",
                    apkPath = outputApk.absolutePath,
                    status = "Success"
                )
                // Catatan: Jika fungsi DAO Anda bukan insertHistory, sesuaikan (misal: addHistory / insert).
                historyDao.insertHistory(record)

                _uiState.update {
                    it.copy(
                        isBuilding = false,
                        outputApkPath = outputApk.absolutePath,
                        progressMessage = "Build selesai"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBuilding = false,
                        errorMessage = e.message ?: "Build gagal",
                        progressMessage = ""
                    )
                }
            }
        }
    }

    private fun extractZipToCache(context: Context, zipUri: Uri): File {
        val destDir = File(context.cacheDir, "zip_extract_${System.currentTimeMillis()}")
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw IOException("Gagal membuat folder ekstrak: ${destDir.absolutePath}")
        }

        val rawStream = context.contentResolver.openInputStream(zipUri)
            ?: throw IOException("Tidak bisa membuka file ZIP")

        rawStream.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    val destFile = File(destDir, entryName)

                    // Zip Slip protection.
                    val canonicalBase = destDir.canonicalPath + File.separator
                    val canonicalDest = destFile.canonicalPath
                    if (!canonicalDest.startsWith(canonicalBase) &&
                        canonicalDest != destDir.canonicalPath
                    ) {
                        throw SecurityException("Entri ZIP tidak aman (path traversal): $entryName")
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos -> zis.copyTo(fos) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        if (!File(destDir, "index.html").exists()) {
            destDir.deleteRecursively()
            throw IllegalArgumentException("File index.html wajib ada di dalam ZIP!")
        }

        return destDir
    }

    private fun copyUriToCache(context: Context, uri: Uri, targetName: String): File {
        val destFile = File(context.cacheDir, targetName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        } ?: throw IOException("Gagal membuka file: $uri")
        return destFile
    }

    private fun copyAssetToCache(context: Context, assetName: String): File {
        val destFile = File(context.cacheDir, assetName)
        context.assets.open(assetName).use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        }
        return destFile
    }

    private fun queryFileName(uri: Uri): String? {
        val ctx = getApplication<Application>()
        return ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        } ?: uri.lastPathSegment
    }
}