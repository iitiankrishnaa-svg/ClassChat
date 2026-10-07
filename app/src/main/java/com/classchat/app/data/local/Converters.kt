package com.classchat.app.data.local

import androidx.room.TypeConverter
import com.classchat.app.model.DeliveryStatus

class Converters {
    @TypeConverter
    fun fromDeliveryStatus(status: DeliveryStatus): String {
        return status.name
    }

    @TypeConverter
    fun toDeliveryStatus(value: String): DeliveryStatus {
        return try {
            DeliveryStatus.valueOf(value)
        } catch (e: Exception) {
            DeliveryStatus.PENDING
        }
    }
}
