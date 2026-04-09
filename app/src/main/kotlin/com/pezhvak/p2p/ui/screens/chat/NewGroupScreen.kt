package com.pezhvak.p2p.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.pezhvak.p2p.core.db.dao.ContactDao
import com.pezhvak.p2p.core.db.dao.ConversationDao
import com.pezhvak.p2p.core.db.entities.ConversationEntity
import com.pezhvak.p2p.core.db.entities.ConversationType
import com.pezhvak.p2p.core.identity.KeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun NewGroupScreen(
    onBack: () -> Unit,
    onGroupCreated: (groupId: String) -> Unit,
    viewModel: NewGroupViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsState()
    var groupName by remember { mutableStateOf("") }
    val selectedPubKeys = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                title = { Text("New Group", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.createGroup(groupName, selectedPubKeys.toList()) { id ->
                                onGroupCreated(id)
                            }
                        },
                        enabled = groupName.isNotBlank() && selectedPubKeys.isNotEmpty(),
                    ) { Text("Create") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    leadingIcon = { Icon(Icons.Default.Group, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                Text("Add Members (${selectedPubKeys.size} selected)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (contacts.isEmpty()) {
                item {
                    Text("No contacts yet. Start a DM first to add contacts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp))
                }
            } else {
                items(contacts, key = { it.pubKeyHex }) { contact ->
                    val selected = contact.pubKeyHex in selectedPubKeys
                    ListItem(
                        headlineContent = { Text(contact.displayName) },
                        supportingContent = { Text(contact.pubKeyHex.take(16) + "…") },
                        leadingContent = {
                            Checkbox(checked = selected, onCheckedChange = {
                                if (selected) selectedPubKeys.remove(contact.pubKeyHex)
                                else selectedPubKeys.add(contact.pubKeyHex)
                            })
                        },
                        modifier = Modifier.clickable {
                            if (selected) selectedPubKeys.remove(contact.pubKeyHex)
                            else selectedPubKeys.add(contact.pubKeyHex)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
            }
        }
    }
}

@HiltViewModel
class NewGroupViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val conversationDao: ConversationDao,
    private val keyManager: KeyManager,
) : ViewModel() {

    val contacts = contactDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun createGroup(name: String, memberPubKeys: List<String>, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val groupId = sha256((name + System.currentTimeMillis()).toByteArray()).toHexString().take(32)
            val myKey = keyManager.getOrCreateIdentity().pubKeyHex
            val allMembers = (memberPubKeys + myKey).distinct()
            val membersJson = buildString {
                append("[")
                allMembers.forEachIndexed { i, key ->
                    if (i > 0) append(",")
                    append("\"$key\"")
                }
                append("]")
            }
            conversationDao.upsert(ConversationEntity(
                id = groupId,
                type = ConversationType.GROUP,
                name = name,
                description = null,
                avatarUrl = null,
                participantPubKeys = membersJson,
                encryptedGroupKey = null,
                creatorPubKey = myKey,
                createdAt = System.currentTimeMillis(),
                lastMessageAt = System.currentTimeMillis(),
                lastMessagePreview = null,
                unreadCount = 0,
                isMuted = false,
                isPinned = false,
                isArchived = false,
                nostrRelayHints = null,
            ))
            onCreated(groupId)
        }
    }
}
