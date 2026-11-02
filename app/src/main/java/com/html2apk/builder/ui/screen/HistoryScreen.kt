package com.html2apk.builder.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.html2apk.builder.ui.BuilderViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PERBAIKAN: Mengembalikan parameter menjadi viewModel (sesuai pemanggilan di MainActivity.kt).
 */
@Composable
fun HistoryScreen(
    viewModel: BuilderViewModel
) {
    // Mengambil data dari historyFlow yang ada di ViewModel
    val historyList by viewModel.historyFlow.collectAsState(initial = emptyList())
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Riwayat Build",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (historyList.isEmpty()) {
            Text(text = "Belum ada riwayat build.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(historyList) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = item.appName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = item.packageName,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Path: ${item.apkPath}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Status: ${item.status}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            // Jika Anda ingin menampilkan timestamp, tambahkan baris di bawah ini:
                            // Text(text = "Waktu: ${dateFormat.format(Date(item.timestamp))}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}