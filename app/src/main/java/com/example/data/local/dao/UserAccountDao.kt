package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.UserAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_accounts WHERE isLoggedIn = 1 LIMIT 1")
    fun getLoggedInAccountFlow(): Flow<UserAccountEntity?>

    @Query("SELECT * FROM user_accounts WHERE isLoggedIn = 1 LIMIT 1")
    suspend fun getLoggedInAccount(): UserAccountEntity?

    @Query("SELECT * FROM user_accounts WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getAccountByEmail(email: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAccount(account: UserAccountEntity)

    @Query("UPDATE user_accounts SET isLoggedIn = 0")
    suspend fun clearAllLoginStates()

    @Query("UPDATE user_accounts SET isLoggedIn = 1, lastLoginTimestamp = :timestamp WHERE LOWER(email) = LOWER(:email)")
    suspend fun setLoggedIn(email: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_accounts SET isLoggedIn = 0 WHERE LOWER(email) = LOWER(:email)")
    suspend fun logout(email: String)

    @Query("SELECT COUNT(*) FROM user_accounts WHERE isLoggedIn = 1")
    fun getLoggedInCount(): Flow<Int>
}
