package com.pezhvak.p2p.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pezhvak.p2p.ui.screens.calls.CallScreen
import com.pezhvak.p2p.ui.screens.channels.ChannelListScreen
import com.pezhvak.p2p.ui.screens.channels.ChannelScreen
import com.pezhvak.p2p.ui.screens.channels.CreateChannelScreen
import com.pezhvak.p2p.ui.screens.chat.*
import com.pezhvak.p2p.ui.screens.forums.CreateThreadScreen
import com.pezhvak.p2p.ui.screens.forums.ForumListScreen
import com.pezhvak.p2p.ui.screens.forums.ForumThreadScreen
import com.pezhvak.p2p.ui.screens.home.HomeScreen
import com.pezhvak.p2p.ui.screens.home.IdentityScreen
import com.pezhvak.p2p.ui.screens.news.NewsScreen
import com.pezhvak.p2p.ui.screens.onboarding.OnboardingScreen
import com.pezhvak.p2p.ui.screens.settings.RelaySettingsScreen
import com.pezhvak.p2p.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Onboarding      : Screen("onboarding")
    object Home            : Screen("home")
    object Conversations   : Screen("conversations")
    object Chat            : Screen("chat/{conversationId}") {
        fun route(id: String) = "chat/${encode(id)}"
    }
    object NewChat         : Screen("new_chat")
    object NewGroup        : Screen("new_group")
    object Channels        : Screen("channels")
    object Channel         : Screen("channel/{channelId}") {
        fun route(id: String) = "channel/${encode(id)}"
    }
    object CreateChannel   : Screen("create_channel")
    object Forums          : Screen("forums")
    object ForumThread     : Screen("forum_thread/{threadId}") {
        fun route(id: String) = "forum_thread/${encode(id)}"
    }
    object CreateThread    : Screen("create_thread/{forumId}") {
        fun route(forumId: String = "global") = "create_thread/$forumId"
    }
    object News            : Screen("news")
    object Call            : Screen("call/{peerId}?video={hasVideo}") {
        fun route(peerId: String, hasVideo: Boolean = false) = "call/${encode(peerId)}?video=$hasVideo"
    }
    object Settings        : Screen("settings")
    object RelaySettings   : Screen("relay_settings")
    object Identity        : Screen("identity")

    companion object {
        // URL-encode slashes in IDs to avoid route conflicts
        fun encode(s: String) = s.replace("/", "%2F")
    }
}

@Composable
fun PezhvakNavHost(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(onComplete = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Home.route) {
            HomeScreen(
                navController = navController,
                onNewChat = { navController.navigate(Screen.NewChat.route) },
                onIdentity = { navController.navigate(Screen.Identity.route) },
            )
        }

        composable(Screen.Conversations.route) {
            ConversationListScreen(
                onConversationClick = { id -> navController.navigate(Screen.Chat.route(id)) },
                onNewChat = { navController.navigate(Screen.NewChat.route) },
            )
        }

        composable(Screen.NewChat.route) {
            NewChatScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { pubKey, name ->
                    // Ensure conversation exists, then navigate
                    navController.navigate(Screen.Chat.route(pubKey))
                },
                onNewGroup = { navController.navigate(Screen.NewGroup.route) },
            )
        }

        composable(Screen.NewGroup.route) {
            NewGroupScreen(
                onBack = { navController.popBackStack() },
                onGroupCreated = { id ->
                    navController.navigate(Screen.Chat.route(id)) {
                        popUpTo(Screen.NewGroup.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { back ->
            val id = back.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId = id,
                onBack = { navController.popBackStack() },
                onCallClick = { hasVideo -> navController.navigate(Screen.Call.route(id, hasVideo)) },
            )
        }

        composable(Screen.Channels.route) {
            ChannelListScreen(
                onChannelClick = { id -> navController.navigate(Screen.Channel.route(id)) },
                onCreateChannel = { navController.navigate(Screen.CreateChannel.route) },
            )
        }

        composable(Screen.CreateChannel.route) {
            CreateChannelScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.navigate(Screen.Channel.route(id)) {
                        popUpTo(Screen.CreateChannel.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Screen.Channel.route,
            arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
        ) { back ->
            val id = back.arguments?.getString("channelId") ?: return@composable
            ChannelScreen(channelId = id, onBack = { navController.popBackStack() })
        }

        composable(Screen.Forums.route) {
            ForumListScreen(
                onThreadClick = { id -> navController.navigate(Screen.ForumThread.route(id)) },
                onNewThread = { navController.navigate(Screen.CreateThread.route()) },
            )
        }

        composable(
            route = Screen.CreateThread.route,
            arguments = listOf(navArgument("forumId") { type = NavType.StringType }),
        ) { back ->
            val forumId = back.arguments?.getString("forumId") ?: "global"
            CreateThreadScreen(
                forumId = forumId,
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.navigate(Screen.ForumThread.route(id)) {
                        popUpTo(Screen.CreateThread.route()) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Screen.ForumThread.route,
            arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
        ) { back ->
            val id = back.arguments?.getString("threadId") ?: return@composable
            ForumThreadScreen(threadId = id, onBack = { navController.popBackStack() })
        }

        composable(Screen.News.route) { NewsScreen() }

        composable(
            route = Screen.Call.route,
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType },
                navArgument("hasVideo") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { back ->
            val peerId = back.arguments?.getString("peerId") ?: return@composable
            val hasVideo = back.arguments?.getBoolean("hasVideo") ?: false
            CallScreen(
                peerId = peerId,
                hasVideo = hasVideo,
                onCallEnd = { navController.popBackStack() },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onRelaySettings = { navController.navigate(Screen.RelaySettings.route) },
                onIdentity = { navController.navigate(Screen.Identity.route) },
            )
        }

        composable(Screen.RelaySettings.route) {
            RelaySettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Identity.route) {
            IdentityScreen(onBack = { navController.popBackStack() })
        }
    }
}
