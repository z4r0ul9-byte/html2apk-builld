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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
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
 * State UI Builder (Level 8).
 * Menambahkan field wizard Langkah 1 - Info Aplikasi.
 */
data class BuilderUiState(
    // File & icon
    val selectedFileUri: Uri? = null,
    val selectedFileName: String? = null,
    val selectedIconUri: Uri? = null,

    // Build status
    val isBuilding: Boolean = false,
    val progressMessage: String = "",
    val outputApkPath: String? = null,
    val errorMessage: String? = null,

    // ── Level 8: Langkah 1 / 10 - Info Aplikasi ─────────────────────
    val appName: String = "",
    val packageName: String = "com.html2apk.generated",
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val minSdk: Int = 24,
    val targetSdk: Int = 34,
    val maxSdk: Int? = null,
    val urlScheme: String? = null,
    val autoIncrement: Boolean = false
)

class BuilderViewModel(
    application: Application,
    private val historyDao: BuildHistoryDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BuilderUiState())
    val uiState: StateFlow<BuilderUiState> = _uiState.asStateFlow()

    val historyFlow = historyDao.getAllHistory()
        .flowOn(Dispatchers.IO)
        .catch { emit(emptyList()) }

    // ------------------------------------------------------------------
    // File & icon callbacks (dipertahankan)
    // ------------------------------------------------------------------

    fun onFileSelected(uri: Uri) {
        val name = queryFileName(uri) ?: "unknown"
        _uiState.update {
            it.copy(
                selectedFileUri = uri,
                selectedFileName = name,
                errorMessage = null,
                outputApkPath = null,
                // Auto-fill appName dari nama file (jika masih kosong)
                appName = it.appName.ifBlank {
                    name.substringBeforeLast(".").replaceFirstChar { c -> c.uppercase() }
                }
            )
        }
    }

    fun onIconSelected(uri: Uri) {
        _uiState.update { it.copy(selectedIconUri = uri) }
    }

    // ------------------------------------------------------------------
    // Wizard field callbacks
    // ------------------------------------------------------------------

    fun onAppNameChanged(value: String) {
        _uiState.update { it.copy(appName = value) }
    }

    fun onPackageNameChanged(value: String) {
        _uiState.update { it.copy(packageName = value) }
    }

    fun onVersionCodeChanged(value: String) {
        val parsed = value.filter { it.isDigit() }.toIntOrNull() ?: 1
        _uiState.update { it.copy(versionCode = parsed.coerceAtLeast(1)) }
    }

    fun onVersionNameChanged(value: String) {
        _uiState.update { it.copy(versionName = value) }
    }

    fun onMinSdkChanged(value: String) {
        val parsed = value.filter { it.isDigit() }.toIntOrNull() ?: 24
        _uiState.update { it.copy(minSdk = parsed.coerceAtLeast(1)) }
    }

    fun onTargetSdkChanged(value: String) {
        val parsed = value.filter { it.isDigit() }.toIntOrNull() ?: 34
        _uiState.update { it.copy(targetSdk = parsed.coerceAtLeast(1)) }
    }

    fun onMaxSdkChanged(value: String) {
        val trimmed = value.trim()
        val parsed = if (trimmed.isEmpty()) null
        else trimmed.filter { it.isDigit() }.toIntOrNull()
        _uiState.update { it.copy(maxSdk = parsed) }
    }

    fun onUrlSchemeChanged(value: String) {
        _uiState.update {
            it.copy(urlScheme = value.trim().takeIf { v -> v.isNotEmpty() })
        }
    }

    fun onAutoIncrementChanged(value: Boolean) {
        _uiState.update { it.copy(autoIncrement = value) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ------------------------------------------------------------------
    // Build
    // ------------------------------------------------------------------

    fun startBuild() {
        val state = _uiState.value
        val fileUri = state.selectedFileUri ?: run {
            _uiState.update {
                it.copy(errorMessage = "Pilih file HTML atau ZIP terlebih dahulu")
            }
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
                // 1. Resolve versionCode (auto-increment jika dicentang)
                val resolvedVersionCode = resolveVersionCode(state)

                // 2. Update state agar user melihat versionCode final
                _uiState.update { it.copy(versionCode = resolvedVersionCode) }

                val outputApk = withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()

                    _uiState.update { it.copy(progressMessage = "Menyiapkan file input...") }
                    val inputFile: File = if (fileName.endsWith(".zip", ignoreCase = true)) {
                        extractZipToCache(ctx, fileUri)
                    } else {
                        copyUriToCache(ctx, fileUri, "input.html")
                    }

                    _uiState.update { it.copy(progressMessage = "Menyalin template APK...") }
                    val templateApk = copyAssetToCache(ctx, "template.apk")

                    val iconFile: File? = state.selectedIconUri?.let { iconUri ->
                        copyUriToCache(ctx, iconUri, "launcher_icon.png")
                    }

                    val keystoreFile = copyAssetToCache(ctx, "debug.keystore")

                    _uiState.update { it.copy(progressMessage = "Repacking & signing APK...") }
                    val output = File(ctx.cacheDir, "output_${System.currentTimeMillis()}.apk")

                    Repacker(ctx).repack(
                        templateApk = templateApk,
                        inputFile = inputFile,
                        outputApk = output,
                        iconFile = iconFile,
                        newAppName = state.appName.takeIf { it.isNotBlank() },
                        newPackageName = state.packageName.takeIf { it.isNotBlank() },
                        versionCode = resolvedVersionCode,
                        versionName = state.versionName.ifBlank { "1.0" },
                        minSdk = state.minSdk,
                        targetSdk = state.targetSdk,
                        maxSdk = state.maxSdk,
                        urlScheme = state.urlScheme,
                        keystoreFile = keystoreFile,
                        keystorePass = "android",
                        keyAlias = "androiddebugkey",
                        keyPass = "android"
                    )
                    output
                }

                // 3. Simpan ke history
                val record = BuildHistory(
                    appName = state.appName.ifBlank { fileName.substringBeforeLast(".") },
                    packageName = state.packageName.ifBlank { "com.html2apk.generated" },
                    apkPath = outputApk.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    status = "SUCCESS",
                    versionCode = resolvedVersionCode,
                    versionName = state.versionName.ifBlank { "1.0" }
                )
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
                try {
                    val failedRecord = BuildHistory(
                        appName = state.appName.ifBlank { fileName.substringBeforeLast(".") },
                        packageName = state.packageName.ifBlank { "com.html2apk.generated" },
                        apkPath = "",
                        timestamp = System.currentTimeMillis(),
                        status = "FAILED",
                        versionCode = state.versionCode,
                        versionName = state.versionName.ifBlank { "1.0" }
                    )
                    historyDao.insertHistory(failedRecord)
                } catch (_: Exception) { /* abaikan */ }
            }
        }
    }

    /**
     * Hitung versionCode efektif:
     * - Jika autoIncrement = false → pakai state.versionCode.
     * - Jika autoIncrement = true  → pakai (latest sukses di Room) + 1,
     *   fallback ke state.versionCode bila belum ada history.
     */
    private suspend fun resolveVersionCode(state: BuilderUiState): Int {
        if (!state.autoIncrement) return state.versionCode
        val pkg = state.packageName.ifBlank { "com.html2apk.generated" }
        return try {
            val latest = withContext(Dispatchers.IO) {
                historyDao.getLatestVersionCode(pkg)
            }
            if (latest != null) latest + 1 else state.versionCode
        } catch (_: Exception) {
            state.versionCode
        }
    }

    // ------------------------------------------------------------------
    // IO Helpers (dipertahankan)
    // ------------------------------------------------------------------

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

                    val canonicalBase = destDir.canonicalPath + File.separator
                    val canonicalDest = destFile.canonicalPath
                    if (!canonicalDest.startsWith(canonicalBase) &&
                        canonicalDest != destDir.canonicalPath
                    ) {
                        throw SecurityException("Entri ZIP tidak aman: $entryName")
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
