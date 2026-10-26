package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class ManualStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val tips: String? = null
)

data class ManualSection(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val subtitle: String,
    val steps: List<ManualStep>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManualDialog(
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSectionId by remember { mutableStateOf("ALL") }
    var expandedSectionIds by remember { mutableStateOf(setOf("setup", "register", "payment", "inventory", "printing", "backup")) }

    val sections = remember {
        listOf(
            ManualSection(
                id = "setup",
                title = "1. Initial Store Setup",
                icon = Icons.Default.Storefront,
                subtitle = "Configure business details, currency, and tax presets",
                steps = listOf(
                    ManualStep(
                        stepNumber = 1,
                        title = "Open Settings Tab",
                        description = "Navigate to the Settings tab in the bottom bar or left navigation rail.",
                        tips = "You can customize your store profile at any time."
                    ),
                    ManualStep(
                        stepNumber = 2,
                        title = "Select Country & Currency Preset",
                        description = "Tap 'Select Country & Currency Preset' to automatically configure standard currency symbol (e.g., $, ₹, €, £), tax names (VAT/GST/Sales Tax), and default rates.",
                        tips = "Over 12 major international country templates are pre-built."
                    ),
                    ManualStep(
                        stepNumber = 3,
                        title = "Customize Business Info & Receipt Header",
                        description = "Enter your Business Name, Tagline, Store Address, Phone Number, and custom receipt header/footer messages.",
                        tips = "These details will appear printed on all customer thermal receipts and PDF invoices."
                    ),
                    ManualStep(
                        stepNumber = 4,
                        title = "Save Configuration",
                        description = "Scroll down and tap 'Save Configuration Changes' to persist your store settings to the local database.",
                        tips = "Changes take effect instantly on all new receipts and checkout operations."
                    )
                )
            ),
            ManualSection(
                id = "register",
                title = "2. POS Register & Orders",
                icon = Icons.Default.PointOfSale,
                subtitle = "Take orders, search catalog, scan barcodes, and link customers",
                steps = listOf(
                    ManualStep(
                        stepNumber = 1,
                        title = "Add Items to Cart",
                        description = "Browse catalog categories or type keywords in the search bar. Tap any product card to immediately add it to the active cart.",
                        tips = "Tap '+' icon to add a custom ad-hoc item not in the inventory."
                    ),
                    ManualStep(
                        stepNumber = 2,
                        title = "Scan Barcodes with Camera",
                        description = "Tap the Barcode Scanner icon next to the search bar. Point your camera at any UPC/EAN barcode to automatically lookup and add the product to your order.",
                        tips = "Supports continuous scan mode for rapid checkout."
                    ),
                    ManualStep(
                        stepNumber = 3,
                        title = "Attach Customer Profile (Optional)",
                        description = "Tap the customer banner ('Walk-in Customer') to link an existing customer or register a new one to track loyalty points and purchase history.",
                        tips = "Customers earn reward points automatically on paid orders."
                    ),
                    ManualStep(
                        stepNumber = 4,
                        title = "Review Cart & Adjust Quantities",
                        description = "On mobile, tap the floating bottom cart bar. On tablet, view the persistent right pane. Use the + and - buttons to adjust quantities or remove items.",
                        tips = "Tap the trash bin icon to clear the entire cart if needed."
                    ),
                    ManualStep(
                        stepNumber = 5,
                        title = "Proceed to Charge",
                        description = "Tap the 'Charge [Total]' button to open the Payment Modal.",
                        tips = "You can apply item discounts, transaction discounts, and tips in the checkout screen."
                    )
                )
            ),
            ManualSection(
                id = "payment",
                title = "3. Checkout & Payment Methods",
                icon = Icons.Default.Payment,
                subtitle = "Accept Cash with exact change calculator, Card, and Dynamic UPI QR",
                steps = listOf(
                    ManualStep(
                        stepNumber = 1,
                        title = "Choose Payment Method",
                        description = "Select between Cash, Card, UPI / QR, or Customer Credit in the payment modal.",
                        tips = "The system auto-calculates total tax and applied discounts."
                    ),
                    ManualStep(
                        stepNumber = 2,
                        title = "Cash Change Calculation",
                        description = "When Cash is selected, tap quick tender amount chips or enter custom cash received. The system immediately displays the exact change due to the customer.",
                        tips = "Prevents cashier cash calculation errors."
                    ),
                    ManualStep(
                        stepNumber = 3,
                        title = "UPI Dynamic QR Code (India / Digital)",
                        description = "If UPI is enabled in Settings, selecting UPI generates a dynamic Bharat QR code with the exact order amount and invoice reference for the customer to scan with any banking app.",
                        tips = "Supported apps: Google Pay, PhonePe, Paytm, BHIM, Cred."
                    ),
                    ManualStep(
                        stepNumber = 4,
                        title = "Complete Transaction & Auto-Print",
                        description = "Tap 'Complete Payment'. If a thermal printer is connected, the receipt will print automatically, and the invoice will be archived in the Receipts tab.",
                        tips = "A verified QR code is included on each receipt for tamper-proof validation."
                    )
                )
            ),
            ManualSection(
                id = "inventory",
                title = "4. Inventory & Catalog Management",
                icon = Icons.Default.Inventory2,
                subtitle = "Track stock, create products, receive low-stock alerts, and print barcodes",
                steps = listOf(
                    ManualStep(
                        stepNumber = 1,
                        title = "Add New Products",
                        description = "Navigate to the Items tab and tap the '+ Add Item' floating button. Enter Product Name, Category, Price, Cost Price, Stock Quantity, SKU, and Barcode.",
                        tips = "Tap 'Auto Generate SKU/Barcode' if the item has no manufacturer code."
                    ),
                    ManualStep(
                        stepNumber = 2,
                        title = "Manage Stock & Low Stock Warnings",
                        description = "Items with stock $\\le 10$ display a yellow warning badge, while out-of-stock items display a red alert. Filter low stock items directly using the 'Alerts' KPI card.",
                        tips = "Easily monitor reorder thresholds to prevent stockouts."
                    ),
                    ManualStep(
                        stepNumber = 3,
                        title = "Print & Generate Item Barcodes",
                        description = "Tap any product in the inventory list and select 'Barcode & Label'. You can preview generated Code128 / QR codes and print price tags to your thermal printer.",
                        tips = "Labels fit standard 58mm and 80mm adhesive sticker rolls."
                    ),
                    ManualStep(
                        stepNumber = 4,
                        title = "Business Analytics & Insights",
                        description = "Tap 'Business Analytical Dashboard' at the top of the Items tab to view sales charts, top-selling products, profit margins, and inventory valuations.",
                        tips = "Export analytics data for accounting and bookkeeping."
                    )
                )
            ),
            ManualSection(
                id = "receipts",
                title = "5. Receipts, Invoicing & Refunds",
                icon = Icons.Default.ReceiptLong,
                subtitle = "Manage receipt history, reprint receipts, share PDF invoices, and process voids",
                steps = listOf(
                    ManualStep(
                        stepNumber = 1,
                        title = "Search & Filter Invoices",
                        description = "Go to the Receipts tab. Use the search bar to locate receipts by Invoice Number, Customer Name, or Cashier. Filter by Paid, Refunded, or Voided.",
                        tips = "Invoices are stored securely in local SQLite Room database."
                    ),
                    ManualStep(
                        stepNumber = 2,
                        title = "View Receipt Details & Verify QR",
                        description = "Tap any receipt card to open the interactive digital invoice. The system verifies the cryptographic hash against tamper attempts.",
                        tips = "Green badge confirms receipt data is authentic."
                    ),
                    ManualStep(
                        stepNumber = 3,
                        title = "Reprint or Share PDF",
                        description = "Tap 'Print Receipt' for physical thermal printing, or 'Share PDF / WhatsApp' to generate a formatted PDF tax invoice to send via WhatsApp, Email, or Drive.",
                        tips = "Compatible with all Android system print and sharing dialogs."
                    ),
                    ManualStep(
                        stepNumber = 4,
                        title = "Process Refund or Void Transaction",
                        description = "Tap 'Refund' or 'Void' on an eligible invoice. If Supervisor PIN protection is enabled, enter your 4-digit Master PIN to authorize.",
                        tips = "Refunded items are automatically credited back into your inventory stock."
                    )
                )
            ),
            ManualSection(
                id = "printing",
                title = "6. Thermal Printer Setup",
                icon = Icons.Default.Print,
                subtitle = "Connect Bluetooth ESC/POS or Wi-Fi LAN network receipt printers",
                steps = listOf(
                    ManualStep(
                        stepNumber = 1,
                        title = "Connect Bluetooth ESC/POS Printer",
                        description = "Turn on your Bluetooth thermal printer and pair it in Android Bluetooth Settings. In the POS Printer tab, tap 'Scan Bluetooth Devices', select your printer, and tap 'Connect'.",
                        tips = "Common pairing PINs are '0000' or '1234'."
                    ),
                    ManualStep(
                        stepNumber = 2,
                        title = "Connect Wi-Fi / Ethernet LAN Printer",
                        description = "Under 'Wi-Fi / LAN Network Printer', enter your thermal printer's local IP address (e.g., 192.168.1.100) and port (default 9100). Tap 'Connect'.",
                        tips = "Ensure your Android POS tablet is connected to the same Wi-Fi router."
                    ),
                    ManualStep(
                        stepNumber = 3,
                        title = "Set Paper Width (58mm vs 80mm)",
                        description = "In the Settings tab, select either 58mm (2-inch pocket printer) or 80mm (3-inch countertop printer) to properly wrap text and align columns.",
                        tips = "80mm supports wider item descriptions and side-by-side tax breakdown."
                    ),
                    ManualStep(
                        stepNumber = 4,
                        title = "Run Test Print",
                        description = "Tap 'Print Test Receipt' to verify text clarity, character alignment, barcode scanning, and auto-cutter function.",
                        tips = "Once connected, printer status indicator turns green in the top bar."
                    )
                )
            ),
            ManualSection(
                id = "backup",
                title = "7. Google Drive Backup & Cloud Sync",
                icon = Icons.Default.CloudSync,
                subtitle = "Automatic Google Drive backups, local JSON export, and multi-device restore",
                steps = listOf(
                    ManualStep(
                        stepNumber = 1,
                        title = "Configure Backup Target Gmail Account",
                        description = "Go to the Sync Center tab. Under Google Drive Backup, enter your Gmail ID. To prevent unauthorized tampering, editing or deleting the target Gmail ID requires unlocking it with your Master Supervisor PIN.",
                        tips = "Data is backed up exclusively to the configured private Google Drive."
                    ),
                    ManualStep(
                        stepNumber = 2,
                        title = "Create Cloud Backup",
                        description = "Tap 'Upload Backup to Google Drive'. The app compresses all products, receipts, customers, and business settings into an encrypted snapshot file.",
                        tips = "Backup files are named with timestamp (e.g., POS_Backup_2026-08-18.json)."
                    ),
                    ManualStep(
                        stepNumber = 3,
                        title = "Enable Automatic Scheduled Sync",
                        description = "Toggle on 'Auto Daily Backup' in the Sync Center to ensure your transaction records are synced automatically at the end of every business shift.",
                        tips = "Prevents data loss even if the device is lost or damaged."
                    ),
                    ManualStep(
                        stepNumber = 4,
                        title = "Restore or Transfer to New Device",
                        description = "To restore on a new tablet or phone, open Sync Center, configure the same Google Account, select the latest backup snapshot, and tap 'Restore Database'.",
                        tips = "All products, inventory counts, and historical receipts will be restored seamlessly."
                    )
                )
            ),
            ManualSection(
                id = "security",
                title = "8. Multi-Store & Supervisor PIN Security",
                icon = Icons.Default.Security,
                subtitle = "Manage multiple business locations and protect sensitive actions",
                steps = listOf(
                    ManualStep(
                        stepNumber = 1,
                        title = "Add Multiple Business Profiles",
                        description = "In Settings, tap 'Multiple Business Profiles' -> 'Add Business'. Create separate independent store profiles (e.g., Branch 1, Branch 2, Warehouse).",
                        tips = "Each business profile maintains its own completely isolated Catalog, Customers, and Receipts database workspace."
                    ),
                    ManualStep(
                        stepNumber = 2,
                        title = "Switch Active Store",
                        description = "Tap 'Switch Store' anytime to switch between different retail branches without logging out. All Items, Receipts, and Customers will immediately auto-change to match the active business workspace.",
                        tips = "Active store name is displayed in the header banner."
                    ),
                    ManualStep(
                        stepNumber = 3,
                        title = "Set & Secure 4-Digit Supervisor PIN",
                        description = "Under 'Security & Supervisor Access', set a 4-digit PIN (default: '1234'). The PIN is hidden by default (••••). To view or change the PIN, you must first unlock the field by entering the current PIN.",
                        tips = "Any changes to the Master PIN auto-update instantly across the entire system."
                    ),
                    ManualStep(
                        stepNumber = 4,
                        title = "Enforce PIN for Sensitive Operations",
                        description = "Enable 'Require PIN for Refunds & Voids'. When turned on, cashiers cannot refund or delete receipts without supervisor PIN authorization. It also locks Google Drive email configuration.",
                        tips = "Protects against unauthorized inventory deductions or refund fraud."
                    )
                )
            )
        )
    }

    val filteredSections = remember(sections, searchQuery, selectedSectionId) {
        sections.filter { section ->
            val matchCategory = selectedSectionId == "ALL" || section.id == selectedSectionId
            val matchSearch = searchQuery.isEmpty() ||
                    section.title.contains(searchQuery, ignoreCase = true) ||
                    section.subtitle.contains(searchQuery, ignoreCase = true) ||
                    section.steps.any {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                it.description.contains(searchQuery, ignoreCase = true) ||
                                (it.tips?.contains(searchQuery, ignoreCase = true) == true)
                    }
            matchCategory && matchSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top App Bar with Title & Close Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "POS User Manual & Guide",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Step-by-step instructions & feature walkthrough",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_user_manual")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close User Manual")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar for Help Topics
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search instructions, e.g. printer, refund, UPI...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_search_manual")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Topic Filter Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedSectionId == "ALL",
                            onClick = { selectedSectionId = "ALL" },
                            label = { Text("All Topics (${sections.size})") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    items(sections) { sec ->
                        FilterChip(
                            selected = selectedSectionId == sec.id,
                            onClick = { selectedSectionId = sec.id },
                            label = { Text(sec.title.substringAfter(". ")) },
                            leadingIcon = { Icon(sec.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                // Manual Content List
                if (filteredSections.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No matching guide topics found", style = MaterialTheme.typography.titleMedium)
                            Text("Try searching another keyword or select 'All Topics'", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredSections, key = { it.id }) { section ->
                            val isExpanded = expandedSectionIds.contains(section.id)

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Section Header with Accordion Toggle
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedSectionIds = if (isExpanded) {
                                                    expandedSectionIds - section.id
                                                } else {
                                                    expandedSectionIds + section.id
                                                }
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        section.icon,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = section.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = section.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                expandedSectionIds = if (isExpanded) {
                                                    expandedSectionIds - section.id
                                                } else {
                                                    expandedSectionIds + section.id
                                                }
                                            }
                                        ) {
                                            Icon(
                                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isExpanded) "Collapse" else "Expand"
                                            )
                                        }
                                    }

                                    // Steps Breakdown
                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            section.steps.forEach { step ->
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(12.dp),
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        // Step Badge Number
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.size(26.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Text(
                                                                    text = "${step.stepNumber}",
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 13.sp
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.width(12.dp))

                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = step.title,
                                                                fontWeight = FontWeight.Bold,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Spacer(modifier = Modifier.height(3.dp))
                                                            Text(
                                                                text = step.description,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                lineHeight = 18.sp
                                                            )

                                                            if (!step.tips.isNullOrBlank()) {
                                                                Spacer(modifier = Modifier.height(6.dp))
                                                                Surface(
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                                                    modifier = Modifier.fillMaxWidth()
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Default.Lightbulb,
                                                                            contentDescription = null,
                                                                            tint = MaterialTheme.colorScheme.tertiary,
                                                                            modifier = Modifier.size(14.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text(
                                                                            text = step.tips,
                                                                            fontSize = 11.sp,
                                                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                                            fontWeight = FontWeight.Medium
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
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Close Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_bottom_close_manual")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Done / Close Guide", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
