package com.classchat.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.classchat.app.data.repository.ChatRepository
import com.classchat.app.data.repository.UserRepository
import com.classchat.app.model.Message
import com.classchat.app.model.PeerUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    val peerId: String,
    val peerName: String,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    val peerUser: StateFlow<PeerUser?> = userRepository.nearbyPeers
        .map { peers -> peers.find { it.userId == peerId } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            userRepository.getPeer(peerId)
        )

    init {
        viewModelScope.launch {
            val myProfile = userRepository.getProfile()
            chatRepository.getDirectConversation(peerId, myProfile.userId).collect { list ->
                _messages.value = list
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.trim().isEmpty()) return
        viewModelScope.launch {
            chatRepository.sendDirectMessage(peerId, peerName, text.trim())
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            chatRepository.retryMessage(messageId)
        }
    }

    class Factory(
        private val peerId: String,
        private val peerName: String,
        private val chatRepository: ChatRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(peerId, peerName, chatRepository, userRepository) as T
        }
    }
}
