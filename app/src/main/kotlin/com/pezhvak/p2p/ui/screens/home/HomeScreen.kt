package com.pezhvak.p2p.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pezhvak.p2p.ui.navigation.Screen

@Composable
fun HomeScreen(
    navController: NavController,
    onNewChat: () -> Unit,
    onIdentity: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pezhvak", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onIdentity) {
                        Icon(Icons.Default.QrCode2, "My Identity")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChat) {
                Icon(Icons.Default.Edit, "New message")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MeshStatusCard(uiState) }
            item { TransportStatusRow(uiState) }
            item {
                Text("Quick Actions", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { navController.navigate(Screen.NewChat.route) },
                        label = { Text("New DM") },
                        leadingIcon = { Icon(Icons.Default.Chat, null, Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { navController.navigate(Screen.NewGroup.route) },
                        label = { Text("New Group") },
                        leadingIcon = { Icon(Icons.Default.GroupAdd, null, Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = onIdentity,
                        label = { Text("My ID") },
                        leadingIcon = { Icon(Icons.Default.QrCode2, null, Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
fun MeshStatusCard(uiState: HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Hub, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Mesh Network", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    if (uiState.totalPeers > 0) "${uiState.totalPeers} peers connected"
                    else "Searching for peers…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.weight(1f))
            val dotColor = when {
                uiState.totalPeers >= 3 -> Color(0xFF4CAF50)
                uiState.totalPeers > 0  -> Color(0xFFFF9800)
                else                    -> Color(0xFFFF5252)
            }
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(dotColor))
        }
    }
}

@Composable
fun TransportStatusRow(uiState: HomeUiState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransportBadge("Nostr", uiState.nostrRelaysConnected, Icons.Default.Cloud, Modifier.weight(1f))
        TransportBadge("BLE", uiState.bleDevices, Icons.Default.Bluetooth, Modifier.weight(1f))
        TransportBadge("WiFi P2P", if (uiState.wifiDirectConnected) 1 else 0, Icons.Default.Wifi, Modifier.weight(1f))
    }
}

@Composable
fun TransportBadge(
    name: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null,
                tint = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(name, style = MaterialTheme.typography.labelSmall)
            Text(
                if (count > 0) "$count" else "–",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
