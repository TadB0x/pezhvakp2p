package com.pezhvak.p2p.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf("") }
    val pages = onboardingPages()

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        // Page content
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                    slideOutHorizontally { width -> -width } + fadeOut()
                }
            ) { page ->
                if (page < pages.size) {
                    OnboardingPage(pages[page])
                } else {
                    SetupProfilePage(
                        displayName = displayName,
                        onNameChange = { displayName = it }
                    )
                }
            }
        }

        // Dots + buttons
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Page dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size + 1) { i ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(if (i == currentPage) 24.dp else 8.dp, 8.dp)
                            .background(
                                if (i == currentPage)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            val isLastPage = currentPage == pages.size
            Button(
                onClick = {
                    if (isLastPage) {
                        if (displayName.isNotBlank()) {
                            viewModel.createIdentity(displayName)
                            onComplete()
                        }
                    } else {
                        currentPage++
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLastPage || displayName.isNotBlank(),
            ) {
                Text(if (isLastPage) "Get Started" else "Continue",
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            if (currentPage > 0) {
                TextButton(onClick = { currentPage-- }) {
                    Text("Back")
                }
            }
        }
    }
}

data class OnboardingPageData(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

fun onboardingPages() = listOf(
    OnboardingPageData(
        icon = Icons.Default.Hub,
        title = "Mesh Communications",
        description = "Connect with people even without internet. Pezhvak creates a mesh network using Bluetooth and WiFi – messages hop between devices to reach their destination.",
    ),
    OnboardingPageData(
        icon = Icons.Default.Lock,
        title = "Unbreakable Encryption",
        description = "Every message is end-to-end encrypted using state-of-the-art cryptography. Your private key never leaves your device. Not even we can read your messages.",
    ),
    OnboardingPageData(
        icon = Icons.Default.Verified,
        title = "Anti-Tamper News",
        description = "News items are cryptographically signed. Any attempt to alter content is instantly detectable – fake news cannot spread through Pezhvak.",
    ),
    OnboardingPageData(
        icon = Icons.Default.WifiOff,
        title = "Works Without Internet",
        description = "Bluetooth mesh, WiFi Direct, and Nostr relays work together. As long as two devices are within range, communication flows.",
    ),
)

@Composable
fun OnboardingPage(page: OnboardingPageData) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(120.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(page.icon, null,
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(40.dp))
        Text(page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SetupProfilePage(displayName: String, onNameChange: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Person, null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))
        Text("What should we call you?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text("This name is shown to people you communicate with.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = onNameChange,
            label = { Text("Display Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, null) },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Your identity is generated locally. Your private key never leaves this device.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
