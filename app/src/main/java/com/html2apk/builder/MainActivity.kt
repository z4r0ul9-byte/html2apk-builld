package com.html2apk.builder

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.html2apk.builder.ui.theme.HTML2APKBuilderTheme

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

    // Menyimpan nama file yang dipilih pengguna.
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    // File picker memakai ActivityResultContracts.GetContent.
    // Filter "*/*" agar HTML maupun ZIP bisa dipilih.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileName = resolveDisplayName(context, uri)
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
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
                text = "Pilih file HTML atau ZIP sebagai sumber aplikasi.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Pilih File HTML/ZIP")
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
                    // Level 1: hanya Toast dummy, belum ada logika build.
                    Toast.makeText(
                        context,
                        "Fitur build akan diimplementasikan di Level 2",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                enabled = selectedFileName != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Mulai Build")
            }
        }
    }
}

/**
 * Mengambil nama tampilan file dari Uri content provider.
 * Fallback ke lastPathSegment jika nama tidak tersedia.
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
