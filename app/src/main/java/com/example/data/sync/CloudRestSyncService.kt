package com.example.data.sync

import android.util.Log
import com.example.data.local.dao.*
import com.example.data.local.entity.*
import com.example.data.model.CreditNoteStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.ReceiptStatus
import com.example.data.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RestSyncResult(
    val success: Boolean,
    val itemsUploaded: Int = 0,
    val itemsRestored: Int = 0,
    val message: String = ""
)

/**
 * Universal Cloud Sync & Webhook Service using standard HTTPS REST calls.
 * Works with Google Apps Script webhooks, MockAPI, Supabase, n8n, Zapier, or any JSON REST endpoint.
 * Requires ZERO google-services.json or external SDKs.
 */
class CloudRestSyncService(
    private val productDao: ProductDao,
    private val receiptDao: ReceiptDao,
    private val customerDao: CustomerDao,
    private val storeConfigDao: StoreConfigDao,
    private val businessProfileDao: BusinessProfileDao,
    private val creditNoteDao: CreditNoteDao? = null
) {
    private val TAG = "CloudRestSyncService"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Serializes all local products, receipts, customers, businesses & config to a JSON payload.
     */
    suspend fun generateCompleteBackupJson(userEmail: String): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", "1.0")
        root.put("accountEmail", userEmail)
        root.put("exportedAt", System.currentTimeMillis())

        // 1. Config
        val config = storeConfigDao.getStoreConfig() ?: StoreConfigEntity()
        val configObj = JSONObject().apply {
            put("activeBusinessId", config.activeBusinessId)
            put("businessName", config.businessName)
            put("tagline", config.tagline)
            put("address", config.address)
            put("phone", config.phone)
            put("email", config.email)
            put("taxId", config.taxId)
            put("countryCode", config.countryCode)
            put("countryName", config.countryName)
            put("currencySymbol", config.currencySymbol)
            put("currencyCode", config.currencyCode)
            put("taxName", config.taxName)
            put("defaultTaxPercent", config.defaultTaxPercent)
            put("enableTax", config.enableTax)
            put("paperWidthMm", config.paperWidthMm)
            put("receiptHeader", config.receiptHeader)
            put("receiptFooter", config.receiptFooter)
            put("showQrVerification", config.showQrVerification)
            put("showBarcode", config.showBarcode)
            put("enableUpiPayment", config.enableUpiPayment)
            put("upiId", config.upiId)
            put("upiPayeeName", config.upiPayeeName)
            put("enableDenominationLogo", config.enableDenominationLogo)
            put("autoPrintOnSale", config.autoPrintOnSale)
            put("masterPin", config.masterPin)
            put("isPinRequiredForRefund", config.isPinRequiredForRefund)
        }
        root.put("config", configObj)

        // 2. Products
        val products = productDao.getAllProductsList()
        val productsArr = JSONArray()
        for (p in products) {
            val pObj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("category", p.category)
                put("barcode", p.barcode)
                put("sku", p.sku)
                put("price", p.price)
                put("costPrice", p.costPrice)
                put("stockQuantity", p.stockQuantity)
                put("taxRatePercent", p.taxRatePercent)
                put("colorHex", p.colorHex)
                put("isActive", p.isActive)
                put("updatedAt", p.updatedAt)
            }
            productsArr.put(pObj)
        }
        root.put("products", productsArr)

        // 3. Customers
        val customers = customerDao.getAllCustomers()
        val customersArr = JSONArray()
        for (c in customers) {
            val cObj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("email", c.email)
                put("address", c.address)
                put("notes", c.notes)
                put("totalSpent", c.totalSpent)
                put("totalVisits", c.totalVisits)
                put("createdAt", c.createdAt)
                put("updatedAt", c.updatedAt)
            }
            customersArr.put(cObj)
        }
        root.put("customers", customersArr)

        // 4. Receipts / Invoices
        val receipts = receiptDao.getAllReceiptsList()
        val receiptsArr = JSONArray()
        for (r in receipts) {
            val rObj = JSONObject().apply {
                put("id", r.id)
                put("invoiceNumber", r.invoiceNumber)
                put("customerName", r.customerName)
                put("customerPhone", r.customerPhone)
                put("itemsJson", r.itemsJson)
                put("subtotal", r.subtotal)
                put("taxAmount", r.taxAmount)
                put("discountAmount", r.discountAmount)
                put("discountPercent", r.discountPercent)
                put("tipAmount", r.tipAmount)
                put("totalAmount", r.totalAmount)
                put("amountTendered", r.amountTendered)
                put("changeDue", r.changeDue)
                put("paymentMethod", r.paymentMethod.name)
                put("paymentDetails", r.paymentDetails)
                put("status", r.status.name)
                put("cashierName", r.cashierName)
                put("notes", r.notes)
                put("securityHash", r.securityHash)
                put("createdAt", r.createdAt)
                put("returnedItemsJson", r.returnedItemsJson)
                put("appliedCreditNoteNumber", r.appliedCreditNoteNumber)
                put("appliedCreditOriginalInvoice", r.appliedCreditOriginalInvoice)
                put("creditNoteAppliedAmount", r.creditNoteAppliedAmount)
            }
            receiptsArr.put(rObj)
        }
        root.put("receipts", receiptsArr)

        // 4.5 Credit Notes / Trade Notes
        val creditNotes = creditNoteDao?.getAllCreditNotesList() ?: emptyList()
        val cnArr = JSONArray()
        for (cn in creditNotes) {
            val cnObj = JSONObject().apply {
                put("id", cn.id)
                put("businessId", cn.businessId)
                put("creditNoteNumber", cn.creditNoteNumber)
                put("originalReceiptId", cn.originalReceiptId)
                put("originalInvoiceNumber", cn.originalInvoiceNumber)
                put("customerName", cn.customerName)
                put("customerPhone", cn.customerPhone)
                put("returnedItemsJson", cn.returnedItemsJson)
                put("totalCreditAmount", cn.totalCreditAmount)
                put("remainingBalance", cn.remainingBalance)
                put("status", cn.status.name)
                put("cashierName", cn.cashierName)
                put("notes", cn.notes)
                put("createdAt", cn.createdAt)
                put("updatedAt", cn.updatedAt)
            }
            cnArr.put(cnObj)
        }
        root.put("creditNotes", cnArr)

        // 5. Businesses
        val businesses = businessProfileDao.getAllBusinesses()
        val busArr = JSONArray()
        for (b in businesses) {
            val bObj = JSONObject().apply {
                put("id", b.id)
                put("businessName", b.businessName)
                put("tagline", b.tagline)
                put("address", b.address)
                put("phone", b.phone)
                put("email", b.email)
                put("taxId", b.taxId)
                put("countryCode", b.countryCode)
                put("countryName", b.countryName)
                put("currencySymbol", b.currencySymbol)
                put("currencyCode", b.currencyCode)
                put("defaultTaxPercent", b.defaultTaxPercent)
                put("taxName", b.taxName)
                put("enableTax", b.enableTax)
                put("receiptHeader", b.receiptHeader)
                put("receiptFooter", b.receiptFooter)
                put("paperWidthMm", b.paperWidthMm)
                put("enableUpiPayment", b.enableUpiPayment)
                put("upiId", b.upiId)
                put("upiPayeeName", b.upiPayeeName)
                put("enableDenominationLogo", b.enableDenominationLogo)
                put("isDefault", b.isDefault)
                put("createdAt", b.createdAt)
            }
            busArr.put(bObj)
        }
        root.put("businesses", busArr)

        root.toString(2)
    }

    /**
     * Restores database from a full JSON payload string.
     */
    suspend fun restoreDatabaseFromJson(jsonStr: String): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        try {
            val root = JSONObject(jsonStr)

            // 1. Restore Products
            if (root.has("products")) {
                val pArr = root.getJSONArray("products")
                for (i in 0 until pArr.length()) {
                    val obj = pArr.getJSONObject(i)
                    val product = ProductEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "Product"),
                        category = obj.optString("category", "General"),
                        price = obj.optDouble("price", 0.0),
                        costPrice = obj.optDouble("costPrice", 0.0),
                        sku = obj.optString("sku", ""),
                        barcode = obj.optString("barcode", ""),
                        stockQuantity = obj.optInt("stockQuantity", 100),
                        taxRatePercent = obj.optDouble("taxRatePercent", 5.0),
                        colorHex = obj.optString("colorHex", "#1E3A8A"),
                        isActive = obj.optBoolean("isActive", true),
                        isSynced = true,
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    productDao.insertProduct(product)
                    restoredCount++
                }
            }

            // 2. Restore Customers
            if (root.has("customers")) {
                val cArr = root.getJSONArray("customers")
                for (i in 0 until cArr.length()) {
                    val obj = cArr.getJSONObject(i)
                    val customer = CustomerEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "Customer"),
                        phone = obj.optString("phone", ""),
                        email = obj.optString("email", ""),
                        address = obj.optString("address", ""),
                        notes = obj.optString("notes", ""),
                        totalSpent = obj.optDouble("totalSpent", 0.0),
                        totalVisits = obj.optInt("totalVisits", 0),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    customerDao.insertCustomer(customer)
                    restoredCount++
                }
            }

            // 3. Restore Receipts
            if (root.has("receipts")) {
                val rArr = root.getJSONArray("receipts")
                for (i in 0 until rArr.length()) {
                    val obj = rArr.getJSONObject(i)
                    val paymentMethod = try {
                        PaymentMethod.valueOf(obj.optString("paymentMethod", "CASH"))
                    } catch (e: Exception) {
                        PaymentMethod.CASH
                    }
                    val status = try {
                        ReceiptStatus.valueOf(obj.optString("status", "PAID"))
                    } catch (e: Exception) {
                        ReceiptStatus.PAID
                    }

                    val receipt = ReceiptEntity(
                        id = obj.optLong("id", 0L),
                        invoiceNumber = obj.optString("invoiceNumber", "INV-${System.currentTimeMillis()}"),
                        customerName = obj.optString("customerName", "Walk-in Customer"),
                        customerPhone = obj.optString("customerPhone", ""),
                        itemsJson = obj.optString("itemsJson", "[]"),
                        subtotal = obj.optDouble("subtotal", 0.0),
                        taxAmount = obj.optDouble("taxAmount", 0.0),
                        discountAmount = obj.optDouble("discountAmount", 0.0),
                        discountPercent = obj.optDouble("discountPercent", 0.0),
                        tipAmount = obj.optDouble("tipAmount", 0.0),
                        totalAmount = obj.optDouble("totalAmount", 0.0),
                        amountTendered = obj.optDouble("amountTendered", 0.0),
                        changeDue = obj.optDouble("changeDue", 0.0),
                        paymentMethod = paymentMethod,
                        paymentDetails = obj.optString("paymentDetails", ""),
                        status = status,
                        cashierName = obj.optString("cashierName", "Cashier"),
                        notes = obj.optString("notes", ""),
                        securityHash = obj.optString("securityHash", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isSynced = true,
                        syncStatus = SyncStatus.SYNCED,
                        returnedItemsJson = obj.optString("returnedItemsJson", "[]"),
                        appliedCreditNoteNumber = obj.optString("appliedCreditNoteNumber", ""),
                        appliedCreditOriginalInvoice = obj.optString("appliedCreditOriginalInvoice", ""),
                        creditNoteAppliedAmount = obj.optDouble("creditNoteAppliedAmount", 0.0)
                    )
                    receiptDao.insertReceipt(receipt)
                    restoredCount++
                }
            }

            // 3.5 Restore Credit Notes / Trade Notes
            if (root.has("creditNotes") && creditNoteDao != null) {
                val cnArr = root.getJSONArray("creditNotes")
                for (i in 0 until cnArr.length()) {
                    val obj = cnArr.getJSONObject(i)
                    val cnStatus = try {
                        CreditNoteStatus.valueOf(obj.optString("status", "ACTIVE"))
                    } catch (e: Exception) {
                        CreditNoteStatus.ACTIVE
                    }
                    val cn = CreditNoteEntity(
                        id = obj.optLong("id", 0L),
                        businessId = obj.optLong("businessId", 1L),
                        creditNoteNumber = obj.optString("creditNoteNumber", "CN-${System.currentTimeMillis()}"),
                        originalReceiptId = obj.optLong("originalReceiptId", 0L),
                        originalInvoiceNumber = obj.optString("originalInvoiceNumber", ""),
                        customerName = obj.optString("customerName", "Customer"),
                        customerPhone = obj.optString("customerPhone", ""),
                        returnedItemsJson = obj.optString("returnedItemsJson", "[]"),
                        totalCreditAmount = obj.optDouble("totalCreditAmount", 0.0),
                        remainingBalance = obj.optDouble("remainingBalance", 0.0),
                        status = cnStatus,
                        cashierName = obj.optString("cashierName", "Cashier"),
                        notes = obj.optString("notes", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    creditNoteDao.insertCreditNote(cn)
                    restoredCount++
                }
            }

            // 4. Restore Config
            if (root.has("config")) {
                val cfgObj = root.getJSONObject("config")
                val current = storeConfigDao.getStoreConfig() ?: StoreConfigEntity()
                val updated = current.copy(
                    activeBusinessId = cfgObj.optLong("activeBusinessId", current.activeBusinessId),
                    businessName = cfgObj.optString("businessName", current.businessName),
                    tagline = cfgObj.optString("tagline", current.tagline),
                    address = cfgObj.optString("address", current.address),
                    phone = cfgObj.optString("phone", current.phone),
                    email = cfgObj.optString("email", current.email),
                    taxId = cfgObj.optString("taxId", current.taxId),
                    countryCode = cfgObj.optString("countryCode", current.countryCode),
                    countryName = cfgObj.optString("countryName", current.countryName),
                    currencySymbol = cfgObj.optString("currencySymbol", current.currencySymbol),
                    currencyCode = cfgObj.optString("currencyCode", current.currencyCode),
                    taxName = cfgObj.optString("taxName", current.taxName),
                    defaultTaxPercent = cfgObj.optDouble("defaultTaxPercent", current.defaultTaxPercent),
                    enableTax = cfgObj.optBoolean("enableTax", current.enableTax),
                    paperWidthMm = cfgObj.optInt("paperWidthMm", current.paperWidthMm),
                    receiptHeader = cfgObj.optString("receiptHeader", current.receiptHeader),
                    receiptFooter = cfgObj.optString("receiptFooter", current.receiptFooter),
                    showQrVerification = cfgObj.optBoolean("showQrVerification", current.showQrVerification),
                    showBarcode = cfgObj.optBoolean("showBarcode", current.showBarcode),
                    enableUpiPayment = cfgObj.optBoolean("enableUpiPayment", current.enableUpiPayment),
                    upiId = cfgObj.optString("upiId", current.upiId),
                    upiPayeeName = cfgObj.optString("upiPayeeName", current.upiPayeeName),
                    enableDenominationLogo = cfgObj.optBoolean("enableDenominationLogo", current.enableDenominationLogo),
                    autoPrintOnSale = cfgObj.optBoolean("autoPrintOnSale", current.autoPrintOnSale),
                    masterPin = cfgObj.optString("masterPin", current.masterPin),
                    isPinRequiredForRefund = cfgObj.optBoolean("isPinRequiredForRefund", current.isPinRequiredForRefund)
                )
                storeConfigDao.saveStoreConfig(updated)
                restoredCount++
            }

            // 5. Restore Businesses
            if (root.has("businesses")) {
                val bArr = root.getJSONArray("businesses")
                for (i in 0 until bArr.length()) {
                    val obj = bArr.getJSONObject(i)
                    val bus = BusinessProfileEntity(
                        id = obj.optLong("id", 0L),
                        businessName = obj.optString("businessName", "Business"),
                        tagline = obj.optString("tagline", ""),
                        address = obj.optString("address", ""),
                        phone = obj.optString("phone", ""),
                        email = obj.optString("email", ""),
                        taxId = obj.optString("taxId", ""),
                        countryCode = obj.optString("countryCode", "US"),
                        countryName = obj.optString("countryName", "United States"),
                        currencySymbol = obj.optString("currencySymbol", "$"),
                        currencyCode = obj.optString("currencyCode", "USD"),
                        defaultTaxPercent = obj.optDouble("defaultTaxPercent", 8.25),
                        taxName = obj.optString("taxName", "Sales Tax"),
                        enableTax = obj.optBoolean("enableTax", true),
                        receiptHeader = obj.optString("receiptHeader", "--- THANK YOU FOR YOUR BUSINESS ---"),
                        receiptFooter = obj.optString("receiptFooter", "Goods sold are returnable within 14 days\nwith original receipt.\nHave a wonderful day!"),
                        paperWidthMm = obj.optInt("paperWidthMm", 58),
                        enableUpiPayment = obj.optBoolean("enableUpiPayment", false),
                        upiId = obj.optString("upiId", ""),
                        upiPayeeName = obj.optString("upiPayeeName", ""),
                        enableDenominationLogo = obj.optBoolean("enableDenominationLogo", true),
                        isDefault = obj.optBoolean("isDefault", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                    businessProfileDao.insertBusiness(bus)
                    restoredCount++
                }
            }

            Log.d(TAG, "Successfully restored $restoredCount database records from JSON")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore database from JSON", e)
        }

        restoredCount
    }

    /**
     * Pushes complete store payload to a custom HTTP/HTTPS Webhook or REST endpoint.
     */
    suspend fun pushToRemoteEndpoint(endpointUrl: String, userEmail: String): RestSyncResult = withContext(Dispatchers.IO) {
        try {
            if (endpointUrl.isBlank() || !endpointUrl.startsWith("http")) {
                return@withContext RestSyncResult(false, 0, 0, "Invalid endpoint URL")
            }

            val payloadJson = generateCompleteBackupJson(userEmail)
            val requestBody = payloadJson.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)
                .addHeader("User-Agent", "ApexPOS-Android/1.0")
                .addHeader("X-POS-User", userEmail)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                // If server responded with data payload to restore
                var restored = 0
                if (responseBody.trim().startsWith("{") && responseBody.contains("\"products\"")) {
                    restored = restoreDatabaseFromJson(responseBody)
                }
                RestSyncResult(true, 1, restored, "Synced successfully to cloud webhook (HTTP ${response.code})")
            } else {
                RestSyncResult(false, 0, 0, "Cloud returned HTTP ${response.code}: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing to remote REST endpoint", e)
            RestSyncResult(false, 0, 0, "Cloud sync failed: ${e.localizedMessage ?: "Connection error"}")
        }
    }

    /**
     * Pulls data from a custom HTTP/HTTPS Webhook or REST endpoint and updates local database.
     */
    suspend fun pullFromRemoteEndpoint(endpointUrl: String, userEmail: String): RestSyncResult = withContext(Dispatchers.IO) {
        try {
            if (endpointUrl.isBlank() || !endpointUrl.startsWith("http")) {
                return@withContext RestSyncResult(false, 0, 0, "Invalid endpoint URL")
            }

            val urlWithQuery = if (endpointUrl.contains("?")) {
                "$endpointUrl&action=pull&user=$userEmail"
            } else {
                "$endpointUrl?action=pull&user=$userEmail"
            }

            val request = Request.Builder()
                .url(urlWithQuery)
                .get()
                .addHeader("User-Agent", "ApexPOS-Android/1.0")
                .addHeader("X-POS-User", userEmail)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val restored = restoreDatabaseFromJson(responseBody)
                RestSyncResult(true, 0, restored, "Restored $restored records from Cloud Server")
            } else {
                RestSyncResult(false, 0, 0, "Server returned HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling from remote REST endpoint", e)
            RestSyncResult(false, 0, 0, "Cloud pull failed: ${e.localizedMessage ?: "Connection error"}")
        }
    }
}
