package dev.tomerklein.dradis.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private enum class Tab(val label: String) { STATUS("Status"), SETTINGS("Settings"), LOGS("Logs") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var tab by remember { mutableStateOf(Tab.STATUS) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("DRADIS") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.STATUS,
                    onClick = { tab = Tab.STATUS },
                    icon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                    label = { Text(Tab.STATUS.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.SETTINGS,
                    onClick = { tab = Tab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(Tab.SETTINGS.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.LOGS,
                    onClick = { tab = Tab.LOGS },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(Tab.LOGS.label) },
                )
            }
        },
    ) { inner ->
        val modifier = Modifier.padding(inner)
        when (tab) {
            Tab.STATUS -> StatusScreen(modifier)
            Tab.SETTINGS -> SettingsScreen(modifier)
            Tab.LOGS -> LogsScreen(modifier)
        }
    }
}
