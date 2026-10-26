package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.CartItem
import com.example.data.model.PaperWidth
import com.example.data.model.PaymentMethod
import com.example.util.BarcodeQrUtils
import com.example.data.model.ReceiptStatus
import com.example.data.model.SyncStatus
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailModal(
    receipt: ReceiptEntity,
    config: StoreConfigEntity,
    items: List<CartItem>,
    isTamperVerified: Boolean,
    onDismiss: () -> Unit,
    onPrint: (PaperWidth) -> Unit,
    onSharePdf: () -> Unit,
    onPrintPdf: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRefund: () -> Unit,
    onVoid: () -> Unit,
    onItemReturn: (() -> Unit)? = null
) {
    var previewPaperWidth by remember {
        mutableStateOf(if (config.paperWidthMm == 80) PaperWidth.MM_80 else PaperWidth.MM_58)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("receipt_detail_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Receipt #${receipt.invoiceNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Action Buttons Top Bar (Edit, Delete, PDF Share, PDF Print)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "RECEIPT ACTIONS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Edit Receipt Button
                            OutlinedButton(
                                onClick = onEdit,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("edit_receipt_btn"),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", fontSize = 12.sp)
                            }

                            // Share PDF Invoice Button
                            FilledTonalButton(
                                onClick = onSharePdf,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("share_pdf_btn"),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF Invoice", fontSize = 12.sp)
                            }

                            // Delete Receipt Button
                            OutlinedButton(
                                onClick = onDelete,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("delete_receipt_btn"),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // System Print Manager PDF Print
                        OutlinedButton(
                            onClick = onPrintPdf,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("print_pdf_doc_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print Full PDF Invoice (A4 / System)", fontSize = 12.sp)
                        }

                        if (receipt.status == ReceiptStatus.PAID && onItemReturn != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = onItemReturn,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldTertiary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("partial_item_return_btn"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.AssignmentReturn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Partial Item Return & Issue Credit Note", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Width switch tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilterChip(
                        selected = previewPaperWidth == PaperWidth.MM_58,
                        onClick = { previewPaperWidth = PaperWidth.MM_58 },
                        label = { Text("58mm (Compact)") },
                        leadingIcon = {
                            if (previewPaperWidth == PaperWidth.MM_58) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = previewPaperWidth == PaperWidth.MM_80,
                        onClick = { previewPaperWidth = PaperWidth.MM_80 },
                        label = { Text("80mm (Standard POS)") },
                        leadingIcon = {
                            if (previewPaperWidth == PaperWidth.MM_80) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }

                // Security & Sync Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Sync Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (receipt.syncStatus) {
                            SyncStatus.SYNCED -> StatusSuccess.copy(alpha = 0.15f)
                            SyncStatus.SYNCING -> StatusInfo.copy(alpha = 0.15f)
                            SyncStatus.PENDING -> StatusWarning.copy(alpha = 0.15f)
                            SyncStatus.FAILED -> StatusError.copy(alpha = 0.15f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (receipt.syncStatus) {
                                    SyncStatus.SYNCED -> Icons.Default.CloudDone
                                    SyncStatus.SYNCING -> Icons.Default.Sync
                                    SyncStatus.PENDING -> Icons.Default.CloudQueue
                                    SyncStatus.FAILED -> Icons.Default.SyncProblem
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = when (receipt.syncStatus) {
                                    SyncStatus.SYNCED -> StatusSuccess
                                    SyncStatus.SYNCING -> StatusInfo
                                    SyncStatus.PENDING -> StatusWarning
                                    SyncStatus.FAILED -> StatusError
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = receipt.syncStatus.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (receipt.syncStatus) {
                                    SyncStatus.SYNCED -> StatusSuccess
                                    SyncStatus.SYNCING -> StatusInfo
                                    SyncStatus.PENDING -> StatusWarning
                                    SyncStatus.FAILED -> StatusError
                                }
                            )
                        }
                    }

                    // Cryptographic Integrity Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isTamperVerified) StatusSuccess.copy(alpha = 0.15f) else StatusError.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isTamperVerified) Icons.Default.VerifiedUser else Icons.Default.GppBad,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isTamperVerified) StatusSuccess else StatusError
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isTamperVerified) "SHA-256 Verified" else "Tampered",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTamperVerified) StatusSuccess else StatusError
                            )
                        }
                    }
                }

                // Realistic Thermal Paper View
                ThermalPaperPreview(
                    receipt = receipt,
                    config = config,
                    items = items,
                    paperWidth = previewPaperWidth
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPrint(previewPaperWidth) },
                modifier = Modifier.testTag("print_receipt_btn")
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print ESC/POS (${previewPaperWidth.mm}mm)")
            }
        },
        dismissButton = {
            if (receipt.status == ReceiptStatus.PAID) {
                OutlinedButton(
                    onClick = onRefund,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                    modifier = Modifier.testTag("refund_receipt_btn")
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refund")
                }
            }
        }
    )
}

/**
 * Dialog to edit receipt customer details, cashier, payment method, discounts, tip, and line items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReceiptDialog(
    receipt: ReceiptEntity,
    config: StoreConfigEntity,
    initialItems: List<CartItem>,
    onDismiss: () -> Unit,
    onSave: (
        receiptId: Long,
        customerName: String,
        customerPhone: String,
        cashierName: String,
        paymentMethod: PaymentMethod,
        notes: String,
        items: List<CartItem>,
        discountPercent: Double,
        tipAmount: Double,
        amountTendered: Double
    ) -> Unit
) {
    var customerName by remember { mutableStateOf(receipt.customerName) }
    var customerPhone by remember { mutableStateOf(receipt.customerPhone) }
    var cashierName by remember { mutableStateOf(receipt.cashierName) }
    var selectedPaymentMethod by remember { mutableStateOf(receipt.paymentMethod) }
    var notes by remember { mutableStateOf(receipt.notes) }
    var discountPercentStr by remember { mutableStateOf(receipt.discountPercent.toString()) }
    var tipAmountStr by remember { mutableStateOf(String.format("%.2f", receipt.tipAmount)) }
    var amountTenderedStr by remember { mutableStateOf(String.format("%.2f", receipt.amountTendered)) }

    val itemsList = remember { mutableStateListOf<CartItem>().apply { addAll(initialItems) } }

    val discountPercent = discountPercentStr.toDoubleOrNull() ?: 0.0
    val tipAmount = tipAmountStr.toDoubleOrNull() ?: 0.0
    val grossRetailTotal = itemsList.sumOf { it.unitPrice * it.quantity }
    val discountAmount = grossRetailTotal * (discountPercent / 100.0)
    val discountedGross = grossRetailTotal - discountAmount

    var subtotal = 0.0
    var taxAmount = 0.0
    if (config.enableTax) {
        for (item in itemsList) {
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
    val amountTendered = amountTenderedStr.toDoubleOrNull() ?: totalAmount
    val changeDue = if (amountTendered > totalAmount) amountTendered - totalAmount else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("edit_receipt_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Edit Receipt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Invoice #${receipt.invoiceNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live Recalculated Summary Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Recalculated Total:", fontWeight = FontWeight.Bold)
                            Text(
                                "${config.currencySymbol}${String.format("%.2f", totalAmount)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            if (config.enableTax) "Subtotal: ${config.currencySymbol}${String.format("%.2f", subtotal)} • ${config.taxName}: ${config.currencySymbol}${String.format("%.2f", taxAmount)} • Discount: -${config.currencySymbol}${String.format("%.2f", discountAmount)}"
                            else "Subtotal: ${config.currencySymbol}${String.format("%.2f", subtotal)} • Discount: -${config.currencySymbol}${String.format("%.2f", discountAmount)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Customer Name & Phone
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Customer Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("edit_customer_name_input")
                )

                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text("Customer Phone") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("edit_customer_phone_input")
                )

                OutlinedTextField(
                    value = cashierName,
                    onValueChange = { cashierName = it },
                    label = { Text("Cashier Name") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("edit_cashier_name_input")
                )

                // Payment Method Selector
                Text("Payment Method", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaymentMethod.values().take(4).forEach { method ->
                        FilterChip(
                            selected = selectedPaymentMethod == method,
                            onClick = { selectedPaymentMethod = method },
                            label = { Text(method.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Discount & Tip Dropdown Selection & Custom Value Inputs
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Discount Rate (%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val discPresets = listOf("0%" to "0", "5%" to "5", "10%" to "10", "15%" to "15", "20%" to "20", "50%" to "50")
                        discPresets.forEach { (label, valStr) ->
                            FilterChip(
                                selected = discountPercentStr == valStr,
                                onClick = { discountPercentStr = valStr },
                                label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Tip / Gratuity (${config.currencySymbol})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tipPresets = listOf(
                            "No Tip" to "0.00",
                            "${config.currencySymbol}1" to "1.00",
                            "${config.currencySymbol}2" to "2.00",
                            "${config.currencySymbol}5" to "5.00",
                            "${config.currencySymbol}10" to "10.00"
                        )
                        tipPresets.forEach { (label, valStr) ->
                            FilterChip(
                                selected = tipAmountStr == valStr,
                                onClick = { tipAmountStr = valStr },
                                label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = discountPercentStr,
                            onValueChange = { discountPercentStr = it },
                            label = { Text("Custom Discount %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("edit_discount_input")
                        )
                        OutlinedTextField(
                            value = tipAmountStr,
                            onValueChange = { tipAmountStr = it },
                            label = { Text("Custom Tip (${config.currencySymbol})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("edit_tip_input")
                        )
                    }
                }

                if (selectedPaymentMethod == PaymentMethod.CASH) {
                    OutlinedTextField(
                        value = amountTenderedStr,
                        onValueChange = { amountTenderedStr = it },
                        label = { Text("Amount Tendered") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("edit_tendered_input")
                    )
                }

                // Line Items Management
                Text("Purchased Items (${itemsList.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                itemsList.forEachIndexed { index, item ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    "${config.currencySymbol}${String.format("%.2f", item.unitPrice)} each • Total: ${config.currencySymbol}${String.format("%.2f", item.total)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (item.quantity > 1) {
                                            itemsList[index] = item.copy(quantity = item.quantity - 1)
                                        } else {
                                            itemsList.removeAt(index)
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                }
                                Text("${item.quantity}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                                IconButton(
                                    onClick = {
                                        itemsList[index] = item.copy(quantity = item.quantity + 1)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Receipt Notes / Memo") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        receipt.id,
                        customerName,
                        customerPhone,
                        cashierName,
                        selectedPaymentMethod,
                        notes,
                        itemsList.toList(),
                        discountPercent,
                        tipAmount,
                        amountTendered
                    )
                },
                modifier = Modifier.testTag("save_receipt_edit_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save & Recalculate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Confirmation dialog for permanently deleting a receipt.
 */
@Composable
fun DeleteReceiptConfirmationDialog(
    receipt: ReceiptEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = {
            Text("Delete Receipt #${receipt.invoiceNumber}?")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Are you sure you want to permanently delete this receipt? This action will:")
                Text("• Restore the purchased quantities back into product inventory stock.", fontSize = 13.sp)
                Text("• Remove this sale from local transaction records.", fontSize = 13.sp)
                Text("• Enqueue a deletion tombstone to synchronize across connected POS devices.", fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_delete_receipt_btn")
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete Receipt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun ThermalPaperPreview(
    receipt: com.example.data.local.entity.ReceiptEntity,
    config: com.example.data.local.entity.StoreConfigEntity,
    items: List<com.example.data.model.CartItem>,
    paperWidth: com.example.data.model.PaperWidth
) {
    val currency = config.currencySymbol
    val context = LocalContext.current
    val logoBitmap = remember(config.logoUri) {
        com.example.util.LogoImageUtils.loadLogoBitmap(context, config.logoUri, maxDimension = 240)
    }

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = Color(0xFFFDFBF7),
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Diagonal watermark of generated invoice number centered on the invoice
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = receipt.invoiceNumber,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A).copy(alpha = 0.08f),
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.rotate(-30f)
                )
            }

            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Centered Business Logo for Thermal POS Preview
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap.asImageBitmap(),
                        contentDescription = "Business Logo",
                        modifier = Modifier
                            .size(60.dp)
                            .padding(bottom = 6.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }

                Text(
                    text = config.businessName.ifBlank { "STORE NAME" }.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (config.tagline.isNotBlank()) {
                    Text(
                        text = config.tagline,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (config.address.isNotBlank()) {
                    Text(
                        text = config.address,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (config.phone.isNotBlank()) {
                    Text(
                        text = "Tel: ${config.phone}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (config.enableTax && config.taxId.isNotBlank()) {
                    Text(
                        text = "Tax ID / VAT: ${config.taxId}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val invoiceTitle = if (config.enableTax) "TAX INVOICE" else "INVOICE"
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "*** $invoiceTitle ***",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "Invoice #${receipt.invoiceNumber}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))

                items.forEach { item ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.quantity}x ${item.name}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("$currency${"%.2f".format(item.total)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        // Conditional Item Discount Section: Only appear if discount details added
                        if (item.itemDiscountAmount > 0.0 && item.discountType != com.example.data.model.ItemDiscountType.NONE) {
                            val discLabel = when (item.discountType) {
                                com.example.data.model.ItemDiscountType.PERCENTAGE -> "Discount (-${item.discountValue.toInt()}%):"
                                com.example.data.model.ItemDiscountType.FLAT -> "Discount (Flat):"
                                com.example.data.model.ItemDiscountType.NONE -> ""
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(discLabel, fontSize = 11.sp, color = Color(0xFF16A34A))
                                Text("-$currency${"%.2f".format(item.itemDiscountAmount)}", fontSize = 11.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (config.enableTax) "Subtotal (Excl. Tax):" else "Subtotal:", fontSize = 12.sp)
                    Text("$currency${"%.2f".format(receipt.subtotal)}", fontSize = 12.sp)
                }

                // Conditional Discount Summary Section: Only appear if discount details added
                val totalDiscounts = receipt.discountAmount + items.sumOf { it.itemDiscountAmount }
                if (totalDiscounts > 0.0) {
                    val discLabel = if (receipt.discountPercent > 0.0) {
                        "Total Discount (${receipt.discountPercent.toInt()}%):"
                    } else {
                        "Total Discount:"
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(discLabel, fontSize = 12.sp, color = Color(0xFF16A34A))
                        Text("-$currency${"%.2f".format(totalDiscounts)}", fontSize = 12.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                    }
                }

                if (config.enableTax && receipt.taxAmount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${config.taxName} (Included):", fontSize = 12.sp)
                        Text("$currency${"%.2f".format(receipt.taxAmount)}", fontSize = 12.sp)
                    }
                }
                if (receipt.tipAmount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tip / Gratuity:", fontSize = 12.sp)
                        Text("+$currency${"%.2f".format(receipt.tipAmount)}", fontSize = 12.sp)
                    }
                }
                if (receipt.creditNoteAppliedAmount > 0.0 && receipt.appliedCreditNoteNumber.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(Color(0xFFCCFBF1), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "Credit Note (${receipt.appliedCreditNoteNumber}):",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F766E)
                            )
                            Text(
                                "-$currency${"%.2f".format(receipt.creditNoteAppliedAmount)}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F766E)
                            )
                        }
                        if (receipt.appliedCreditOriginalInvoice.isNotEmpty()) {
                            Text(
                                text = "Adjusted from Bill #${receipt.appliedCreditOriginalInvoice}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F766E)
                            )
                        }
                    }
                }
                Divider(modifier = Modifier.padding(vertical = 6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL DUE:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("$currency${"%.2f".format(receipt.totalAmount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
