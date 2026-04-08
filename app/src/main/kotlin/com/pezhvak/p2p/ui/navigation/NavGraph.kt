package com.pezhvak.p2p.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pezhvak.p2p.ui.screens.calls.CallScreen
import com.pezhvak.p2p.ui.screens.channels.ChannelListScreen
import com.pezhvak.p2p.ui.screens.channels.ChannelScreen
import com.pezhvak.p2p.ui.screens.chat.ChatScreen
import com.pezhvak.p2p.ui.screens.chat.ConversationListScreen
import com.pezhvak.p2p.ui.screens.forums.ForumListScreen
import com.pezhvak.p2p.ui.screens.forums.ForumThreadScreen
import com.pezhvak.p2p.ui.screens.home.HomeScreen
import com.pezhvak.p2p.ui.screens.news.NewsScreen
import com.pezhvak.p2p.ui.screens.onboarding.OnboardingScreen
import com.pezhvak.p2p.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Conversations : Screen("conversations")
    object Chat : Screen("chat/{conversationId}") {
        fun route(id: String) = "chat/$id"
    }
    object NewChat : Screen("new_chat")
    object Channels : Screen("channels")
    object Channel : Screen("channel/{channelId}") {
        fun route(id: String) = "channel/$id"
    }
    object Forums : Screen("forums")
    object ForumThread : Screen("forum_thread/{threadId}") {
        fun route(id: String) = "forum_thread/$id"
    }
    object News : Screen("news")
    object Call : Screen("call/{peerId}?video={hasVideo}") {
        fun route(peerId: String, hasVideo: Boolean = false) = "call/$peerId?video=$hasVideo"
    }
    object Settings : Screen("settings")
    object Profile : Screen("profile/{pubKey}") {
        fun route(pubKey: String) = "profile/$pubKey"
    }
}

@Composable
fun PezhvakNavHost(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(onComplete = { navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Onboarding.route) { inclusive = true }
            }})
        }
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.Conversations.route) {
            ConversationListScreen(
                onConversationClick = { id -> navController.navigate(Screen.Chat.route(id)) },
                onNewChat = { navController.navigate(Screen.NewChat.route) }
            )
        }
        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId = id,
                onBack = { navController.popBackStack() },
                onCallClick = { hasVideo -> navController.navigate(Screen.Call.route(id, hasVideo)) }
            )
        }
        composable(Screen.Channels.route) {
            ChannelListScreen(
                onChannelClick = { id -> navController.navigate(Screen.Channel.route(id)) }
            )
        }
        composable(
            route = Screen.Channel.route,
            arguments = listOf(navArgument("channelId") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("channelId") ?: return@composable
            ChannelScreen(channelId = id, onBack = { navController.popBackStack() })
        }
        composable(Screen.Forums.route) {
            ForumListScreen(
                onThreadClick = { id -> navController.navigate(Screen.ForumThread.route(id)) }
            )
        }
        composable(
            route = Screen.ForumThread.route,
            arguments = listOf(navArgument("threadId") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("threadId") ?: return@composable
            ForumThreadScreen(threadId = id, onBack = { navController.popBackStack() })
        }
        composable(Screen.News.route) {
            NewsScreen()
        }
        composable(
            route = Screen.Call.route,
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType },
                navArgument("hasVideo") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStack ->
            val peerId = backStack.arguments?.getString("peerId") ?: return@composable
            val hasVideo = backStack.arguments?.getBoolean("hasVideo") ?: false
            CallScreen(
                peerId = peerId,
                hasVideo = hasVideo,
                onCallEnd = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
