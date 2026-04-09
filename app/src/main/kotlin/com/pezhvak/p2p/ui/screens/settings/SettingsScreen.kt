package com.pezhvak.p2p.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRelaySettings: () -> Unit = {},
    onIdentity: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showExportKeyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                title = { Text("Settings", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Profile
            item { SettingsSection("Identity") }
            item {
                SettingItem(
                    icon = Icons.Default.Person,
                    title = "Display Name",
                    subtitle = state.displayName,
                    onClick = { /* edit dialog */ }
                )
            }
            item {
                SettingItem(
                    icon = Icons.Default.Key,
                    title = "My Public Key",
                    subtitle = state.pubKeyHex.take(16) + "…",
                    onClick = onIdentity,
                )
            }
            item {
                SettingItem(
                    icon = Icons.Default.Download,
                    title = "Backup Private Key",
                    subtitle = "Export your identity for backup",
                    onClick = { showExportKeyDialog = true },
                )
            }

            // Transport
            item { SettingsSection("Network") }
            item {
                SettingToggle(
                    icon = Icons.Default.Bluetooth,
                    title = "BLE Mesh",
                    subtitle = "Connect via Bluetooth mesh",
                    checked = state.bleEnabled,
                    onCheckedChange = { viewModel.setBleMeshEnabled(it) }
                )
            }
            item {
                SettingToggle(
                    icon = Icons.Default.Wifi,
                    title = "WiFi Direct",
                    subtitle = "Connect via WiFi P2P",
                    checked = state.wifiDirectEnabled,
                    onCheckedChange = { viewModel.setWifiDirectEnabled(it) }
                )
            }
            item {
                SettingItem(
                    icon = Icons.Default.Cloud,
                    title = "Nostr Relays",
                    subtitle = "${state.relayCount} relays configured",
                    onClick = onRelaySettings,
                )
            }

            // Privacy
            item { SettingsSection("Privacy & Security") }
            item {
                SettingToggle(
                    icon = Icons.Default.Visibility,
                    title = "Read Receipts",
                    subtitle = "Let others know when you've read their messages",
                    checked = state.readReceiptsEnabled,
                    onCheckedChange = { viewModel.setReadReceipts(it) }
                )
            }
            item {
                SettingToggle(
                    icon = Icons.Default.AccessTime,
                    title = "Online Status",
                    subtitle = "Show when you're active",
                    checked = state.showOnlineStatus,
                    onCheckedChange = { viewModel.setShowOnlineStatus(it) }
                )
            }
            item {
                SettingItem(
                    icon = Icons.Default.Block,
                    title = "Blocked Users",
                    subtitle = "${state.blockedCount} users blocked",
                    onClick = { /* blocked list */ }
                )
            }

            // App
            item { SettingsSection("App") }
            item {
                SettingToggle(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Theme",
                    subtitle = "Follow system setting",
                    checked = state.darkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )
            }
            item {
                SettingToggle(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Message and call notifications",
                    checked = state.notificationsEnabled,
                    onCheckedChange = { viewModel.setNotifications(it) }
                )
            }

            // About
            item { SettingsSection("About") }
            item {
                SettingItem(
                    icon = Icons.Default.Info,
                    title = "Pezhvak P2P",
                    subtitle = "Version 1.0.0 • Open Source",
                    onClick = { }
                )
            }
        }
    }

    if (showExportKeyDialog) {
        AlertDialog(
            onDismissRequest = { showExportKeyDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("Export Private Key") },
            text = {
                Text("Your private key controls your identity. Never share it. Store it safely offline.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.exportKey()
                        showExportKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { showExportKeyDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun SettingToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}
