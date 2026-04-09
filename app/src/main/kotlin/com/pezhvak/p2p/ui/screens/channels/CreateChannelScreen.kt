package com.pezhvak.p2p.ui.screens.channels

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pezhvak.p2p.core.crypto.sha256
import com.pezhvak.p2p.core.crypto.toHexString
import com.pezhvak.p2p.core.db.dao.ChannelDao
import com.pezhvak.p2p.core.db.entities.ChannelEntity
import com.pezhvak.p2p.core.identity.KeyManager
import com.pezhvak.p2p.transport.nostr.NostrEvent
import com.pezhvak.p2p.transport.nostr.NostrKind
import com.pezhvak.p2p.transport.nostr.NostrRelayManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun CreateChannelScreen(
    onBack: () -> Unit,
    onCreated: (channelId: String) -> Unit,
    viewModel: CreateChannelViewModel = hiltViewModel(),
) {
    var name by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var isCreating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                title = { Text("Create Channel", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Channel Name") },
                leadingIcon = { Icon(Icons.Default.Campaign, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
            )
            ListItem(
                headlineContent = { Text("Public Channel") },
                supportingContent = { Text("Anyone can find and subscribe") },
                trailingContent = {
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        isCreating = true
                        viewModel.createChannel(name, about, isPublic) { id ->
                            onCreated(id)
                        }
                    }
                },
                enabled = name.isNotBlank() && !isCreating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Create Channel")
                }
            }
        }
    }
}

@HiltViewModel
class CreateChannelViewModel @Inject constructor(
    private val channelDao: ChannelDao,
    private val keyManager: KeyManager,
    private val nostrRelayManager: NostrRelayManager,
) : ViewModel() {

    fun createChannel(name: String, about: String, isPublic: Boolean, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val event = NostrEvent.build(
                privKeyHex = keyManager.getPrivateKeyHex(),
                kind = NostrKind.CHANNEL_CREATE,
                content = """{"name":"$name","about":"$about","isPublic":$isPublic}""",
            )
            nostrRelayManager.publish(event)
            channelDao.upsert(ChannelEntity(
                id = event.id,
                name = name,
                about = about.ifBlank { null },
                pictureUrl = null,
                creatorPubKey = keyManager.getOrCreateIdentity().pubKeyHex,
                isPublic = isPublic,
                nostrEventId = event.id,
                subscriberCount = 1,
                createdAt = System.currentTimeMillis(),
                isSubscribed = true,
                isOwned = true,
            ))
            onCreated(event.id)
        }
    }
}
