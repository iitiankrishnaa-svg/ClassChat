package com.classchat.app

import android.app.Application
import com.classchat.app.data.ble.BleManager
import com.classchat.app.data.local.AppDatabase
import com.classchat.app.data.local.PreferencesManager
import com.classchat.app.data.repository.ChatRepository
import com.classchat.app.data.repository.UserRepository

class ClassChatApplication : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var bleManager: BleManager
        private set

    lateinit var userRepository: UserRepository
        private set

    lateinit var chatRepository: ChatRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        preferencesManager = PreferencesManager(this)
        database = AppDatabase.getInstance(this)

        // Initialize Chat notification channel
        com.classchat.app.notification.ChatNotificationManager.init(this)

        bleManager = BleManager(
            context = this,
            onMessageReceived = { packet -> chatRepository.onPacketReceived(packet) },
            onAckReceived = { messageId -> chatRepository.onAckReceived(messageId) }
        )

        userRepository = UserRepository(preferencesManager, bleManager)
        chatRepository = ChatRepository(this, database.messageDao(), preferencesManager, bleManager)
    }

    override fun onTerminate() {
        super.onTerminate()
        bleManager.destroy()
    }

    companion object {
        lateinit var instance: ClassChatApplication
            private set
    }
}
