package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.CreditNoteStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.ReceiptStatus
import com.example.data.model.SyncStatus

class Converters {
    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod): String = value.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod =
        try { PaymentMethod.valueOf(value) } catch (e: Exception) { PaymentMethod.CASH }

    @TypeConverter
    fun fromReceiptStatus(value: ReceiptStatus): String = value.name

    @TypeConverter
    fun toReceiptStatus(value: String): ReceiptStatus =
        try { ReceiptStatus.valueOf(value) } catch (e: Exception) { ReceiptStatus.PAID }

    @TypeConverter
    fun fromCreditNoteStatus(value: CreditNoteStatus): String = value.name

    @TypeConverter
    fun toCreditNoteStatus(value: String): CreditNoteStatus =
        try { CreditNoteStatus.valueOf(value) } catch (e: Exception) { CreditNoteStatus.ACTIVE }

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus =
        try { SyncStatus.valueOf(value) } catch (e: Exception) { SyncStatus.PENDING }

    @TypeConverter
    fun fromAccountProvider(value: com.example.data.local.entity.AccountProvider): String = value.name

    @TypeConverter
    fun toAccountProvider(value: String): com.example.data.local.entity.AccountProvider =
        try { com.example.data.local.entity.AccountProvider.valueOf(value) } catch (e: Exception) { com.example.data.local.entity.AccountProvider.GMAIL }
}

