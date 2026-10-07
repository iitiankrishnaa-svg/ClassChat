package com.classchat.app.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object NameSetup : Screen("name_setup")
    object Permissions : Screen("permissions")
    object Main : Screen("main")
    object GroupChat : Screen("group_chat")
    object Settings : Screen("settings")

    object DirectChat : Screen("direct_chat/{peerId}/{peerName}") {
        fun createRoute(peerId: String, peerName: String): String {
            val encodedPeerId = URLEncoder.encode(peerId, StandardCharsets.UTF_8.name())
            val encodedName = URLEncoder.encode(peerName, StandardCharsets.UTF_8.name())
            return "direct_chat/$encodedPeerId/$encodedName"
        }

        fun decodeArgs(encodedId: String, encodedName: String): Pair<String, String> {
            val id = try {
                URLDecoder.decode(encodedId, StandardCharsets.UTF_8.name())
            } catch (e: Exception) {
                encodedId
            }
            val name = try {
                URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
            } catch (e: Exception) {
                encodedName
            }
            return Pair(id, name)
        }
    }
}
