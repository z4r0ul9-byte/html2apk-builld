package com.html2apk.builder.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.html2apk.builder.BuildConfig
import java.io.File

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val keystoreFile = File(context.cacheDir, "keystore.p12")
    val keystoreStatus = if (keystoreFile.exists()) {
        "Tersedia (${keystoreFile.length() / 1024} KB)"
    } else {
        "Belum dibuat — akan otomatis dibuat saat build pertama"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tentang Aplikasi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Nama: HTML2APK Builder")
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Versi: ${BuildConfig.VERSION_NAME}")
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Package: ${context.packageName}")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Keystore",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Status: $keystoreStatus")
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Lokasi: ${keystoreFile.absolutePath}")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aplikasi ini menggunakan keystore debug internal " +
                        "yang dibuat otomatis. APK hasil build dapat di-install " +
                        "untuk keperluan pengujian, namun tidak disarankan untuk " +
                        "distribusi produksi."
                )
            }
        }
    }
}
