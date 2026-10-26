package dev.tomerklein.dradis.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private enum class Tab(val label: String) { STATUS("Status"), SETTINGS("Settings"), LOGS("Logs"), TOPICS("Topics"), GALLERY("Gallery") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var tab by remember { mutableStateOf(Tab.STATUS) }
    var discovering by remember { mutableStateOf(false) }
    // About moved out of the bottom bar (Material allows at most five items) into the top bar.
    var about by remember { mutableStateOf(false) }
    fun select(t: Tab) { tab = t; discovering = false; about = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dradis") },
                actions = {
                    IconButton(onClick = { discovering = true; about = false }) {
                        Icon(Icons.Filled.Search, contentDescription = "Discover topics")
                    }
                    IconButton(onClick = { about = true; discovering = false }) {
                        Icon(Icons.Filled.Info, contentDescription = "About")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.STATUS,
                    onClick = { select(Tab.STATUS) },
                    icon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                    label = { Text(Tab.STATUS.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.SETTINGS,
                    onClick = { select(Tab.SETTINGS) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(Tab.SETTINGS.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.LOGS,
                    onClick = { select(Tab.LOGS) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(Tab.LOGS.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.TOPICS,
                    onClick = { select(Tab.TOPICS) },
                    icon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                    label = { Text(Tab.TOPICS.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.GALLERY,
                    onClick = { select(Tab.GALLERY) },
                    icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                    label = { Text(Tab.GALLERY.label) },
                )
            }
        },
    ) { inner ->
        val modifier = Modifier.padding(inner)
        if (discovering) {
            DiscoverScreen(onClose = { discovering = false }, modifier = modifier)
            return@Scaffold
        }
        if (about) {
            AboutScreen(modifier)
            return@Scaffold
        }
        when (tab) {
            Tab.STATUS -> StatusScreen(modifier)
            Tab.SETTINGS -> SettingsScreen(modifier)
            Tab.LOGS -> LogsScreen(modifier)
            Tab.GALLERY -> GalleryScreen(modifier)
            Tab.TOPICS -> TopicsScreen(modifier)
        }
    }
}
