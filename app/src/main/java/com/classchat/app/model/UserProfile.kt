package com.classchat.app.model

/**
 * Represents the local student profile.
 * Stored locally on device, never sent to external servers.
 */
data class UserProfile(
    val userId: String,
    val displayName: String,
    val shortId: String = userId.uppercase()
)
