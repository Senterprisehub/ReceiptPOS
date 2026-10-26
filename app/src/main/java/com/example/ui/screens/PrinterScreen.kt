package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.PaperWidth
import com.example.printer.DiscoveredBluetoothDevice
import com.example.printer.DiscoveredWifiPrinter
import com.example.printer.PrintLogEntry
import com.example.printer.PrinterConnectionState
import com.example.printer.WifiPrinterConnectionState
import com.example.ui.POSUiState
import com.example.ui.POSViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class PrinterTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    BLUETOOTH("Bluetooth", Icons.Default.Bluetooth),
    WIFI("Wi-Fi / LAN", Icons.Default.Wifi),
    DIAGNOSTICS("Diagnostics & Logs", Icons.Default.Build)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterScreen(
    viewModel: POSViewModel,
    uiState: POSUiState,
    printerState: PrinterConnectionState,
    discoveredPrinters: List<DiscoveredBluetoothDevice>,
    wifiPrinterState: WifiPrinterConnectionState,
    discoveredWifiPrinters: List<DiscoveredWifiPrinter>,
    printLogs: List<PrintLogEntry>,
    storeConfig: StoreConfigEntity,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(PrinterTab.BLUETOOTH) }
    var showAddWifiDialog by remember { mutableStateOf(false) }

    // Bluetooth permissions launcher
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            viewModel.startBluetoothScan()
        } else {
            viewModel.showToast("Bluetooth permissions are required to search for thermal printers.")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))

            // Unified Active Connection Status Banner
            DualPrinterStatusBanner(
                btState = printerState,
                wifiState = wifiPrinterState,
                onDisconnectBt = { viewModel.disconnectPrinter() },
                onDisconnectWifi = { viewModel.disconnectWifiPrinter() }
            )
        }

        // Tab Row Switcher
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PrinterTab.values().forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PrinterTab.values().size),
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text(tab.label, fontSize = 12.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        when (selectedTab) {
            PrinterTab.BLUETOOTH -> {
                item {
                    BluetoothPrintersSection(
                        printerState = printerState,
                        discoveredPrinters = discoveredPrinters,
                        onScanClick = { permissionLauncher.launch(permissionsToRequest) },
                        onStopScanClick = { viewModel.stopBluetoothScan() },
                        onConnectClick = { device -> viewModel.connectPrinter(device) },
                        onDisconnectClick = { viewModel.disconnectPrinter() }
                    )
                }
            }
            PrinterTab.WIFI -> {
                item {
                    WifiPrintersSection(
                        wifiState = wifiPrinterState,
                        discoveredPrinters = discoveredWifiPrinters,
                        localIp = viewModel.getLocalDeviceIp(),
                        onStartScan = { subnet, port -> viewModel.startWifiScan(subnet, port) },
                        onStopScan = { viewModel.stopWifiScan() },
                        onConnect = { ip, port, name -> viewModel.connectWifiPrinter(ip, port, name) },
                        onDisconnect = { viewModel.disconnectWifiPrinter() },
                        onAddManualClick = { showAddWifiDialog = true },
                        onDeleteCustom = { device -> viewModel.removeCustomWifiPrinter(device) }
                    )
                }
            }
            PrinterTab.DIAGNOSTICS -> {
                item {
                    DiagnosticControlsSection(
                        storeConfig = storeConfig,
                        onPrintSelfTest = { viewModel.printSelfTest() },
                        onKickDrawer = { viewModel.kickCashDrawer() },
                        onCutPaper = { viewModel.triggerPaperCut() }
                    )
                }

                item {
                    SpoolerLogsSection(printLogs = printLogs)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showAddWifiDialog) {
        AddWifiPrinterDialog(
            localIp = viewModel.getLocalDeviceIp(),
            onDismiss = { showAddWifiDialog = false },
            onAddAndConnect = { ip, port, name, autoConnect ->
                viewModel.addCustomWifiPrinter(ip, port, name)
                if (autoConnect) {
                    viewModel.connectWifiPrinter(ip, port, name)
                }
                showAddWifiDialog = false
            }
        )
    }
}

@Composable
fun DualPrinterStatusBanner(
    btState: PrinterConnectionState,
    wifiState: WifiPrinterConnectionState,
    onDisconnectBt: () -> Unit,
    onDisconnectWifi: () -> Unit
) {
    val isBtConnected = btState is PrinterConnectionState.Connected
    val isWifiConnected = wifiState is WifiPrinterConnectionState.Connected

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when {
            isBtConnected || isWifiConnected -> StatusSuccess.copy(alpha = 0.12f)
            btState is PrinterConnectionState.Error || wifiState is WifiPrinterConnectionState.Error -> StatusError.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                isBtConnected || isWifiConnected -> StatusSuccess
                btState is PrinterConnectionState.Error || wifiState is WifiPrinterConnectionState.Error -> StatusError
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isBtConnected || isWifiConnected -> StatusSuccess
                                    btState is PrinterConnectionState.Error || wifiState is WifiPrinterConnectionState.Error -> StatusError
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isWifiConnected -> Icons.Default.Wifi
                                isBtConnected -> Icons.Default.Bluetooth
                                else -> Icons.Default.Print
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = when {
                                isBtConnected && isWifiConnected -> "Dual Printers Active"
                                isBtConnected -> "Bluetooth: ${(btState as PrinterConnectionState.Connected).deviceName}"
                                isWifiConnected -> "Wi-Fi: ${(wifiState as WifiPrinterConnectionState.Connected).printerName}"
                                btState is PrinterConnectionState.Connecting -> "Connecting to Bluetooth..."
                                wifiState is WifiPrinterConnectionState.Connecting -> "Connecting to Wi-Fi Socket..."
                                btState is PrinterConnectionState.Printing || wifiState is WifiPrinterConnectionState.Printing -> "Printing ESC/POS Job..."
                                else -> "No Thermal Printer Connected"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = when {
                                isBtConnected && isWifiConnected -> "ESC/POS ready on Bluetooth & Wi-Fi TCP/IP"
                                isBtConnected -> "Ready to print on ${(btState as PrinterConnectionState.Connected).deviceAddress}"
                                isWifiConnected -> "TCP Socket connected on ${(wifiState as WifiPrinterConnectionState.Connected).ip}:${(wifiState as WifiPrinterConnectionState.Connected).port}"
                                btState is PrinterConnectionState.Error -> (btState as PrinterConnectionState.Error).message
                                wifiState is WifiPrinterConnectionState.Error -> (wifiState as WifiPrinterConnectionState.Error).message
                                else -> "Receipts will route to connected printer or virtual spooler"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (isBtConnected || isWifiConnected) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isBtConnected) {
                        OutlinedButton(
                            onClick = onDisconnectBt,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Disconnect BT", fontSize = 11.sp)
                        }
                    }
                    if (isWifiConnected) {
                        OutlinedButton(
                            onClick = onDisconnectWifi,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.WifiOff, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Disconnect Wi-Fi", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BluetoothPrintersSection(
    printerState: PrinterConnectionState,
    discoveredPrinters: List<DiscoveredBluetoothDevice>,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onConnectClick: (DiscoveredBluetoothDevice) -> Unit,
    onDisconnectClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Bluetooth Thermal Printers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Standard ESC/POS SPP (Serial Port Profile)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (printerState is PrinterConnectionState.Scanning) {
                    FilledTonalButton(
                        onClick = onStopScanClick,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick = onScanClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("scan_printers_btn")
                    ) {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Devices")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (discoveredPrinters.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "No Bluetooth devices found yet",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Text(
                            "Tap 'Scan Devices' above or pair your printer in Android Bluetooth Settings.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    discoveredPrinters.forEach { device ->
                        val isCurrentConnected = printerState is PrinterConnectionState.Connected &&
                                printerState.deviceAddress == device.address

                        val isConnecting = printerState is PrinterConnectionState.Connecting &&
                                printerState.deviceAddress == device.address

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrentConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isCurrentConnected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isCurrentConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Print,
                                            contentDescription = null,
                                            tint = if (isCurrentConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = device.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            if (device.isBonded) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        "Paired",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${device.deviceType} • ${device.address}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (isCurrentConnected) {
                                    OutlinedButton(
                                        onClick = onDisconnectClick,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Disconnect", fontSize = 12.sp)
                                    }
                                } else if (isConnecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Button(
                                        onClick = { onConnectClick(device) },
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Connect", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WifiPrintersSection(
    wifiState: WifiPrinterConnectionState,
    discoveredPrinters: List<DiscoveredWifiPrinter>,
    localIp: String?,
    onStartScan: (subnet: String?, port: Int) -> Unit,
    onStopScan: () -> Unit,
    onConnect: (ip: String, port: Int, name: String) -> Unit,
    onDisconnect: () -> Unit,
    onAddManualClick: () -> Unit,
    onDeleteCustom: (DiscoveredWifiPrinter) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Wi-Fi & Ethernet Network Printers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Raw TCP Socket on Port 9100 / 9101 (Epson, Star, Munbyn, Xprinter)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onAddManualClick,
                        modifier = Modifier.testTag("add_wifi_printer_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add IP Printer")
                    }

                    if (wifiState is WifiPrinterConnectionState.Scanning) {
                        FilledTonalButton(
                            onClick = onStopScan,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = { onStartScan(null, 9100) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("scan_lan_printers_btn")
                        ) {
                            Icon(Icons.Default.WifiFind, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan LAN", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (wifiState is WifiPrinterConnectionState.Scanning) {
                Spacer(modifier = Modifier.height(10.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Scanning Subnet for Port 9100... (${wifiState.scannedCount}/${wifiState.totalHosts})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${wifiState.foundCount} Found",
                            fontSize = 11.sp,
                            color = EmeraldTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { wifiState.scannedCount.toFloat() / wifiState.totalHosts.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Discovered / Configured Wi-Fi Printers List
            if (discoveredPrinters.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "No Network Printers configured yet",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Text(
                            "Tap '+ Add' to specify your Printer's IP Address (e.g. 192.168.1.100) or 'Scan LAN' to discover automatically.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    discoveredPrinters.forEach { device ->
                        val isConnected = wifiState is WifiPrinterConnectionState.Connected &&
                                wifiState.ip == device.ip && wifiState.port == device.port
                        val isConnecting = wifiState is WifiPrinterConnectionState.Connecting &&
                                wifiState.ip == device.ip && wifiState.port == device.port

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isConnected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Print,
                                            contentDescription = null,
                                            tint = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = device.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    "TCP :${device.port}",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${device.ip} • ${device.model}" + if (device.responseTimeMs > 0) " (${device.responseTimeMs}ms)" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isConnected) {
                                        OutlinedButton(
                                            onClick = onDisconnect,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text("Disconnect", fontSize = 12.sp)
                                        }
                                    } else if (isConnecting) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        Button(
                                            onClick = { onConnect(device.ip, device.port, device.name) },
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text("Connect", fontSize = 12.sp)
                                        }
                                    }

                                    if (device.isCustomConfigured && !isConnected) {
                                        IconButton(
                                            onClick = { onDeleteCustom(device) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove Printer",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticControlsSection(
    storeConfig: StoreConfigEntity,
    onPrintSelfTest: () -> Unit,
    onKickDrawer: () -> Unit,
    onCutPaper: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "ESC/POS Hardware & Diagnostic Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Send raw thermal control sequences to verify feed, cut, drawer kick, and ESC/POS barcodes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPrintSelfTest,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("print_self_test_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Print Self-Test", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = onCutPaper,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cut_paper_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cut Paper", fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onKickDrawer,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("kick_drawer_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kick Cash Drawer", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onPrintSelfTest,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test QR & Barcode", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SpoolerLogsSection(printLogs: List<PrintLogEntry>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Print Spooler Logs (${printLogs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Virtual Spooler Active",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldTertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (printLogs.isEmpty()) {
                Text(
                    "No print jobs recorded yet. Complete a checkout or run a self-test.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    printLogs.take(8).forEach { log ->
                        PrintLogCard(log = log)
                    }
                }
            }
        }
    }
}

@Composable
fun PrintLogCard(log: PrintLogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (log.success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (log.success) StatusSuccess else StatusError,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = log.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = log.deviceTarget,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "$timeStr • ${log.bytesCount} B",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded && log.receiptTextPreview.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ThermalPaperBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ThermalPaperEdge),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = log.receiptTextPreview,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = ThermalInk,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddWifiPrinterDialog(
    localIp: String?,
    onDismiss: () -> Unit,
    onAddAndConnect: (ip: String, port: Int, name: String, autoConnect: Boolean) -> Unit
) {
    var ipAddress by remember {
        val prefix = localIp?.substringBeforeLast(".") ?: "192.168.1"
        mutableStateOf("$prefix.100")
    }
    var portStr by remember { mutableStateOf("9100") }
    var printerName by remember { mutableStateOf("POS Network Printer") }
    var autoConnect by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Wi-Fi Thermal Printer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Configure raw TCP/IP socket endpoint for standard ESC/POS network thermal printer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = printerName,
                    onValueChange = { printerName = it },
                    label = { Text("Printer Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("IP Address") },
                        placeholder = { Text("192.168.1.100") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )

                    OutlinedTextField(
                        value = portStr,
                        onValueChange = { portStr = it },
                        label = { Text("Port") },
                        placeholder = { Text("9100") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick Presets
                Text("Quick Presets:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            portStr = "9100"
                            printerName = "Epson TM-T88 Network"
                        },
                        label = { Text("Epson (9100)", fontSize = 10.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            portStr = "9100"
                            printerName = "Star TSP100 LAN"
                        },
                        label = { Text("Star (9100)", fontSize = 10.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            portStr = "9100"
                            printerName = "Munbyn / Xprinter POS"
                        },
                        label = { Text("Munbyn", fontSize = 10.sp) }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoConnect,
                        onCheckedChange = { autoConnect = it }
                    )
                    Text("Connect immediately", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portStr.toIntOrNull() ?: 9100
                    if (ipAddress.isNotBlank()) {
                        onAddAndConnect(ipAddress.trim(), port, printerName.trim(), autoConnect)
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Printer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
