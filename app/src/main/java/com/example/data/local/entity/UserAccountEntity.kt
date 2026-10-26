package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AccountProvider(val displayName: String) {
    GMAIL("Gmail (Google)"),
    OUTLOOK("Outlook (Microsoft)")
}

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey
    val email: String,
    val displayName: String,
    val provider: AccountProvider,
    val passwordHash: String = "",
    val profilePhotoUrl: String? = null,
    val isLoggedIn: Boolean = false,
    val lastLoginTimestamp: Long = System.currentTimeMillis(),
    val cloudSyncEnabled: Boolean = true,
    val cloudStorageTier: String = "Cloud Storage Sync Active (Free Tier)"
)
