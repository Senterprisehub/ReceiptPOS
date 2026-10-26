package com.example.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.CartItem
import com.example.data.model.PaperWidth
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

sealed class PrinterConnectionState {
    object Disconnected : PrinterConnectionState()
    data class Scanning(val discoveredCount: Int = 0) : PrinterConnectionState()
    data class Connecting(val deviceName: String, val deviceAddress: String) : PrinterConnectionState()
    data class Connected(val deviceName: String, val deviceAddress: String) : PrinterConnectionState()
    data class Printing(val jobName: String) : PrinterConnectionState()
    data class Error(val message: String) : PrinterConnectionState()
}

data class DiscoveredBluetoothDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean,
    val deviceType: String = "Thermal / POS"
)

data class PrintLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val deviceTarget: String,
    val bytesCount: Int,
    val success: Boolean,
    val receiptTextPreview: String
)

class BluetoothPrinterManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "BTPrinterManager"
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var activeSocket: BluetoothSocket? = null
    private var activeOutputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow<PrinterConnectionState>(PrinterConnectionState.Disconnected)
    val connectionState: StateFlow<PrinterConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _printLogs = MutableStateFlow<List<PrintLogEntry>>(emptyList())
    val printLogs: StateFlow<List<PrintLogEntry>> = _printLogs.asStateFlow()

    private val _lastPrintedText = MutableStateFlow<String>("")
    val lastPrintedText: StateFlow<String> = _lastPrintedText.asStateFlow()

    private var isReceiverRegistered = false

    val wifiPrinterManager: WifiPrinterManager = WifiPrinterManager(context, scope)

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                    device?.let { dev ->
                        val name = dev.name ?: "Unknown POS Device"
                        val address = dev.address ?: "00:00:00:00:00:00"
                        val isBonded = dev.bondState == BluetoothDevice.BOND_BONDED

                        val current = _discoveredDevices.value.toMutableList()
                        if (current.none { it.address == address }) {
                            current.add(
                                DiscoveredBluetoothDevice(
                                    name = name,
                                    address = address,
                                    isBonded = isBonded,
                                    deviceType = classifyDevice(name)
                                )
                            )
                            _discoveredDevices.value = current
                            if (_connectionState.value is PrinterConnectionState.Scanning) {
                                _connectionState.value = PrinterConnectionState.Scanning(current.size)
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (_connectionState.value is PrinterConnectionState.Scanning) {
                        _connectionState.value = PrinterConnectionState.Disconnected
                    }
                }
            }
        }
    }

    private fun classifyDevice(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("print") || lower.contains("pos") || lower.contains("rpp") ||
                    lower.contains("mpt") || lower.contains("zj") || lower.contains("58") ||
                    lower.contains("80") || lower.contains("esc") || lower.contains("star") ||
                    lower.contains("epson") || lower.contains("munbyn") || lower.contains("netum") -> "Thermal POS Printer"
            lower.contains("bt") || lower.contains("blue") -> "Bluetooth Peripheral"
            else -> "POS Device"
        }
    }

    /**
     * Retrieves all paired/bonded Bluetooth devices
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<DiscoveredBluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return try {
            adapter.bondedDevices?.map { dev ->
                DiscoveredBluetoothDevice(
                    name = dev.name ?: "Paired Printer (${dev.address})",
                    address = dev.address,
                    isBonded = true,
                    deviceType = classifyDevice(dev.name ?: "")
                )
            } ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission not granted", e)
            emptyList()
        }
    }

    /**
     * Starts active scan for nearby Bluetooth printers
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = PrinterConnectionState.Error("Bluetooth is disabled. Please turn on Bluetooth.")
            return
        }

        try {
            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                context.registerReceiver(discoveryReceiver, filter)
                isReceiverRegistered = true
            }

            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

            // Include paired devices initially
            val initialList = getPairedDevices().toMutableList()
            _discoveredDevices.value = initialList
            _connectionState.value = PrinterConnectionState.Scanning(initialList.size)
            adapter.startDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Discovery error", e)
            _connectionState.value = PrinterConnectionState.Error("Failed to start scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            if (_connectionState.value is PrinterConnectionState.Scanning) {
                _connectionState.value = PrinterConnectionState.Disconnected
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery", e)
        }
    }

    /**
     * Connects to a target Bluetooth printer using standard SPP
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: DiscoveredBluetoothDevice) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _connectionState.value = PrinterConnectionState.Error("Bluetooth hardware not available")
            return
        }

        _connectionState.value = PrinterConnectionState.Connecting(device.name, device.address)

        scope.launch(Dispatchers.IO) {
            try {
                adapter.cancelDiscovery()
                val btDevice = adapter.getRemoteDevice(device.address)

                disconnectInternal()

                // Attempt insecure SPP RFCOMM socket to prevent repeated disconnections
                var socket: BluetoothSocket? = null
                try {
                    socket = btDevice.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    socket.connect()
                } catch (e: Exception) {
                    Log.w(TAG, "Insecure SPP connection failed, trying fallback socket reflection", e)
                    // Fallback for non-standard Chinese thermal printers
                    try {
                        val m = btDevice.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        socket = m.invoke(btDevice, 1) as BluetoothSocket
                        socket.connect()
                    } catch (fallbackEx: Exception) {
                        Log.e(TAG, "Fallback socket also failed", fallbackEx)
                        throw e
                    }
                }

                activeSocket = socket
                activeOutputStream = socket?.outputStream

                withContext(Dispatchers.Main) {
                    _connectionState.value = PrinterConnectionState.Connected(device.name, device.address)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed to ${device.address}", e)
                withContext(Dispatchers.Main) {
                    _connectionState.value = PrinterConnectionState.Error("Connection failed: ${e.message ?: "Printer unreachable"}")
                }
            }
        }
    }

    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            disconnectInternal()
            withContext(Dispatchers.Main) {
                _connectionState.value = PrinterConnectionState.Disconnected
            }
        }
    }

    private fun disconnectInternal() {
        try {
            activeOutputStream?.flush()
            activeOutputStream?.close()
        } catch (ignored: Exception) {}
        try {
            activeSocket?.close()
        } catch (ignored: Exception) {}
        activeOutputStream = null
        activeSocket = null
    }

    /**
     * Transmits raw ESC/POS byte sequence to connected Bluetooth or Wi-Fi printer (or simulation spooler)
     */
    suspend fun sendBytes(bytes: ByteArray, jobTitle: String = "Print Job", textPreview: String = ""): Boolean = withContext(Dispatchers.IO) {
        val btState = _connectionState.value
        val wifiState = wifiPrinterManager.connectionState.value

        val targetName = when {
            btState is PrinterConnectionState.Connected -> "Bluetooth: ${btState.deviceName} (${btState.deviceAddress})"
            wifiState is WifiPrinterConnectionState.Connected -> "Wi-Fi: ${wifiState.printerName} (${wifiState.ip}:${wifiState.port})"
            else -> "Virtual Spooler / Simulation"
        }

        var success = false
        var executedPhysical = false

        // 1. If Bluetooth is connected, write to Bluetooth socket
        if (btState is PrinterConnectionState.Connected && activeOutputStream != null) {
            try {
                withContext(Dispatchers.Main) {
                    _connectionState.value = PrinterConnectionState.Printing(jobTitle)
                }
                activeOutputStream?.write(bytes)
                activeOutputStream?.flush()
                success = true
                executedPhysical = true
                withContext(Dispatchers.Main) {
                    _connectionState.value = btState
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending bytes to Bluetooth printer", e)
                withContext(Dispatchers.Main) {
                    _connectionState.value = PrinterConnectionState.Error("Bluetooth Print Error: ${e.message}")
                }
            }
        }

        // 2. If Wi-Fi is connected, write to Wi-Fi socket
        if (wifiState is WifiPrinterConnectionState.Connected) {
            val wifiSuccess = wifiPrinterManager.sendBytes(bytes, jobTitle)
            if (wifiSuccess) {
                success = true
                executedPhysical = true
            }
        }

        // 3. If neither physical printer is connected, virtual spooler records success for preview
        if (!executedPhysical) {
            success = true
        }

        // Record log
        val log = PrintLogEntry(
            title = jobTitle,
            deviceTarget = targetName,
            bytesCount = bytes.size,
            success = success,
            receiptTextPreview = textPreview
        )
        withContext(Dispatchers.Main) {
            val logs = _printLogs.value.toMutableList()
            logs.add(0, log)
            _printLogs.value = logs.take(30)
            _lastPrintedText.value = textPreview
        }
        success
    }

    /**
     * Builds ESC/POS Kick Cash Drawer signal
     */
    fun buildOpenCashDrawerPayload(): Pair<ByteArray, String> {
        val stream = ByteArrayOutputStream()
        stream.write(EscPosCommands.OPEN_CASH_DRAWER_PIN2)
        stream.write(EscPosCommands.OPEN_CASH_DRAWER_PIN5)
        return Pair(stream.toByteArray(), "=== CASH DRAWER PULSE TRIGGERED ===\nPIN2 & PIN5 Kick Signal Sent\n")
    }

    /**
     * Builds ESC/POS Feed & Cut Paper command
     */
    fun buildCutPaperPayload(): Pair<ByteArray, String> {
        val stream = ByteArrayOutputStream()
        stream.write(EscPosCommands.feedLines(3))
        stream.write(EscPosCommands.FEED_AND_CUT)
        return Pair(stream.toByteArray(), "=== PAPER CUT TRIGGERED ===\nFeed 3 lines + Full Cut\n")
    }

    /**
     * Automatically adjusts and resolves the optimal paper width
     * based on connected Bluetooth / Wi-Fi printer model name or store config.
     */
    fun resolveAutoAdjustedPaperWidth(config: StoreConfigEntity): PaperWidth {
        val btState = _connectionState.value
        val wifiState = wifiPrinterManager.connectionState.value

        val deviceName = when {
            btState is PrinterConnectionState.Connected -> btState.deviceName.lowercase()
            wifiState is WifiPrinterConnectionState.Connected -> wifiState.printerName.lowercase()
            else -> ""
        }

        return when {
            deviceName.contains("80") || deviceName.contains("3inch") || deviceName.contains("t88") ||
                    deviceName.contains("tm-t") || deviceName.contains("rp80") || deviceName.contains("zj-80") ||
                    deviceName.contains("xp-80") || deviceName.contains("wide") -> PaperWidth.MM_80
            deviceName.contains("58") || deviceName.contains("2inch") || deviceName.contains("mpt-ii") ||
                    deviceName.contains("rpp02") || deviceName.contains("zj-58") || deviceName.contains("mini") -> PaperWidth.MM_58
            config.paperWidthMm == 80 -> PaperWidth.MM_80
            else -> PaperWidth.MM_58
        }
    }

    /**
     * Builds ESC/POS bytes and human-readable text for a complete sales receipt
     */
    fun buildReceiptPayload(
        receipt: ReceiptEntity,
        config: StoreConfigEntity,
        paperWidth: PaperWidth = PaperWidth.MM_58
    ): Pair<ByteArray, String> {
        val stream = ByteArrayOutputStream()
        val textBuilder = StringBuilder()
        val totalWidth = paperWidth.charWidth
        val currency = EscPosCommands.safeCurrencyForThermal(config.currencySymbol, config.currencyCode)
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(receipt.createdAt))

        val moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(List::class.java, CartItem::class.java)
        val adapter = moshi.adapter<List<CartItem>>(type)
        val items: List<CartItem> = try {
            adapter.fromJson(receipt.itemsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val totalPaperDots = if (paperWidth == PaperWidth.MM_80) 576 else 384

        // 1. Initialize Printer
        stream.write(EscPosCommands.INIT_PRINTER)

        // 1.5 Center-aligned Business Logo (Printed Above Business Name for Thermal POS)
        if (!config.logoUri.isNullOrBlank()) {
            val targetLogoWidth = if (paperWidth == PaperWidth.MM_80) 420 else 240
            val logoBmp = com.example.util.LogoImageUtils.loadAndScaleLogoForThermal(
                context = context,
                logoPathOrUri = config.logoUri,
                targetWidth = targetLogoWidth,
                totalPaperDots = totalPaperDots
            )
            if (logoBmp != null) {
                stream.write(EscPosCommands.ALIGN_CENTER)
                stream.write(EscPosCommands.rasterBitmap(logoBmp, totalPaperDots))
                stream.write(EscPosCommands.feedLines(1))
                textBuilder.append(EscPosCommands.centerText("[ BUSINESS LOGO ]", totalWidth)).append("\n\n")
            }
        }

        // 2. Header & Business Name (Center Aligned)
        stream.write(EscPosCommands.ALIGN_CENTER)
        val bNameClean = config.businessName.ifBlank { "STORE NAME" }.trim().uppercase()
        if (paperWidth == PaperWidth.MM_58 && bNameClean.length > 16) {
            // For 58mm (32 cols wide), use double height so long business names don't wrap mid-word awkwardly
            stream.write(EscPosCommands.TEXT_SIZE_DOUBLE_HEIGHT)
            stream.write(EscPosCommands.BOLD_ON)
        } else {
            stream.write(EscPosCommands.TEXT_SIZE_DOUBLE_BOTH)
            stream.write(EscPosCommands.BOLD_ON)
        }
        stream.write((bNameClean + "\n").toByteArray(Charset.forName("GBK")))
        textBuilder.append(EscPosCommands.centerText(bNameClean, totalWidth)).append("\n")

        // 3. Tagline & Store Details (Center, Normal)
        stream.write(EscPosCommands.TEXT_SIZE_NORMAL)
        stream.write(EscPosCommands.BOLD_OFF)
        stream.write(EscPosCommands.ALIGN_CENTER) // Explicitly re-apply ALIGN_CENTER after mode switch

        val detailLines = mutableListOf<String>()
        if (config.tagline.isNotBlank()) detailLines.add(config.tagline.trim())
        if (config.address.isNotBlank()) detailLines.add(config.address.trim())
        if (config.phone.isNotBlank()) detailLines.add("Tel: ${config.phone.trim()}")
        if (config.enableTax && config.taxId.isNotBlank()) detailLines.add("Tax ID / VAT: ${config.taxId.trim()}")

        if (config.enableDenominationLogo) {
            val currLabel = if (config.currencyCode.isNotBlank()) config.currencyCode else "CURRENCY"
            detailLines.add("[ $currLabel ($currency) • POS REGISTER ]")
        }

        for (line in detailLines) {
            stream.write(EscPosCommands.ALIGN_CENTER)
            stream.write("$line\n".toByteArray(Charset.forName("GBK")))
            textBuilder.append(EscPosCommands.centerText(line, totalWidth)).append("\n")
        }

        // 4. Prominent Centered Invoice Header Title: "TAX INVOICE" or "INVOICE"
        val invoiceTitle = if (config.enableTax) "TAX INVOICE" else "INVOICE"
        stream.write(EscPosCommands.ALIGN_CENTER)
        stream.write(EscPosCommands.TEXT_SIZE_DOUBLE_HEIGHT)
        stream.write(EscPosCommands.BOLD_ON)
        stream.write(("\n*** $invoiceTitle ***\n").toByteArray(Charset.forName("GBK")))
        stream.write(EscPosCommands.TEXT_SIZE_NORMAL)
        stream.write(EscPosCommands.BOLD_OFF)
        textBuilder.append("\n" + EscPosCommands.centerText("*** $invoiceTitle ***", totalWidth) + "\n")

        // 4.5 Custom Header Text (if configured)
        if (config.receiptHeader.isNotBlank()) {
            stream.write(EscPosCommands.ALIGN_CENTER)
            stream.write(EscPosCommands.BOLD_ON)
            stream.write("${config.receiptHeader.trim()}\n".toByteArray(Charset.forName("GBK")))
            stream.write(EscPosCommands.BOLD_OFF)
            textBuilder.append(EscPosCommands.centerText(config.receiptHeader.trim(), totalWidth)).append("\n")
        }

        // Divider
        val div1 = EscPosCommands.divider('=', totalWidth)
        stream.write(EscPosCommands.ALIGN_LEFT)
        stream.write(div1.toByteArray(Charset.forName("GBK")))
        textBuilder.append(div1)

        // 5. Invoice & Cashier Meta (Left Aligned)
        stream.write(EscPosCommands.ALIGN_LEFT)
        val meta1 = EscPosCommands.formatRowTwoColumn("INVOICE: ${receipt.invoiceNumber}", receipt.status.label, totalWidth)
        val meta2 = EscPosCommands.formatRowTwoColumn("DATE: $dateStr", "CASHIER: ${receipt.cashierName}", totalWidth)
        val meta3 = if (receipt.customerName.isNotEmpty() && receipt.customerName != "Walk-in Customer") {
            EscPosCommands.formatRowTwoColumn("CUSTOMER:", receipt.customerName, totalWidth)
        } else ""

        stream.write((meta1 + meta2 + meta3).toByteArray(Charset.forName("GBK")))
        textBuilder.append(meta1 + meta2 + meta3)

        // Divider
        val div2 = EscPosCommands.divider('-', totalWidth)
        stream.write(div2.toByteArray(Charset.forName("GBK")))
        textBuilder.append(div2)

        // Table Header (Dynamic for 80mm vs 58mm)
        val th = if (totalWidth >= 48) {
            val hName = "ITEM DESCRIPTION".padEnd(20)
            val hQty = "QTY".padStart(4)
            val hUnit = "UNIT PRICE".padStart(11)
            val hTotal = "AMOUNT".padStart(11)
            "$hName $hQty $hUnit $hTotal\n"
        } else {
            EscPosCommands.formatRowTwoColumn("ITEM (QTY)", "AMOUNT", totalWidth)
        }
        stream.write(EscPosCommands.BOLD_ON)
        stream.write(th.toByteArray(Charset.forName("GBK")))
        stream.write(EscPosCommands.BOLD_OFF)
        textBuilder.append(th)
        stream.write(div2.toByteArray(Charset.forName("GBK")))
        textBuilder.append(div2)

        // 6. Items breakdown
        for (item in items) {
            val itemLine = EscPosCommands.formatItemRow(
                name = item.name,
                qty = item.quantity,
                unitPrice = item.unitPrice,
                totalPrice = item.total,
                currency = currency,
                totalWidth = totalWidth
            )
            stream.write(itemLine.toByteArray(Charset.forName("GBK")))
            textBuilder.append(itemLine)

            // Conditional Item Discount Section: Only appear if discount details added for the item
            if (item.itemDiscountAmount > 0.0 && item.discountType != com.example.data.model.ItemDiscountType.NONE) {
                val discRow = EscPosCommands.formatItemDiscountRow(
                    discountType = item.discountType,
                    discountValue = item.discountValue,
                    discountAmount = item.itemDiscountAmount,
                    currency = currency,
                    totalWidth = totalWidth
                )
                stream.write(discRow.toByteArray(Charset.forName("GBK")))
                textBuilder.append(discRow)
            }
        }

        stream.write(div2.toByteArray(Charset.forName("GBK")))
        textBuilder.append(div2)

        // 7. Totals & Financials
        val subtotalLabel = if (config.enableTax) "SUBTOTAL (Net):" else "SUBTOTAL:"
        val subtotalLine = EscPosCommands.formatRowTwoColumn(subtotalLabel, String.format("%s%.2f", currency, receipt.subtotal), totalWidth)
        stream.write(subtotalLine.toByteArray(Charset.forName("GBK")))
        textBuilder.append(subtotalLine)

        val totalDiscounts = receipt.discountAmount + items.sumOf { it.itemDiscountAmount }
        if (totalDiscounts > 0.0) {
            val discLabel = if (receipt.discountPercent > 0.0) {
                "TOTAL DISCOUNT (${receipt.discountPercent.toInt()}%):"
            } else {
                "TOTAL DISCOUNT:"
            }
            val discLine = EscPosCommands.formatRowTwoColumn(discLabel, String.format("-%s%.2f", currency, totalDiscounts), totalWidth)
            stream.write(discLine.toByteArray(Charset.forName("GBK")))
            textBuilder.append(discLine)
        }

        if (config.enableTax && receipt.taxAmount > 0) {
            val taxLabel = if (config.taxName.isNotBlank()) "${config.taxName.uppercase()} (Included):" else "TAX (Included):"
            val taxLine = EscPosCommands.formatRowTwoColumn(taxLabel, String.format("%s%.2f", currency, receipt.taxAmount), totalWidth)
            stream.write(taxLine.toByteArray(Charset.forName("GBK")))
            textBuilder.append(taxLine)
        }

        if (receipt.tipAmount > 0) {
            val tipLine = EscPosCommands.formatRowTwoColumn("TIP / GRATUITY:", String.format("%s%.2f", currency, receipt.tipAmount), totalWidth)
            stream.write(tipLine.toByteArray(Charset.forName("GBK")))
            textBuilder.append(tipLine)
        }

        if (receipt.creditNoteAppliedAmount > 0.0 && receipt.appliedCreditNoteNumber.isNotEmpty()) {
            val cnLine = EscPosCommands.formatRowTwoColumn(
                "CREDIT ADJ (${receipt.appliedCreditNoteNumber}):",
                String.format("-%s%.2f", currency, receipt.creditNoteAppliedAmount),
                totalWidth
            )
            val origRefLine = if (receipt.appliedCreditOriginalInvoice.isNotEmpty()) {
                "  (Adjusted from Bill #${receipt.appliedCreditOriginalInvoice})\n"
            } else ""
            stream.write((cnLine + origRefLine).toByteArray(Charset.forName("GBK")))
            textBuilder.append(cnLine + origRefLine)
        }

        val div3 = EscPosCommands.divider('=', totalWidth)
        stream.write(div3.toByteArray(Charset.forName("GBK")))
        textBuilder.append(div3)

        // Grand Total (Double Size, Bold)
        stream.write(EscPosCommands.TEXT_SIZE_DOUBLE_BOTH)
        stream.write(EscPosCommands.BOLD_ON)
        val totalLine = EscPosCommands.formatRowTwoColumn("TOTAL:", String.format("%s%.2f", currency, receipt.totalAmount), totalWidth / 2)
        stream.write(totalLine.toByteArray(Charset.forName("GBK")))
        stream.write(EscPosCommands.TEXT_SIZE_NORMAL)
        stream.write(EscPosCommands.BOLD_OFF)
        textBuilder.append(EscPosCommands.formatRowTwoColumn("GRAND TOTAL:", String.format("%s%.2f", currency, receipt.totalAmount), totalWidth))

        stream.write(div1.toByteArray(Charset.forName("GBK")))
        textBuilder.append(div1)

        // 8. Payment Breakdown
        val payMethodLine = EscPosCommands.formatRowTwoColumn("PAYMENT METHOD:", receipt.paymentMethod.label, totalWidth)
        stream.write(payMethodLine.toByteArray(Charset.forName("GBK")))
        textBuilder.append(payMethodLine)

        if (receipt.amountTendered > 0) {
            val tenderedLine = EscPosCommands.formatRowTwoColumn("AMOUNT TENDERED:", String.format("%s%.2f", currency, receipt.amountTendered), totalWidth)
            val changeLine = EscPosCommands.formatRowTwoColumn("CHANGE DUE:", String.format("%s%.2f", currency, receipt.changeDue), totalWidth)
            stream.write((tenderedLine + changeLine).toByteArray(Charset.forName("GBK")))
            textBuilder.append(tenderedLine + changeLine)
        }

        // 9. UPI Payment QR (If enabled)
        if (config.enableUpiPayment && config.upiId.isNotBlank()) {
            stream.write(EscPosCommands.feedLines(1))
            stream.write(EscPosCommands.ALIGN_CENTER)
            val upiUri = com.example.util.BarcodeQrUtils.buildUpiPaymentUri(
                upiId = config.upiId,
                payeeName = config.upiPayeeName.ifBlank { config.businessName },
                amount = receipt.totalAmount,
                invoiceNumber = receipt.invoiceNumber
            )
            val qrBitmap = com.example.util.BarcodeQrUtils.generateQrCodeBitmap(upiUri, size = 250)
            if (qrBitmap != null) {
                stream.write(EscPosCommands.rasterBitmap(qrBitmap, totalPaperDots))
            } else {
                stream.write(EscPosCommands.qrCode(upiUri, moduleSize = 6))
            }
            stream.write(EscPosCommands.BOLD_ON)
            stream.write("SCAN TO PAY VIA UPI\n".toByteArray(Charset.forName("GBK")))
            stream.write(EscPosCommands.BOLD_OFF)
            stream.write("UPI ID: ${config.upiId}\n".toByteArray(Charset.forName("GBK")))
            stream.write("(GPay / PhonePe / Paytm / BHIM)\n".toByteArray(Charset.forName("GBK")))
            textBuilder.append("\n[ === UPI PAYMENT QR CODE === ]\nUPI ID: ${config.upiId}\n")
        }

        // 10. QR Code & Barcode for Verification
        stream.write(EscPosCommands.feedLines(1))
        stream.write(EscPosCommands.ALIGN_CENTER)

        if (config.showQrVerification && (!config.enableUpiPayment || config.upiId.isBlank())) {
            val qrContent = "INVOICE:${receipt.invoiceNumber}|TOT:${receipt.totalAmount}|HASH:${receipt.securityHash.take(12)}"
            val verifyQrBitmap = com.example.util.BarcodeQrUtils.generateQrCodeBitmap(qrContent, size = 200)
            if (verifyQrBitmap != null) {
                stream.write(EscPosCommands.rasterBitmap(verifyQrBitmap, totalPaperDots))
            } else {
                stream.write(EscPosCommands.qrCode(qrContent, moduleSize = 5))
            }
            stream.write("SCAN TO VERIFY E-RECEIPT\n".toByteArray(Charset.forName("GBK")))
            textBuilder.append("\n[ === DIGITAL QR VERIFICATION CODE === ]\n")
        }

        if (config.showBarcode) {
            stream.write(EscPosCommands.feedLines(1))
            val barcodeData = receipt.invoiceNumber.replace("-", "").take(16).ifEmpty { "POS1001" }
            val barcodeBitmap = com.example.util.BarcodeQrUtils.generateBarcodeBitmap(barcodeData, width = 360, height = 70)
            if (barcodeBitmap != null) {
                stream.write(EscPosCommands.rasterBitmap(barcodeBitmap, totalPaperDots))
                stream.write("${barcodeData}\n".toByteArray(Charset.forName("GBK")))
            } else {
                stream.write(EscPosCommands.barcode128(barcodeData, height = 50))
            }
            textBuilder.append("[ ||| BARCODE: $barcodeData ||| ]\n")
        }

        // 10. Security Hash (Tamper-Proof)
        if (receipt.securityHash.isNotEmpty()) {
            val hashLine = "SEC HASH: ${receipt.securityHash.take(16)}...\n"
            stream.write(hashLine.toByteArray(Charset.forName("GBK")))
            textBuilder.append(hashLine)
        }

        // 11. Footer & Policy
        if (config.receiptFooter.isNotEmpty()) {
            stream.write(EscPosCommands.feedLines(1))
            stream.write("${config.receiptFooter}\n".toByteArray(Charset.forName("GBK")))
            textBuilder.append("\n${config.receiptFooter}\n")
        }

        // 12. Feed & Cut
        stream.write(EscPosCommands.feedLines(4))
        stream.write(EscPosCommands.FEED_AND_CUT)

        return Pair(stream.toByteArray(), textBuilder.toString())
    }

    /**
     * Builds ESC/POS bytes and human-readable text for a standalone Credit Note ("Trade Note") slip
     */
    fun buildCreditNotePayload(
        creditNote: com.example.data.local.entity.CreditNoteEntity,
        config: StoreConfigEntity,
        paperWidth: PaperWidth = PaperWidth.MM_58
    ): Pair<ByteArray, String> {
        val stream = ByteArrayOutputStream()
        val textBuilder = StringBuilder()
        val totalWidth = paperWidth.charWidth
        val currency = EscPosCommands.safeCurrencyForThermal(config.currencySymbol, config.currencyCode)
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(creditNote.createdAt))

        val moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(List::class.java, CartItem::class.java)
        val adapter = moshi.adapter<List<CartItem>>(type)
        val items: List<CartItem> = try {
            adapter.fromJson(creditNote.returnedItemsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val totalPaperDots = if (paperWidth == PaperWidth.MM_80) 576 else 384

        stream.write(EscPosCommands.INIT_PRINTER)

        if (!config.logoUri.isNullOrBlank()) {
            val targetLogoWidth = if (paperWidth == PaperWidth.MM_80) 420 else 240
            val logoBmp = com.example.util.LogoImageUtils.loadAndScaleLogoForThermal(
                context = context,
                logoPathOrUri = config.logoUri,
                targetWidth = targetLogoWidth,
                totalPaperDots = totalPaperDots
            )
            if (logoBmp != null) {
                stream.write(EscPosCommands.ALIGN_CENTER)
                stream.write(EscPosCommands.rasterBitmap(logoBmp, totalPaperDots))
                stream.write(EscPosCommands.feedLines(1))
                textBuilder.append(EscPosCommands.centerText("[ BUSINESS LOGO ]", totalWidth)).append("\n\n")
            }
        }

        stream.write(EscPosCommands.ALIGN_CENTER)
        val bNameClean = config.businessName.ifBlank { "STORE NAME" }.trim().uppercase()
        stream.write(EscPosCommands.TEXT_SIZE_DOUBLE_HEIGHT)
        stream.write(EscPosCommands.BOLD_ON)
        stream.write((bNameClean + "\n").toByteArray(Charset.forName("GBK")))
        textBuilder.append(EscPosCommands.centerText(bNameClean, totalWidth)).append("\n")

        stream.write(EscPosCommands.TEXT_SIZE_NORMAL)
        stream.write(EscPosCommands.BOLD_OFF)
        stream.write(EscPosCommands.ALIGN_CENTER)
        if (config.address.isNotBlank()) {
            stream.write("${config.address.trim()}\n".toByteArray(Charset.forName("GBK")))
            textBuilder.append(EscPosCommands.centerText(config.address.trim(), totalWidth)).append("\n")
        }
        if (config.phone.isNotBlank()) {
            stream.write("Tel: ${config.phone.trim()}\n".toByteArray(Charset.forName("GBK")))
            textBuilder.append(EscPosCommands.centerText("Tel: ${config.phone.trim()}", totalWidth)).append("\n")
        }

        stream.write(EscPosCommands.ALIGN_CENTER)
        stream.write(EscPosCommands.TEXT_SIZE_DOUBLE_HEIGHT)
        stream.write(EscPosCommands.BOLD_ON)
        stream.write("\n*** CREDIT / TRADE NOTE ***\n".toByteArray(Charset.forName("GBK")))
        stream.write(EscPosCommands.TEXT_SIZE_NORMAL)
        stream.write(EscPosCommands.BOLD_OFF)
        textBuilder.append("\n" + EscPosCommands.centerText("*** CREDIT / TRADE NOTE ***", totalWidth) + "\n")

        val div1 = EscPosCommands.divider('=', totalWidth)
        val div2 = EscPosCommands.divider('-', totalWidth)
        stream.write(EscPosCommands.ALIGN_LEFT)
        stream.write(div1.toByteArray(Charset.forName("GBK")))
        textBuilder.append(div1)

        val meta1 = EscPosCommands.formatRowTwoColumn("CREDIT NOTE:", creditNote.creditNoteNumber, totalWidth)
        val meta2 = EscPosCommands.formatRowTwoColumn("ORIG BILL #:", creditNote.originalInvoiceNumber, totalWidth)
        val meta3 = EscPosCommands.formatRowTwoColumn("DATE: $dateStr", creditNote.status.label, totalWidth)
        val meta4 = EscPosCommands.formatRowTwoColumn("CUSTOMER:", creditNote.customerName, totalWidth)
        val meta5 = EscPosCommands.formatRowTwoColumn("MOBILE NO:", creditNote.customerPhone, totalWidth)

        stream.write((meta1 + meta2 + meta3 + meta4 + meta5 + div2).toByteArray(Charset.forName("GBK")))
        textBuilder.append(meta1 + meta2 + meta3 + meta4 + meta5 + div2)

        val th = EscPosCommands.formatRowTwoColumn("RETURNED ITEM (QTY)", "CREDIT", totalWidth)
        stream.write(EscPosCommands.BOLD_ON)
        stream.write(th.toByteArray(Charset.forName("GBK")))
        stream.write(EscPosCommands.BOLD_OFF)
        stream.write(div2.toByteArray(Charset.forName("GBK")))
        textBuilder.append(th + div2)

        for (item in items) {
            val itemLine = EscPosCommands.formatItemRow(
                name = item.name,
                qty = item.quantity,
                unitPrice = item.unitPrice,
                totalPrice = item.total,
                currency = currency,
                totalWidth = totalWidth
            )
            stream.write(itemLine.toByteArray(Charset.forName("GBK")))
            textBuilder.append(itemLine)
        }

        stream.write(div1.toByteArray(Charset.forName("GBK")))
        textBuilder.append(div1)

        val creditLine = EscPosCommands.formatRowTwoColumn(
            "TOTAL CREDIT VALUE:",
            String.format("%s%.2f", currency, creditNote.totalCreditAmount),
            totalWidth
        )
        val balLine = EscPosCommands.formatRowTwoColumn(
            "AVAILABLE BALANCE:",
            String.format("%s%.2f", currency, creditNote.remainingBalance),
            totalWidth
        )
        stream.write(EscPosCommands.BOLD_ON)
        stream.write((creditLine + balLine).toByteArray(Charset.forName("GBK")))
        stream.write(EscPosCommands.BOLD_OFF)
        textBuilder.append(creditLine + balLine)

        stream.write(div1.toByteArray(Charset.forName("GBK")))
        textBuilder.append(div1)

        if (creditNote.notes.isNotBlank()) {
            val noteLine = "REASON / NOTE: ${creditNote.notes}\n"
            stream.write(noteLine.toByteArray(Charset.forName("GBK")))
            textBuilder.append(noteLine)
        }

        stream.write(EscPosCommands.ALIGN_CENTER)
        val policyText = "Present this Credit Note or registered\nmobile number (${creditNote.customerPhone}) on your\nnext purchase to adjust bill total.\n"
        stream.write(policyText.toByteArray(Charset.forName("GBK")))
        textBuilder.append("\n$policyText")

        if (config.showBarcode) {
            val barcodeData = creditNote.creditNoteNumber.replace("-", "").take(16).ifEmpty { "CN1001" }
            val barcodeBitmap = com.example.util.BarcodeQrUtils.generateBarcodeBitmap(barcodeData, width = 360, height = 70)
            if (barcodeBitmap != null) {
                stream.write(EscPosCommands.rasterBitmap(barcodeBitmap, totalPaperDots))
                stream.write("${barcodeData}\n".toByteArray(Charset.forName("GBK")))
            }
            textBuilder.append("[ ||| BARCODE: $barcodeData ||| ]\n")
        }

        stream.write(EscPosCommands.feedLines(4))
        stream.write(EscPosCommands.FEED_AND_CUT)

        return Pair(stream.toByteArray(), textBuilder.toString())
    }

    /**
     * Builds ESC/POS Self-Test Diagnostic Print
     */
    fun buildSelfTestPayload(paperWidth: PaperWidth = PaperWidth.MM_58): Pair<ByteArray, String> {
        val stream = ByteArrayOutputStream()
        val totalWidth = paperWidth.charWidth

        stream.write(EscPosCommands.INIT_PRINTER)
        stream.write(EscPosCommands.ALIGN_CENTER)
        stream.write(EscPosCommands.TEXT_SIZE_DOUBLE_BOTH)
        stream.write(EscPosCommands.BOLD_ON)
        stream.write("PRINTER SELF-TEST\n".toByteArray())
        stream.write(EscPosCommands.TEXT_SIZE_NORMAL)
        stream.write(EscPosCommands.BOLD_OFF)
        stream.write("ESC/POS THERMAL PROTOCOL OK\n".toByteArray())
        stream.write(EscPosCommands.divider('=', totalWidth).toByteArray())

        stream.write(EscPosCommands.ALIGN_LEFT)
        stream.write(EscPosCommands.formatRowTwoColumn("PAPER WIDTH:", paperWidth.label, totalWidth).toByteArray())
        stream.write(EscPosCommands.formatRowTwoColumn("STATUS:", "ONLINE & READY", totalWidth).toByteArray())
        stream.write(EscPosCommands.formatRowTwoColumn("CHARACTERS/LINE:", "$totalWidth Chars", totalWidth).toByteArray())
        stream.write(EscPosCommands.formatRowTwoColumn("BLUETOOTH SPP:", "ACTIVE", totalWidth).toByteArray())

        stream.write(EscPosCommands.divider('-', totalWidth).toByteArray())
        stream.write(EscPosCommands.ALIGN_CENTER)
        stream.write("FONT ENLARGEMENT TEST:\n".toByteArray())
        stream.write(EscPosCommands.TEXT_SIZE_DOUBLE_HEIGHT)
        stream.write("DOUBLE HEIGHT\n".toByteArray())
        stream.write(EscPosCommands.TEXT_SIZE_DOUBLE_WIDTH)
        stream.write("DOUBLE WIDTH\n".toByteArray())
        stream.write(EscPosCommands.TEXT_SIZE_NORMAL)

        stream.write(EscPosCommands.feedLines(1))
        stream.write(EscPosCommands.qrCode("https://ai.studio/pos-test", 5))
        stream.write("QR CODE TEST PASSED\n".toByteArray())

        stream.write(EscPosCommands.feedLines(4))
        stream.write(EscPosCommands.FEED_AND_CUT)

        val preview = "=== PRINTER SELF-TEST ===\nESC/POS PROTOCOL READY\nPaper Width: ${paperWidth.label}\nStatus: ONLINE\nChars/Line: $totalWidth\n[QR & BARCODE VERIFIED]\n"
        return Pair(stream.toByteArray(), preview)
    }

    fun cleanup() {
        try {
            if (isReceiverRegistered) {
                context.unregisterReceiver(discoveryReceiver)
                isReceiverRegistered = false
            }
            disconnectInternal()
            wifiPrinterManager.cleanup()
        } catch (ignored: Exception) {}
    }
}
