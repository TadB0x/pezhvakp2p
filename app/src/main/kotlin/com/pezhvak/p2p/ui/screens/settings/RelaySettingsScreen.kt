package com.pezhvak.p2p.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pezhvak.p2p.transport.nostr.NostrClient
import com.pezhvak.p2p.transport.nostr.NostrRelayManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun RelaySettingsScreen(
    onBack: () -> Unit,
    viewModel: RelaySettingsViewModel = hiltViewModel(),
) {
    val relays by viewModel.relays.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newRelayUrl by remember { mutableStateOf("wss://") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                title = { Text("Nostr Relays", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add relay")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Active Relays", fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text("${relays.size} relays — messages are sent to all connected relays simultaneously")
                    },
                    leadingContent = { Icon(Icons.Default.Cloud, null) }
                )
                HorizontalDivider()
            }
            items(relays, key = { it }) { url ->
                val state = statuses[url]
                val (tint, label) = when (state) {
                    NostrClient.ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary to "Connected"
                    NostrClient.ConnectionState.CONNECTING,
                    NostrClient.ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.tertiary to "Connecting…"
                    else -> MaterialTheme.colorScheme.outline to "Disconnected"
                }
                ListItem(
                    headlineContent = { Text(url.removePrefix("wss://").removePrefix("ws://")) },
                    supportingContent = { Text(label, color = tint) },
                    leadingContent = {
                        Icon(Icons.Default.Circle, null,
                            tint = tint,
                            modifier = Modifier.size(12.dp))
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeRelay(url) }) {
                            Icon(Icons.Default.Delete, "Remove",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            }
            item {
                if (relays.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CloudOff, null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("No relays added")
                            Text("Tap + to add a relay", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; newRelayUrl = "wss://" },
            title = { Text("Add Relay") },
            text = {
                OutlinedTextField(
                    value = newRelayUrl,
                    onValueChange = { newRelayUrl = it },
                    label = { Text("WebSocket URL") },
                    placeholder = { Text("wss://relay.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRelayUrl.startsWith("wss://") || newRelayUrl.startsWith("ws://")) {
                            viewModel.addRelay(newRelayUrl.trim())
                            showAddDialog = false
                            newRelayUrl = "wss://"
                        }
                    },
                    enabled = newRelayUrl.length > 6,
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; newRelayUrl = "wss://" }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@HiltViewModel
class RelaySettingsViewModel @Inject constructor(
    private val nostrRelayManager: NostrRelayManager,
) : ViewModel() {

    // Directly mirror the persisted relay list from the manager
    val relays: StateFlow<List<String>> = nostrRelayManager.relayUrls
    val statuses = nostrRelayManager.relayStatuses

    fun addRelay(url: String) {
        nostrRelayManager.addRelay(url)
    }

    fun removeRelay(url: String) {
        nostrRelayManager.removeRelay(url)
    }

    fun clearAll() {
        relays.value.forEach { nostrRelayManager.removeRelay(it) }
    }
}
