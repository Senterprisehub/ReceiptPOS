package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CreditNoteEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.PaymentMethod
import com.example.ui.POSUiState
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusSuccess

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentDialog(
    uiState: POSUiState,
    config: StoreConfigEntity,
    availableCreditNotes: List<CreditNoteEntity> = emptyList(),
    onDismiss: () -> Unit,
    onPaymentMethodSelected: (PaymentMethod) -> Unit,
    onAmountTenderedChanged: (String) -> Unit,
    onDiscountPercentChanged: (Double) -> Unit,
    onTipAmountChanged: (Double) -> Unit,
    onCustomerInfoChanged: (String, String) -> Unit,
    onAutoPrintToggled: (Boolean) -> Unit,
    onOpenCustomerDirectory: (() -> Unit)? = null,
    onApplyCreditNote: (CreditNoteEntity?) -> Unit = {},
    onConfirmCheckout: () -> Unit
) {
    val currency = config.currencySymbol
    var isWalkIn by remember {
        mutableStateOf(uiState.selectedCustomer == null && (uiState.customerName == "Walk-in Customer" || uiState.customerName.isBlank()))
    }
    var customerName by remember { mutableStateOf(if (uiState.customerName == "Walk-in Customer") "" else uiState.customerName) }
    var customerPhone by remember { mutableStateOf(uiState.customerPhone) }

    LaunchedEffect(uiState.selectedCustomer, uiState.customerName, uiState.customerPhone) {
        if (uiState.selectedCustomer != null) {
            isWalkIn = false
            customerName = uiState.selectedCustomer.name
            customerPhone = uiState.selectedCustomer.phone
        } else if (uiState.customerName == "Walk-in Customer" || uiState.customerName.isBlank()) {
            isWalkIn = true
        } else {
            isWalkIn = false
            customerName = uiState.customerName
            customerPhone = uiState.customerPhone
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("payment_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Collect Payment",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${uiState.totalItemsCount} items in cart",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    .verticalScroll(rememberScrollState())
            ) {
                // Grand Total Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "TOTAL PAYABLE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = String.format("%s%.2f", currency, uiState.totalAmount),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (uiState.creditNoteAppliedAmount > 0.0 && uiState.appliedCreditNote != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EmeraldTertiary.copy(alpha = 0.16f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Bill Total: ${String.format("%s%.2f", currency, uiState.grossTotalBeforeCredit)} • Credit Note (${uiState.appliedCreditNote.creditNoteNumber}): -${String.format("%s%.2f", currency, uiState.creditNoteAppliedAmount)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldTertiary,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Adjusted from Bill #${uiState.appliedCreditNote.originalInvoiceNumber}" +
                                            if (uiState.remainingCreditNoteBalanceAfterSale > 0.0)
                                                " • Remaining Credit for Future: ${String.format("%s%.2f", currency, uiState.remainingCreditNoteBalanceAfterSale)}"
                                            else " • Fully Redeemed",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldTertiary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Payment Method Selector Grid
                Text(
                    "Payment Method",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentMethod.values().forEach { method ->
                        val isSelected = uiState.selectedPaymentMethod == method
                        Surface(
                            onClick = { onPaymentMethodSelected(method) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("pay_method_${method.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (method) {
                                        PaymentMethod.CASH -> Icons.Default.AttachMoney
                                        PaymentMethod.CREDIT_CARD -> Icons.Default.CreditCard
                                        PaymentMethod.DEBIT_CARD -> Icons.Default.Payment
                                        PaymentMethod.NFC_CONTACTLESS -> Icons.Default.Contactless
                                        PaymentMethod.UPI_QR -> Icons.Default.QrCode2
                                        PaymentMethod.SPLIT -> Icons.Default.CallSplit
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = method.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Cash Quick Tender Calculator
                if (uiState.selectedPaymentMethod == PaymentMethod.CASH) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Cash Tendered",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = uiState.amountTenderedStr,
                        onValueChange = onAmountTenderedChanged,
                        label = { Text("Tendered Amount ($currency)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Tender Chips
                    val exact = uiState.totalAmount
                    val quickBills = listOf(
                        "Exact" to exact,
                        "${currency}5" to 5.0,
                        "${currency}10" to 10.0,
                        "${currency}20" to 20.0,
                        "${currency}50" to 50.0,
                        "${currency}100" to 100.0
                    ).filter { it.second >= exact || it.first == "Exact" }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickBills.forEach { (label, amount) ->
                            SuggestionChip(
                                onClick = { onAmountTenderedChanged(String.format("%.2f", amount)) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Change Due Display
                    if (uiState.changeDue > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusSuccess.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "CHANGE DUE TO CUSTOMER:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = EmeraldTertiary
                                )
                                Text(
                                    text = String.format("%s%.2f", currency, uiState.changeDue),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = EmeraldTertiary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- TIP / GRATUITY SECTION WITH DROPDOWN & CUSTOM VALUE ADDITION ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Add Tip / Gratuity",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.tipAmount > 0) {
                        Text(
                            text = "+${currency}${String.format("%.2f", uiState.tipAmount)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                var tipDropdownExpanded by remember { mutableStateOf(false) }
                var showCustomTipInput by remember { mutableStateOf(false) }
                var customTipText by remember { mutableStateOf(if (uiState.tipAmount > 0) String.format("%.2f", uiState.tipAmount) else "") }

                val tipOptions = remember(currency, uiState.discountedGross) {
                    listOf(
                        "No Tip (${currency}0.00)" to 0.0,
                        "${currency}1.00 Tip" to 1.0,
                        "${currency}2.00 Tip" to 2.0,
                        "${currency}3.00 Tip" to 3.0,
                        "${currency}5.00 Tip" to 5.0,
                        "${currency}10.00 Tip" to 10.0,
                        "10% Tip (${currency}${String.format("%.2f", uiState.discountedGross * 0.10)})" to (uiState.discountedGross * 0.10),
                        "15% Tip (${currency}${String.format("%.2f", uiState.discountedGross * 0.15)})" to (uiState.discountedGross * 0.15),
                        "20% Tip (${currency}${String.format("%.2f", uiState.discountedGross * 0.20)})" to (uiState.discountedGross * 0.20),
                        "✏️ Add Custom Tip Amount..." to -1.0
                    )
                }

                val currentTipLabel = remember(uiState.tipAmount, showCustomTipInput) {
                    if (showCustomTipInput) "Custom Tip Amount"
                    else tipOptions.find { Math.abs(it.second - uiState.tipAmount) < 0.01 && it.second >= 0.0 }?.first
                        ?: if (uiState.tipAmount > 0) "${currency}${String.format("%.2f", uiState.tipAmount)} Tip" else "No Tip (${currency}0.00)"
                }

                // Dropdown Box for Tip
                Box(modifier = Modifier.fillMaxWidth().testTag("tip_dropdown_box")) {
                    OutlinedButton(
                        onClick = { tipDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolunteerActivism, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentTipLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = tipDropdownExpanded,
                        onDismissRequest = { tipDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        tipOptions.forEach { (label, value) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontWeight = if (value == -1.0 && showCustomTipInput) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    tipDropdownExpanded = false
                                    if (value == -1.0) {
                                        showCustomTipInput = true
                                    } else {
                                        showCustomTipInput = false
                                        onTipAmountChanged(value)
                                    }
                                }
                            )
                        }
                    }
                }

                // Quick Preset Chips for Tip
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickTips = listOf(
                        "No Tip" to 0.0,
                        "${currency}1" to 1.0,
                        "${currency}2" to 2.0,
                        "${currency}5" to 5.0,
                        "10%" to (uiState.subtotal * 0.10)
                    )
                    quickTips.forEach { (label, amount) ->
                        FilterChip(
                            selected = !showCustomTipInput && Math.abs(uiState.tipAmount - amount) < 0.01,
                            onClick = {
                                showCustomTipInput = false
                                onTipAmountChanged(amount)
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                    FilterChip(
                        selected = showCustomTipInput,
                        onClick = { showCustomTipInput = !showCustomTipInput },
                        label = { Text("Custom...", fontSize = 11.sp) }
                    )
                }

                // Custom Tip Value Input
                if (showCustomTipInput) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customTipText,
                        onValueChange = { input ->
                            customTipText = input
                            val num = input.toDoubleOrNull() ?: 0.0
                            onTipAmountChanged(num)
                        },
                        label = { Text("Custom Tip Amount ($currency)") },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        placeholder = { Text("e.g. 2.50") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("custom_tip_input")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- DISCOUNT SECTION WITH DROPDOWN & CUSTOM VALUE ADDITION ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Discount",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.discountPercent > 0) {
                        Text(
                            text = "-${uiState.discountPercent.toInt()}% (-${currency}${String.format("%.2f", uiState.discountAmount)})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                var discountDropdownExpanded by remember { mutableStateOf(false) }
                var showCustomDiscountInput by remember { mutableStateOf(false) }
                var customDiscountText by remember { mutableStateOf(if (uiState.discountPercent > 0) uiState.discountPercent.toString() else "") }

                val discountOptions = listOf(
                    "No Discount (0%)" to 0.0,
                    "5% Discount" to 5.0,
                    "10% Discount" to 10.0,
                    "15% Discount" to 15.0,
                    "20% Discount" to 20.0,
                    "25% Discount" to 25.0,
                    "50% Discount" to 50.0,
                    "✏️ Add Custom Discount % / Value..." to -1.0
                )

                val currentDiscountLabel = remember(uiState.discountPercent, showCustomDiscountInput) {
                    if (showCustomDiscountInput) "Custom Discount Value"
                    else discountOptions.find { Math.abs(it.second - uiState.discountPercent) < 0.01 && it.second >= 0.0 }?.first
                        ?: if (uiState.discountPercent > 0) "${uiState.discountPercent.toInt()}% Discount" else "No Discount (0%)"
                }

                // Dropdown Box for Discount
                Box(modifier = Modifier.fillMaxWidth().testTag("discount_dropdown_box")) {
                    OutlinedButton(
                        onClick = { discountDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentDiscountLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = discountDropdownExpanded,
                        onDismissRequest = { discountDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        discountOptions.forEach { (label, value) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontWeight = if (value == -1.0 && showCustomDiscountInput) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    discountDropdownExpanded = false
                                    if (value == -1.0) {
                                        showCustomDiscountInput = true
                                    } else {
                                        showCustomDiscountInput = false
                                        onDiscountPercentChanged(value)
                                    }
                                }
                            )
                        }
                    }
                }

                // Quick Preset Chips for Discount
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickDiscounts = listOf(0.0, 5.0, 10.0, 15.0, 20.0)
                    quickDiscounts.forEach { disc ->
                        FilterChip(
                            selected = !showCustomDiscountInput && Math.abs(uiState.discountPercent - disc) < 0.01,
                            onClick = {
                                showCustomDiscountInput = false
                                onDiscountPercentChanged(disc)
                            },
                            label = { Text(if (disc == 0.0) "None" else "${disc.toInt()}%", fontSize = 11.sp) }
                        )
                    }
                    FilterChip(
                        selected = showCustomDiscountInput,
                        onClick = { showCustomDiscountInput = !showCustomDiscountInput },
                        label = { Text("Custom...", fontSize = 11.sp) }
                    )
                }

                // Custom Discount Value Input
                if (showCustomDiscountInput) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customDiscountText,
                        onValueChange = { input ->
                            customDiscountText = input
                            val num = input.toDoubleOrNull() ?: 0.0
                            onDiscountPercentChanged(num)
                        },
                        label = { Text("Custom Discount Percentage (%)") },
                        leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null) },
                        placeholder = { Text("e.g. 7.5 or 12.5") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("custom_discount_input")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Customer Info (Walk-in vs Add Details)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Customer Selection",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    if (onOpenCustomerDirectory != null) {
                        TextButton(
                            onClick = onOpenCustomerDirectory,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("browse_customers_btn")
                        ) {
                            Icon(Icons.Default.PeopleAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Saved Profiles", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Walk-in vs Add Customer Details Segmented Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isWalkIn,
                        onClick = {
                            isWalkIn = true
                            customerName = ""
                            customerPhone = ""
                            onCustomerInfoChanged("Walk-in Customer", "")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Walk-in Customer", fontSize = 12.sp, fontWeight = if (isWalkIn) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pay_chip_walk_in")
                    )

                    FilterChip(
                        selected = !isWalkIn,
                        onClick = {
                            isWalkIn = false
                            if (customerName.isBlank()) {
                                customerName = ""
                            }
                            onCustomerInfoChanged(customerName.ifEmpty { "Customer" }, customerPhone)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Add Details", fontSize = 12.sp, fontWeight = if (!isWalkIn) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pay_chip_add_details")
                    )
                }

                if (!isWalkIn) {
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = {
                            customerName = it
                            onCustomerInfoChanged(customerName.ifEmpty { "Customer" }, customerPhone)
                        },
                        label = { Text("Customer Name *") },
                        placeholder = { Text("e.g. John Doe / Business Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_cust_name_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = {
                            customerPhone = it
                            onCustomerInfoChanged(customerName.ifEmpty { "Customer" }, customerPhone)
                        },
                        label = { Text("Phone / E-Receipt SMS (Optional)") },
                        placeholder = { Text("e.g. +1 (415) 555-0198") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_cust_phone_input")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- CREDIT NOTE / TRADE NOTE ADJUSTMENT SECTION (OPTIONAL) ---
                var showCreditNoteSelector by remember { mutableStateOf(false) }
                var creditNoteSearchQuery by remember { mutableStateOf("") }

                val normCustomerPhone = normalizePhone(customerPhone)
                val customerMatchingCreditNotes = remember(availableCreditNotes, normCustomerPhone) {
                    if (normCustomerPhone.length >= 4) {
                        availableCreditNotes.filter {
                            normalizePhone(it.customerPhone).contains(normCustomerPhone) ||
                                normCustomerPhone.contains(normalizePhone(it.customerPhone).takeIf { p -> p.length >= 4 } ?: "---")
                        }
                    } else emptyList()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AssignmentReturn,
                            contentDescription = null,
                            tint = EmeraldTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Adjust Old Credit / Trade Note (Optional)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (uiState.appliedCreditNote != null) {
                        TextButton(
                            onClick = { onApplyCreditNote(null) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("remove_applied_credit_note_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Remove", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (uiState.appliedCreditNote != null) {
                    val cn = uiState.appliedCreditNote
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldTertiary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldTertiary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${cn.creditNoteNumber} Applied",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp,
                                    color = EmeraldTertiary
                                )
                                Text(
                                    text = "-${String.format("%s%.2f", currency, uiState.creditNoteAppliedAmount)}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = EmeraldTertiary
                                )
                            }
                            Text(
                                text = "From Original Bill #${cn.originalInvoiceNumber} • Customer: ${cn.customerName} (${cn.customerPhone})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (uiState.remainingCreditNoteBalanceAfterSale > 0.0)
                                    "Remaining Credit Balance Saved for Future Bills: ${String.format("%s%.2f", currency, uiState.remainingCreditNoteBalanceAfterSale)}"
                                else
                                    "Full Credit Note balance utilized on this bill.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldTertiary
                            )
                        }
                    }
                } else {
                    // Auto-detected Credit Notes for the entered customer phone number
                    if (customerMatchingCreditNotes.isNotEmpty()) {
                        customerMatchingCreditNotes.forEach { cn ->
                            Surface(
                                onClick = { onApplyCreditNote(cn) },
                                shape = RoundedCornerShape(10.dp),
                                color = EmeraldTertiary.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldTertiary.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .testTag("auto_detected_cn_${cn.creditNoteNumber}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Available Credit Note Found for ${cn.customerPhone}!",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = EmeraldTertiary
                                        )
                                        Text(
                                            "${cn.creditNoteNumber} (Ref Bill #${cn.originalInvoiceNumber}) • Bal: ${String.format("%s%.2f", currency, cn.remainingBalance)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Button(
                                        onClick = { onApplyCreditNote(cn) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldTertiary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Apply Credit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Button to manually browse or search old Credit Note bill
                    OutlinedButton(
                        onClick = { showCreditNoteSelector = !showCreditNoteSelector },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toggle_credit_note_selector_btn")
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = EmeraldTertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (availableCreditNotes.isEmpty())
                                "No Active Credit Notes Available"
                            else
                                "Add / Adjust Old Credit Note Bill (${availableCreditNotes.size} Active)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            if (showCreditNoteSelector) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }

                    if (showCreditNoteSelector && availableCreditNotes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = creditNoteSearchQuery,
                            onValueChange = { creditNoteSearchQuery = it },
                            label = { Text("Search by Mobile #, Credit Note # (CN-...), or Bill #") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_credit_note_checkout_input")
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val filteredCreditNotes = remember(availableCreditNotes, creditNoteSearchQuery) {
                            if (creditNoteSearchQuery.isBlank()) availableCreditNotes
                            else {
                                val q = creditNoteSearchQuery.trim()
                                availableCreditNotes.filter {
                                    it.creditNoteNumber.contains(q, ignoreCase = true) ||
                                        it.originalInvoiceNumber.contains(q, ignoreCase = true) ||
                                        it.customerPhone.contains(q, ignoreCase = true) ||
                                        it.customerName.contains(q, ignoreCase = true)
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filteredCreditNotes.take(5).forEach { cn ->
                                Surface(
                                    onClick = {
                                        onApplyCreditNote(cn)
                                        showCreditNoteSelector = false
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("select_credit_note_item_${cn.creditNoteNumber}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${cn.creditNoteNumber} • From Bill #${cn.originalInvoiceNumber}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp
                                            )
                                            Text(
                                                "${cn.customerName} • Mobile: ${cn.customerPhone}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                String.format("%s%.2f", currency, cn.remainingBalance),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.5.sp,
                                                color = EmeraldTertiary
                                            )
                                            Text(
                                                "Tap to Adjust",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Auto-print toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Print Thermal Receipt", fontWeight = FontWeight.Medium)
                            Text("Send ESC/POS commands via Bluetooth", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = uiState.autoPrintOnSale,
                        onCheckedChange = onAutoPrintToggled
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmCheckout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("complete_checkout_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Complete Sale & Issue Receipt (${String.format("%s%.2f", currency, uiState.totalAmount)})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        },
        dismissButton = {}
    )
}
