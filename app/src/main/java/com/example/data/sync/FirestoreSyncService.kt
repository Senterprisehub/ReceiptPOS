package com.example.data.sync

import android.util.Log
import com.example.data.local.dao.*
import com.example.data.local.entity.*
import com.example.data.model.PaymentMethod
import com.example.data.model.ReceiptStatus
import com.example.data.model.SyncStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manages bi-directional cloud synchronization between the local Room SQLite DB
 * and Firebase Firestore, organized per user account ID (email slug).
 */
class FirestoreSyncService(
    private val productDao: ProductDao,
    private val receiptDao: ReceiptDao,
    private val customerDao: CustomerDao,
    private val storeConfigDao: StoreConfigDao,
    private val businessProfileDao: BusinessProfileDao
) {
    private val TAG = "FirestoreSyncService"

    private fun getFirestoreInstance(): FirebaseFirestore? {
        return try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore not initialized: ${e.message}")
            null
        }
    }

    private fun getUserDocPath(userEmail: String): String {
        return userEmail.trim().lowercase().replace(".", "_").replace("@", "_at_")
    }

    /**
     * Uploads all local store data (products, receipts, businesses, config, customers)
     * to the user's cloud Firestore collection.
     */
    suspend fun uploadLocalDataToCloud(userEmail: String): Boolean = withContext(Dispatchers.IO) {
        val firestore = getFirestoreInstance() ?: return@withContext false
        val userPath = getUserDocPath(userEmail)
        val userDoc = firestore.collection("users_pos_data").document(userPath)

        try {
            // 1. Upload Store Config
            val config = storeConfigDao.getStoreConfig()
            if (config != null) {
                val configMap = hashMapOf(
                    "businessName" to config.businessName,
                    "tagline" to config.tagline,
                    "address" to config.address,
                    "phone" to config.phone,
                    "email" to config.email,
                    "taxId" to config.taxId,
                    "taxName" to config.taxName,
                    "defaultTaxPercent" to config.defaultTaxPercent,
                    "enableTax" to config.enableTax,
                    "currencySymbol" to config.currencySymbol,
                    "currencyCode" to config.currencyCode,
                    "countryCode" to config.countryCode,
                    "countryName" to config.countryName,
                    "receiptHeader" to config.receiptHeader,
                    "receiptFooter" to config.receiptFooter,
                    "paperWidthMm" to config.paperWidthMm,
                    "activeBusinessId" to config.activeBusinessId,
                    "showQrVerification" to config.showQrVerification,
                    "showBarcode" to config.showBarcode,
                    "enableUpiPayment" to config.enableUpiPayment,
                    "upiId" to config.upiId,
                    "upiPayeeName" to config.upiPayeeName,
                    "autoPrintOnSale" to config.autoPrintOnSale,
                    "masterPin" to config.masterPin,
                    "isPinRequiredForRefund" to config.isPinRequiredForRefund,
                    "isPinRequiredForSettings" to config.isPinRequiredForSettings,
                    "syncedAt" to System.currentTimeMillis()
                )
                userDoc.collection("config").document("store_config").set(configMap, SetOptions.merge()).await()
            }

            // 2. Upload Products
            val products = productDao.getAllProductsList()
            for (prod in products) {
                val prodMap = hashMapOf(
                    "id" to prod.id,
                    "name" to prod.name,
                    "category" to prod.category,
                    "barcode" to prod.barcode,
                    "sku" to prod.sku,
                    "price" to prod.price,
                    "costPrice" to prod.costPrice,
                    "stockQuantity" to prod.stockQuantity,
                    "taxRatePercent" to prod.taxRatePercent,
                    "colorHex" to prod.colorHex,
                    "isActive" to prod.isActive,
                    "updatedAt" to prod.updatedAt,
                    "syncedAt" to System.currentTimeMillis()
                )
                userDoc.collection("products").document("prod_${prod.id}").set(prodMap, SetOptions.merge()).await()
            }

            // 3. Upload Customers
            val customers = customerDao.getAllCustomers()
            for (cust in customers) {
                val custMap = hashMapOf(
                    "id" to cust.id,
                    "name" to cust.name,
                    "phone" to cust.phone,
                    "email" to cust.email,
                    "address" to cust.address,
                    "notes" to cust.notes,
                    "totalSpent" to cust.totalSpent,
                    "totalVisits" to cust.totalVisits,
                    "createdAt" to cust.createdAt,
                    "updatedAt" to cust.updatedAt
                )
                userDoc.collection("customers").document("cust_${cust.id}").set(custMap, SetOptions.merge()).await()
            }

            // 4. Upload Receipts
            val receipts = receiptDao.getAllReceiptsList()
            for (receipt in receipts) {
                val rMap = hashMapOf(
                    "id" to receipt.id,
                    "invoiceNumber" to receipt.invoiceNumber,
                    "customerName" to receipt.customerName,
                    "customerPhone" to receipt.customerPhone,
                    "itemsJson" to receipt.itemsJson,
                    "subtotal" to receipt.subtotal,
                    "taxAmount" to receipt.taxAmount,
                    "discountAmount" to receipt.discountAmount,
                    "discountPercent" to receipt.discountPercent,
                    "tipAmount" to receipt.tipAmount,
                    "totalAmount" to receipt.totalAmount,
                    "amountTendered" to receipt.amountTendered,
                    "changeDue" to receipt.changeDue,
                    "paymentMethod" to receipt.paymentMethod.name,
                    "paymentDetails" to receipt.paymentDetails,
                    "status" to receipt.status.name,
                    "cashierName" to receipt.cashierName,
                    "notes" to receipt.notes,
                    "securityHash" to receipt.securityHash,
                    "createdAt" to receipt.createdAt,
                    "syncedAt" to System.currentTimeMillis()
                )
                userDoc.collection("receipts").document("rec_${receipt.id}").set(rMap, SetOptions.merge()).await()
                receiptDao.markAsSynced(receipt.id, System.currentTimeMillis())
            }

            // 5. Upload Businesses
            val businesses = businessProfileDao.getAllBusinesses()
            for (bus in businesses) {
                val bMap = hashMapOf(
                    "id" to bus.id,
                    "businessName" to bus.businessName,
                    "tagline" to bus.tagline,
                    "address" to bus.address,
                    "phone" to bus.phone,
                    "email" to bus.email,
                    "taxId" to bus.taxId,
                    "countryCode" to bus.countryCode,
                    "countryName" to bus.countryName,
                    "currencySymbol" to bus.currencySymbol,
                    "currencyCode" to bus.currencyCode,
                    "defaultTaxPercent" to bus.defaultTaxPercent,
                    "taxName" to bus.taxName,
                    "enableTax" to bus.enableTax,
                    "receiptHeader" to bus.receiptHeader,
                    "receiptFooter" to bus.receiptFooter,
                    "paperWidthMm" to bus.paperWidthMm,
                    "enableUpiPayment" to bus.enableUpiPayment,
                    "upiId" to bus.upiId,
                    "upiPayeeName" to bus.upiPayeeName,
                    "isDefault" to bus.isDefault,
                    "createdAt" to bus.createdAt
                )
                userDoc.collection("businesses").document("bus_${bus.id}").set(bMap, SetOptions.merge()).await()
            }

            // Mark user document with metadata
            userDoc.set(
                hashMapOf(
                    "lastCloudSync" to System.currentTimeMillis(),
                    "email" to userEmail,
                    "productsCount" to products.size,
                    "receiptsCount" to receipts.size,
                    "customersCount" to customers.size
                ),
                SetOptions.merge()
            ).await()

            Log.d(TAG, "Successfully synced ${receipts.size} receipts & ${products.size} products to Firestore for $userEmail")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing local data to Firestore", e)
            false
        }
    }

    /**
     * Pulls all cloud store data for the given user from Firestore and restores/merges
     * into the local Room database.
     */
    suspend fun restoreDataFromCloud(userEmail: String): Int = withContext(Dispatchers.IO) {
        val firestore = getFirestoreInstance() ?: return@withContext 0
        val userPath = getUserDocPath(userEmail)
        val userDoc = firestore.collection("users_pos_data").document(userPath)

        var restoredItemsCount = 0

        try {
            // 1. Pull Products
            val prodSnap = userDoc.collection("products").get().await()
            for (doc in prodSnap.documents) {
                val id = doc.getLong("id") ?: continue
                val name = doc.getString("name") ?: "Product"
                val category = doc.getString("category") ?: "General"
                val barcode = doc.getString("barcode") ?: ""
                val sku = doc.getString("sku") ?: ""
                val price = doc.getDouble("price") ?: 0.0
                val costPrice = doc.getDouble("costPrice") ?: 0.0
                val stockQuantity = doc.getLong("stockQuantity")?.toInt() ?: 100
                val taxRatePercent = doc.getDouble("taxRatePercent") ?: 5.0
                val colorHex = doc.getString("colorHex") ?: "#1E3A8A"
                val isActive = doc.getBoolean("isActive") ?: true
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

                val product = ProductEntity(
                    id = id,
                    name = name,
                    category = category,
                    price = price,
                    costPrice = costPrice,
                    sku = sku,
                    barcode = barcode,
                    stockQuantity = stockQuantity,
                    taxRatePercent = taxRatePercent,
                    colorHex = colorHex,
                    isActive = isActive,
                    isSynced = true,
                    updatedAt = updatedAt
                )
                productDao.insertProduct(product)
                restoredItemsCount++
            }

            // 2. Pull Customers
            val custSnap = userDoc.collection("customers").get().await()
            for (doc in custSnap.documents) {
                val id = doc.getLong("id") ?: continue
                val name = doc.getString("name") ?: "Customer"
                val phone = doc.getString("phone") ?: ""
                val email = doc.getString("email") ?: ""
                val address = doc.getString("address") ?: ""
                val notes = doc.getString("notes") ?: ""
                val totalSpent = doc.getDouble("totalSpent") ?: 0.0
                val totalVisits = doc.getLong("totalVisits")?.toInt() ?: 0
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

                val customer = CustomerEntity(
                    id = id,
                    name = name,
                    phone = phone,
                    email = email,
                    address = address,
                    notes = notes,
                    totalSpent = totalSpent,
                    totalVisits = totalVisits,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
                customerDao.insertCustomer(customer)
                restoredItemsCount++
            }

            // 3. Pull Receipts
            val recSnap = userDoc.collection("receipts").get().await()
            for (doc in recSnap.documents) {
                val id = doc.getLong("id") ?: continue
                val invoiceNumber = doc.getString("invoiceNumber") ?: "INV-${id}"
                val customerName = doc.getString("customerName") ?: "Walk-in Customer"
                val customerPhone = doc.getString("customerPhone") ?: ""
                val itemsJson = doc.getString("itemsJson") ?: "[]"
                val subtotal = doc.getDouble("subtotal") ?: 0.0
                val taxAmount = doc.getDouble("taxAmount") ?: 0.0
                val discountAmount = doc.getDouble("discountAmount") ?: 0.0
                val discountPercent = doc.getDouble("discountPercent") ?: 0.0
                val tipAmount = doc.getDouble("tipAmount") ?: 0.0
                val totalAmount = doc.getDouble("totalAmount") ?: 0.0
                val amountTendered = doc.getDouble("amountTendered") ?: 0.0
                val changeDue = doc.getDouble("changeDue") ?: 0.0
                val paymentMethodStr = doc.getString("paymentMethod") ?: "CASH"
                val paymentDetails = doc.getString("paymentDetails") ?: ""
                val statusStr = doc.getString("status") ?: "PAID"
                val cashierName = doc.getString("cashierName") ?: "Cashier"
                val notes = doc.getString("notes") ?: ""
                val securityHash = doc.getString("securityHash") ?: ""
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                val paymentMethod = try {
                    PaymentMethod.valueOf(paymentMethodStr)
                } catch (e: Exception) {
                    PaymentMethod.CASH
                }

                val status = try {
                    ReceiptStatus.valueOf(statusStr)
                } catch (e: Exception) {
                    ReceiptStatus.PAID
                }

                val receipt = ReceiptEntity(
                    id = id,
                    invoiceNumber = invoiceNumber,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    itemsJson = itemsJson,
                    subtotal = subtotal,
                    taxAmount = taxAmount,
                    discountAmount = discountAmount,
                    discountPercent = discountPercent,
                    tipAmount = tipAmount,
                    totalAmount = totalAmount,
                    amountTendered = amountTendered,
                    changeDue = changeDue,
                    paymentMethod = paymentMethod,
                    paymentDetails = paymentDetails,
                    status = status,
                    cashierName = cashierName,
                    notes = notes,
                    securityHash = securityHash,
                    createdAt = createdAt,
                    isSynced = true,
                    syncStatus = SyncStatus.SYNCED
                )
                receiptDao.insertReceipt(receipt)
                restoredItemsCount++
            }

            // 4. Pull Store Config
            val configDoc = userDoc.collection("config").document("store_config").get().await()
            if (configDoc.exists()) {
                val current = storeConfigDao.getStoreConfig() ?: StoreConfigEntity()
                val updated = current.copy(
                    businessName = configDoc.getString("businessName") ?: current.businessName,
                    tagline = configDoc.getString("tagline") ?: current.tagline,
                    address = configDoc.getString("address") ?: current.address,
                    phone = configDoc.getString("phone") ?: current.phone,
                    email = configDoc.getString("email") ?: current.email,
                    taxId = configDoc.getString("taxId") ?: current.taxId,
                    taxName = configDoc.getString("taxName") ?: current.taxName,
                    defaultTaxPercent = configDoc.getDouble("defaultTaxPercent") ?: current.defaultTaxPercent,
                    enableTax = configDoc.getBoolean("enableTax") ?: current.enableTax,
                    currencySymbol = configDoc.getString("currencySymbol") ?: current.currencySymbol,
                    currencyCode = configDoc.getString("currencyCode") ?: current.currencyCode,
                    countryCode = configDoc.getString("countryCode") ?: current.countryCode,
                    countryName = configDoc.getString("countryName") ?: current.countryName,
                    receiptHeader = configDoc.getString("receiptHeader") ?: current.receiptHeader,
                    receiptFooter = configDoc.getString("receiptFooter") ?: current.receiptFooter,
                    paperWidthMm = configDoc.getLong("paperWidthMm")?.toInt() ?: current.paperWidthMm,
                    activeBusinessId = configDoc.getLong("activeBusinessId") ?: current.activeBusinessId,
                    showQrVerification = configDoc.getBoolean("showQrVerification") ?: current.showQrVerification,
                    showBarcode = configDoc.getBoolean("showBarcode") ?: current.showBarcode,
                    enableUpiPayment = configDoc.getBoolean("enableUpiPayment") ?: current.enableUpiPayment,
                    upiId = configDoc.getString("upiId") ?: current.upiId,
                    upiPayeeName = configDoc.getString("upiPayeeName") ?: current.upiPayeeName,
                    autoPrintOnSale = configDoc.getBoolean("autoPrintOnSale") ?: current.autoPrintOnSale,
                    masterPin = configDoc.getString("masterPin") ?: current.masterPin,
                    isPinRequiredForRefund = configDoc.getBoolean("isPinRequiredForRefund") ?: current.isPinRequiredForRefund,
                    isPinRequiredForSettings = configDoc.getBoolean("isPinRequiredForSettings") ?: current.isPinRequiredForSettings
                )
                storeConfigDao.saveStoreConfig(updated)
                restoredItemsCount++
            }

            // 5. Pull Businesses
            val busSnap = userDoc.collection("businesses").get().await()
            for (doc in busSnap.documents) {
                val id = doc.getLong("id") ?: continue
                val business = BusinessProfileEntity(
                    id = id,
                    businessName = doc.getString("businessName") ?: "Business",
                    tagline = doc.getString("tagline") ?: "",
                    address = doc.getString("address") ?: "",
                    phone = doc.getString("phone") ?: "",
                    email = doc.getString("email") ?: "",
                    taxId = doc.getString("taxId") ?: "",
                    countryCode = doc.getString("countryCode") ?: "US",
                    countryName = doc.getString("countryName") ?: "United States",
                    currencySymbol = doc.getString("currencySymbol") ?: "$",
                    currencyCode = doc.getString("currencyCode") ?: "USD",
                    taxName = doc.getString("taxName") ?: "Sales Tax",
                    defaultTaxPercent = doc.getDouble("defaultTaxPercent") ?: 8.25,
                    enableTax = doc.getBoolean("enableTax") ?: true,
                    receiptHeader = doc.getString("receiptHeader") ?: "--- THANK YOU FOR YOUR BUSINESS ---",
                    receiptFooter = doc.getString("receiptFooter") ?: "Have a wonderful day!",
                    paperWidthMm = doc.getLong("paperWidthMm")?.toInt() ?: 58,
                    enableUpiPayment = doc.getBoolean("enableUpiPayment") ?: false,
                    upiId = doc.getString("upiId") ?: "",
                    upiPayeeName = doc.getString("upiPayeeName") ?: "",
                    isDefault = doc.getBoolean("isDefault") ?: false,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
                businessProfileDao.insertBusiness(business)
                restoredItemsCount++
            }

            Log.d(TAG, "Restored $restoredItemsCount items from Firestore cloud for $userEmail")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from Firestore", e)
        }

        restoredItemsCount
    }
}
