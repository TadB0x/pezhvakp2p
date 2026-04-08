package com.pezhvak.p2p.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pezhvak", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.NewChat.route) }) {
                Icon(Icons.Default.Edit, "New message")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Mesh Status Card ──────────────────────────────────────────
            item {
                MeshStatusCard(uiState)
            }
            // ── Transport Status ──────────────────────────────────────────
            item {
                TransportStatusRow(uiState)
            }
            // ── Quick Actions ─────────────────────────────────────────────
            item {
                Text("Quick Actions", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionChip(
                        icon = Icons.Default.PersonAdd, label = "Add Contact",
                        onClick = { /* QR scanner */ }
                    )
                    QuickActionChip(
                        icon = Icons.Default.GroupAdd, label = "New Group",
                        onClick = { /* group creation */ }
                    )
                    QuickActionChip(
                        icon = Icons.Default.Share, label = "My ID",
                        onClick = { /* show QR */ }
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Hub, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Mesh Network", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        if (uiState.totalPeers > 0) "${uiState.totalPeers} peers connected"
                        else "Searching for peers…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.weight(1f))
                // Animated connectivity indicator
                MeshIndicator(peers = uiState.totalPeers)
            }
        }
    }
}

@Composable
fun MeshIndicator(peers: Int) {
    val color = when {
        peers >= 3 -> Color(0xFF4CAF50)
        peers > 0 -> Color(0xFFFF9800)
        else -> Color(0xFFFF5252)
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun TransportStatusRow(uiState: HomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransportBadge("Nostr", uiState.nostrRelaysConnected, Icons.Default.Cloud,
            modifier = Modifier.weight(1f))
        TransportBadge("BLE", uiState.bleDevices, Icons.Default.Bluetooth,
            modifier = Modifier.weight(1f))
        TransportBadge("WiFi P2P", if (uiState.wifiDirectConnected) 1 else 0,
            Icons.Default.Wifi, modifier = Modifier.weight(1f))
    }
}

@Composable
fun TransportBadge(
    name: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null,
                tint = if (count > 0) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(name, style = MaterialTheme.typography.labelSmall)
            Text(
                if (count > 0) "$count" else "–",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (count > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun QuickActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) }
    )
}
