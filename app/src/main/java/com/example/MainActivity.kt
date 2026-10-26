package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.printer.PrinterConnectionState
import com.example.ui.POSViewModel
import com.example.ui.POSViewModelFactory
import com.example.ui.components.PinLockDialog
import com.example.ui.screens.*
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

enum class AppScreen(val label: String, val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector, val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    REGISTER("Register", Icons.Filled.PointOfSale, Icons.Outlined.PointOfSale),
    ITEMS("Items", Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
    RECEIPTS("Receipts", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    CUSTOMERS("Customers", Icons.Filled.People, Icons.Outlined.People),
    PRINTER("Printer", Icons.Filled.Print, Icons.Outlined.Print),
    BACKUP("Backup", Icons.Filled.SettingsBackupRestore, Icons.Outlined.SettingsBackupRestore),
    SETTINGS("Settings", Icons.Filled.Storefront, Icons.Outlined.Storefront)
}

class MainActivity : ComponentActivity() {

    private val viewModel: POSViewModel by viewModels {
        val app = application as POSApplication
        POSViewModelFactory(app.repository)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = uiState.isDarkTheme ?: systemDark

            MyApplicationTheme(darkTheme = isDarkTheme) {
                val products by viewModel.products.collectAsStateWithLifecycle()
                val allCatalogProducts by viewModel.allCatalogProducts.collectAsStateWithLifecycle()
                val businesses by viewModel.businesses.collectAsStateWithLifecycle()
                val categories by viewModel.categories.collectAsStateWithLifecycle()
                val receipts by viewModel.receipts.collectAsStateWithLifecycle()
                val customers by viewModel.customers.collectAsStateWithLifecycle()
                val unsyncedCount by viewModel.unsyncedCount.collectAsStateWithLifecycle()
                val totalSales by viewModel.totalSalesSum.collectAsStateWithLifecycle()
                val storeConfig by viewModel.storeConfig.collectAsStateWithLifecycle()
                val printerState by viewModel.printerState.collectAsStateWithLifecycle()
                val discoveredPrinters by viewModel.discoveredPrinters.collectAsStateWithLifecycle()
                val wifiPrinterState by viewModel.wifiPrinterState.collectAsStateWithLifecycle()
                val discoveredWifiPrinters by viewModel.discoveredWifiPrinters.collectAsStateWithLifecycle()
                val printLogs by viewModel.printLogs.collectAsStateWithLifecycle()
                val syncState by viewModel.syncEngineState.collectAsStateWithLifecycle()
                val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()

                val isAnyPrinterConnected = printerState is PrinterConnectionState.Connected ||
                        wifiPrinterState is com.example.printer.WifiPrinterConnectionState.Connected
                val isAnyPrinterBusy = printerState is PrinterConnectionState.Printing ||
                        printerState is PrinterConnectionState.Connecting ||
                        wifiPrinterState is com.example.printer.WifiPrinterConnectionState.Printing ||
                        wifiPrinterState is com.example.printer.WifiPrinterConnectionState.Connecting
                val isAnyPrinterError = printerState is PrinterConnectionState.Error ||
                        wifiPrinterState is com.example.printer.WifiPrinterConnectionState.Error

                val currentAccount by viewModel.currentAccount.collectAsStateWithLifecycle()

                var currentScreen by remember { mutableStateOf(AppScreen.REGISTER) }
                val snackbarHostState = remember { SnackbarHostState() }

                // Trigger snackbar on toast message
                LaunchedEffect(uiState.toastMessage) {
                    uiState.toastMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearToast()
                    }
                }

                // Responsive layout container
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideScreen = maxWidth >= 600.dp
                    val isCompactHeight = maxHeight < 500.dp

                    // Reusable top bar
                    val topAppBarComposable: @Composable () -> Unit = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            title = {
                                val currentPreset = com.example.data.model.CurrencyCountryPresets.findPresetByCountry(storeConfig.countryCode)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { viewModel.setShowBusinessSelectorDialog(true) }
                                ) {
                                    Text(
                                        text = currentPreset?.flagEmoji ?: "🏪",
                                        fontSize = 20.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = storeConfig.businessName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Black,
                                                maxLines = 1
                                            )
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Switch Store",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = "${storeConfig.currencyCode} (${storeConfig.currencySymbol}) • ${storeConfig.defaultTaxPercent}% ${storeConfig.taxName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                }
                            },
                            actions = {
                                // Dark Theme & Light Theme changing Button
                                IconButton(
                                    onClick = { viewModel.toggleTheme(systemDark) },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .testTag("theme_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                        contentDescription = if (isDarkTheme) "Switch to Light Theme" else "Switch to Dark Theme",
                                        tint = if (isDarkTheme) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Real-time Dual Bluetooth & Wi-Fi Printer Status Pill
                                Surface(
                                    onClick = { currentScreen = AppScreen.PRINTER },
                                    shape = RoundedCornerShape(20.dp),
                                    color = when {
                                        isAnyPrinterConnected -> StatusSuccess.copy(alpha = 0.15f)
                                        isAnyPrinterBusy -> MaterialTheme.colorScheme.primaryContainer
                                        isAnyPrinterError -> StatusError.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isAnyPrinterConnected -> StatusSuccess
                                                        isAnyPrinterError -> StatusError
                                                        isAnyPrinterBusy -> MaterialTheme.colorScheme.primary
                                                        else -> Color.Gray
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = when {
                                                wifiPrinterState is com.example.printer.WifiPrinterConnectionState.Connected -> Icons.Default.Wifi
                                                printerState is PrinterConnectionState.Connected -> Icons.Default.Bluetooth
                                                else -> Icons.Default.Print
                                            },
                                            contentDescription = "Printer Status",
                                            modifier = Modifier.size(15.dp),
                                            tint = when {
                                                isAnyPrinterConnected -> StatusSuccess
                                                isAnyPrinterError -> StatusError
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }

                    // Content switcher
                    val screenContent: @Composable () -> Unit = {
                        when (currentScreen) {
                            AppScreen.REGISTER -> RegisterScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                products = products,
                                categories = categories,
                                storeConfig = storeConfig
                            )
                            AppScreen.ITEMS -> ItemManagementScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                catalogProducts = allCatalogProducts,
                                categories = categories,
                                storeConfig = storeConfig
                            )
                            AppScreen.RECEIPTS -> ReceiptsScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                receipts = receipts,
                                totalSales = totalSales,
                                unsyncedCount = unsyncedCount,
                                storeConfig = storeConfig
                            )
                            AppScreen.CUSTOMERS -> CustomersScreen(
                                viewModel = viewModel
                            )
                            AppScreen.PRINTER -> PrinterScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                printerState = printerState,
                                discoveredPrinters = discoveredPrinters,
                                wifiPrinterState = wifiPrinterState,
                                discoveredWifiPrinters = discoveredWifiPrinters,
                                printLogs = printLogs,
                                storeConfig = storeConfig
                            )
                            AppScreen.BACKUP -> SyncCenterScreen(
                                viewModel = viewModel,
                                syncState = syncState,
                                syncLogs = syncLogs,
                                unsyncedCount = unsyncedCount
                            )
                            AppScreen.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                storeConfig = storeConfig,
                                businesses = businesses,
                                products = products
                            )
                        }
                    }

                    if (isWideScreen) {
                        // Wide Screen / Landscape: Navigation Rail (Left) + Scaffold (Right)
                        Row(modifier = Modifier.fillMaxSize()) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                header = {
                                    IconButton(
                                        onClick = { viewModel.setShowBusinessSelectorDialog(true) },
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Storefront, contentDescription = "Store", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                AppScreen.values().forEach { screen ->
                                    val isSelected = currentScreen == screen
                                    NavigationRailItem(
                                        selected = isSelected,
                                        onClick = { currentScreen = screen },
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    if (screen == AppScreen.PRINTER && isAnyPrinterConnected) {
                                                        Badge(containerColor = StatusSuccess)
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                                    contentDescription = screen.label,
                                                    modifier = Modifier.size(if (isCompactHeight) 20.dp else 24.dp)
                                                )
                                            }
                                        },
                                        label = { Text(screen.label, fontSize = 9.5.sp, maxLines = 1) },
                                        modifier = Modifier
                                            .testTag("nav_${screen.name.lowercase()}")
                                            .padding(vertical = 1.dp)
                                    )
                                }
                            }

                            VerticalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            Scaffold(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                topBar = topAppBarComposable,
                                snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    screenContent()
                                }
                            }
                        }
                    } else {
                        // Compact / Standard Mobile Screen: TopAppBar + Bottom NavigationBar
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            topBar = topAppBarComposable,
                            bottomBar = {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 4.dp
                                ) {
                                    AppScreen.values().forEach { screen ->
                                        val isSelected = currentScreen == screen
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { currentScreen = screen },
                                            alwaysShowLabel = false,
                                            icon = {
                                                BadgedBox(
                                                    badge = {
                                                        if (screen == AppScreen.PRINTER && isAnyPrinterConnected) {
                                                            Badge(containerColor = StatusSuccess)
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                                        contentDescription = screen.label
                                                    )
                                                }
                                            },
                                            label = { Text(screen.label, fontSize = 10.sp, maxLines = 1) },
                                            modifier = Modifier.testTag("nav_${screen.name.lowercase()}")
                                        )
                                    }
                                }
                            },
                            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                ) {
                                screenContent()
                            }
                        }
                    }
                }

                    // Security PIN Lock Dialog for protected actions
                    if (uiState.showPinLockDialog) {
                        PinLockDialog(
                            action = uiState.pendingProtectedAction,
                            onDismiss = { viewModel.setShowPinLockDialog(false) },
                            onPinEntered = { pin -> viewModel.verifyPinAndExecute(pin) }
                        )
                    }

                    // Global Receipt Detail Modal
                    if (uiState.showReceiptDetailModal && uiState.selectedReceipt != null) {
                        val receipt = uiState.selectedReceipt!!
                        val items = viewModel.parseItemsJson(receipt.itemsJson)
                        val isVerified = viewModel.verifyReceiptIntegrity(receipt)
                        val context = androidx.compose.ui.platform.LocalContext.current
                        com.example.ui.components.ReceiptDetailModal(
                            receipt = receipt,
                            config = storeConfig,
                            items = items,
                            isTamperVerified = isVerified,
                            onDismiss = { viewModel.closeReceiptModal() },
                            onPrint = { width -> viewModel.printCurrentReceipt(receipt.id, width) },
                            onSharePdf = { viewModel.sharePdfInvoice(context, receipt) },
                            onPrintPdf = { viewModel.printPdfInvoice(context, receipt) },
                            onEdit = { viewModel.openEditReceiptDialog(receipt) },
                            onDelete = { viewModel.openDeleteReceiptDialog(receipt) },
                            onItemReturn = { viewModel.openItemReturnDialog(receipt) },
                            onRefund = { viewModel.requestRefund(receipt.id) },
                            onVoid = { viewModel.requestVoid(receipt.id) }
                        )
                    }

                    // Global Partial Item Return & Credit Note Dialog (by Customer Phone)
                    if (uiState.showItemReturnDialog) {
                        com.example.ui.components.ItemReturnByPhoneDialog(
                            receipts = receipts,
                            customers = customers,
                            preselectedReceipt = uiState.preselectedReceiptForReturn,
                            config = storeConfig,
                            parseItems = { viewModel.parseItemsJson(it) },
                            onDismiss = { viewModel.closeItemReturnDialog() },
                            onConfirmReturn = { receiptId, returnedQuantitiesByKey, customerPhone, customerName, notes, autoPrint ->
                                viewModel.requestCreateCreditNoteReturn(
                                    receiptId = receiptId,
                                    returnedQuantitiesByKey = returnedQuantitiesByKey,
                                    customerPhone = customerPhone,
                                    customerName = customerName,
                                    notes = notes,
                                    autoPrint = autoPrint
                                )
                            }
                        )
                    }

                    // Global Credit Note Detail & Print Modal
                    if (uiState.showCreditNoteDetailModal && uiState.selectedCreditNote != null) {
                        val cn = uiState.selectedCreditNote!!
                        val returnedItems = viewModel.parseItemsJson(cn.returnedItemsJson)
                        val context = androidx.compose.ui.platform.LocalContext.current
                        com.example.ui.components.CreditNoteDetailModal(
                            creditNote = cn,
                            config = storeConfig,
                            returnedItems = returnedItems,
                            onDismiss = { viewModel.closeCreditNoteModal() },
                            onPrintThermal = { width -> viewModel.printCreditNote(cn.id, width) },
                            onSharePdf = { viewModel.shareCreditNotePdf(context, cn) },
                            onPrintPdf = { viewModel.printCreditNotePdf(context, cn) }
                        )
                    }

                    // Global Edit Receipt Dialog
                    if (uiState.showEditReceiptDialog && uiState.receiptToEdit != null) {
                        val receipt = uiState.receiptToEdit!!
                        val items = viewModel.parseItemsJson(receipt.itemsJson)
                        com.example.ui.components.EditReceiptDialog(
                            receipt = receipt,
                            config = storeConfig,
                            initialItems = items,
                            onDismiss = { viewModel.closeEditReceiptDialog() },
                            onSave = { receiptId, customerName, customerPhone, cashierName, paymentMethod, notes, updatedItems, discountPercent, tipAmount, amountTendered ->
                                viewModel.saveEditedReceipt(
                                    receiptId = receiptId,
                                    customerName = customerName,
                                    customerPhone = customerPhone,
                                    cashierName = cashierName,
                                    paymentMethod = paymentMethod,
                                    notes = notes,
                                    items = updatedItems,
                                    discountPercent = discountPercent,
                                    tipAmount = tipAmount,
                                    amountTendered = amountTendered
                                )
                            }
                        )
                    }

                    // Global Delete Receipt Confirmation Dialog
                    if (uiState.showDeleteReceiptDialog && uiState.receiptToDelete != null) {
                        com.example.ui.components.DeleteReceiptConfirmationDialog(
                            receipt = uiState.receiptToDelete!!,
                            onDismiss = { viewModel.closeDeleteReceiptDialog() },
                            onConfirm = { viewModel.confirmDeleteReceipt(uiState.receiptToDelete!!) }
                        )
                    }

                    // Global Business Selector Dialog
                    if (uiState.showBusinessSelectorDialog) {
                        com.example.ui.components.BusinessSelectorDialog(
                            businesses = businesses,
                            activeBusinessId = storeConfig.activeBusinessId,
                            onSelectBusiness = { businessId ->
                                viewModel.switchBusiness(businessId)
                                viewModel.setShowBusinessSelectorDialog(false)
                            },
                            onAddNewBusiness = {
                                viewModel.setShowBusinessSelectorDialog(false)
                                viewModel.setShowAddBusinessDialog(true)
                            },
                            onEditBusiness = { bus ->
                                viewModel.setShowBusinessSelectorDialog(false)
                                viewModel.openEditBusinessDialog(bus)
                            },
                            onDeleteBusiness = { bus ->
                                viewModel.deleteBusiness(bus)
                            },
                            onDismiss = { viewModel.setShowBusinessSelectorDialog(false) }
                        )
                    }

                    // Global Add Business Dialog
                    if (uiState.showAddBusinessDialog) {
                        com.example.ui.components.AddEditBusinessDialog(
                            initialBusiness = null,
                            onSave = { newBus ->
                                viewModel.addNewBusiness(newBus)
                                viewModel.setShowAddBusinessDialog(false)
                            },
                            onDismiss = { viewModel.setShowAddBusinessDialog(false) }
                        )
                    }

                    // Global Edit Business Dialog
                    if (uiState.showEditBusinessDialog && uiState.businessToEdit != null) {
                        com.example.ui.components.AddEditBusinessDialog(
                            initialBusiness = uiState.businessToEdit,
                            onSave = { updatedBus ->
                                viewModel.updateBusiness(updatedBus)
                                viewModel.closeEditBusinessDialog()
                            },
                            onDismiss = { viewModel.closeEditBusinessDialog() }
                        )
                    }

                    // Global Currency & Country Selector Dialog
                    if (uiState.showCurrencyCountryDialog) {
                        com.example.ui.components.CountryCurrencyPresetDialog(
                            currentCountryCode = storeConfig.countryCode,
                            onSelectPreset = { preset ->
                                viewModel.applyCountryPreset(preset)
                                viewModel.setShowCurrencyCountryDialog(false)
                            },
                            onDismiss = { viewModel.setShowCurrencyCountryDialog(false) }
                        )
                    }
            }
        }
    }
}
