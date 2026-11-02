package com.html2apk.builder

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.html2apk.builder.data.AppDatabase
import com.html2apk.builder.ui.BuilderViewModel
import com.html2apk.builder.ui.screen.BuilderScreen
import com.html2apk.builder.ui.screen.HistoryScreen
import com.html2apk.builder.ui.screen.SettingsScreen
import com.html2apk.builder.ui.theme.HTML2APKBuilderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HTML2APKBuilderTheme {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val db = AppDatabase.getDatabase(context)

    // PERBAIKAN: Menggunakan ViewModelProvider.Factory untuk menginjeksi historyDao
    val viewModel: BuilderViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BuilderViewModel(application, db.historyDao()) as T
            }
        }
    )

    var currentTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = {
                        Icon(Icons.Default.Home, contentDescription = "Builder")
                    },
                    label = { Text("Builder") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "Riwayat"
                        )
                    },
                    label = { Text("Riwayat") }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = {
                        Icon(Icons.Default.Settings, contentDescription = "Setelan")
                    },
                    label = { Text("Setelan") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> BuilderScreen(viewModel = viewModel)
                1 -> HistoryScreen(viewModel = viewModel)
                2 -> SettingsScreen()
            }
        }
    }
}