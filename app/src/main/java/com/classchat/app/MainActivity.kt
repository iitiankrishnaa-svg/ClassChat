package com.classchat.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.classchat.app.notification.ChatNotificationManager
import com.classchat.app.ui.navigation.NavGraph
import com.classchat.app.ui.navigation.Screen
import com.classchat.app.ui.theme.ClassChatTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingRoute = mutableStateOf<String?>(null)

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Bluetooth enabled, restart BLE services if user is onboarded
            val app = ClassChatApplication.instance
            lifecycleScope.launch {
                val profile = app.userRepository.getProfile()
                app.userRepository.startBleServices(profile)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handled gracefully whether granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+ if not already granted
        checkNotificationPermission()

        // Handle navigation if launched from a notification
        handleNotificationIntent(intent)

        setContent {
            ClassChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        onRequestEnableBluetooth = {
                            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            bluetoothEnableLauncher.launch(intent)
                        },
                        pendingRoute = pendingRoute.value,
                        onRouteHandled = { pendingRoute.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val navigateTo = intent.getStringExtra(ChatNotificationManager.EXTRA_NAVIGATE_TO) ?: return
        val peerId = intent.getStringExtra(ChatNotificationManager.EXTRA_PEER_ID) ?: ""
        val peerName = intent.getStringExtra(ChatNotificationManager.EXTRA_PEER_NAME) ?: "Student"

        if (navigateTo == "direct_chat" && peerId.isNotEmpty()) {
            pendingRoute.value = Screen.DirectChat.createRoute(peerId, peerName)
        } else if (navigateTo == "group_chat") {
            pendingRoute.value = Screen.GroupChat.route
        }
    }

    override fun onResume() {
        super.onResume()
        ChatNotificationManager.isAppInForeground = true
        // If Bluetooth is active and user is onboarded, ensure services are running
        val app = ClassChatApplication.instance
        if (app.bleManager.isBluetoothEnabled()) {
            // BLE scanning/advertising stays running
        }
    }

    override fun onPause() {
        super.onPause()
        ChatNotificationManager.isAppInForeground = false
    }
}
