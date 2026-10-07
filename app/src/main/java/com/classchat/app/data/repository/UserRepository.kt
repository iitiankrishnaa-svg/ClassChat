package com.classchat.app.data.repository

import com.classchat.app.data.ble.BleDiagnostics
import com.classchat.app.data.ble.BleManager
import com.classchat.app.data.local.PreferencesManager
import com.classchat.app.model.PeerUser
import com.classchat.app.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class UserRepository(
    private val preferencesManager: PreferencesManager,
    private val bleManager: BleManager
) {
    val userProfile: Flow<UserProfile> = preferencesManager.userProfileFlow
    val isOnboardingCompleted: Flow<Boolean> = preferencesManager.isOnboardingCompleted
    val nearbyPeers: StateFlow<List<PeerUser>> = bleManager.discoveredPeers
    val diagnostics: StateFlow<BleDiagnostics> = bleManager.diagnostics

    suspend fun getProfile(): UserProfile {
        return preferencesManager.getUserProfile()
    }

    suspend fun completeOnboarding(displayName: String): UserProfile {
        val profile = preferencesManager.saveUserProfile(displayName)
        bleManager.startServices(profile)
        return profile
    }

    suspend fun updateDisplayName(newName: String) {
        preferencesManager.updateDisplayName(newName)
        val updated = preferencesManager.getUserProfile()
        bleManager.updateProfile(updated)
    }

    fun startBleServices(profile: UserProfile) {
        bleManager.startServices(profile)
    }

    fun stopBleServices() {
        bleManager.stopServices()
    }

    fun isBluetoothEnabled(): Boolean {
        return bleManager.isBluetoothEnabled()
    }

    fun getPeer(peerId: String): PeerUser? {
        return nearbyPeers.value.find { it.userId == peerId }
    }
}
