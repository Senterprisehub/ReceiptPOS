package com.example.printer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

sealed class WifiPrinterConnectionState {
    object Disconnected : WifiPrinterConnectionState()
    data class Scanning(val scannedCount: Int, val totalHosts: Int, val foundCount: Int) : WifiPrinterConnectionState()
    data class Connecting(val ip: String, val port: Int) : WifiPrinterConnectionState()
    data class Connected(val ip: String, val port: Int, val printerName: String) : WifiPrinterConnectionState()
    data class Printing(val jobName: String, val ip: String) : WifiPrinterConnectionState()
    data class Error(val message: String) : WifiPrinterConnectionState()
}

data class DiscoveredWifiPrinter(
    val ip: String,
    val port: Int = 9100,
    val name: String = "Wi-Fi ESC/POS Printer",
    val model: String = "Raw TCP/IP (Port 9100)",
    val responseTimeMs: Long = 0L,
    val isCustomConfigured: Boolean = false
)

class WifiPrinterManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "WifiPrinterManager"

    private var activeSocket: Socket? = null
    private var activeOutputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow<WifiPrinterConnectionState>(WifiPrinterConnectionState.Disconnected)
    val connectionState: StateFlow<WifiPrinterConnectionState> = _connectionState.asStateFlow()

    private val _discoveredPrinters = MutableStateFlow<List<DiscoveredWifiPrinter>>(emptyList())
    val discoveredPrinters: StateFlow<List<DiscoveredWifiPrinter>> = _discoveredPrinters.asStateFlow()

    private var scanJob: Job? = null

    init {
        // Pre-populate with typical default local printer targets or saved endpoints
        _discoveredPrinters.value = listOf(
            DiscoveredWifiPrinter(
                ip = "192.168.1.100",
                port = 9100,
                name = "POS Counter Printer",
                model = "Epson/Star/Munbyn (Port 9100)",
                isCustomConfigured = true
            ),
            DiscoveredWifiPrinter(
                ip = "192.168.1.200",
                port = 9100,
                name = "Kitchen Order Printer",
                model = "ESC/POS Network 80mm",
                isCustomConfigured = true
            )
        )
    }

    /**
     * Retrieves current local device Wi-Fi IP address
     */
    fun getLocalIpAddress(): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP via WifiManager", e)
        }

        try {
            val en = java.net.NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning network interfaces for IP", e)
        }
        return "192.168.1.1"
    }

    /**
     * Scans the local subnet for TCP Port 9100 (Standard ESC/POS Network Printing port)
     */
    fun scanLocalSubnet(customSubnetPrefix: String? = null, port: Int = 9100) {
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            try {
                val localIp = getLocalIpAddress() ?: "192.168.1.10"
                val prefix = customSubnetPrefix ?: run {
                    val parts = localIp.split(".")
                    if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}." else "192.168.1."
                }

                val totalHosts = 254
                val scannedCounter = AtomicInteger(0)
                val foundList = _discoveredPrinters.value.filter { it.isCustomConfigured }.toMutableList()

                _connectionState.value = WifiPrinterConnectionState.Scanning(0, totalHosts, foundList.size)

                // Concurrently probe IPs in chunks
                val chunkSize = 32
                for (chunkStart in 1..totalHosts step chunkSize) {
                    val chunkEnd = minOf(chunkStart + chunkSize - 1, totalHosts)
                    val deferredList = (chunkStart..chunkEnd).map { hostNum ->
                        async(Dispatchers.IO) {
                            val targetIp = "$prefix$hostNum"
                            val startTime = System.currentTimeMillis()
                            var isReachable = false
                            var socket: Socket? = null
                            try {
                                socket = Socket()
                                socket.connect(InetSocketAddress(targetIp, port), 250)
                                isReachable = true
                            } catch (ignored: Exception) {
                                isReachable = false
                            } finally {
                                try { socket?.close() } catch (ignored: Exception) {}
                            }

                            val count = scannedCounter.incrementAndGet()
                            if (isReachable) {
                                val rtt = System.currentTimeMillis() - startTime
                                val discovered = DiscoveredWifiPrinter(
                                    ip = targetIp,
                                    port = port,
                                    name = "Network Printer ($targetIp)",
                                    model = "ESC/POS TCP Socket",
                                    responseTimeMs = rtt,
                                    isCustomConfigured = false
                                )
                                synchronized(foundList) {
                                    if (foundList.none { it.ip == targetIp && it.port == port }) {
                                        foundList.add(discovered)
                                    }
                                }
                            }

                            if (count % 15 == 0 || count == totalHosts) {
                                withContext(Dispatchers.Main) {
                                    _discoveredPrinters.value = foundList.toList()
                                    _connectionState.value = WifiPrinterConnectionState.Scanning(
                                        scannedCount = count,
                                        totalHosts = totalHosts,
                                        foundCount = foundList.size
                                    )
                                }
                            }
                        }
                    }
                    deferredList.awaitAll()
                }

                withContext(Dispatchers.Main) {
                    _discoveredPrinters.value = foundList.toList()
                    _connectionState.value = WifiPrinterConnectionState.Disconnected
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    _connectionState.value = WifiPrinterConnectionState.Disconnected
                }
            } catch (e: Exception) {
                Log.e(TAG, "Subnet scan error", e)
                withContext(Dispatchers.Main) {
                    _connectionState.value = WifiPrinterConnectionState.Error("Wi-Fi Scan failed: ${e.message}")
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _connectionState.value = WifiPrinterConnectionState.Disconnected
    }

    /**
     * Connects to a Wi-Fi / Ethernet thermal printer over raw TCP Socket
     */
    fun connectToPrinter(ip: String, port: Int = 9100, name: String = "Wi-Fi Printer") {
        scope.launch(Dispatchers.IO) {
            disconnectInternal()

            withContext(Dispatchers.Main) {
                _connectionState.value = WifiPrinterConnectionState.Connecting(ip, port)
            }

            try {
                val socket = Socket()
                socket.soTimeout = 5000
                socket.keepAlive = true
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(ip, port), 4000)

                activeSocket = socket
                activeOutputStream = socket.getOutputStream()

                // Add to discovered/configured list if not present
                val current = _discoveredPrinters.value.toMutableList()
                val existingIndex = current.indexOfFirst { it.ip == ip && it.port == port }
                val updatedItem = DiscoveredWifiPrinter(
                    ip = ip,
                    port = port,
                    name = name.ifBlank { "Wi-Fi Printer ($ip)" },
                    model = "TCP Port $port",
                    isCustomConfigured = true
                )
                if (existingIndex >= 0) {
                    current[existingIndex] = updatedItem
                } else {
                    current.add(0, updatedItem)
                }

                withContext(Dispatchers.Main) {
                    _discoveredPrinters.value = current
                    _connectionState.value = WifiPrinterConnectionState.Connected(ip, port, updatedItem.name)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi Connection to $ip:$port failed", e)
                withContext(Dispatchers.Main) {
                    _connectionState.value = WifiPrinterConnectionState.Error("Connection to $ip:$port failed: ${e.message ?: "Unreachable"}")
                }
            }
        }
    }

    /**
     * Disconnects the active Wi-Fi socket
     */
    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            disconnectInternal()
            withContext(Dispatchers.Main) {
                _connectionState.value = WifiPrinterConnectionState.Disconnected
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
     * Sends raw ESC/POS bytes over TCP Socket to Wi-Fi printer
     */
    suspend fun sendBytes(bytes: ByteArray, jobTitle: String = "Wi-Fi Print"): Boolean = withContext(Dispatchers.IO) {
        val currentState = _connectionState.value
        if (currentState !is WifiPrinterConnectionState.Connected) {
            return@withContext false
        }

        try {
            withContext(Dispatchers.Main) {
                _connectionState.value = WifiPrinterConnectionState.Printing(jobTitle, currentState.ip)
            }

            var stream = activeOutputStream
            if (stream == null || activeSocket?.isClosed == true || activeSocket?.isConnected != true) {
                // Reconnect if socket closed
                val socket = Socket()
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(currentState.ip, currentState.port), 3000)
                activeSocket = socket
                stream = socket.getOutputStream()
                activeOutputStream = stream
            }

            stream.write(bytes)
            stream.flush()

            withContext(Dispatchers.Main) {
                _connectionState.value = currentState
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending bytes over Wi-Fi", e)
            withContext(Dispatchers.Main) {
                _connectionState.value = WifiPrinterConnectionState.Error("Wi-Fi Print error: ${e.message}")
            }
            false
        }
    }

    /**
     * Adds custom configured printer entry
     */
    fun addCustomPrinter(ip: String, port: Int, name: String) {
        val current = _discoveredPrinters.value.toMutableList()
        val index = current.indexOfFirst { it.ip == ip && it.port == port }
        val newDevice = DiscoveredWifiPrinter(
            ip = ip,
            port = port,
            name = name,
            model = "TCP Port $port",
            isCustomConfigured = true
        )
        if (index >= 0) {
            current[index] = newDevice
        } else {
            current.add(0, newDevice)
        }
        _discoveredPrinters.value = current
    }

    fun removeCustomPrinter(device: DiscoveredWifiPrinter) {
        val current = _discoveredPrinters.value.toMutableList()
        current.removeAll { it.ip == device.ip && it.port == device.port }
        _discoveredPrinters.value = current
    }

    fun cleanup() {
        scanJob?.cancel()
        disconnectInternal()
    }
}
