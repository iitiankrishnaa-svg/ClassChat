package com.classchat.app.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.classchat.app.data.repository.ChatRepository
import com.classchat.app.data.repository.UserRepository
import com.classchat.app.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupChatViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    val onlineCount: StateFlow<Int> = userRepository.nearbyPeers
        .map { list -> list.count { it.isOnline } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            chatRepository.getGroupMessages().collect { list ->
                _messages.value = list
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.trim().isEmpty()) return
        viewModelScope.launch {
            chatRepository.sendGroupMessage(text.trim())
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            chatRepository.retryMessage(messageId)
        }
    }

    class Factory(
        private val chatRepository: ChatRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GroupChatViewModel(chatRepository, userRepository) as T
        }
    }
}
