package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.BusinessProfileEntity
import com.example.data.local.entity.CreditNoteEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.local.entity.SyncLogEntity
import com.example.data.model.CartItem
import com.example.data.model.PaperWidth
import com.example.data.model.PaymentMethod
import com.example.data.repository.POSRepository
import com.example.printer.DiscoveredBluetoothDevice
import com.example.printer.PrintLogEntry
import com.example.printer.PrinterConnectionState
import com.example.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class POSUiState(
    // Cart State
    val cartItems: List<CartItem> = emptyList(),
    val discountPercent: Double = 0.0,
    val tipAmount: Double = 0.0,
    val appliedCreditNote: CreditNoteEntity? = null,
    val selectedCustomer: CustomerEntity? = null,
    val customerName: String = "Walk-in Customer",
    val customerPhone: String = "",
    val cashierName: String = "Cashier #1",
    val selectedPaymentMethod: PaymentMethod = PaymentMethod.CASH,
    val amountTenderedStr: String = "",
    val notes: String = "",
    val autoPrintOnSale: Boolean = true,

    // Product Filter State
    val searchQuery: String = "",
    val selectedCategory: String = "All",

    // Active Dialogs & Modals
    val showCustomerSelectionDialog: Boolean = false,
    val showAddCustomerDialog: Boolean = false,
    val showEditCustomerDialog: Boolean = false,
    val customerToEdit: CustomerEntity? = null,
    val showCheckoutDialog: Boolean = false,
    val showCustomItemDialog: Boolean = false,
    val showAddProductDialog: Boolean = false,
    val showEditProductDialog: Boolean = false,
    val productToEdit: ProductEntity? = null,
    val showAdjustStockDialog: Boolean = false,
    val productForStockAdjust: ProductEntity? = null,
    val showBusinessSelectorDialog: Boolean = false,
    val showAddBusinessDialog: Boolean = false,
    val showEditBusinessDialog: Boolean = false,
    val businessToEdit: BusinessProfileEntity? = null,
    val showCurrencyCountryDialog: Boolean = false,
    val showBarcodeScannerDialog: Boolean = false,
    val showReceiptDetailModal: Boolean = false,
    val showEditReceiptDialog: Boolean = false,
    val receiptToEdit: ReceiptEntity? = null,
    val showDeleteReceiptDialog: Boolean = false,
    val receiptToDelete: ReceiptEntity? = null,
    val showItemReturnDialog: Boolean = false,
    val preselectedReceiptForReturn: ReceiptEntity? = null,
    val showCreditNoteDetailModal: Boolean = false,
    val selectedCreditNote: CreditNoteEntity? = null,
    val showWebSyncDialog: Boolean = false,
    val isAutoSyncActive: Boolean = true,
    val showPinLockDialog: Boolean = false,
    val pendingProtectedAction: ProtectedAction? = null,
    val selectedReceipt: ReceiptEntity? = null,
    val toastMessage: String? = null,
    val lastPrintedPreview: String? = null,
    val isDarkTheme: Boolean? = null, // null: System Default, true: Dark, false: Light
    val isTaxEnabled: Boolean = true
) {
    // Total retail price of all items after item-level discounts (tax inclusive)
    val grossRetailTotal: Double
        get() = cartItems.sumOf { it.total }

    // Cart-wide discount amount applied on top of item totals
    val discountAmount: Double
        get() = grossRetailTotal * (discountPercent / 100.0)

    val discountedGross: Double
        get() = (grossRetailTotal - discountAmount).coerceAtLeast(0.0)

    // Tax component extracted from the retail price (accounting for both item discounts and cart discount)
    val taxAmount: Double
        get() = if (!isTaxEnabled) 0.0 else cartItems.sumOf { item ->
            val itemGross = item.total * (1.0 - discountPercent / 100.0)
            if (item.taxRatePercent > 0.0) {
                itemGross - (itemGross / (1.0 + (item.taxRatePercent / 100.0)))
            } else {
                0.0
            }
        }

    // Net taxable base amount (Tax Exclusive base)
    val subtotal: Double
        get() = if (!isTaxEnabled) discountedGross else cartItems.sumOf { item ->
            val itemGross = item.total * (1.0 - discountPercent / 100.0)
            if (item.taxRatePercent > 0.0) {
                itemGross / (1.0 + (item.taxRatePercent / 100.0))
            } else {
                itemGross
            }
        }

    val discountedSubtotal: Double
        get() = subtotal

    val grossTotalBeforeCredit: Double
        get() = discountedGross + tipAmount

    val creditNoteAppliedAmount: Double
        get() = if (appliedCreditNote != null && appliedCreditNote.remainingBalance > 0.0) {
            minOf(appliedCreditNote.remainingBalance, grossTotalBeforeCredit)
        } else {
            0.0
        }

    val remainingCreditNoteBalanceAfterSale: Double
        get() = if (appliedCreditNote != null) {
            (appliedCreditNote.remainingBalance - creditNoteAppliedAmount).coerceAtLeast(0.0)
        } else {
            0.0
        }

    // Customer Total = Discounted Gross Retail Price + Tip - Credit Note Adjustment
    val totalAmount: Double
        get() = (grossTotalBeforeCredit - creditNoteAppliedAmount).coerceAtLeast(0.0)

    val amountTendered: Double
        get() = amountTenderedStr.toDoubleOrNull() ?: totalAmount

    val changeDue: Double
        get() = if (amountTendered > totalAmount) amountTendered - totalAmount else 0.0

    val totalItemsCount: Int
        get() = cartItems.sumOf { it.quantity }
}

sealed class ProtectedAction {
    data class RefundReceipt(val receiptId: Long) : ProtectedAction()
    data class VoidReceipt(val receiptId: Long) : ProtectedAction()
    data class CreateCreditNoteReturn(
        val receiptId: Long,
        val returnedQuantitiesByKey: Map<String, Int>,
        val customerPhone: String,
        val customerName: String,
        val notes: String,
        val autoPrint: Boolean
    ) : ProtectedAction()
    object OpenSettings : ProtectedAction()
    object ResetDatabase : ProtectedAction()
}

class POSViewModel(
    private val repository: POSRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(POSUiState())
    val uiState: StateFlow<POSUiState> = _uiState.asStateFlow()

    // Data streams from Repository
    val products: StateFlow<List<ProductEntity>> = repository.activeProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCatalogProducts: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val businesses: StateFlow<List<com.example.data.local.entity.BusinessProfileEntity>> = repository.allBusinesses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeBusiness: StateFlow<com.example.data.local.entity.BusinessProfileEntity?> = repository.activeBusiness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories: StateFlow<List<String>> = repository.productCategories
        .map { listOf("All") + it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All"))

    val receipts: StateFlow<List<ReceiptEntity>> = repository.allReceipts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val creditNotes: StateFlow<List<CreditNoteEntity>> = repository.allCreditNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableCreditNotes: StateFlow<List<CreditNoteEntity>> = repository.availableCreditNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unsyncedCount: StateFlow<Int> = repository.unsyncedReceiptsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalSalesSum: StateFlow<Double?> = repository.totalSalesSum
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalReceiptsCount: StateFlow<Int> = repository.totalReceiptsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val syncLogs: StateFlow<List<SyncLogEntity>> = repository.syncLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val storeConfig: StateFlow<StoreConfigEntity> = repository.storeConfig
        .map { it ?: StoreConfigEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreConfigEntity())

    val currentAccount: StateFlow<com.example.data.local.entity.UserAccountEntity?> = repository.currentAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val printerState: StateFlow<PrinterConnectionState> = repository.printerManager.connectionState
    val discoveredPrinters: StateFlow<List<DiscoveredBluetoothDevice>> = repository.printerManager.discoveredDevices
    val printLogs: StateFlow<List<PrintLogEntry>> = repository.printerManager.printLogs
    val wifiPrinterState: StateFlow<com.example.printer.WifiPrinterConnectionState> = repository.wifiPrinterManager.connectionState
    val discoveredWifiPrinters: StateFlow<List<com.example.printer.DiscoveredWifiPrinter>> = repository.wifiPrinterManager.discoveredPrinters
    val syncEngineState = repository.syncManager.syncState

    private val _driveState = MutableStateFlow(repository.googleDriveBackupService.getAccountState())
    val driveState: StateFlow<com.example.data.sync.DriveAccountState> = _driveState.asStateFlow()

    fun refreshDriveState() {
        _driveState.value = repository.googleDriveBackupService.getAccountState()
    }

    init {
        refreshPairedDevices()
        startBackgroundAutoSyncEngine()
        viewModelScope.launch {
            storeConfig.collect { config ->
                _uiState.update { it.copy(isTaxEnabled = config.enableTax) }
                // Sanitize any existing cached placeholder URLs from older database versions
                if (config.syncEndpointUrl.contains("apexpos.internal")) {
                    repository.saveStoreConfig(config.copy(syncEndpointUrl = ""))
                }
            }
        }
    }

    fun toggleTaxEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = storeConfig.value
            val updated = current.copy(enableTax = enabled)
            repository.saveStoreConfig(updated)
            _uiState.update { it.copy(isTaxEnabled = enabled) }
            showToast(
                if (enabled) "Tax Calculation ENABLED (${updated.taxName} • ${updated.defaultTaxPercent}%)"
                else "Tax Calculation DISABLED (Entire POS operates in 0% Tax-Exempt Mode)"
            )
        }
    }

    private fun startBackgroundAutoSyncEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(15_000) // 15 seconds continuous sync cycle
                try {
                    if (_uiState.value.isAutoSyncActive && repository.syncManager.isNetworkAvailable()) {
                        repository.syncManager.triggerAutoSync("AUTO_PERIODIC")
                    }
                } catch (ignored: Exception) {}
            }
        }
    }

    fun toggleAutoSync(enabled: Boolean) {
        _uiState.update { it.copy(isAutoSyncActive = enabled) }
        showToast(if (enabled) "Real-time Auto-Sync enabled across devices" else "Auto-Sync paused")
    }

    fun refreshPairedDevices() {
        // Paired devices are retrieved via manager
    }

    // --- CART ACTIONS ---
    fun addToCart(product: ProductEntity) {
        val current = _uiState.value.cartItems.toMutableList()
        val index = current.indexOfFirst { it.productId == product.id && it.productId != 0L }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(quantity = existing.quantity + 1)
        } else {
            val initialDiscountType = try {
                com.example.data.model.ItemDiscountType.valueOf(product.discountType)
            } catch (e: Exception) {
                com.example.data.model.ItemDiscountType.NONE
            }
            current.add(
                CartItem(
                    productId = product.id,
                    name = product.name,
                    unitPrice = product.price,
                    costPrice = product.costPrice,
                    quantity = 1,
                    taxRatePercent = product.taxRatePercent,
                    sku = product.sku,
                    category = product.category,
                    discountType = initialDiscountType,
                    discountValue = product.discountValue
                )
            )
        }
        _uiState.value = _uiState.value.copy(cartItems = current)
    }

    fun addCustomItem(
        name: String,
        price: Double,
        quantity: Int = 1,
        taxRate: Double = 8.5,
        discountType: com.example.data.model.ItemDiscountType = com.example.data.model.ItemDiscountType.NONE,
        discountValue: Double = 0.0
    ) {
        val current = _uiState.value.cartItems.toMutableList()
        current.add(
            CartItem(
                productId = 0L,
                name = name.ifEmpty { "Custom Item" },
                unitPrice = price.coerceAtLeast(0.01),
                quantity = quantity.coerceAtLeast(1),
                taxRatePercent = taxRate,
                category = "Custom",
                discountType = discountType,
                discountValue = discountValue
            )
        )
        _uiState.value = _uiState.value.copy(cartItems = current, showCustomItemDialog = false)
    }

    fun updateCartItemQuantity(item: CartItem, newQty: Int) {
        val current = _uiState.value.cartItems.toMutableList()
        val index = current.indexOf(item)
        if (index >= 0) {
            if (newQty <= 0) {
                current.removeAt(index)
            } else {
                current[index] = item.copy(quantity = newQty)
            }
            _uiState.value = _uiState.value.copy(cartItems = current)
        }
    }

    fun updateCartItemDiscount(item: CartItem, discountType: com.example.data.model.ItemDiscountType, discountValue: Double) {
        val current = _uiState.value.cartItems.toMutableList()
        val index = current.indexOf(item)
        if (index >= 0) {
            current[index] = item.copy(
                discountType = discountType,
                discountValue = discountValue.coerceAtLeast(0.0)
            )
            _uiState.value = _uiState.value.copy(cartItems = current)
        }
    }

    fun removeCartItem(item: CartItem) {
        val current = _uiState.value.cartItems.toMutableList()
        current.remove(item)
        _uiState.value = _uiState.value.copy(cartItems = current)
    }

    fun clearCart() {
        _uiState.value = _uiState.value.copy(
            cartItems = emptyList(),
            discountPercent = 0.0,
            tipAmount = 0.0,
            appliedCreditNote = null,
            selectedCustomer = null,
            customerName = "Walk-in Customer",
            customerPhone = "",
            amountTenderedStr = "",
            notes = "",
            showCheckoutDialog = false
        )
    }

    fun applyCreditNote(creditNote: CreditNoteEntity?) {
        val current = _uiState.value
        val updatedName = if (creditNote != null && (current.customerName.isBlank() || current.customerName == "Walk-in Customer")) {
            creditNote.customerName.ifBlank { "Customer" }
        } else current.customerName
        val updatedPhone = if (creditNote != null && current.customerPhone.isBlank()) {
            creditNote.customerPhone
        } else current.customerPhone

        val nextState = current.copy(
            appliedCreditNote = creditNote,
            customerName = updatedName,
            customerPhone = updatedPhone
        )
        _uiState.value = nextState.copy(
            amountTenderedStr = String.format("%.2f", nextState.totalAmount)
        )
    }

    fun setDiscountPercent(percent: Double) {
        val next = _uiState.value.copy(discountPercent = percent.coerceIn(0.0, 100.0))
        _uiState.value = next.copy(amountTenderedStr = String.format("%.2f", next.totalAmount))
    }

    fun setTipAmount(tip: Double) {
        val next = _uiState.value.copy(tipAmount = tip.coerceAtLeast(0.0))
        _uiState.value = next.copy(amountTenderedStr = String.format("%.2f", next.totalAmount))
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _uiState.value = _uiState.value.copy(selectedPaymentMethod = method)
    }

    fun setAmountTendered(amountStr: String) {
        _uiState.value = _uiState.value.copy(amountTenderedStr = amountStr)
    }

    fun setCustomerInfo(name: String, phone: String) {
        _uiState.value = _uiState.value.copy(customerName = name, customerPhone = phone)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSelectedCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    // --- CHECKOUT & RECEIPT ---
    fun openCheckout() {
        if (_uiState.value.cartItems.isEmpty()) {
            showToast("Cart is empty! Add products first.")
            return
        }
        val total = _uiState.value.totalAmount
        _uiState.value = _uiState.value.copy(
            showCheckoutDialog = true,
            amountTenderedStr = String.format("%.2f", total)
        )
    }

    fun closeCheckout() {
        _uiState.value = _uiState.value.copy(showCheckoutDialog = false)
    }

    fun processCheckout() {
        val state = _uiState.value
        if (state.cartItems.isEmpty()) return

        viewModelScope.launch {
            try {
                val receipt = repository.checkout(
                    cartItems = state.cartItems,
                    customerName = state.customerName,
                    customerPhone = state.customerPhone,
                    paymentMethod = state.selectedPaymentMethod,
                    discountPercent = state.discountPercent,
                    tipAmount = state.tipAmount,
                    amountTendered = state.amountTendered,
                    cashierName = state.cashierName,
                    notes = state.notes,
                    autoPrint = state.autoPrintOnSale,
                    appliedCreditNote = state.appliedCreditNote
                )

                val cnMsg = if (receipt.creditNoteAppliedAmount > 0.0) {
                    " (Adjusted ${receipt.appliedCreditNoteNumber} from Bill #${receipt.appliedCreditOriginalInvoice})"
                } else ""

                _uiState.value = state.copy(
                    showCheckoutDialog = false,
                    selectedReceipt = receipt,
                    showReceiptDetailModal = true,
                    cartItems = emptyList(),
                    discountPercent = 0.0,
                    tipAmount = 0.0,
                    appliedCreditNote = null,
                    selectedCustomer = null,
                    customerName = "Walk-in Customer",
                    customerPhone = "",
                    amountTenderedStr = "",
                    toastMessage = "Payment successful! Receipt #${receipt.invoiceNumber}$cnMsg"
                )
            } catch (e: Exception) {
                showToast("Checkout error: ${e.message}")
            }
        }
    }

    // --- PRINTER ACTIONS (BLUETOOTH & WI-FI / NETWORK) ---
    fun startBluetoothScan() = repository.startPrinterDiscovery()
    fun stopBluetoothScan() = repository.stopPrinterDiscovery()
    fun connectPrinter(device: DiscoveredBluetoothDevice) = repository.connectPrinter(device)
    fun disconnectPrinter() = repository.disconnectPrinter()

    // Wi-Fi Printer Actions
    fun startWifiScan(subnet: String? = null, port: Int = 9100) = repository.startWifiScan(subnet, port)
    fun stopWifiScan() = repository.stopWifiScan()
    fun connectWifiPrinter(ip: String, port: Int = 9100, name: String = "Wi-Fi Printer") =
        repository.connectWifiPrinter(ip, port, name)
    fun disconnectWifiPrinter() = repository.disconnectWifiPrinter()
    fun addCustomWifiPrinter(ip: String, port: Int, name: String) =
        repository.addCustomWifiPrinter(ip, port, name)
    fun removeCustomWifiPrinter(device: com.example.printer.DiscoveredWifiPrinter) =
        repository.removeCustomWifiPrinter(device)
    fun getLocalDeviceIp(): String? = repository.getLocalDeviceIp()

    fun printCurrentReceipt(receiptId: Long, paperWidth: PaperWidth? = null) {
        viewModelScope.launch {
            val success = repository.printReceipt(receiptId, paperWidth)
            if (success) {
                showToast("Receipt sent to printer spooler!")
            } else {
                showToast("Printing failed. Check printer connection.")
            }
        }
    }

    fun printSelfTest() {
        viewModelScope.launch {
            val config = storeConfig.value
            val width = if (config.paperWidthMm == 80) PaperWidth.MM_80 else PaperWidth.MM_58
            repository.printSelfTest(width)
            showToast("Self-Test job queued on thermal printer!")
        }
    }

    fun kickCashDrawer() {
        viewModelScope.launch {
            repository.openCashDrawer()
            showToast("Cash Drawer pulse signal sent (PIN2 & PIN5)")
        }
    }

    fun triggerPaperCut() {
        viewModelScope.launch {
            repository.cutPaper()
            showToast("Paper Cut command sent (GS V)")
        }
    }

    // --- OFFLINE SYNC ACTIONS ---
    fun triggerManualSync() {
        viewModelScope.launch {
            val log = repository.triggerManualSync()
            showToast("Sync finished: ${log.message}")
        }
    }

    // --- SECURITY & PROTECTED ACTIONS ---
    fun requestRefund(receiptId: Long) {
        val config = storeConfig.value
        if (config.isPinRequiredForRefund && config.masterPin.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                showPinLockDialog = true,
                pendingProtectedAction = ProtectedAction.RefundReceipt(receiptId)
            )
        } else {
            executeRefund(receiptId)
        }
    }

    fun requestVoid(receiptId: Long) {
        val config = storeConfig.value
        if (config.isPinRequiredForRefund && config.masterPin.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                showPinLockDialog = true,
                pendingProtectedAction = ProtectedAction.VoidReceipt(receiptId)
            )
        } else {
            executeVoid(receiptId)
        }
    }

    fun openItemReturnDialog(preselectedReceipt: ReceiptEntity? = null) {
        _uiState.value = _uiState.value.copy(
            showItemReturnDialog = true,
            preselectedReceiptForReturn = preselectedReceipt
        )
    }

    fun closeItemReturnDialog() {
        _uiState.value = _uiState.value.copy(
            showItemReturnDialog = false,
            preselectedReceiptForReturn = null
        )
    }

    fun selectCreditNote(creditNote: CreditNoteEntity) {
        _uiState.value = _uiState.value.copy(
            selectedCreditNote = creditNote,
            showCreditNoteDetailModal = true
        )
    }

    fun closeCreditNoteModal() {
        _uiState.value = _uiState.value.copy(
            showCreditNoteDetailModal = false,
            selectedCreditNote = null
        )
    }

    fun requestCreateCreditNoteReturn(
        receiptId: Long,
        returnedQuantitiesByKey: Map<String, Int>,
        customerPhone: String,
        customerName: String,
        notes: String,
        autoPrint: Boolean = false
    ) {
        val config = storeConfig.value
        if (config.isPinRequiredForRefund && config.masterPin.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                showPinLockDialog = true,
                pendingProtectedAction = ProtectedAction.CreateCreditNoteReturn(
                    receiptId = receiptId,
                    returnedQuantitiesByKey = returnedQuantitiesByKey,
                    customerPhone = customerPhone,
                    customerName = customerName,
                    notes = notes,
                    autoPrint = autoPrint
                )
            )
        } else {
            executeCreateCreditNoteReturn(
                receiptId = receiptId,
                returnedQuantitiesByKey = returnedQuantitiesByKey,
                customerPhone = customerPhone,
                customerName = customerName,
                notes = notes,
                autoPrint = autoPrint
            )
        }
    }

    fun verifyPinAndExecute(enteredPin: String) {
        val config = storeConfig.value
        if (SecurityManager.verifyPin(enteredPin, config.masterPin)) {
            val action = _uiState.value.pendingProtectedAction
            _uiState.value = _uiState.value.copy(
                showPinLockDialog = false,
                pendingProtectedAction = null
            )
            when (action) {
                is ProtectedAction.RefundReceipt -> executeRefund(action.receiptId)
                is ProtectedAction.VoidReceipt -> executeVoid(action.receiptId)
                is ProtectedAction.CreateCreditNoteReturn -> executeCreateCreditNoteReturn(
                    receiptId = action.receiptId,
                    returnedQuantitiesByKey = action.returnedQuantitiesByKey,
                    customerPhone = action.customerPhone,
                    customerName = action.customerName,
                    notes = action.notes,
                    autoPrint = action.autoPrint
                )
                is ProtectedAction.OpenSettings -> {}
                is ProtectedAction.ResetDatabase -> {}
                null -> {}
            }
        } else {
            showToast("Incorrect Master PIN. Access Denied.")
        }
    }

    private fun executeCreateCreditNoteReturn(
        receiptId: Long,
        returnedQuantitiesByKey: Map<String, Int>,
        customerPhone: String,
        customerName: String,
        notes: String,
        autoPrint: Boolean
    ) {
        viewModelScope.launch {
            val creditNote = repository.createCreditNoteFromReturn(
                receiptId = receiptId,
                returnedQuantitiesByKey = returnedQuantitiesByKey,
                customerPhone = customerPhone,
                customerName = customerName,
                notes = notes,
                autoPrint = autoPrint
            )
            if (creditNote != null) {
                _uiState.value = _uiState.value.copy(
                    showItemReturnDialog = false,
                    preselectedReceiptForReturn = null,
                    showReceiptDetailModal = false,
                    selectedCreditNote = creditNote,
                    showCreditNoteDetailModal = true
                )
                showToast("Credit Note ${creditNote.creditNoteNumber} generated & saved for ${creditNote.customerPhone}! Stock restored.")
            } else {
                showToast("Could not generate Credit Note. Check selected item quantities.")
            }
        }
    }

    fun printCreditNote(creditNoteId: Long, paperWidth: PaperWidth? = null) {
        viewModelScope.launch {
            val success = repository.printCreditNote(creditNoteId, paperWidth)
            if (success) {
                showToast("Credit Note sent to thermal printer!")
            } else {
                showToast("Printing failed. Check printer connection.")
            }
        }
    }

    fun shareCreditNotePdf(context: android.content.Context, creditNote: CreditNoteEntity) {
        viewModelScope.launch {
            try {
                repository.shareCreditNotePdf(context, creditNote)
                showToast("Opening Credit Note PDF share options...")
            } catch (e: Exception) {
                showToast("Could not generate Credit Note PDF: ${e.message}")
            }
        }
    }

    fun printCreditNotePdf(context: android.content.Context, creditNote: CreditNoteEntity) {
        viewModelScope.launch {
            try {
                repository.printCreditNotePdf(context, creditNote)
                showToast("Sending Credit Note PDF to System Print Manager...")
            } catch (e: Exception) {
                showToast("Print error: ${e.message}")
            }
        }
    }

    private fun executeRefund(receiptId: Long) {
        viewModelScope.launch {
            repository.refundReceipt(receiptId, isVoid = false)
            showToast("Receipt #$receiptId marked as REFUNDED and queued for sync.")
            _uiState.value = _uiState.value.copy(showReceiptDetailModal = false)
        }
    }

    private fun executeVoid(receiptId: Long) {
        viewModelScope.launch {
            repository.refundReceipt(receiptId, isVoid = true)
            showToast("Receipt #$receiptId has been VOIDED.")
            _uiState.value = _uiState.value.copy(showReceiptDetailModal = false)
        }
    }

    fun verifyReceiptIntegrity(receipt: ReceiptEntity): Boolean {
        return SecurityManager.verifyReceiptIntegrity(receipt)
    }

    // --- STORE SETTINGS ---
    fun updateStoreConfig(updated: StoreConfigEntity) {
        viewModelScope.launch {
            repository.saveStoreConfig(updated)
            showToast("Store configuration saved successfully.")
        }
    }

    fun addNewProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.insertProduct(product)
            _uiState.value = _uiState.value.copy(showAddProductDialog = false)
            showToast("Product '${product.name}' added to catalog.")
        }
    }

    fun updateProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.updateProduct(product)
            _uiState.value = _uiState.value.copy(showEditProductDialog = false, productToEdit = null)
            showToast("Product '${product.name}' updated.")
        }
    }

    fun adjustProductStock(productId: Long, newStock: Int) {
        viewModelScope.launch {
            repository.adjustProductStock(productId, newStock)
            _uiState.value = _uiState.value.copy(showAdjustStockDialog = false, productForStockAdjust = null)
            showToast("Inventory stock updated.")
        }
    }

    fun toggleProductActive(productId: Long, isActive: Boolean) {
        viewModelScope.launch {
            repository.toggleProductActive(productId, isActive)
            showToast(if (isActive) "Item restored to active register" else "Item archived from active register")
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            showToast("Product deleted from catalog.")
        }
    }

    // --- MULTI-BUSINESS MANAGEMENT ---
    fun switchBusiness(businessId: Long) {
        viewModelScope.launch {
            repository.switchActiveBusiness(businessId)
            _uiState.value = _uiState.value.copy(showBusinessSelectorDialog = false)
            showToast("Switched active business.")
        }
    }

    fun addNewBusiness(business: com.example.data.local.entity.BusinessProfileEntity) {
        viewModelScope.launch {
            val id = repository.insertBusinessProfile(business)
            repository.switchActiveBusiness(id)
            _uiState.value = _uiState.value.copy(showAddBusinessDialog = false, showBusinessSelectorDialog = false)
            showToast("New business '${business.businessName}' created and activated.")
        }
    }

    fun updateBusiness(business: com.example.data.local.entity.BusinessProfileEntity) {
        viewModelScope.launch {
            repository.updateBusinessProfile(business)
            _uiState.value = _uiState.value.copy(showEditBusinessDialog = false, businessToEdit = null)
            showToast("Business profile updated.")
        }
    }

    fun deleteBusiness(business: com.example.data.local.entity.BusinessProfileEntity) {
        viewModelScope.launch {
            val success = repository.deleteBusinessProfile(business)
            if (success) {
                showToast("Business '${business.businessName}' removed.")
            } else {
                showToast("Cannot delete the only business profile.")
            }
        }
    }

    fun applyCountryPreset(preset: com.example.data.model.CountryCurrencyPreset) {
        val current = storeConfig.value
        val updated = current.copy(
            countryCode = preset.countryCode,
            countryName = preset.countryName,
            currencyCode = preset.currencyCode,
            currencySymbol = preset.currencySymbol,
            taxName = preset.taxName,
            defaultTaxPercent = preset.defaultTaxPercent,
            receiptHeader = preset.defaultReceiptHeader,
            receiptFooter = preset.defaultReceiptFooter
        )
        updateStoreConfig(updated)
        showToast("Applied ${preset.countryName} settings (${preset.currencyCode}, ${preset.defaultTaxPercent}% ${preset.taxName})")
    }

    // --- RECEIPT EDIT, DELETE & PDF ACTIONS ---
    fun openEditReceiptDialog(receipt: ReceiptEntity) {
        _uiState.value = _uiState.value.copy(showEditReceiptDialog = true, receiptToEdit = receipt)
    }

    fun closeEditReceiptDialog() {
        _uiState.value = _uiState.value.copy(showEditReceiptDialog = false, receiptToEdit = null)
    }

    fun saveEditedReceipt(
        receiptId: Long,
        customerName: String,
        customerPhone: String,
        cashierName: String,
        paymentMethod: com.example.data.model.PaymentMethod,
        notes: String,
        items: List<CartItem>,
        discountPercent: Double,
        tipAmount: Double,
        amountTendered: Double
    ) {
        viewModelScope.launch {
            val updated = repository.editReceipt(
                receiptId = receiptId,
                customerName = customerName,
                customerPhone = customerPhone,
                cashierName = cashierName,
                paymentMethod = paymentMethod,
                notes = notes,
                cartItems = items,
                discountPercent = discountPercent,
                tipAmount = tipAmount,
                amountTendered = amountTendered
            )
            if (updated != null) {
                _uiState.value = _uiState.value.copy(
                    showEditReceiptDialog = false,
                    receiptToEdit = null,
                    selectedReceipt = updated
                )
                showToast("Receipt ${updated.invoiceNumber} updated & recalculated successfully.")
            } else {
                showToast("Error updating receipt.")
            }
        }
    }

    fun openDeleteReceiptDialog(receipt: ReceiptEntity) {
        _uiState.value = _uiState.value.copy(showDeleteReceiptDialog = true, receiptToDelete = receipt)
    }

    fun closeDeleteReceiptDialog() {
        _uiState.value = _uiState.value.copy(showDeleteReceiptDialog = false, receiptToDelete = null)
    }

    fun confirmDeleteReceipt(receipt: ReceiptEntity) {
        viewModelScope.launch {
            val success = repository.deleteReceipt(receipt)
            if (success) {
                _uiState.value = _uiState.value.copy(
                    showDeleteReceiptDialog = false,
                    receiptToDelete = null,
                    showReceiptDetailModal = false,
                    selectedReceipt = null
                )
                showToast("Receipt ${receipt.invoiceNumber} deleted. Invoice numbering adjusted.")
            } else {
                showToast("Error deleting receipt.")
            }
        }
    }

    fun sharePdfInvoice(context: android.content.Context, receipt: ReceiptEntity) {
        viewModelScope.launch {
            try {
                repository.sharePdfInvoice(context, receipt)
                showToast("Opening PDF share options...")
            } catch (e: Exception) {
                showToast("Could not generate PDF: ${e.message}")
            }
        }
    }

    var isBatchPdfDownloading by mutableStateOf(false)
        private set

    var isBatchExcelExporting by mutableStateOf(false)
        private set

    fun downloadAllPdfInvoices(context: android.content.Context, receipts: List<ReceiptEntity>) {
        if (receipts.isEmpty()) {
            showToast("No receipts available to download.")
            return
        }
        viewModelScope.launch {
            isBatchPdfDownloading = true
            try {
                val (count, exportFile) = repository.downloadAllPdfInvoices(context, receipts)
                if (count > 0) {
                    showToast("Downloaded $count invoice PDF(s) to Downloads/Invoices folder!")
                    if (exportFile != null && exportFile.exists() && exportFile.isFile) {
                        com.example.util.PdfInvoiceGenerator.shareFile(context, exportFile, "All Invoices PDF Archive")
                    }
                } else {
                    showToast("Failed to generate invoice PDFs.")
                }
            } catch (e: Exception) {
                showToast("Download error: ${e.message}")
            } finally {
                isBatchPdfDownloading = false
            }
        }
    }

    fun exportAllInvoicesToExcel(context: android.content.Context, receipts: List<ReceiptEntity>) {
        if (receipts.isEmpty()) {
            showToast("No receipts available to export.")
            return
        }
        viewModelScope.launch {
            isBatchExcelExporting = true
            try {
                val exportFile = repository.exportInvoicesToExcel(context, receipts)
                if (exportFile.exists() && exportFile.length() > 0) {
                    showToast("Exported ${receipts.size} invoices to Excel! (${exportFile.name})")
                    com.example.util.ExcelInvoiceExporter.shareExcelFile(context, exportFile, "All Invoices Excel Export")
                } else {
                    showToast("Failed to export Excel file.")
                }
            } catch (e: Exception) {
                showToast("Excel export error: ${e.message}")
            } finally {
                isBatchExcelExporting = false
            }
        }
    }

    fun printPdfInvoice(context: android.content.Context, receipt: ReceiptEntity) {
        viewModelScope.launch {
            try {
                repository.printPdfInvoice(context, receipt)
                showToast("Sending PDF to System Print Manager...")
            } catch (e: Exception) {
                showToast("Print error: ${e.message}")
            }
        }
    }

    fun setShowWebSyncDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showWebSyncDialog = show)
    }

    // --- MODAL CONTROLS ---
    fun selectReceipt(receipt: ReceiptEntity) {
        _uiState.value = _uiState.value.copy(selectedReceipt = receipt, showReceiptDetailModal = true)
    }

    fun closeReceiptModal() {
        _uiState.value = _uiState.value.copy(showReceiptDetailModal = false, selectedReceipt = null)
    }

    fun setShowCustomItemDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCustomItemDialog = show)
    }

    fun setShowAddProductDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddProductDialog = show)
    }

    fun openEditProductDialog(product: ProductEntity) {
        _uiState.value = _uiState.value.copy(showEditProductDialog = true, productToEdit = product)
    }

    fun closeEditProductDialog() {
        _uiState.value = _uiState.value.copy(showEditProductDialog = false, productToEdit = null)
    }

    fun openAdjustStockDialog(product: ProductEntity) {
        _uiState.value = _uiState.value.copy(showAdjustStockDialog = true, productForStockAdjust = product)
    }

    fun closeAdjustStockDialog() {
        _uiState.value = _uiState.value.copy(showAdjustStockDialog = false, productForStockAdjust = null)
    }

    fun setShowBusinessSelectorDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showBusinessSelectorDialog = show)
    }

    fun setShowAddBusinessDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddBusinessDialog = show)
    }

    fun openEditBusinessDialog(business: com.example.data.local.entity.BusinessProfileEntity) {
        _uiState.value = _uiState.value.copy(showEditBusinessDialog = true, businessToEdit = business)
    }

    fun closeEditBusinessDialog() {
        _uiState.value = _uiState.value.copy(showEditBusinessDialog = false, businessToEdit = null)
    }

    fun setShowCurrencyCountryDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCurrencyCountryDialog = show)
    }

    fun setShowBarcodeScannerDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showBarcodeScannerDialog = show)
    }

    fun setShowPinLockDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPinLockDialog = show)
    }

    fun showToast(msg: String) {
        _uiState.value = _uiState.value.copy(toastMessage = msg)
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    fun setAutoPrintOnSale(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoPrintOnSale = enabled)
    }

    fun toggleTheme(systemIsDark: Boolean) {
        val currentIsDark = _uiState.value.isDarkTheme ?: systemIsDark
        val nextMode = !currentIsDark
        _uiState.update { it.copy(isDarkTheme = nextMode) }
        showToast(if (nextMode) "Dark Theme activated" else "Light Theme activated")
    }

    fun setThemeMode(isDark: Boolean?) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
        val msg = when (isDark) {
            true -> "Dark Theme activated"
            false -> "Light Theme activated"
            null -> "System Default Theme set"
        }
        showToast(msg)
    }

    fun selectWalkInCustomer() {
        _uiState.update {
            it.copy(
                selectedCustomer = null,
                customerName = "Walk-in Customer",
                customerPhone = "",
                showCustomerSelectionDialog = false
            )
        }
        showToast("Switched to Walk-in Customer")
    }

    fun selectCustomer(customer: CustomerEntity) {
        _uiState.update {
            it.copy(
                selectedCustomer = customer,
                customerName = customer.name,
                customerPhone = customer.phone,
                showCustomerSelectionDialog = false
            )
        }
        showToast("Customer '${customer.name}' attached to sale")
    }

    fun setShowCustomerSelectionDialog(show: Boolean) {
        _uiState.update { it.copy(showCustomerSelectionDialog = show) }
    }

    fun setShowAddCustomerDialog(show: Boolean) {
        _uiState.update { it.copy(showAddCustomerDialog = show) }
    }

    fun openEditCustomerDialog(customer: CustomerEntity) {
        _uiState.update { it.copy(showEditCustomerDialog = true, customerToEdit = customer) }
    }

    fun closeEditCustomerDialog() {
        _uiState.update { it.copy(showEditCustomerDialog = false, customerToEdit = null) }
    }

    fun saveCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            try {
                val id = repository.saveCustomer(customer)
                val updated = if (customer.id == 0L) customer.copy(id = id) else customer
                // If this customer is currently selected, refresh uiState
                if (_uiState.value.selectedCustomer?.id == updated.id || _uiState.value.customerName == updated.name) {
                    _uiState.update {
                        it.copy(
                            selectedCustomer = updated,
                            customerName = updated.name,
                            customerPhone = updated.phone
                        )
                    }
                }
                closeEditCustomerDialog()
                setShowAddCustomerDialog(false)
                showToast("Customer '${customer.name}' saved successfully")
            } catch (e: Exception) {
                showToast("Error saving customer: ${e.message}")
            }
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            try {
                repository.deleteCustomer(customer)
                if (_uiState.value.selectedCustomer?.id == customer.id) {
                    selectWalkInCustomer()
                }
                showToast("Customer '${customer.name}' deleted")
            } catch (e: Exception) {
                showToast("Error deleting customer: ${e.message}")
            }
        }
    }

    fun login(email: String, password: String? = null, onResult: (Boolean, String?) -> Unit) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            onResult(false, "Please enter your email address")
            return
        }
        val isGmail = trimmedEmail.endsWith("@gmail.com", ignoreCase = true) || trimmedEmail.endsWith("@googlemail.com", ignoreCase = true)
        val isOutlook = trimmedEmail.endsWith("@outlook.com", ignoreCase = true) ||
                trimmedEmail.endsWith("@hotmail.com", ignoreCase = true) ||
                trimmedEmail.endsWith("@live.com", ignoreCase = true) ||
                trimmedEmail.endsWith("@msn.com", ignoreCase = true)

        if (!isGmail && !isOutlook) {
            onResult(false, "Only Gmail (@gmail.com) or Outlook (@outlook.com) accounts are supported.")
            return
        }

        viewModelScope.launch {
            try {
                val account = repository.loginAccount(trimmedEmail, password)
                showToast("Logged in as ${account.displayName} (${account.email})")
                // Trigger auto cloud sync for the logged-in account
                repository.syncManager.triggerAutoSync("LOGIN_INITIAL")
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message ?: "Authentication failed")
            }
        }
    }

    fun register(
        email: String,
        displayName: String,
        password: String,
        provider: com.example.data.local.entity.AccountProvider,
        onResult: (Boolean, String?) -> Unit
    ) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            onResult(false, "Email cannot be empty")
            return
        }

        val isGmail = trimmedEmail.endsWith("@gmail.com", ignoreCase = true) || trimmedEmail.endsWith("@googlemail.com", ignoreCase = true)
        val isOutlook = trimmedEmail.endsWith("@outlook.com", ignoreCase = true) ||
                trimmedEmail.endsWith("@hotmail.com", ignoreCase = true) ||
                trimmedEmail.endsWith("@live.com", ignoreCase = true) ||
                trimmedEmail.endsWith("@msn.com", ignoreCase = true)

        if (!isGmail && !isOutlook) {
            onResult(false, "Only Gmail or Outlook accounts are supported.")
            return
        }

        if (password.length < 6) {
            onResult(false, "Password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            try {
                val account = repository.registerAccount(trimmedEmail, displayName, password, provider)
                showToast("Account created! Cloud storage linked to ${account.email}")
                repository.syncManager.triggerAutoSync("REGISTER_INITIAL")
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message ?: "Registration failed")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.logoutCurrentAccount()
            showToast("Successfully signed out. Cloud sync paused.")
        }
    }

    // --- GOOGLE DRIVE BACKUP & RESTORE ---
    fun connectGoogleDriveAccount(
        email: String,
        mode: com.example.data.sync.DriveConnectionMode = com.example.data.sync.DriveConnectionMode.APPS_SCRIPT_BRIDGE,
        bridgeUrl: String = "",
        oauthToken: String = "",
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val trimmed = email.trim()
        if (!repository.googleDriveBackupService.isValidEmail(trimmed)) {
            val err = "Please enter a valid Gmail address (e.g. name@gmail.com)."
            showToast(err)
            onResult(false, err)
            return
        }
        val success = repository.googleDriveBackupService.connectAccount(
            email = trimmed,
            mode = mode,
            bridgeUrl = bridgeUrl,
            oauthToken = oauthToken
        )
        if (success) {
            refreshDriveState()
            showToast("Google Drive settings updated: $trimmed")
            onResult(true, "Connected to $trimmed")
        } else {
            val err = "Failed to connect Google Drive account."
            showToast(err)
            onResult(false, err)
        }
    }

    fun testGoogleDriveConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repository.googleDriveBackupService.testDriveConnection()
            refreshDriveState()
            when (res) {
                is com.example.data.sync.DriveOperationResult.Success -> {
                    showToast(res.message)
                    onResult(true, res.message)
                }
                is com.example.data.sync.DriveOperationResult.Error -> {
                    showToast(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun getAppsScriptTemplate(): String {
        return repository.googleDriveBackupService.getAppsScriptTemplateCode()
    }

    fun disconnectGoogleDriveAccount() {
        repository.googleDriveBackupService.disconnectAccount()
        refreshDriveState()
        showToast("Google Drive account disconnected.")
    }

    fun uploadBackupToGoogleDrive(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.googleDriveBackupService.uploadBackupToDrive()
            refreshDriveState()
            when (result) {
                is com.example.data.sync.DriveOperationResult.Success -> {
                    showToast(result.message)
                    onComplete(true, result.message)
                }
                is com.example.data.sync.DriveOperationResult.Error -> {
                    showToast(result.message)
                    onComplete(false, result.message)
                }
            }
        }
    }

    fun restoreBackupFromGoogleDrive(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.googleDriveBackupService.restoreBackupFromDrive()
            refreshDriveState()
            when (result) {
                is com.example.data.sync.DriveOperationResult.Success -> {
                    showToast(result.message)
                    onComplete(true, result.message)
                }
                is com.example.data.sync.DriveOperationResult.Error -> {
                    showToast(result.message)
                    onComplete(false, result.message)
                }
            }
        }
    }

    fun updateGmailSync(gmail: String) {
        connectGoogleDriveAccount(gmail) { _, _ -> }
    }

    fun removeGmailSync() {
        disconnectGoogleDriveAccount()
    }

    fun syncWithRestEndpoint(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val config = storeConfig.value
            val account = currentAccount.value
            val email = config.email.ifBlank { account?.email ?: "terminal@apexpos.app" }
            val url = config.syncEndpointUrl.trim()

            if (url.isBlank() || !url.startsWith("http")) {
                val err = "Please enter your Google Apps Script Webhook URL or Cloud Endpoint in Settings / Sync Center."
                showToast(err)
                onComplete(false, err)
                return@launch
            }

            val result = repository.cloudRestSyncService.pushToRemoteEndpoint(url, email)
            if (result.success) {
                showToast("Cloud Push: ${result.message}")
                repository.syncManager.triggerAutoSync("REST_WEBHOOK")
                onComplete(true, result.message)
            } else {
                showToast(result.message)
                onComplete(false, result.message)
            }
        }
    }

    fun pullFromRestEndpoint(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val config = storeConfig.value
            val account = currentAccount.value
            val email = config.email.ifBlank { account?.email ?: "terminal@apexpos.app" }
            val url = config.syncEndpointUrl.trim()

            if (url.isBlank() || !url.startsWith("http")) {
                val err = "Please enter your Google Apps Script Webhook URL or Cloud Endpoint in Settings / Sync Center."
                showToast(err)
                onComplete(false, err)
                return@launch
            }

            val result = repository.cloudRestSyncService.pullFromRemoteEndpoint(url, email)
            if (result.success) {
                showToast(result.message)
                onComplete(true, result.message)
            } else {
                showToast(result.message)
                onComplete(false, result.message)
            }
        }
    }

    suspend fun getFullBackupJson(): String {
        val email = currentAccount.value?.email ?: storeConfig.value.email.ifBlank { "admin@apexpos.internal" }
        return repository.cloudRestSyncService.generateCompleteBackupJson(email)
    }

    fun restoreFromJsonString(jsonStr: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val count = repository.cloudRestSyncService.restoreDatabaseFromJson(jsonStr)
                if (count > 0) {
                    val msg = "Successfully restored $count items (products, receipts, config) from Backup!"
                    showToast(msg)
                    onComplete(true, msg)
                } else {
                    val msg = "No valid records found in the provided JSON backup."
                    showToast(msg)
                    onComplete(false, msg)
                }
            } catch (e: Exception) {
                val msg = "Restore failed: ${e.localizedMessage ?: "Invalid JSON"}"
                showToast(msg)
                onComplete(false, msg)
            }
        }
    }

    fun parseItemsJson(json: String): List<CartItem> = repository.parseItems(json)
}

class POSViewModelFactory(private val repository: POSRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(POSViewModel::class.java)) {
            return POSViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
