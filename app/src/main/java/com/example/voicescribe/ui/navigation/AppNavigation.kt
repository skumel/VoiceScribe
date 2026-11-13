package com.example.voicescribe.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.voicescribe.ui.history.HistoryScreen
import com.example.voicescribe.ui.history.HistoryViewModel
import com.example.voicescribe.ui.record.RecordScreen
import com.example.voicescribe.ui.record.RecordViewModel
import com.example.voicescribe.ui.settings.SettingsScreen
import com.example.voicescribe.ui.settings.SettingsViewModel

enum class AppTab(val title: String, val icon: ImageVector) {
    RECORD("Record", Icons.Default.Mic),
    HISTORY("History", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation(
    recordViewModel: RecordViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(AppTab.RECORD) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab }
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)

        when (selectedTab) {
            AppTab.RECORD -> {
                RecordScreen(
                    viewModel = recordViewModel,
                    onNavigateToHistory = { selectedTab = AppTab.HISTORY },
                    modifier = modifier
                )
            }
            AppTab.HISTORY -> {
                HistoryScreen(
                    viewModel = historyViewModel,
                    modifier = modifier
                )
            }
            AppTab.SETTINGS -> {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    modifier = modifier
                )
            }
        }
    }
}