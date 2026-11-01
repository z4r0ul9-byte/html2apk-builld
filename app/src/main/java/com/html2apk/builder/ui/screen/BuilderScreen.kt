package com.html2apk.builder.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.html2apk.builder.ui.BuilderViewModel

@Composable
fun BuilderScreen(viewModel: BuilderViewModel) {
    val context = LocalContext.current

    val htmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> viewModel.setHtmlUri(uri) }

    val iconLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> viewModel.setIconUri(uri) }

    // Tampilkan Toast saat buildResult berubah (sukses/gagal), lalu konsumsi.
    LaunchedEffect(viewModel.buildResult) {
        viewModel.buildResult?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.consumeBuildResult()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "HTML2APK Builder",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { htmlLauncher.launch("*/*") },
                enabled = !viewModel.isBuilding,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Pilih File HTML")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = viewModel.selectedHtmlName ?: "Belum ada file HTML dipilih",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.appName,
                onValueChange = { viewModel.appName = it },
                label = { Text("Nama Aplikasi") },
                placeholder = { Text("Aplikasi Keren") },
                singleLine = true,
                enabled = !viewModel.isBuilding,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = viewModel.packageName,
                onValueChange = { viewModel.packageName = it },
                label = { Text("Package Name") },
                placeholder = { Text("com.contoh.app") },
                singleLine = true,
                enabled = !viewModel.isBuilding,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { iconLauncher.launch("image/png") },
                enabled = !viewModel.isBuilding,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Pilih Ikon (PNG)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = viewModel.selectedIconName ?: "Belum ada ikon dipilih",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { viewModel.startBuild() },
                enabled = !viewModel.isBuilding && viewModel.selectedHtmlUri != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Mulai Build")
            }
        }

        if (viewModel.isBuilding) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
            )
        }
    }
}
