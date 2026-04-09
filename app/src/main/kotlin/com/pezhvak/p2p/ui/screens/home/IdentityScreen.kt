package com.pezhvak.p2p.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.pezhvak.p2p.core.identity.KeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Composable
fun IdentityScreen(
    onBack: () -> Unit,
    viewModel: IdentityViewModel = hiltViewModel(),
) {
    val identity = viewModel.identity
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var showNsecDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                title = { Text("My Identity", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Avatar circle with initial
            Surface(
                modifier = Modifier.size(96.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        identity.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(identity.displayName, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)

            // QR placeholder (ASCII grid stand-in)
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.QrCode2, null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "npub1" + identity.pubKeyHex.take(12) + "…",
                        fontSize = 10.sp,
                        color = Color.Black,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Pubkey display + copy
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Public Key", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        identity.pubKeyHex,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, "npub", "npub1${identity.pubKeyHex}")
                                copied = true
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (copied) "Copied!" else "Copy npub")
                        }
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, "pubkey", identity.pubKeyHex)
                                copied = true
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy hex")
                        }
                    }
                }
            }

            // Backup private key
            OutlinedButton(
                onClick = { showNsecDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Key, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Backup Private Key (nsec)")
            }
        }
    }

    if (showNsecDialog) {
        AlertDialog(
            onDismissRequest = { showNsecDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("⚠ Private Key") },
            text = {
                Column {
                    Text("Your private key controls your identity forever. Never share it. Store it encrypted offline.")
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "nsec1${viewModel.getPrivKeyHex()}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        copyToClipboard(context, "nsec", "nsec1${viewModel.getPrivKeyHex()}")
                        showNsecDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Copy & Close") }
            },
            dismissButton = {
                TextButton(onClick = { showNsecDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

@HiltViewModel
class IdentityViewModel @Inject constructor(private val keyManager: KeyManager) : ViewModel() {
    val identity get() = keyManager.getOrCreateIdentity()
    fun getPrivKeyHex() = keyManager.getPrivateKeyHex()
}
