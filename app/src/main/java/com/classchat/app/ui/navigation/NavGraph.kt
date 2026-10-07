package com.classchat.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.classchat.app.ClassChatApplication
import com.classchat.app.service.ClassChatService
import com.classchat.app.ui.screens.chat.ChatScreen
import com.classchat.app.ui.screens.chat.ChatViewModel
import com.classchat.app.ui.screens.group.GroupChatScreen
import com.classchat.app.ui.screens.group.GroupChatViewModel
import com.classchat.app.ui.screens.main.MainScreen
import com.classchat.app.ui.screens.main.MainViewModel
import com.classchat.app.ui.screens.onboarding.NameSetupScreen
import com.classchat.app.ui.screens.onboarding.PermissionScreen
import com.classchat.app.ui.screens.settings.SettingsScreen
import com.classchat.app.ui.screens.settings.SettingsViewModel
import com.classchat.app.ui.screens.splash.SplashScreen
import kotlinx.coroutines.launch

@Composable
fun NavGraph(
    navController: NavHostController,
    onRequestEnableBluetooth: () -> Unit,
    startDestination: String = Screen.Splash.route,
    pendingRoute: String? = null,
    onRouteHandled: () -> Unit = {}
) {
    val app = ClassChatApplication.instance
    val userRepo = app.userRepository
    val chatRepo = app.chatRepository
    val scope = rememberCoroutineScope()

    var pendingDisplayName by remember { mutableStateOf("") }
    var activePendingRoute by remember { mutableStateOf(pendingRoute) }

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != null &&
                currentRoute != Screen.Splash.route &&
                currentRoute != Screen.NameSetup.route &&
                currentRoute != Screen.Permissions.route
            ) {
                navController.navigate(pendingRoute) {
                    popUpTo(Screen.Main.route) { inclusive = false }
                    launchSingleTop = true
                }
                onRouteHandled()
            } else {
                activePendingRoute = pendingRoute
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Splash Screen
        composable(Screen.Splash.route) {
            SplashScreen(
                userRepository = userRepo,
                onNavigateToMain = {
                    ClassChatService.startService(app)
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                    activePendingRoute?.let { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Main.route) { inclusive = false }
                            launchSingleTop = true
                        }
                        activePendingRoute = null
                        onRouteHandled()
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(Screen.NameSetup.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding: Name Setup
        composable(Screen.NameSetup.route) {
            NameSetupScreen(
                onNameSubmitted = { name ->
                    pendingDisplayName = name
                    navController.navigate(Screen.Permissions.route)
                }
            )
        }

        // Onboarding: Permissions & Bluetooth Enable
        composable(Screen.Permissions.route) {
            PermissionScreen(
                onPermissionsAndBluetoothReady = {
                    scope.launch {
                        userRepo.completeOnboarding(
                            if (pendingDisplayName.isNotEmpty()) pendingDisplayName else "Student"
                        )
                        ClassChatService.startService(app)
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.NameSetup.route) { inclusive = true }
                        }
                        activePendingRoute?.let { route ->
                            navController.navigate(route) {
                                popUpTo(Screen.Main.route) { inclusive = false }
                                launchSingleTop = true
                            }
                            activePendingRoute = null
                            onRouteHandled()
                        }
                    }
                }
            )
        }

        // Main Classroom Screen
        composable(Screen.Main.route) {
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(userRepo, chatRepo)
            )
            MainScreen(
                viewModel = mainViewModel,
                onNavigateToDirectChat = { peerId, peerName ->
                    navController.navigate(Screen.DirectChat.createRoute(peerId, peerName))
                },
                onNavigateToGroupChat = {
                    navController.navigate(Screen.GroupChat.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onRequestEnableBluetooth = onRequestEnableBluetooth
            )
        }

        // 1-to-1 Chat Screen
        composable(
            route = Screen.DirectChat.route,
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val rawPeerId = backStackEntry.arguments?.getString("peerId") ?: ""
            val rawPeerName = backStackEntry.arguments?.getString("peerName") ?: "Student"
            val (peerId, peerName) = Screen.DirectChat.decodeArgs(rawPeerId, rawPeerName)

            val chatViewModel: ChatViewModel = viewModel(
                key = "chat_$peerId",
                factory = ChatViewModel.Factory(peerId, peerName, chatRepo, userRepo)
            )

            ChatScreen(
                viewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Class Group Broadcast Chat Screen
        composable(Screen.GroupChat.route) {
            val groupViewModel: GroupChatViewModel = viewModel(
                factory = GroupChatViewModel.Factory(chatRepo, userRepo)
            )

            GroupChatScreen(
                viewModel = groupViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Settings Screen
        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(userRepo, chatRepo)
            )

            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
