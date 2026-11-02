package com.html2apk.builder.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.html2apk.builder.ui.BuilderViewModel

/**
 * BuilderScreen Level 7.
 * Perubahan dari Level 6:
 * - File picker menerima HTML dan ZIP (via OpenDocument karena mendukung MIME array).
 * - Label tombol diubah menjadi "Pilih File HTML / ZIP".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(
    viewModel: BuilderViewModel,
    onBuildComplete: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    // GetContent hanya menerima String; untuk multi-MIME gunakan OpenDocument (Array<String>).
    val sourceFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onFileSelected(uri)
    }

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onIconSelected(uri)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("HTML to APK Builder") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- Sumber ----------
            Text(
                text = "File Sumber",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.selectedFileName ?: "Belum ada file dipilih",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Dukung: .html tunggal, atau .zip yang berisi index.html + css/js/gambar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            // Level 7: Menerima HTML dan ZIP sekaligus.
                            sourceFilePicker.launch(
                                arrayOf(
                                    "text/html",
                                    "application/zip",
                                    "application/x-zip-compressed"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pilih File HTML / ZIP")
                    }
                }
            }

            // ---------- Ikon ----------
            Text(
                text = "Ikon Aplikasi (opsional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (state.selectedIconUri != null) {
                            "Ikon sudah dipilih"
                        } else {
                            "Belum ada ikon dipilih (pakai ikon default template)"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { iconPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pilih Ikon")
                    }
                }
            }

            // ---------- Build ----------
            Button(
                onClick = { viewModel.startBuild() },
                enabled = state.selectedFileUri != null && !state.isBuilding,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (state.isBuilding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Memproses...")
                } else {
                    Text("Mulai Build APK")
                }
            }

            if (state.isBuilding && state.progressMessage.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = state.progressMessage,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            state.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Error: $error",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            state.outputApkPath?.let { path ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Build berhasil!",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Lokasi: $path",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Button(
                    onClick = { onBuildComplete(path) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Bagikan APK")
                }
            }
        }
    }
}