package com.pezhvak.p2p.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pezhvak.p2p.core.identity.KeyManager
import com.pezhvak.p2p.ui.navigation.PezhvakNavHost
import com.pezhvak.p2p.ui.navigation.Screen
import com.pezhvak.p2p.ui.theme.PezhvakTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var keyManager: KeyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PezhvakTheme {
                PezhvakApp(hasIdentity = keyManager.hasIdentity)
            }
        }
    }
}

@Composable
fun PezhvakApp(hasIdentity: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (hasIdentity) Screen.Home.route else Screen.Onboarding.route
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    // Bottom nav routes
    val bottomNavRoutes = setOf(
        Screen.Home.route,
        Screen.Conversations.route,
        Screen.Channels.route,
        Screen.News.route,
        Screen.Forums.route,
    )
    val showBottomNav = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Hub, "Mesh") },
                        label = { Text("Mesh") },
                        selected = currentRoute == Screen.Home.route,
                        onClick = { navController.navigate(Screen.Home.route) }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Chat, "Chats") },
                        label = { Text("Chats") },
                        selected = currentRoute == Screen.Conversations.route,
                        onClick = { navController.navigate(Screen.Conversations.route) }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Campaign, "Channels") },
                        label = { Text("Channels") },
                        selected = currentRoute == Screen.Channels.route,
                        onClick = { navController.navigate(Screen.Channels.route) }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Forum, "Forums") },
                        label = { Text("Forums") },
                        selected = currentRoute == Screen.Forums.route,
                        onClick = { navController.navigate(Screen.Forums.route) }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Newspaper, "News") },
                        label = { Text("News") },
                        selected = currentRoute == Screen.News.route,
                        onClick = { navController.navigate(Screen.News.route) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            PezhvakNavHost(
                navController = navController,
                startDestination = startDestination,
            )
        }
    }
}
