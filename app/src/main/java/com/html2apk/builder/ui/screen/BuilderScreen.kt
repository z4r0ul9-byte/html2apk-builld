package com.html2apk.builder.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.html2apk.builder.ui.BuilderViewModel

/**
 * BuilderScreen (Level 8).
 *
 * Struktur:
 *  1. Header "Langkah 1 / 10 - Info Aplikasi" + progress bar.
 *  2. Form Info Aplikasi (Nama, Paket, Versi, SDK, URL Scheme).
 *  3. Tombol "Berikutnya" (dummy).
 *  4. Divider.
 *  5. Section pemilihan file + ikon + tombol "Mulai Build"
 *     (dipertahankan agar aplikasi tetap fungsional untuk uji coba).
 */
@Composable
fun BuilderScreen(
    viewModel: BuilderViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onFileSelected(uri)
    }

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.onIconSelected(uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ─── Header Wizard ──────────────────────────────────────────
        Text(
            text = "Langkah 1 / 10 - Info",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        LinearProgressIndicator(
            progress = { 0.1f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Informasi Aplikasi",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Lengkapi metadata APK yang akan dibangun.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ─── Form Info Aplikasi ─────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Nama Aplikasi
                OutlinedTextField(
                    value = uiState.appName,
                    onValueChange = viewModel::onAppNameChanged,
                    label = { Text("Nama Aplikasi") },
                    supportingText = {
                        Text("Nama yang akan tampil di launcher perangkat.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Nama Paket
                OutlinedTextField(
                    value = uiState.packageName,
                    onValueChange = viewModel::onPackageNameChanged,
                    label = { Text("Nama Paket") },
                    supportingText = {
                        Text("Format: com.example.app (harus unik).")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Kode Versi + Nama Versi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.versionCode.toString(),
                        onValueChange = viewModel::onVersionCodeChanged,
                        label = { Text("Kode Versi") },
                        supportingText = { Text("Integer ≥ 1") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.versionName,
                        onValueChange = viewModel::onVersionNameChanged,
                        label = { Text("Nama Versi") },
                        supportingText = { Text("Mis. 1.0.0") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Auto-increment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.autoIncrement,
                        onCheckedChange = viewModel::onAutoIncrementChanged
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-increment kode versi",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Kode versi naik otomatis di setiap build sukses.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Min SDK + Target SDK
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.minSdk.toString(),
                        onValueChange = viewModel::onMinSdkChanged,
                        label = { Text("SDK Minimum") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.targetSdk.toString(),
                        onValueChange = viewModel::onTargetSdkChanged,
                        label = { Text("Target SDK") },
                        supportingText = { Text("Default: 34") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Max SDK (opsional)
                OutlinedTextField(
                    value = uiState.maxSdk?.toString() ?: "",
                    onValueChange = viewModel::onMaxSdkChanged,
                    label = { Text("SDK Maksimum (opsional)") },
                    supportingText = { Text("Kosongkan jika tidak dibatasi.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // URL Scheme
                OutlinedTextField(
                    value = uiState.urlScheme ?: "",
                    onValueChange = viewModel::onUrlSchemeChanged,
                    label = { Text("Skema URL Kustom (opsional)") },
                    supportingText = {
                        Text("Mis. myapp → deep link myapp://...")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ─── Tombol "Berikutnya" (dummy) ────────────────────────────
        Button(
            onClick = {
                Toast.makeText(
                    context,
                    "Langkah berikutnya belum diimplementasikan",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Berikutnya")
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // ─── Section Build (dipertahankan) ─────────────────────────
        Text(
            text = "Sumber & Ikon",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("text/html", "application/zip")) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Pilih HTML/ZIP")
            }
            OutlinedButton(
                onClick = { iconPicker.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Pilih Ikon")
            }
        }

        if (uiState.selectedFileName != null) {
            Text(
                text = "File: ${uiState.selectedFileName}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (uiState.selectedIconUri != null) {
            Text(
                text = "Ikon: ${uiState.selectedIconUri?.lastPathSegment ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = viewModel::startBuild,
            enabled = !uiState.isBuilding && uiState.selectedFileUri != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uiState.isBuilding) "Memproses..." else "Mulai Build")
        }

        // ─── Status ─────────────────────────────────────────────────
        if (uiState.isBuilding) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(uiState.progressMessage, style = MaterialTheme.typography.bodySmall)
            }
        }

        uiState.errorMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Error: $msg",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        uiState.outputApkPath?.let { path ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Build sukses", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
