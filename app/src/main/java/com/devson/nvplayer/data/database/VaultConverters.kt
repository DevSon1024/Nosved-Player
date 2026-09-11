package com.devson.nvplayer.data.database

import androidx.room.TypeConverter
import com.devson.nvplayer.domain.model.VaultStorageMode

class VaultConverters {
    @TypeConverter
    fun fromVaultStorageMode(mode: VaultStorageMode?): String {
        return (mode ?: VaultStorageMode.ENCRYPTED).name
    }

    @TypeConverter
    fun toVaultStorageMode(value: String?): VaultStorageMode {
        return try {
            VaultStorageMode.valueOf(value ?: VaultStorageMode.ENCRYPTED.name)
        } catch (_: Exception) {
            VaultStorageMode.ENCRYPTED
        }
    }
}
