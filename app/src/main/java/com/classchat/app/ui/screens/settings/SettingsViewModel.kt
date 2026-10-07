package com.classchat.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.classchat.app.data.ble.BleDiagnostics
import com.classchat.app.data.repository.ChatRepository
import com.classchat.app.data.repository.UserRepository
import com.classchat.app.model.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = userRepository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val diagnostics: StateFlow<BleDiagnostics> = userRepository.diagnostics

    fun updateName(newName: String) {
        if (newName.trim().isEmpty()) return
        viewModelScope.launch {
            userRepository.updateDisplayName(newName.trim())
        }
    }

    fun clearAllChats() {
        viewModelScope.launch {
            chatRepository.clearAllMessages()
        }
    }

    class Factory(
        private val userRepository: UserRepository,
        private val chatRepository: ChatRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(userRepository, chatRepository) as T
        }
    }
}
