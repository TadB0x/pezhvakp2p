package com.pezhvak.p2p.ui.screens.chat

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pezhvak.p2p.core.db.entities.ContactEntity

@Composable
fun NewChatScreen(
    onBack: () -> Unit,
    onOpenChat: (conversationId: String, name: String) -> Unit,
    onNewGroup: () -> Unit,
    viewModel: NewChatViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    var pubKeyInput by remember { mutableStateOf("") }
    var showPasteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                title = { Text("New Message", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNewGroup) {
                        Icon(Icons.Default.GroupAdd, "New Group")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Direct pubkey entry
            OutlinedTextField(
                value = pubKeyInput,
                onValueChange = { pubKeyInput = it.trim() },
                label = { Text("Enter pubkey or npub…") },
                leadingIcon = { Icon(Icons.Default.Key, null) },
                trailingIcon = {
                    if (pubKeyInput.length >= 16) {
                        IconButton(onClick = {
                            val id = if (pubKeyInput.startsWith("npub1"))
                                pubKeyInput.drop(5) else pubKeyInput
                            onOpenChat(id, id.take(12) + "…")
                        }) { Icon(Icons.Default.ArrowForward, "Start chat") }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )

            if (contacts.isEmpty()) {
                // No contacts yet – show paste/scan options
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Default.PersonSearch, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No contacts yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Paste someone's public key above to start chatting, or connect with nearby peers over Bluetooth.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "Contacts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn {
                    items(contacts, key = { it.pubKeyHex }) { contact ->
                        ContactRow(contact, onClick = {
                            onOpenChat(contact.pubKeyHex, contact.displayName)
                        })
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ContactRow(contact: ContactEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(contact.petname ?: contact.displayName, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(contact.pubKeyHex.take(16) + "…") },
        leadingContent = {
            Surface(
                modifier = Modifier.size(44.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        (contact.petname ?: contact.displayName).take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        trailingContent = {
            if (contact.isTrusted) {
                Icon(Icons.Default.Verified, "Trusted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
