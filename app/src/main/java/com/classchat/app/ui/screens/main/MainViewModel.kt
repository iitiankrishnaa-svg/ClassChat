package com.classchat.app.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.classchat.app.data.ble.BleDiagnostics
import com.classchat.app.data.repository.ChatRepository
import com.classchat.app.data.repository.UserRepository
import com.classchat.app.model.Message
import com.classchat.app.model.PeerUser
import com.classchat.app.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = userRepository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nearbyStudents: StateFlow<List<PeerUser>> = userRepository.nearbyPeers
    val diagnostics: StateFlow<BleDiagnostics> = userRepository.diagnostics

    private val _recentChats = MutableStateFlow<List<Message>>(emptyList())
    val recentChats: StateFlow<List<Message>> = _recentChats.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.userProfile.collect { profile ->
                chatRepository.getRecentConversations(profile.userId).collect { chats ->
                    _recentChats.value = chats
                }
            }
        }
    }

    fun restartServices() {
        viewModelScope.launch {
            val profile = userRepository.getProfile()
            userRepository.startBleServices(profile)
        }
    }

    class Factory(
        private val userRepository: UserRepository,
        private val chatRepository: ChatRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(userRepository, chatRepository) as T
        }
    }
}
