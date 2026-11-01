package com.html2apk.builder

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.html2apk.builder.ui.theme.HTML2APKBuilderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HTML2APKBuilderTheme {
                BuilderScreen()
            }
        }
    }
}

@Composable
fun BuilderScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isBuilding by remember { mutableStateOf(false) }

    // File picker (kontrak GetContent) — sesuai Level 1.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = resolveDisplayName(context, uri)
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "HTML2APK Builder",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Pilih file HTML sebagai sumber aplikasi.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    enabled = !isBuilding,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Pilih File HTML")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = selectedFileName ?: "Belum ada file dipilih",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        isBuilding = true
                        scope.launch {
                            try {
                                // Seluruh operasi berat (I/O) dijalankan di Dispatchers.IO.
                                val outputFile = withContext(Dispatchers.IO) {
                                    // 1. Salin template.apk dari assets ke cache.
                                    val templateFile = File(context.cacheDir, "template.apk")
                                    if (templateFile.exists()) templateFile.delete()
                                    context.assets.open("template.apk").use { input ->
                                        templateFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }

                                    // 2. Siapkan file output di DIRECTORY_DOWNLOADS (scoped storage).
                                    val outputDir = context.getExternalFilesDir(
                                        Environment.DIRECTORY_DOWNLOADS
                                    ) ?: throw IllegalStateException(
                                        "Direktori unduhan eksternal tidak tersedia"
                                    )
                                    outputDir.mkdirs()
                                    val outFile = File(outputDir, "output.apk")
                                    if (outFile.exists() && !outFile.delete()) {
                                        throw IllegalStateException(
                                            "Gagal menghapus output lama: ${outFile.absolutePath}"
                                        )
                                    }

                                    // 3. Salin HTML (via content URI) ke cache sebagai file nyata.
                                    val htmlFile = copyUriToCache(context, uri)

                                    // 4. Repack template + HTML user ke output.apk.
                                    Repacker.repack(templateFile, htmlFile, outFile)
                                    outFile
                                }

                                // Kembali di Main thread: matikan loading + Toast sukses.
                                isBuilding = false
                                val sizeKb = outputFile.length() / 1024
                                Toast.makeText(
                                    context,
                                    "Sukses! APK tersimpan di: ${outputFile.absolutePath} " +
                                        "(Ukuran: $sizeKb KB). Catatan: APK belum di-sign.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                // Handling error: matikan loading + Toast error di Main thread.
                                isBuilding = false
                                Toast.makeText(
                                    context,
                                    "Gagal build: ${e.message ?: e.javaClass.simpleName}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isBuilding && selectedUri != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Mulai Build")
                }
            }

            // Loading overlay terpusat saat proses build berjalan.
            if (isBuilding) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }
        }
    }
}

/**
 * Menyalin isi `content://` URI ke file cache lokal agar bisa dibaca
 * sebagai `File` oleh Repacker.
 */
private fun copyUriToCache(context: Context, uri: Uri): File {
    val cacheFile = File(context.cacheDir, "input.html")
    if (cacheFile.exists() && !cacheFile.delete()) {
        throw IllegalStateException("Gagal menghapus cache input lama")
    }

    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("Gagal membuka file HTML: $uri")

    inputStream.use { input ->
        cacheFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    return cacheFile
}

/**
 * Mengambil nama tampilan file dari Uri content provider.
 */
private fun resolveDisplayName(context: Context, uri: Uri): String {
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
