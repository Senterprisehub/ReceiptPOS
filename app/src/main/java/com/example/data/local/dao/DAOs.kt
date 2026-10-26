package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.*
import com.example.data.model.ReceiptStatus
import com.example.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Query("SELECT r.* FROM receipts r INNER JOIN store_config s ON r.businessId = s.activeBusinessId WHERE s.id = 1 ORDER BY createdAt DESC")
    fun getAllReceipts(): Flow<List<ReceiptEntity>>

    @Query("SELECT r.* FROM receipts r INNER JOIN store_config s ON r.businessId = s.activeBusinessId WHERE s.id = 1 ORDER BY createdAt DESC")
    suspend fun getAllReceiptsList(): List<ReceiptEntity>

    @Query("SELECT r.* FROM receipts r INNER JOIN store_config s ON r.businessId = s.activeBusinessId WHERE s.id = 1 ORDER BY createdAt ASC, id ASC")
    suspend fun getAllReceiptsChronological(): List<ReceiptEntity>

    @Query("SELECT r.* FROM receipts r INNER JOIN store_config s ON r.businessId = s.activeBusinessId WHERE s.id = 1 AND isSynced = 0 ORDER BY createdAt ASC")
    fun getUnsyncedReceiptsFlow(): Flow<List<ReceiptEntity>>

    @Query("SELECT r.* FROM receipts r INNER JOIN store_config s ON r.businessId = s.activeBusinessId WHERE s.id = 1 AND isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedReceiptsList(): List<ReceiptEntity>

    @Query("SELECT r.* FROM receipts r INNER JOIN store_config s ON r.businessId = s.activeBusinessId WHERE s.id = 1 AND r.id = :id")
    suspend fun getReceiptById(id: Long): ReceiptEntity?

    @Query("SELECT r.* FROM receipts r INNER JOIN store_config s ON r.businessId = s.activeBusinessId WHERE s.id = 1 AND invoiceNumber = :invoiceNumber")
    suspend fun getReceiptByInvoiceNumber(invoiceNumber: String): ReceiptEntity?

    @Query("SELECT COUNT(*) FROM receipts r INNER JOIN store_config s ON r.businessId = s.activeBusinessId WHERE s.id = 1 AND isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM receipts r INNER JOIN store_config s ON r.businessId = s.activeBusinessId WHERE s.id = 1 ")
    fun getTotalReceiptsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM receipts r INNER JOIN store_config s ON r.businessId = s.activeBusinessId WHERE s.id = 1 ")
    suspend fun getReceiptsCount(): Int

    @Query("SELECT r.* FROM receipts r INNER JOIN store_config s ON r.businessId = s.activeBusinessId WHERE s.id = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getLatestReceipt(): ReceiptEntity?

    @Query("SELECT SUM(totalAmount) FROM receipts r INNER JOIN store_config s ON r.businessId = s.activeBusinessId WHERE s.id = 1 AND status = 'PAID'")
    fun getTotalSalesSum(): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Update
    suspend fun updateReceipt(receipt: ReceiptEntity)

    @Query("UPDATE receipts SET status = :newStatus WHERE id = :id")
    suspend fun updateReceiptStatus(id: Long, newStatus: ReceiptStatus)

    @Query("UPDATE receipts SET isSynced = 1, syncStatus = 'SYNCED', lastSyncAttempt = :timestamp, syncError = null WHERE id = :id")
    suspend fun markAsSynced(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE receipts SET isSynced = 0, syncStatus = :status, lastSyncAttempt = :timestamp, syncError = :error WHERE id = :id")
    suspend fun markSyncFailed(id: Long, status: SyncStatus = SyncStatus.FAILED, timestamp: Long = System.currentTimeMillis(), error: String?)

    @Delete
    suspend fun deleteReceipt(receipt: ReceiptEntity)
}

@Dao
interface ProductDao {
    @Query("SELECT p.* FROM products p INNER JOIN store_config s ON p.businessId = s.activeBusinessId WHERE s.id = 1 AND isActive = 1 ORDER BY category ASC, name ASC")
    fun getAllActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT p.* FROM products p INNER JOIN store_config s ON p.businessId = s.activeBusinessId WHERE s.id = 1 ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT p.* FROM products p INNER JOIN store_config s ON p.businessId = s.activeBusinessId WHERE s.id = 1 ORDER BY name ASC")
    suspend fun getAllProductsList(): List<ProductEntity>

    @Query("SELECT DISTINCT p.category FROM products p INNER JOIN store_config s ON p.businessId = s.activeBusinessId WHERE s.id = 1 AND isActive = 1")
    fun getCategories(): Flow<List<String>>

    @Query("SELECT p.* FROM products p INNER JOIN store_config s ON p.businessId = s.activeBusinessId WHERE s.id = 1 AND p.id = :id")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT p.* FROM products p INNER JOIN store_config s ON p.businessId = s.activeBusinessId WHERE s.id = 1 AND barcode = :barcode OR sku = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET stockQuantity = stockQuantity - :qty WHERE id = :id AND stockQuantity >= :qty")
    suspend fun decrementStock(id: Long, qty: Int): Int

    @Query("UPDATE products SET stockQuantity = stockQuantity + :qty WHERE id = :id")
    suspend fun incrementStock(id: Long, qty: Int)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status != 'SYNCED' ORDER BY createdAt ASC")
    fun getPendingQueue(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status != 'SYNCED' ORDER BY createdAt ASC")
    suspend fun getPendingQueueList(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status != 'SYNCED'")
    fun getPendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: SyncQueueEntity): Long

    @Update
    suspend fun updateQueueItem(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET status = 'SYNCING', lastAttemptAt = :timestamp WHERE id = :id")
    suspend fun markSyncing(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'SYNCED', lastAttemptAt = :timestamp, errorMessage = null WHERE id = :id")
    suspend fun markCompleted(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'FAILED', retryCount = retryCount + 1, lastAttemptAt = :timestamp, errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()
}

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentLogs(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLogEntity): Long

    @Query("DELETE FROM sync_logs")
    suspend fun clearLogs()
}

@Dao
interface StoreConfigDao {
    @Query("SELECT * FROM store_config WHERE id = 1")
    fun getStoreConfigFlow(): Flow<StoreConfigEntity?>

    @Query("SELECT * FROM store_config WHERE id = 1")
    suspend fun getStoreConfig(): StoreConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStoreConfig(config: StoreConfigEntity)
}

@Dao
interface BusinessProfileDao {
    @Query("SELECT * FROM business_profiles ORDER BY isDefault DESC, createdAt ASC")
    fun getAllBusinessesFlow(): Flow<List<BusinessProfileEntity>>

    @Query("SELECT * FROM business_profiles ORDER BY isDefault DESC, createdAt ASC")
    suspend fun getAllBusinesses(): List<BusinessProfileEntity>

    @Query("SELECT * FROM business_profiles WHERE id = :id")
    suspend fun getBusinessById(id: Long): BusinessProfileEntity?

    @Query("SELECT * FROM business_profiles WHERE isDefault = 1 LIMIT 1")
    fun getDefaultBusinessFlow(): Flow<BusinessProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusiness(business: BusinessProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusinesses(businesses: List<BusinessProfileEntity>)

    @Update
    suspend fun updateBusiness(business: BusinessProfileEntity)

    @Delete
    suspend fun deleteBusiness(business: BusinessProfileEntity)

    @Query("UPDATE business_profiles SET isDefault = CASE WHEN id = :activeId THEN 1 ELSE 0 END")
    suspend fun setDefaultBusiness(activeId: Long)
}

@Dao
interface CustomerDao {
    @Query("SELECT c.* FROM customers c INNER JOIN store_config s ON c.businessId = s.activeBusinessId WHERE s.id = 1 ORDER BY updatedAt DESC, name ASC")
    fun getAllCustomersFlow(): Flow<List<CustomerEntity>>

    @Query("SELECT c.* FROM customers c INNER JOIN store_config s ON c.businessId = s.activeBusinessId WHERE s.id = 1 ORDER BY updatedAt DESC, name ASC")
    suspend fun getAllCustomers(): List<CustomerEntity>

    @Query("SELECT c.* FROM customers c INNER JOIN store_config s ON c.businessId = s.activeBusinessId WHERE s.id = 1 AND c.id = :id")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT c.* FROM customers c INNER JOIN store_config s ON c.businessId = s.activeBusinessId WHERE s.id = 1 AND c.phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): CustomerEntity?

    @Query("SELECT c.* FROM customers c INNER JOIN store_config s ON c.businessId = s.activeBusinessId WHERE s.id = 1 AND (c.name LIKE '%' || :query || '%' OR c.phone LIKE '%' || :query || '%' OR c.email LIKE '%' || :query || '%') ORDER BY c.name ASC")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("UPDATE customers SET totalSpent = totalSpent + :amount, totalVisits = totalVisits + 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun recordSale(id: Long, amount: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET totalSpent = totalSpent + :amount, totalVisits = totalVisits + 1, updatedAt = :timestamp WHERE name = :name")
    suspend fun recordSaleByName(name: String, amount: Double, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)
}

@Dao
interface CreditNoteDao {
    @Query("SELECT cn.* FROM credit_notes cn INNER JOIN store_config s ON cn.businessId = s.activeBusinessId WHERE s.id = 1 ORDER BY cn.createdAt DESC")
    fun getAllCreditNotesFlow(): Flow<List<CreditNoteEntity>>

    @Query("SELECT cn.* FROM credit_notes cn INNER JOIN store_config s ON cn.businessId = s.activeBusinessId WHERE s.id = 1 ORDER BY cn.createdAt DESC")
    suspend fun getAllCreditNotesList(): List<CreditNoteEntity>

    @Query("SELECT cn.* FROM credit_notes cn INNER JOIN store_config s ON cn.businessId = s.activeBusinessId WHERE s.id = 1 AND cn.remainingBalance > 0.009 ORDER BY cn.createdAt DESC")
    fun getAvailableCreditNotesFlow(): Flow<List<CreditNoteEntity>>

    @Query("SELECT cn.* FROM credit_notes cn INNER JOIN store_config s ON cn.businessId = s.activeBusinessId WHERE s.id = 1 AND cn.id = :id")
    suspend fun getCreditNoteById(id: Long): CreditNoteEntity?

    @Query("SELECT cn.* FROM credit_notes cn INNER JOIN store_config s ON cn.businessId = s.activeBusinessId WHERE s.id = 1 AND cn.creditNoteNumber = :creditNoteNumber LIMIT 1")
    suspend fun getCreditNoteByNumber(creditNoteNumber: String): CreditNoteEntity?

    @Query("SELECT cn.* FROM credit_notes cn INNER JOIN store_config s ON cn.businessId = s.activeBusinessId WHERE s.id = 1 ORDER BY cn.id DESC LIMIT 1")
    suspend fun getLatestCreditNote(): CreditNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreditNote(creditNote: CreditNoteEntity): Long

    @Update
    suspend fun updateCreditNote(creditNote: CreditNoteEntity)

    @Delete
    suspend fun deleteCreditNote(creditNote: CreditNoteEntity)
}



