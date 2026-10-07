package com.classchat.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.classchat.app.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "classchat_prefs")

class PreferencesManager(private val context: Context) {

    private object PreferencesKeys {
        val USER_ID = stringPreferencesKey("user_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val userProfileFlow: Flow<UserProfile> = context.dataStore.data.map { preferences ->
        val rawUserId = preferences[PreferencesKeys.USER_ID]
        val userId = if (rawUserId != null) {
            normalizeToCanonicalId(rawUserId)
        } else {
            generateAndSaveNewUserId()
        }
        val displayName = preferences[PreferencesKeys.DISPLAY_NAME] ?: "Student"
        UserProfile(userId = userId, displayName = displayName)
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
    }

    suspend fun getUserProfile(): UserProfile {
        return userProfileFlow.first()
    }

    suspend fun saveUserProfile(displayName: String): UserProfile {
        val currentProfile = context.dataStore.data.first()
        val rawUserId = currentProfile[PreferencesKeys.USER_ID]
        val userId = if (rawUserId != null) {
            normalizeToCanonicalId(rawUserId)
        } else {
            val randomBits = (UUID.randomUUID().mostSignificantBits and 0xFFFFFFFFL)
            String.format("%08X", randomBits)
        }

        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_ID] = userId
            preferences[PreferencesKeys.DISPLAY_NAME] = displayName.trim()
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = true
        }
        return UserProfile(userId = userId, displayName = displayName.trim())
    }

    suspend fun updateDisplayName(newName: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISPLAY_NAME] = newName.trim()
        }
    }

    private suspend fun generateAndSaveNewUserId(): String {
        val randomBits = (UUID.randomUUID().mostSignificantBits and 0xFFFFFFFFL)
        val newId = String.format("%08X", randomBits)
        context.dataStore.edit { preferences ->
            if (!preferences.contains(PreferencesKeys.USER_ID)) {
                preferences[PreferencesKeys.USER_ID] = newId
            }
        }
        return newId
    }

    companion object {
        fun normalizeToCanonicalId(rawId: String): String {
            val clean = rawId.trim()
            return if (clean.length == 8 && clean.all { it in "0123456789ABCDEFabcdef" }) {
                clean.uppercase()
            } else {
                String.format("%08X", (clean.hashCode().toLong() and 0xFFFFFFFFL))
            }
        }
    }
}
