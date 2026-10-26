package com.example.data.repository

import com.example.data.local.dao.*
import com.example.data.local.entity.*
import com.example.data.model.*
import com.example.data.sync.OfflineSyncManager
import com.example.printer.BluetoothPrinterManager
import com.example.printer.DiscoveredBluetoothDevice
import com.example.security.SecurityManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class POSRepository(
    val receiptDao: ReceiptDao,
    val productDao: ProductDao,
    val customerDao: CustomerDao,
    val creditNoteDao: CreditNoteDao,
    val syncQueueDao: SyncQueueDao,
    val syncLogDao: SyncLogDao,
    val storeConfigDao: StoreConfigDao,
    val businessProfileDao: BusinessProfileDao,
    val userAccountDao: UserAccountDao,
    val printerManager: BluetoothPrinterManager,
    val syncManager: OfflineSyncManager,
    val cloudRestSyncService: com.example.data.sync.CloudRestSyncService,
    val googleDriveBackupService: com.example.data.sync.GoogleDriveBackupService
) {
    private val moshi = Moshi.Builder().build()
    private val itemsListType = Types.newParameterizedType(List::class.java, CartItem::class.java)
    private val itemsAdapter = moshi.adapter<List<CartItem>>(itemsListType)

    // Flow streams for UI
    val allReceipts: Flow<List<ReceiptEntity>> = receiptDao.getAllReceipts()
    val allCreditNotes: Flow<List<CreditNoteEntity>> = creditNoteDao.getAllCreditNotesFlow()
    val availableCreditNotes: Flow<List<CreditNoteEntity>> = creditNoteDao.getAvailableCreditNotesFlow()
    val activeProducts: Flow<List<ProductEntity>> = productDao.getAllActiveProducts()
    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomersFlow()
    val productCategories: Flow<List<String>> = productDao.getCategories()
    val allBusinesses: Flow<List<BusinessProfileEntity>> = businessProfileDao.getAllBusinessesFlow()
    val activeBusiness: Flow<BusinessProfileEntity?> = businessProfileDao.getDefaultBusinessFlow()
    val unsyncedReceiptsCount: Flow<Int> = receiptDao.getUnsyncedCount()
    val totalSalesSum: Flow<Double?> = receiptDao.getTotalSalesSum()
    val totalReceiptsCount: Flow<Int> = receiptDao.getTotalReceiptsCount()
    val pendingSyncCount: Flow<Int> = syncQueueDao.getPendingCount()
    val syncLogs: Flow<List<SyncLogEntity>> = syncLogDao.getRecentLogs()
    val storeConfig: Flow<StoreConfigEntity?> = storeConfigDao.getStoreConfigFlow()
    val currentAccount: Flow<UserAccountEntity?> = userAccountDao.getLoggedInAccountFlow()

    fun getPairedBluetoothPrinters(): List<DiscoveredBluetoothDevice> =
        printerManager.getPairedDevices()

    fun startPrinterDiscovery() = printerManager.startDiscovery()
    fun stopPrinterDiscovery() = printerManager.stopDiscovery()
    fun connectPrinter(device: DiscoveredBluetoothDevice) = printerManager.connectToDevice(device)
    fun disconnectPrinter() = printerManager.disconnect()

    // Wi-Fi / Ethernet Printer Controls
    val wifiPrinterManager get() = printerManager.wifiPrinterManager
    fun startWifiScan(subnet: String? = null, port: Int = 9100) = wifiPrinterManager.scanLocalSubnet(subnet, port)
    fun stopWifiScan() = wifiPrinterManager.stopScan()
    fun connectWifiPrinter(ip: String, port: Int = 9100, name: String = "Wi-Fi Printer") =
        wifiPrinterManager.connectToPrinter(ip, port, name)
    fun disconnectWifiPrinter() = wifiPrinterManager.disconnect()
    fun addCustomWifiPrinter(ip: String, port: Int, name: String) =
        wifiPrinterManager.addCustomPrinter(ip, port, name)
    fun removeCustomWifiPrinter(device: com.example.printer.DiscoveredWifiPrinter) =
        wifiPrinterManager.removeCustomPrinter(device)
    fun getLocalDeviceIp(): String? = wifiPrinterManager.getLocalIpAddress()

    /**
     * Triggers Cash Drawer Kick pulse via connected thermal printer
     */
    suspend fun openCashDrawer(): Boolean {
        val payload = printerManager.buildOpenCashDrawerPayload()
        return printerManager.sendBytes(payload.first, "Kick Cash Drawer", payload.second)
    }

    /**
     * Triggers Paper Cut command via connected thermal printer
     */
    suspend fun cutPaper(): Boolean {
        val payload = printerManager.buildCutPaperPayload()
        return printerManager.sendBytes(payload.first, "Cut Paper", payload.second)
    }

    /**
     * Checks out a cart, generates cryptographic hash, records in Room DB,
     * queues for offline sync, and triggers thermal print if requested.
     */
    suspend fun checkout(
        cartItems: List<CartItem>,
        customerName: String,
        customerPhone: String,
        paymentMethod: PaymentMethod,
        discountPercent: Double,
        tipAmount: Double,
        amountTendered: Double,
        cashierName: String,
        notes: String,
        autoPrint: Boolean = true,
        appliedCreditNote: CreditNoteEntity? = null
    ): ReceiptEntity {
        val itemsGrossAfterItemDiscounts = cartItems.sumOf { it.total }
        val overallDiscountAmount = itemsGrossAfterItemDiscounts * (discountPercent / 100.0)
        val discountedGross = itemsGrossAfterItemDiscounts - overallDiscountAmount

        val currentConfig = storeConfigDao.getStoreConfig()
        val isTaxActive = currentConfig?.enableTax ?: true

        var subtotal = 0.0
        var taxAmount = 0.0
        if (isTaxActive) {
            for (item in cartItems) {
                val itemGrossDiscounted = item.total * (1.0 - discountPercent / 100.0)
                val itemTaxable = if (item.taxRatePercent > 0.0) {
                    itemGrossDiscounted / (1.0 + (item.taxRatePercent / 100.0))
                } else {
                    itemGrossDiscounted
                }
                val itemTax = itemGrossDiscounted - itemTaxable
                subtotal += itemTaxable
                taxAmount += itemTax
            }
        } else {
            subtotal = discountedGross
            taxAmount = 0.0
        }

        val grossTotalBeforeCredit = discountedGross + tipAmount
        val freshCreditNote = appliedCreditNote?.let { creditNoteDao.getCreditNoteById(it.id) ?: it }
        val creditApplied = if (freshCreditNote != null && freshCreditNote.remainingBalance > 0.0) {
            minOf(freshCreditNote.remainingBalance, grossTotalBeforeCredit)
        } else {
            0.0
        }

        val totalAmount = (grossTotalBeforeCredit - creditApplied).coerceAtLeast(0.0)
        val changeDue = if (amountTendered > totalAmount) amountTendered - totalAmount else 0.0

        val itemsJson = itemsAdapter.toJson(cartItems)
        val now = System.currentTimeMillis()
        val invoiceNumber = generateNextInvoiceNumber(now)

        val securityHash = SecurityManager.generateReceiptHash(
            invoiceNumber = invoiceNumber,
            createdAt = now,
            totalAmount = totalAmount,
            cashierName = cashierName,
            itemsJson = itemsJson
        )

        val activeBizId = storeConfigDao.getStoreConfig()?.activeBusinessId ?: 1L
        val receipt = ReceiptEntity(
            businessId = activeBizId,
            invoiceNumber = invoiceNumber,
            customerName = customerName.ifEmpty { "Walk-in Customer" },
            customerPhone = customerPhone,
            itemsJson = itemsJson,
            subtotal = subtotal,
            taxAmount = taxAmount,
            discountAmount = overallDiscountAmount,
            discountPercent = discountPercent,
            tipAmount = tipAmount,
            totalAmount = totalAmount,
            amountTendered = amountTendered,
            changeDue = changeDue,
            paymentMethod = paymentMethod,
            paymentDetails = if (paymentMethod == PaymentMethod.CASH) "Tendered: $${String.format("%.2f", amountTendered)}" else "${paymentMethod.label} Authorized",
            status = ReceiptStatus.PAID,
            cashierName = cashierName,
            notes = notes,
            securityHash = securityHash,
            createdAt = now,
            isSynced = false,
            syncStatus = SyncStatus.PENDING,
            appliedCreditNoteNumber = if (creditApplied > 0.0) (freshCreditNote?.creditNoteNumber ?: "") else "",
            appliedCreditOriginalInvoice = if (creditApplied > 0.0) (freshCreditNote?.originalInvoiceNumber ?: "") else "",
            creditNoteAppliedAmount = creditApplied
        )

        val receiptId = receiptDao.insertReceipt(receipt)
        val savedReceipt = receipt.copy(id = receiptId)

        // Deduct from Credit Note balance if applied
        if (freshCreditNote != null && creditApplied > 0.0) {
            val newRemaining = (freshCreditNote.remainingBalance - creditApplied).coerceAtLeast(0.0)
            val newStatus = if (newRemaining <= 0.009) {
                CreditNoteStatus.REDEEMED
            } else {
                CreditNoteStatus.PARTIALLY_USED
            }
            creditNoteDao.updateCreditNote(
                freshCreditNote.copy(
                    remainingBalance = if (newRemaining <= 0.009) 0.0 else newRemaining,
                    status = newStatus,
                    updatedAt = now
                )
            )
        }

        // Maintain and update customer profile if not anonymous walk-in
        if (customerName.isNotBlank() && !customerName.equals("Walk-in Customer", ignoreCase = true)) {
            try {
                val existingByPhone = if (customerPhone.isNotBlank()) customerDao.getCustomerByPhone(customerPhone) else null
                if (existingByPhone != null) {
                    customerDao.recordSale(existingByPhone.id, totalAmount)
                } else {
                    // Check if customer with this exact name exists
                    val allCust = customerDao.getAllCustomers()
                    val existingByName = allCust.firstOrNull { it.name.equals(customerName.trim(), ignoreCase = true) }
                    if (existingByName != null) {
                        customerDao.recordSale(existingByName.id, totalAmount)
                    } else {
                        // Create customer profile in system
                        customerDao.insertCustomer(
                            CustomerEntity(
                                name = customerName.trim(),
                                phone = customerPhone.trim(),
                                totalSpent = totalAmount,
                                totalVisits = 1
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Log or continue gracefully
            }
        }

        // Decrement inventory stock
        for (item in cartItems) {
            if (item.productId > 0) {
                productDao.decrementStock(item.productId, item.quantity)
            }
        }

        // Queue in Sync Engine
        syncQueueDao.insertQueueItem(
            SyncQueueEntity(
                entityType = "RECEIPT",
                entityId = receiptId,
                operation = "CREATE",
                payloadJson = itemsJson
            )
        )

        // Auto print to Bluetooth printer if requested
        if (autoPrint) {
            val config = storeConfigDao.getStoreConfig() ?: StoreConfigEntity()
            val paperWidth = if (config.paperWidthMm == 80) PaperWidth.MM_80 else PaperWidth.MM_58
            val payload = printerManager.buildReceiptPayload(savedReceipt, config, paperWidth)
            printerManager.sendBytes(payload.first, "Invoice $invoiceNumber", payload.second)
        }

        return savedReceipt
    }

    /**
     * Generates the next sequential invoice number in the format:
     * INV-YYMMDD-1000 (AUTO COUNT) e.g., INV-260818-1001, INV-260818-1002
     */
    suspend fun generateNextInvoiceNumber(timestamp: Long = System.currentTimeMillis()): String {
        val datePart = SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date(timestamp))
        val allReceipts = receiptDao.getAllReceiptsChronological()
        val nextSeq = 1000 + allReceipts.size + 1
        return "INV-$datePart-$nextSeq"
    }

    /**
     * Automatically adjusts invoice numbering across all remaining receipts in chronological order
     * so that numbers remain strictly consecutive with no gaps (e.g. 1001, 1002, 1003...).
     */
    suspend fun adjustInvoiceNumbering() {
        val receipts = receiptDao.getAllReceiptsChronological()
        receipts.forEachIndexed { index, receipt ->
            val datePart = SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date(receipt.createdAt))
            val sequence = 1000 + (index + 1)
            val expectedInvoiceNumber = "INV-$datePart-$sequence"
            if (receipt.invoiceNumber != expectedInvoiceNumber) {
                val newSecurityHash = SecurityManager.generateReceiptHash(
                    invoiceNumber = expectedInvoiceNumber,
                    createdAt = receipt.createdAt,
                    totalAmount = receipt.totalAmount,
                    cashierName = receipt.cashierName,
                    itemsJson = receipt.itemsJson
                )
                val updatedReceipt = receipt.copy(
                    invoiceNumber = expectedInvoiceNumber,
                    securityHash = newSecurityHash
                )
                receiptDao.updateReceipt(updatedReceipt)
            }
        }
    }

    /**
     * Resolves the auto-adjusted paper width from connected printer or configuration
     */
    suspend fun getAutoAdjustedPaperWidth(): PaperWidth {
        val config = storeConfigDao.getStoreConfig() ?: StoreConfigEntity()
        return printerManager.resolveAutoAdjustedPaperWidth(config)
    }

    /**
     * Prints a receipt by ID, automatically adapting to the connected printer layout
     */
    suspend fun printReceipt(receiptId: Long, paperWidth: PaperWidth? = null): Boolean {
        val receipt = receiptDao.getReceiptById(receiptId) ?: return false
        val config = storeConfigDao.getStoreConfig() ?: StoreConfigEntity()
        val width = paperWidth ?: printerManager.resolveAutoAdjustedPaperWidth(config)
        val payload = printerManager.buildReceiptPayload(receipt, config, width)
        return printerManager.sendBytes(payload.first, "Print ${receipt.invoiceNumber}", payload.second)
    }

    /**
     * Edits an existing receipt and recalculates totals and security hash
     */
    suspend fun editReceipt(
        receiptId: Long,
        customerName: String,
        customerPhone: String,
        cashierName: String,
        paymentMethod: PaymentMethod,
        notes: String,
        cartItems: List<CartItem>,
        discountPercent: Double,
        tipAmount: Double,
        amountTendered: Double
    ): ReceiptEntity? {
        val original = receiptDao.getReceiptById(receiptId) ?: return null

        val grossRetailTotal = cartItems.sumOf { it.unitPrice * it.quantity }
        val discountAmount = grossRetailTotal * (discountPercent / 100.0)
        val discountedGross = grossRetailTotal - discountAmount

        val currentConfig = storeConfigDao.getStoreConfig()
        val isTaxActive = currentConfig?.enableTax ?: true

        var subtotal = 0.0
        var taxAmount = 0.0
        if (isTaxActive) {
            for (item in cartItems) {
                val itemGrossDiscounted = (item.unitPrice * item.quantity) * (1.0 - discountPercent / 100.0)
                val itemTaxable = if (item.taxRatePercent > 0.0) {
                    itemGrossDiscounted / (1.0 + (item.taxRatePercent / 100.0))
                } else {
                    itemGrossDiscounted
                }
                val itemTax = itemGrossDiscounted - itemTaxable
                subtotal += itemTaxable
                taxAmount += itemTax
            }
        } else {
            subtotal = discountedGross
            taxAmount = 0.0
        }

        val totalAmount = discountedGross + tipAmount
        val changeDue = if (amountTendered > totalAmount) amountTendered - totalAmount else 0.0

        val itemsJson = itemsAdapter.toJson(cartItems)

        val newSecurityHash = SecurityManager.generateReceiptHash(
            invoiceNumber = original.invoiceNumber,
            createdAt = original.createdAt,
            totalAmount = totalAmount,
            cashierName = cashierName,
            itemsJson = itemsJson
        )

        val updatedReceipt = original.copy(
            customerName = customerName.ifEmpty { "Walk-in Customer" },
            customerPhone = customerPhone,
            cashierName = cashierName,
            paymentMethod = paymentMethod,
            paymentDetails = if (paymentMethod == PaymentMethod.CASH) "Tendered: ${String.format("%.2f", amountTendered)}" else "${paymentMethod.label} Authorized",
            notes = notes,
            itemsJson = itemsJson,
            subtotal = subtotal,
            discountAmount = discountAmount,
            discountPercent = discountPercent,
            taxAmount = taxAmount,
            tipAmount = tipAmount,
            totalAmount = totalAmount,
            amountTendered = amountTendered,
            changeDue = changeDue,
            securityHash = newSecurityHash,
            isSynced = false,
            syncStatus = SyncStatus.PENDING
        )

        receiptDao.updateReceipt(updatedReceipt)

        // Queue update in sync engine
        syncQueueDao.insertQueueItem(
            SyncQueueEntity(
                entityType = "RECEIPT",
                entityId = receiptId,
                operation = "UPDATE",
                payloadJson = itemsJson
            )
        )

        if (syncManager.isNetworkAvailable()) {
            syncManager.triggerAutoSync("AUTO_EDIT_RECEIPT")
        }

        return updatedReceipt
    }

    /**
     * Deletes a receipt, restores inventory if paid, and syncs tombstone
     */
    suspend fun deleteReceipt(receipt: ReceiptEntity): Boolean {
        // Restore stock if the receipt was in PAID status
        if (receipt.status == ReceiptStatus.PAID) {
            try {
                val items: List<CartItem>? = itemsAdapter.fromJson(receipt.itemsJson)
                items?.forEach { item ->
                    if (item.productId > 0) {
                        productDao.incrementStock(item.productId, item.quantity)
                    }
                }
            } catch (ignored: Exception) {}
        }

        receiptDao.deleteReceipt(receipt)

        // Automatically adjust invoice numbering across all remaining receipts
        adjustInvoiceNumbering()

        syncQueueDao.insertQueueItem(
            SyncQueueEntity(
                entityType = "RECEIPT",
                entityId = receipt.id,
                operation = "DELETE",
                payloadJson = "{}"
            )
        )

        if (syncManager.isNetworkAvailable()) {
            syncManager.triggerAutoSync("AUTO_DELETE_RECEIPT")
        }

        return true
    }

    /**
     * Generates a PDF invoice file for a receipt
     */
    suspend fun generatePdfInvoice(context: android.content.Context, receipt: ReceiptEntity): java.io.File {
        val config = storeConfigDao.getStoreConfig() ?: StoreConfigEntity()
        val items = parseItems(receipt.itemsJson)
        return com.example.util.PdfInvoiceGenerator.generatePdfFile(context, receipt, config, items)
    }

    /**
     * Shares a PDF invoice file
     */
    suspend fun sharePdfInvoice(context: android.content.Context, receipt: ReceiptEntity) {
        val file = generatePdfInvoice(context, receipt)
        com.example.util.PdfInvoiceGenerator.sharePdf(context, file, receipt.invoiceNumber)
    }

    /**
     * Prints a PDF invoice document via Android Print Manager
     */
    suspend fun printPdfInvoice(context: android.content.Context, receipt: ReceiptEntity) {
        val file = generatePdfInvoice(context, receipt)
        com.example.util.PdfInvoiceGenerator.printPdf(context, file, "Invoice ${receipt.invoiceNumber}")
    }

    /**
     * Batch exports all receipts as PDFs into the public Downloads directory and creates a ZIP archive.
     */
    suspend fun downloadAllPdfInvoices(
        context: android.content.Context,
        receipts: List<ReceiptEntity>
    ): Pair<Int, java.io.File?> {
        if (receipts.isEmpty()) return Pair(0, null)

        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val invoicesFolder = java.io.File(downloadsDir, "Invoices").apply { mkdirs() }

        var successCount = 0
        val generatedFiles = mutableListOf<java.io.File>()

        receipts.forEach { receipt ->
            try {
                val pdfFile = generatePdfInvoice(context, receipt)
                val cleanNo = receipt.invoiceNumber.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                val targetFile = java.io.File(invoicesFolder, "Invoice_$cleanNo.pdf")
                pdfFile.copyTo(targetFile, overwrite = true)
                generatedFiles.add(targetFile)
                successCount++
            } catch (e: Exception) {
                android.util.Log.e("POSRepository", "Error generating PDF for ${receipt.invoiceNumber}", e)
            }
        }

        var zipFile: java.io.File? = null
        if (generatedFiles.isNotEmpty()) {
            try {
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                zipFile = java.io.File(downloadsDir, "All_Invoices_$timestamp.zip")
                java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zipOut ->
                    generatedFiles.forEach { file ->
                        java.io.FileInputStream(file).use { fi ->
                            val zipEntry = java.util.zip.ZipEntry(file.name)
                            zipOut.putNextEntry(zipEntry)
                            fi.copyTo(zipOut)
                            zipOut.closeEntry()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("POSRepository", "Error zipping invoices", e)
            }
        }

        return Pair(successCount, zipFile ?: invoicesFolder)
    }

    /**
     * Exports receipts to an Excel/CSV spreadsheet file
     */
    suspend fun exportInvoicesToExcel(
        context: android.content.Context,
        receipts: List<ReceiptEntity>
    ): java.io.File {
        val config = storeConfigDao.getStoreConfig() ?: StoreConfigEntity()
        return com.example.util.ExcelInvoiceExporter.exportInvoicesToExcel(context, receipts, config)
    }

    /**
     * Prints ESC/POS Self-Test
     */
    suspend fun printSelfTest(paperWidth: PaperWidth? = null): Boolean {
        val config = storeConfigDao.getStoreConfig() ?: StoreConfigEntity()
        val width = paperWidth ?: printerManager.resolveAutoAdjustedPaperWidth(config)
        val payload = printerManager.buildSelfTestPayload(width)
        return printerManager.sendBytes(payload.first, "Printer Self-Test", payload.second)
    }

    /**
     * Refunds or Voids an existing receipt
     */
    suspend fun refundReceipt(receiptId: Long, isVoid: Boolean = false): Boolean {
        val receipt = receiptDao.getReceiptById(receiptId) ?: return false
        val newStatus = if (isVoid) ReceiptStatus.VOIDED else ReceiptStatus.REFUNDED
        receiptDao.updateReceiptStatus(receiptId, newStatus)

        // Restore inventory stock
        try {
            val items: List<CartItem>? = itemsAdapter.fromJson(receipt.itemsJson)
            items?.forEach { item ->
                if (item.productId > 0) {
                    productDao.incrementStock(item.productId, item.quantity)
                }
            }
        } catch (ignored: Exception) {}

        // Add to sync queue
        syncQueueDao.insertQueueItem(
            SyncQueueEntity(
                entityType = "RECEIPT",
                entityId = receiptId,
                operation = "UPDATE",
                payloadJson = "{\"status\":\"${newStatus.name}\"}"
            )
        )

        // Auto sync if online
        if (syncManager.isNetworkAvailable()) {
            syncManager.triggerAutoSync("AUTO_REFUND")
        }

        return true
    }

    suspend fun saveStoreConfig(config: StoreConfigEntity) {
        storeConfigDao.saveStoreConfig(config)
        syncQueueDao.insertQueueItem(
            SyncQueueEntity(
                entityType = "CONFIG",
                entityId = 1,
                operation = "UPDATE",
                payloadJson = "{}"
            )
        )
    }

    suspend fun insertProduct(product: ProductEntity): Long {
        val activeBizId = storeConfigDao.getStoreConfig()?.activeBusinessId ?: 1L
        val prodToInsert = if (product.businessId == 1L && activeBizId != 1L) product.copy(businessId = activeBizId) else product
        val id = productDao.insertProduct(prodToInsert)
        syncQueueDao.insertQueueItem(
            SyncQueueEntity(
                entityType = "PRODUCT",
                entityId = id,
                operation = "CREATE",
                payloadJson = "{}"
            )
        )
        return id
    }

    suspend fun updateProduct(product: ProductEntity) {
        productDao.updateProduct(product)
        syncQueueDao.insertQueueItem(
            SyncQueueEntity(
                entityType = "PRODUCT",
                entityId = product.id,
                operation = "UPDATE",
                payloadJson = "{}"
            )
        )
    }

    suspend fun adjustProductStock(productId: Long, newStock: Int) {
        val prod = productDao.getProductById(productId) ?: return
        val updated = prod.copy(stockQuantity = newStock.coerceAtLeast(0), updatedAt = System.currentTimeMillis())
        productDao.updateProduct(updated)
    }

    suspend fun toggleProductActive(productId: Long, isActive: Boolean) {
        val prod = productDao.getProductById(productId) ?: return
        val updated = prod.copy(isActive = isActive, updatedAt = System.currentTimeMillis())
        productDao.updateProduct(updated)
    }

    suspend fun deleteProduct(product: ProductEntity) {
        productDao.deleteProduct(product)
        syncQueueDao.insertQueueItem(
            SyncQueueEntity(
                entityType = "PRODUCT",
                entityId = product.id,
                operation = "DELETE",
                payloadJson = "{}"
            )
        )
    }

    // --- MULTI-BUSINESS MANAGEMENT ---
    suspend fun insertBusinessProfile(business: BusinessProfileEntity): Long {
        val id = businessProfileDao.insertBusiness(business)
        if (business.isDefault) {
            switchActiveBusiness(id)
        }
        return id
    }

    suspend fun updateBusinessProfile(business: BusinessProfileEntity) {
        businessProfileDao.updateBusiness(business)
        val currentConfig = storeConfigDao.getStoreConfig()
        if (currentConfig != null && currentConfig.activeBusinessId == business.id) {
            val updatedConfig = currentConfig.copy(
                businessName = business.businessName,
                tagline = business.tagline,
                address = business.address,
                phone = business.phone,
                email = business.email,
                taxId = business.taxId,
                countryCode = business.countryCode,
                countryName = business.countryName,
                currencyCode = business.currencyCode,
                currencySymbol = business.currencySymbol,
                taxName = business.taxName,
                defaultTaxPercent = business.defaultTaxPercent,
                enableTax = business.enableTax,
                receiptHeader = business.receiptHeader,
                receiptFooter = business.receiptFooter,
                paperWidthMm = business.paperWidthMm,
                enableUpiPayment = business.enableUpiPayment,
                upiId = business.upiId,
                upiPayeeName = business.upiPayeeName,
                enableDenominationLogo = business.enableDenominationLogo,
                logoUri = business.logoUri
            )
            storeConfigDao.saveStoreConfig(updatedConfig)
        }
    }

    suspend fun deleteBusinessProfile(business: BusinessProfileEntity): Boolean {
        val all = businessProfileDao.getAllBusinesses()
        if (all.size <= 1) {
            return false // Keep at least one business
        }
        businessProfileDao.deleteBusiness(business)
        val currentConfig = storeConfigDao.getStoreConfig()
        if (currentConfig?.activeBusinessId == business.id) {
            val remaining = businessProfileDao.getAllBusinesses()
            if (remaining.isNotEmpty()) {
                switchActiveBusiness(remaining.first().id)
            }
        }
        return true
    }

    suspend fun switchActiveBusiness(businessId: Long) {
        businessProfileDao.setDefaultBusiness(businessId)
        val business = businessProfileDao.getBusinessById(businessId) ?: return
        val currentConfig = storeConfigDao.getStoreConfig() ?: StoreConfigEntity()
        val updatedConfig = currentConfig.copy(
            activeBusinessId = business.id,
            businessName = business.businessName,
            tagline = business.tagline,
            address = business.address,
            phone = business.phone,
            email = business.email,
            taxId = business.taxId,
            countryCode = business.countryCode,
            countryName = business.countryName,
            currencyCode = business.currencyCode,
            currencySymbol = business.currencySymbol,
            taxName = business.taxName,
            defaultTaxPercent = business.defaultTaxPercent,
            enableTax = business.enableTax,
            receiptHeader = business.receiptHeader,
            receiptFooter = business.receiptFooter,
            paperWidthMm = business.paperWidthMm,
            enableUpiPayment = business.enableUpiPayment,
            upiId = business.upiId,
            upiPayeeName = business.upiPayeeName,
            enableDenominationLogo = business.enableDenominationLogo,
            logoUri = business.logoUri
        )
        storeConfigDao.saveStoreConfig(updatedConfig)
    }

    suspend fun triggerManualSync(): SyncLogEntity = syncManager.performSync("MANUAL")

    suspend fun saveCustomer(customer: CustomerEntity): Long {
        return if (customer.id == 0L) {
            val activeBizId = storeConfigDao.getStoreConfig()?.activeBusinessId ?: 1L
            val custToInsert = if (customer.businessId == 1L && activeBizId != 1L) customer.copy(businessId = activeBizId) else customer
            customerDao.insertCustomer(custToInsert)
        } else {
            customerDao.updateCustomer(customer.copy(updatedAt = System.currentTimeMillis()))
            customer.id
        }
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        customerDao.deleteCustomer(customer)
    }

    fun searchCustomers(query: String): Flow<List<CustomerEntity>> {
        return customerDao.searchCustomers(query)
    }

    suspend fun getCustomerById(id: Long): CustomerEntity? = customerDao.getCustomerById(id)

    suspend fun getCustomerByPhone(phone: String): CustomerEntity? = customerDao.getCustomerByPhone(phone)

    suspend fun getLoggedInAccount(): UserAccountEntity? = userAccountDao.getLoggedInAccount()

    suspend fun loginAccount(email: String, password: String? = null): UserAccountEntity {
        val trimmedEmail = email.trim()
        val provider = if (trimmedEmail.endsWith("@gmail.com", ignoreCase = true) || trimmedEmail.endsWith("@googlemail.com", ignoreCase = true)) {
            AccountProvider.GMAIL
        } else {
            AccountProvider.OUTLOOK
        }

        userAccountDao.clearAllLoginStates()
        val existing = userAccountDao.getAccountByEmail(trimmedEmail)
        val defaultName = trimmedEmail.substringBefore("@")
            .replace(".", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

        val account = existing?.copy(
            isLoggedIn = true,
            lastLoginTimestamp = System.currentTimeMillis()
        ) ?: UserAccountEntity(
            email = trimmedEmail,
            displayName = defaultName,
            provider = provider,
            passwordHash = password ?: "",
            isLoggedIn = true,
            lastLoginTimestamp = System.currentTimeMillis()
        )

        userAccountDao.insertOrUpdateAccount(account)
        return account
    }

    suspend fun registerAccount(
        email: String,
        displayName: String,
        password: String,
        provider: AccountProvider
    ): UserAccountEntity {
        val trimmedEmail = email.trim()
        userAccountDao.clearAllLoginStates()
        val account = UserAccountEntity(
            email = trimmedEmail,
            displayName = if (displayName.isNotBlank()) displayName.trim() else trimmedEmail.substringBefore("@"),
            provider = provider,
            passwordHash = password,
            isLoggedIn = true,
            lastLoginTimestamp = System.currentTimeMillis()
        )
        userAccountDao.insertOrUpdateAccount(account)
        return account
    }

    suspend fun logoutCurrentAccount() {
        userAccountDao.clearAllLoginStates()
    }

    fun parseItems(itemsJson: String): List<CartItem> {
        return try {
            itemsAdapter.fromJson(itemsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Generates the next sequential Credit Note number in the format:
     * CN-YYMMDD-1001
     */
    suspend fun generateNextCreditNoteNumber(timestamp: Long = System.currentTimeMillis()): String {
        val datePart = SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date(timestamp))
        val allCreditNotes = creditNoteDao.getAllCreditNotesList()
        val nextSeq = 1000 + allCreditNotes.size + 1
        return "CN-$datePart-$nextSeq"
    }

    /**
     * Creates a partial item return Credit Note ("Trade Note") from an existing receipt,
     * increments product inventory stock for returned items, and records returned quantities on the receipt.
     */
    suspend fun createCreditNoteFromReturn(
        receiptId: Long,
        returnedQuantitiesByKey: Map<String, Int>,
        customerPhone: String,
        customerName: String,
        notes: String = "",
        autoPrint: Boolean = false
    ): CreditNoteEntity? {
        val originalReceipt = receiptDao.getReceiptById(receiptId) ?: return null
        val purchasedItems = parseItems(originalReceipt.itemsJson)
        val alreadyReturnedItems = parseItems(originalReceipt.returnedItemsJson).toMutableList()

        val newlyReturnedItems = mutableListOf<CartItem>()
        var totalCreditAmount = 0.0
        val invoiceDiscountMultiplier = (1.0 - (originalReceipt.discountPercent.coerceIn(0.0, 100.0) / 100.0))

        for ((index, item) in purchasedItems.withIndex()) {
            val itemKey = "${index}_${item.productId}_${item.name}"
            val requestedReturnQty = returnedQuantitiesByKey[itemKey] ?: 0
            if (requestedReturnQty <= 0) continue

            val prevReturnedQty = alreadyReturnedItems
                .filter { it.productId == item.productId && it.name == item.name && Math.abs(it.unitPrice - item.unitPrice) < 0.01 }
                .sumOf { it.quantity }
            val maxReturnable = (item.quantity - prevReturnedQty).coerceAtLeast(0)
            val actualReturnQty = requestedReturnQty.coerceAtMost(maxReturnable)
            if (actualReturnQty <= 0) continue

            // Pro-rated unit price after item discount and invoice-level discount
            val effectiveUnitNetAfterDiscounts = if (item.quantity > 0) {
                (item.total / item.quantity) * invoiceDiscountMultiplier
            } else {
                item.unitPrice * invoiceDiscountMultiplier
            }

            val returnedCartItem = item.copy(
                quantity = actualReturnQty,
                unitPrice = effectiveUnitNetAfterDiscounts,
                discountType = ItemDiscountType.NONE,
                discountValue = 0.0
            )
            newlyReturnedItems.add(returnedCartItem)
            totalCreditAmount += effectiveUnitNetAfterDiscounts * actualReturnQty

            // Automatically restore returned items back to inventory stock
            if (item.productId > 0) {
                productDao.incrementStock(item.productId, actualReturnQty)
            }

            // Merge into alreadyReturnedItems
            val existingIdx = alreadyReturnedItems.indexOfFirst {
                it.productId == item.productId && it.name == item.name && Math.abs(it.unitPrice - item.unitPrice) < 0.01
            }
            if (existingIdx >= 0) {
                val prev = alreadyReturnedItems[existingIdx]
                alreadyReturnedItems[existingIdx] = prev.copy(quantity = prev.quantity + actualReturnQty)
            } else {
                alreadyReturnedItems.add(item.copy(quantity = actualReturnQty))
            }
        }

        if (newlyReturnedItems.isEmpty() || totalCreditAmount <= 0.0) {
            return null
        }

        val now = System.currentTimeMillis()
        val cnNumber = generateNextCreditNoteNumber(now)
        val activeBizId = storeConfigDao.getStoreConfig()?.activeBusinessId ?: originalReceipt.businessId
        val resolvedPhone = customerPhone.trim().ifEmpty { originalReceipt.customerPhone.trim() }
        val resolvedName = customerName.trim().ifEmpty {
            originalReceipt.customerName.ifBlank { "Registered Customer" }
        }

        val creditNote = CreditNoteEntity(
            businessId = activeBizId,
            creditNoteNumber = cnNumber,
            originalReceiptId = originalReceipt.id,
            originalInvoiceNumber = originalReceipt.invoiceNumber,
            customerName = resolvedName,
            customerPhone = resolvedPhone,
            returnedItemsJson = itemsAdapter.toJson(newlyReturnedItems),
            totalCreditAmount = totalCreditAmount,
            remainingBalance = totalCreditAmount,
            status = CreditNoteStatus.ACTIVE,
            cashierName = originalReceipt.cashierName,
            notes = notes.trim(),
            createdAt = now,
            updatedAt = now
        )

        val cnId = creditNoteDao.insertCreditNote(creditNote)
        val savedCreditNote = creditNote.copy(id = cnId)

        // Also update the original receipt's returnedItemsJson (and customerPhone if it was blank)
        val totalPurchasedQty = purchasedItems.sumOf { it.quantity }
        val totalReturnedQty = alreadyReturnedItems.sumOf { it.quantity }
        val updatedReceiptStatus = if (totalReturnedQty >= totalPurchasedQty) {
            ReceiptStatus.REFUNDED
        } else {
            originalReceipt.status
        }

        val updatedReceipt = originalReceipt.copy(
            returnedItemsJson = itemsAdapter.toJson(alreadyReturnedItems),
            customerPhone = originalReceipt.customerPhone.ifBlank { resolvedPhone },
            customerName = if (originalReceipt.customerName == "Walk-in Customer" && resolvedName != "Walk-in Customer") resolvedName else originalReceipt.customerName,
            status = updatedReceiptStatus,
            isSynced = false,
            syncStatus = SyncStatus.PENDING
        )
        receiptDao.updateReceipt(updatedReceipt)

        // Ensure customer is registered in CustomerEntity directory by phone if not already present
        if (resolvedPhone.isNotBlank()) {
            try {
                val existingCustomer = customerDao.getCustomerByPhone(resolvedPhone)
                if (existingCustomer == null) {
                    customerDao.insertCustomer(
                        CustomerEntity(
                            businessId = activeBizId,
                            name = resolvedName,
                            phone = resolvedPhone,
                            notes = "Registered via Item Return ($cnNumber)"
                        )
                    )
                }
            } catch (ignored: Exception) {}
        }

        if (autoPrint) {
            printCreditNote(cnId)
        }

        if (syncManager.isNetworkAvailable()) {
            syncManager.triggerAutoSync("AUTO_CREDIT_NOTE")
        }

        return savedCreditNote
    }

    /**
     * Prints a standalone Credit Note slip via thermal printer
     */
    suspend fun printCreditNote(creditNoteId: Long, paperWidth: PaperWidth? = null): Boolean {
        val cn = creditNoteDao.getCreditNoteById(creditNoteId) ?: return false
        val config = storeConfigDao.getStoreConfig() ?: StoreConfigEntity()
        val width = paperWidth ?: printerManager.resolveAutoAdjustedPaperWidth(config)
        val payload = printerManager.buildCreditNotePayload(cn, config, width)
        return printerManager.sendBytes(payload.first, "Print ${cn.creditNoteNumber}", payload.second)
    }

    /**
     * Generates a PDF file for a Credit Note
     */
    suspend fun generateCreditNotePdf(context: android.content.Context, creditNote: CreditNoteEntity): java.io.File {
        val config = storeConfigDao.getStoreConfig() ?: StoreConfigEntity()
        val items = parseItems(creditNote.returnedItemsJson)
        return com.example.util.PdfInvoiceGenerator.generateCreditNotePdfFile(context, creditNote, config, items)
    }

    /**
     * Shares a Credit Note PDF via Android Share Sheet
     */
    suspend fun shareCreditNotePdf(context: android.content.Context, creditNote: CreditNoteEntity) {
        val file = generateCreditNotePdf(context, creditNote)
        com.example.util.PdfInvoiceGenerator.sharePdf(context, file, creditNote.creditNoteNumber)
    }

    /**
     * Prints a Credit Note PDF via Android System Print Manager
     */
    suspend fun printCreditNotePdf(context: android.content.Context, creditNote: CreditNoteEntity) {
        val file = generateCreditNotePdf(context, creditNote)
        com.example.util.PdfInvoiceGenerator.printPdf(context, file, "Credit Note ${creditNote.creditNoteNumber}")
    }
}

