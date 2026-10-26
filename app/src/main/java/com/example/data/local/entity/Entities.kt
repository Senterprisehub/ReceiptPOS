package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.CreditNoteStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.ReceiptStatus
import com.example.data.model.SyncStatus

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val businessId: Long = 1L,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    val totalSpent: Double = 0.0,
    val totalVisits: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val businessId: Long = 1L,
    val invoiceNumber: String,
    val customerName: String = "Walk-in Customer",
    val customerPhone: String = "",
    val itemsJson: String, // Serialized List<CartItem>
    val subtotal: Double,
    val taxAmount: Double,
    val discountAmount: Double = 0.0,
    val discountPercent: Double = 0.0,
    val tipAmount: Double = 0.0,
    val totalAmount: Double,
    val amountTendered: Double = 0.0,
    val changeDue: Double = 0.0,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val paymentDetails: String = "",
    val status: ReceiptStatus = ReceiptStatus.PAID,
    val cashierName: String = "Cashier #1",
    val notes: String = "",
    val securityHash: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val lastSyncAttempt: Long = 0L,
    val syncError: String? = null,
    val returnedItemsJson: String = "[]", // Serialized List<CartItem> returned from this invoice
    val appliedCreditNoteNumber: String = "", // e.g., CN-261026-1001
    val appliedCreditOriginalInvoice: String = "", // e.g., INV-261026-1001
    val creditNoteAppliedAmount: Double = 0.0
)

@Entity(tableName = "credit_notes")
data class CreditNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val businessId: Long = 1L,
    val creditNoteNumber: String, // e.g. CN-261026-1001
    val originalReceiptId: Long,
    val originalInvoiceNumber: String, // e.g. INV-261026-1001
    val customerName: String,
    val customerPhone: String,
    val returnedItemsJson: String, // Serialized List<CartItem>
    val totalCreditAmount: Double,
    val remainingBalance: Double,
    val status: CreditNoteStatus = CreditNoteStatus.ACTIVE,
    val cashierName: String = "Cashier #1",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val businessId: Long = 1L,
    val name: String,
    val category: String,
    val price: Double,
    val costPrice: Double = 0.0,
    val sku: String = "",
    val barcode: String = "",
    val stockQuantity: Int = 100,
    val taxRatePercent: Double = 5.0,
    val colorHex: String = "#1E3A8A",
    val discountType: String = "NONE", // "NONE", "PERCENTAGE", "FLAT"
    val discountValue: Double = 0.0,
    val isActive: Boolean = true,
    val isSynced: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityType: String, // "RECEIPT", "PRODUCT", "CONFIG"
    val entityId: Long,
    val operation: String, // "CREATE", "UPDATE", "DELETE"
    val payloadJson: String,
    val status: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long = 0L,
    val errorMessage: String? = null
)

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val triggerType: String, // "AUTO_NETWORK", "MANUAL", "PERIODIC"
    val itemsProcessed: Int,
    val itemsSucceeded: Int,
    val itemsFailed: Int,
    val durationMs: Long,
    val status: String, // "SUCCESS", "PARTIAL", "FAILED"
    val message: String
)

@Entity(tableName = "business_profiles")
data class BusinessProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val businessName: String,
    val tagline: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val taxId: String = "",
    val countryCode: String = "US",
    val countryName: String = "United States",
    val currencyCode: String = "USD",
    val currencySymbol: String = "$",
    val taxName: String = "Sales Tax",
    val defaultTaxPercent: Double = 8.25,
    val enableTax: Boolean = true,
    val receiptHeader: String = "--- THANK YOU FOR YOUR BUSINESS ---",
    val receiptFooter: String = "Goods sold are returnable within 14 days\nwith original receipt.\nHave a wonderful day!",
    val paperWidthMm: Int = 58,
    val enableUpiPayment: Boolean = false,
    val upiId: String = "",
    val upiPayeeName: String = "",
    val enableDenominationLogo: Boolean = true,
    val logoUri: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "store_config")
data class StoreConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val activeBusinessId: Long = 1L,
    val businessName: String = "Apex Retail & Coffee",
    val tagline: String = "Quality & Freshness Daily",
    val address: String = "742 Evergreen Terrace, Suite 100",
    val phone: String = "+1 (555) 839-2041",
    val email: String = "billing@apexretail.io",
    val taxId: String = "US-TX-9843201",
    val countryCode: String = "US",
    val countryName: String = "United States",
    val currencyCode: String = "USD",
    val currencySymbol: String = "$",
    val taxName: String = "Sales Tax",
    val defaultTaxPercent: Double = 8.25,
    val enableTax: Boolean = true,
    val receiptHeader: String = "--- THANK YOU FOR YOUR BUSINESS ---",
    val receiptFooter: String = "Goods sold are returnable within 14 days\nwith original receipt.\nHave a wonderful day!",
    val paperWidthMm: Int = 58, // 58 or 80
    val showQrVerification: Boolean = true,
    val showBarcode: Boolean = true,
    val enableUpiPayment: Boolean = false,
    val upiId: String = "",
    val upiPayeeName: String = "",
    val enableDenominationLogo: Boolean = true,
    val logoUri: String? = null,
    val autoPrintOnSale: Boolean = false,
    val masterPin: String = "1234",
    val isPinRequiredForRefund: Boolean = true,
    val isPinRequiredForSettings: Boolean = false,
    val syncEndpointUrl: String = "",
    val autoSyncOnWifi: Boolean = true,
    val syncConflictStrategy: String = "CLIENT_WINS" // "CLIENT_WINS", "SERVER_WINS", "LATEST_TIMESTAMP"
)
