package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CreditNoteEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.CartItem
import com.example.data.model.CreditNoteStatus
import com.example.data.model.PaperWidth
import com.example.data.model.ReceiptStatus
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog for initiating a Partial Item Return by Customer Mobile Number and generating a Credit Note ("Trade Note").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemReturnByPhoneDialog(
    receipts: List<ReceiptEntity>,
    customers: List<CustomerEntity>,
    config: StoreConfigEntity,
    preselectedReceipt: ReceiptEntity? = null,
    parseItems: (String) -> List<CartItem>,
    onDismiss: () -> Unit,
    onConfirmReturn: (
        receiptId: Long,
        returnedQuantitiesByKey: Map<String, Int>,
        customerPhone: String,
        customerName: String,
        notes: String,
        autoPrint: Boolean
    ) -> Unit
) {
    val currency = config.currencySymbol
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }

    var phoneInput by remember(preselectedReceipt) {
        mutableStateOf(preselectedReceipt?.customerPhone ?: "")
    }
    var selectedReceipt by remember(preselectedReceipt) {
        mutableStateOf(preselectedReceipt)
    }
    var customerNameInput by remember(selectedReceipt, phoneInput) {
        val matchedCust = customers.firstOrNull {
            phoneInput.isNotBlank() && normalizePhone(it.phone) == normalizePhone(phoneInput)
        }
        mutableStateOf(
            selectedReceipt?.customerName?.takeIf { it.isNotBlank() && it != "Walk-in Customer" }
                ?: matchedCust?.name
                ?: ""
        )
    }
    var returnNotes by remember { mutableStateOf("") }
    var autoPrintCreditNote by remember { mutableStateOf(config.autoPrintOnSale) }

    // Map of itemKey -> quantity to return
    val returnQtyMap = remember(selectedReceipt) { mutableStateMapOf<String, Int>() }

    // Normalize phone digits helper for flexible matching
    val normalizedSearch = normalizePhone(phoneInput)

    // Matching bills for the entered mobile number (or invoice number if user types invoice #)
    val matchingReceipts = remember(receipts, phoneInput, normalizedSearch) {
        val paidReceipts = receipts.filter { it.status == ReceiptStatus.PAID }
        if (phoneInput.isBlank()) {
            paidReceipts.filter { it.customerPhone.isNotBlank() }
        } else {
            paidReceipts.filter { r ->
                val rPhoneNorm = normalizePhone(r.customerPhone)
                (normalizedSearch.isNotEmpty() && rPhoneNorm.contains(normalizedSearch)) ||
                    r.customerPhone.contains(phoneInput.trim(), ignoreCase = true) ||
                    r.customerName.contains(phoneInput.trim(), ignoreCase = true) ||
                    r.invoiceNumber.contains(phoneInput.trim(), ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .testTag("item_return_by_phone_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AssignmentReturn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (selectedReceipt == null) "Item Return by Phone" else "Select Items to Return",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (selectedReceipt == null)
                                "Step 1: Enter customer mobile number to find bill"
                            else
                                "Step 2: Bill #${selectedReceipt?.invoiceNumber} • Generate Credit Note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
                if (selectedReceipt != null && preselectedReceipt == null) {
                    TextButton(
                        onClick = { selectedReceipt = null },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Change Bill", fontSize = 12.sp)
                    }
                }
            }
        },
        text = {
            if (selectedReceipt == null) {
                // STEP 1: Enter Customer Mobile Number & Select Bill
                val customersWithPhone = remember(customers) {
                    customers.filter { it.phone.isNotBlank() }.take(5)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Customer Registered Mobile Number *") },
                            placeholder = { Text("Enter mobile number (or name / bill #)") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            trailingIcon = {
                                if (phoneInput.isNotEmpty()) {
                                    IconButton(onClick = { phoneInput = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("return_customer_phone_search_input")
                        )
                    }

                    // Quick Customer Phone Chips if registered customers exist
                    if (customersWithPhone.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    "Registered Customers:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    customersWithPhone.take(3).forEach { cust ->
                                        SuggestionChip(
                                            onClick = {
                                                phoneInput = cust.phone
                                                customerNameInput = cust.name
                                            },
                                            label = {
                                                Text(
                                                    "${cust.name} (${cust.phone})",
                                                    fontSize = 10.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = if (phoneInput.isBlank())
                                "Recent Customer Bills (${matchingReceipts.size})"
                            else
                                "Matching Bills for '$phoneInput' (${matchingReceipts.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (matchingReceipts.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No paid bills found for this phone number.",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        "Tip: You can also type the Invoice Number (e.g. INV-...) to select a walk-in bill and link it to the customer's phone number.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(matchingReceipts, key = { it.id }) { receipt ->
                            val billItems = remember(receipt.itemsJson) { parseItems(receipt.itemsJson) }
                            val returnedItems = remember(receipt.returnedItemsJson) { parseItems(receipt.returnedItemsJson) }
                            val totalBought = billItems.sumOf { it.quantity }
                            val totalReturned = returnedItems.sumOf { it.quantity }
                            val remainingReturnable = (totalBought - totalReturned).coerceAtLeast(0)

                            Surface(
                                onClick = {
                                    if (remainingReturnable > 0) {
                                        selectedReceipt = receipt
                                        if (phoneInput.isBlank() && receipt.customerPhone.isNotBlank()) {
                                            phoneInput = receipt.customerPhone
                                        }
                                        if (receipt.customerName.isNotBlank() && receipt.customerName != "Walk-in Customer") {
                                            customerNameInput = receipt.customerName
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("return_bill_item_${receipt.invoiceNumber}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = receipt.invoiceNumber,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.5.sp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (remainingReturnable > 0)
                                                    StatusSuccess.copy(alpha = 0.15f)
                                                else
                                                    StatusError.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = if (remainingReturnable > 0) "$remainingReturnable Returnable" else "All Returned",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (remainingReturnable > 0) StatusSuccess else StatusError,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${receipt.customerName} ${if (receipt.customerPhone.isNotBlank()) "• ${receipt.customerPhone}" else ""}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${dateFormat.format(Date(receipt.createdAt))} • ${billItems.joinToString(", ") { "${it.quantity}x ${it.name}" }}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = String.format("%s%.2f", currency, receipt.totalAmount),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Select →",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // STEP 2: Choose Specific Item(s) & Return Quantities from Selected Bill
                val receipt = selectedReceipt!!
                val purchasedItems = remember(receipt.itemsJson) { parseItems(receipt.itemsJson) }
                val alreadyReturnedItems = remember(receipt.returnedItemsJson) { parseItems(receipt.returnedItemsJson) }
                val invoiceDiscountMultiplier = (1.0 - (receipt.discountPercent.coerceIn(0.0, 100.0) / 100.0))

                // Calculate live Credit Note total
                var totalCreditValue = 0.0
                var totalReturnUnits = 0
                purchasedItems.forEachIndexed { idx, item ->
                    val key = "${idx}_${item.productId}_${item.name}"
                    val qty = returnQtyMap[key] ?: 0
                    if (qty > 0) {
                        val unitNet = if (item.quantity > 0) {
                            (item.total / item.quantity) * invoiceDiscountMultiplier
                        } else {
                            item.unitPrice * invoiceDiscountMultiplier
                        }
                        totalCreditValue += unitNet * qty
                        totalReturnUnits += qty
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Summary Banner for Credit Note Value
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldTertiary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldTertiary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "CREDIT NOTE VALUE TO ISSUE",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldTertiary
                                )
                                Text(
                                    "$totalReturnUnits item(s) selected from ${receipt.invoiceNumber}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = String.format("%s%.2f", currency, totalCreditValue),
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = EmeraldTertiary
                            )
                        }
                    }

                    // Customer Mobile Number & Name (Saved with Credit Note for future bill adjustment)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Customer Mobile *") },
                            placeholder = { Text("Required for Credit Note") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("return_step2_phone_input")
                        )
                        OutlinedTextField(
                            value = customerNameInput,
                            onValueChange = { customerNameInput = it },
                            label = { Text("Customer Name") },
                            placeholder = { Text("Customer Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.9f)
                                .testTag("return_step2_name_input")
                        )
                    }

                    Text(
                        "Select Item(s) & Quantity to Return:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    purchasedItems.forEachIndexed { index, item ->
                        val itemKey = "${index}_${item.productId}_${item.name}"
                        val prevReturnedQty = alreadyReturnedItems
                            .filter { it.productId == item.productId && it.name == item.name && Math.abs(it.unitPrice - item.unitPrice) < 0.01 }
                            .sumOf { it.quantity }
                        val maxReturnable = (item.quantity - prevReturnedQty).coerceAtLeast(0)
                        val selectedQty = returnQtyMap[itemKey] ?: 0
                        val unitRefundValue = if (item.quantity > 0) {
                            (item.total / item.quantity) * invoiceDiscountMultiplier
                        } else {
                            item.unitPrice * invoiceDiscountMultiplier
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedQty > 0)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (selectedQty > 0)
                                androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("return_item_row_$index")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = "Bought: ${item.quantity} • Credit/unit: ${String.format("%s%.2f", currency, unitRefundValue)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (prevReturnedQty > 0) {
                                        Text(
                                            text = "Already returned: $prevReturnedQty (Max returnable: $maxReturnable)",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = StatusWarning
                                        )
                                    }
                                    if (selectedQty > 0) {
                                        Text(
                                            text = "Return Credit: +${String.format("%s%.2f", currency, unitRefundValue * selectedQty)}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = EmeraldTertiary
                                        )
                                    }
                                }

                                if (maxReturnable <= 0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = StatusError.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "Returned",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusError,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                if (selectedQty > 0) {
                                                    returnQtyMap[itemKey] = selectedQty - 1
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease Return Qty")
                                        }
                                        Text(
                                            text = "$selectedQty / $maxReturnable",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                if (selectedQty < maxReturnable) {
                                                    returnQtyMap[itemKey] = selectedQty + 1
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.AddCircle,
                                                contentDescription = "Increase Return Qty",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = returnNotes,
                        onValueChange = { returnNotes = it },
                        label = { Text("Return Reason / Notes (Optional)") },
                        placeholder = { Text("e.g. Size exchange, damaged packaging") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Print Credit Note Slip", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                "Returned items will automatically be added back to stock",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoPrintCreditNote,
                            onCheckedChange = { autoPrintCreditNote = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectedReceipt != null) {
                val totalSelectedUnits = returnQtyMap.values.sum()
                val effectivePhone = phoneInput.trim().ifEmpty { selectedReceipt?.customerPhone?.trim() ?: "" }
                Button(
                    onClick = {
                        val r = selectedReceipt ?: return@Button
                        onConfirmReturn(
                            r.id,
                            returnQtyMap.toMap(),
                            effectivePhone,
                            customerNameInput.trim().ifEmpty { r.customerName },
                            returnNotes.trim(),
                            autoPrintCreditNote
                        )
                    },
                    enabled = totalSelectedUnits > 0 && effectivePhone.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldTertiary),
                    modifier = Modifier.testTag("confirm_generate_credit_note_btn")
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (effectivePhone.isBlank()) "Enter Mobile # to Issue Credit Note"
                        else "Generate Credit Note ($totalSelectedUnits Item${if (totalSelectedUnits == 1) "" else "s"})",
                        fontWeight = FontWeight.Bold
                    )
                }
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
 * Modal displaying a standalone Credit Note ("Trade Note") with thermal preview, ESC/POS printing, and PDF export/printing.
 */
@Composable
fun CreditNoteDetailModal(
    creditNote: CreditNoteEntity,
    config: StoreConfigEntity,
    returnedItems: List<CartItem>,
    onDismiss: () -> Unit,
    onPrintThermal: (PaperWidth) -> Unit,
    onSharePdf: () -> Unit,
    onPrintPdf: () -> Unit
) {
    var previewPaperWidth by remember(config.paperWidthMm) {
        mutableStateOf(if (config.paperWidthMm == 80) PaperWidth.MM_80 else PaperWidth.MM_58)
    }
    val currency = config.currencySymbol
    val dateStr = remember(creditNote.createdAt) {
        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(creditNote.createdAt))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .testTag("credit_note_detail_modal"),
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AssignmentReturn,
                            contentDescription = null,
                            tint = EmeraldTertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Credit / Trade Note",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "${creditNote.creditNoteNumber} • From Bill #${creditNote.originalInvoiceNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Toolbar: Print PDF & Share PDF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPrintPdf,
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Print PDF Bill", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = onSharePdf,
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
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
                // Paper Width Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilterChip(
                        selected = previewPaperWidth == PaperWidth.MM_58,
                        onClick = { previewPaperWidth = PaperWidth.MM_58 },
                        label = { Text("58mm Slip") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = previewPaperWidth == PaperWidth.MM_80,
                        onClick = { previewPaperWidth = PaperWidth.MM_80 },
                        label = { Text("80mm Slip") }
                    )
                }

                // Thermal Slip Preview Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color(0xFFFDFBF7),
                    shadowElevation = 4.dp,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = creditNote.creditNoteNumber,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F766E).copy(alpha = 0.08f),
                                letterSpacing = 2.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.rotate(-28f)
                            )
                        }

                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = config.businessName.ifBlank { "STORE NAME" }.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )
                            if (config.address.isNotBlank()) {
                                Text(config.address, fontSize = 11.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
                            }
                            if (config.phone.isNotBlank()) {
                                Text("Tel: ${config.phone}", fontSize = 11.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                color = Color(0xFFCCFBF1),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "*** CREDIT / TRADE NOTE ***",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F766E),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Credit Note #${creditNote.creditNoteNumber}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = "Ref Original Bill: #${creditNote.originalInvoiceNumber}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F766E)
                            )
                            Text(
                                text = "Date: $dateStr",
                                fontSize = 10.5.sp,
                                color = Color.DarkGray
                            )

                            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Customer:", fontSize = 11.5.sp, color = Color.DarkGray)
                                Text(creditNote.customerName, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Registered Mobile:", fontSize = 11.5.sp, color = Color.DarkGray)
                                Text(creditNote.customerPhone, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Status:", fontSize = 11.5.sp, color = Color.DarkGray)
                                Text(
                                    creditNote.status.label.uppercase(),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when (creditNote.status) {
                                        CreditNoteStatus.ACTIVE -> Color(0xFF15803D)
                                        CreditNoteStatus.PARTIALLY_USED -> Color(0xFFD97706)
                                        CreditNoteStatus.REDEEMED -> Color.Gray
                                    }
                                )
                            }

                            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray)

                            Text(
                                "RETURNED ITEMS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            returnedItems.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${item.quantity}x ${item.name}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Black,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        String.format("%s%.2f", currency, item.total),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("TOTAL CREDIT VALUE:", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color.Black)
                                Text(
                                    String.format("%s%.2f", currency, creditNote.totalCreditAmount),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = Color.Black
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("AVAILABLE BALANCE:", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF0F766E))
                                Text(
                                    String.format("%s%.2f", currency, creditNote.remainingBalance),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F766E)
                                )
                            }

                            if (creditNote.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Note: ${creditNote.notes}",
                                    fontSize = 11.sp,
                                    color = Color.DarkGray,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Present this Credit Note (#${creditNote.creditNoteNumber}) or registered mobile (${creditNote.customerPhone}) on your next purchase to adjust bill total.",
                                fontSize = 10.5.sp,
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPrintThermal(previewPaperWidth) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldTertiary),
                modifier = Modifier.testTag("print_credit_note_thermal_btn")
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print Credit Note (${previewPaperWidth.mm}mm)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

fun normalizePhone(phone: String): String {
    return phone.filter { it.isDigit() }
}
