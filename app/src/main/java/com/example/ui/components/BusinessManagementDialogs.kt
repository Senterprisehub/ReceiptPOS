package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.BusinessProfileEntity
import com.example.data.model.CountryCurrencyPreset
import com.example.data.model.CurrencyCountryPresets
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun BusinessSelectorDialog(
    businesses: List<BusinessProfileEntity>,
    activeBusinessId: Long,
    onSelectBusiness: (Long) -> Unit,
    onAddNewBusiness: () -> Unit,
    onEditBusiness: (BusinessProfileEntity) -> Unit,
    onDeleteBusiness: (BusinessProfileEntity) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Business Profiles",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select or manage store locations & businesses",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Bar
                Button(
                    onClick = onAddNewBusiness,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_add_new_business"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Business Profile", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Divider()

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(businesses) { business ->
                        val isActive = business.id == activeBusinessId
                        val preset = CurrencyCountryPresets.findPresetByCountry(business.countryCode)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = if (isActive) 2.dp else 1.dp,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onSelectBusiness(business.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = preset?.flagEmoji ?: "🏪",
                                            fontSize = 24.sp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = business.businessName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (isActive) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = StatusSuccess.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(6.dp),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusSuccess)
                                                    ) {
                                                        Text(
                                                            text = "ACTIVE",
                                                            color = StatusSuccess,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (business.tagline.isNotEmpty()) {
                                                Text(
                                                    text = business.tagline,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Row {
                                        IconButton(onClick = { onEditBusiness(business) }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit Business",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        if (businesses.size > 1) {
                                            IconButton(onClick = { onDeleteBusiness(business) }) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Business",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Details chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                "${business.currencyCode} (${business.currencySymbol})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                    if (business.enableTax) {
                                        AssistChip(
                                            onClick = {},
                                            label = {
                                                Text(
                                                    "${business.defaultTaxPercent}% ${business.taxName}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            },
                                            leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        )
                                    } else {
                                        AssistChip(
                                            onClick = {},
                                            label = {
                                                Text(
                                                    "0% Tax-Free",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            },
                                            leadingIcon = { Icon(Icons.Default.MoneyOff, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        )
                                    }
                                }

                                if (business.address.isNotEmpty() || business.phone.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = listOfNotNull(business.address.takeIf { it.isNotEmpty() }, business.phone.takeIf { it.isNotEmpty() }).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }

                                if (!isActive) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { onSelectBusiness(business.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Switch to this Business", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun AddEditBusinessDialog(
    initialBusiness: BusinessProfileEntity? = null,
    onSave: (BusinessProfileEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val isEdit = initialBusiness != null

    var businessName by remember { mutableStateOf(initialBusiness?.businessName ?: "") }
    var tagline by remember { mutableStateOf(initialBusiness?.tagline ?: "") }
    var address by remember { mutableStateOf(initialBusiness?.address ?: "") }
    var phone by remember { mutableStateOf(initialBusiness?.phone ?: "") }
    var email by remember { mutableStateOf(initialBusiness?.email ?: "") }
    var taxId by remember { mutableStateOf(initialBusiness?.taxId ?: "") }

    var selectedCountryCode by remember { mutableStateOf(initialBusiness?.countryCode ?: "US") }
    var countryName by remember { mutableStateOf(initialBusiness?.countryName ?: "United States") }
    var currencyCode by remember { mutableStateOf(initialBusiness?.currencyCode ?: "USD") }
    var currencySymbol by remember { mutableStateOf(initialBusiness?.currencySymbol ?: "$") }
    var taxName by remember { mutableStateOf(initialBusiness?.taxName ?: "Sales Tax") }
    var defaultTaxPercentStr by remember { mutableStateOf((initialBusiness?.defaultTaxPercent ?: 8.25).toString()) }
    var enableTax by remember { mutableStateOf(initialBusiness?.enableTax ?: true) }
    var receiptHeader by remember { mutableStateOf(initialBusiness?.receiptHeader ?: "--- THANK YOU FOR YOUR BUSINESS ---") }
    var receiptFooter by remember { mutableStateOf(initialBusiness?.receiptFooter ?: "Goods returnable with valid receipt in 14 days.\nHave a wonderful day!") }
    var paperWidthMm by remember { mutableIntStateOf(initialBusiness?.paperWidthMm ?: 58) }
    var enableUpiPayment by remember { mutableStateOf(initialBusiness?.enableUpiPayment ?: false) }
    var upiId by remember { mutableStateOf(initialBusiness?.upiId ?: "") }
    var upiPayeeName by remember { mutableStateOf(initialBusiness?.upiPayeeName ?: "") }
    var enableDenominationLogo by remember { mutableStateOf(initialBusiness?.enableDenominationLogo ?: true) }
    var logoUri by remember { mutableStateOf(initialBusiness?.logoUri) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val logoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val savedPath = com.example.util.LogoImageUtils.saveLogoFromUri(
                context = context,
                sourceUri = uri,
                businessId = initialBusiness?.id ?: System.currentTimeMillis()
            )
            if (savedPath != null) {
                logoUri = savedPath
            }
        }
    }

    var showPresetSelector by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEdit) "Edit Business Profile" else "Add New Business",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Country & Currency Preset Auto-tax selector banner
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Country & Currency Preset",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            "Auto-configures Currency, Currency Symbol & Official Tax Rate",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = { showPresetSelector = true },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        val preset = CurrencyCountryPresets.findPresetByCountry(selectedCountryCode)
                                        Text("${preset?.flagEmoji ?: "🌐"} $countryName")
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Currency: $currencyCode ($currencySymbol)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (enableTax) {
                                        Text(
                                            text = "Auto Tax: $defaultTaxPercentStr% ($taxName)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = EmeraldTertiary
                                        )
                                    } else {
                                        Text(
                                            text = "0% Tax-Free Mode",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Business Logo (Optional - if blank, receipts/invoices render cleanly without logo)
                    item {
                        val logoBmp = remember(logoUri) {
                            com.example.util.LogoImageUtils.loadLogoBitmap(context, logoUri, maxDimension = 160)
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                        .clickable { logoPickerLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (logoBmp != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = logoBmp.asImageBitmap(),
                                            contentDescription = "Business Logo",
                                            modifier = Modifier.fillMaxSize().padding(4.dp)
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Default.AddPhotoAlternate,
                                                contentDescription = "Upload Logo",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Text("No Logo", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Business Logo (Optional)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = if (logoUri != null) "Uploaded. Printed on receipts & invoices." else "If blank, bills & invoices generate cleanly without a logo.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(
                                            onClick = { logoPickerLauncher.launch("image/*") },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (logoUri != null) "Change" else "Upload Logo", fontSize = 11.sp)
                                        }

                                        if (logoUri != null) {
                                            OutlinedButton(
                                                onClick = { logoUri = null },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Remove", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Business Name & Tagline
                    item {
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it },
                            label = { Text("Business Name *") },
                            placeholder = { Text("e.g. Downtown Espresso & Pastry") },
                            leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_business_name")
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = tagline,
                            onValueChange = { tagline = it },
                            label = { Text("Tagline / Slogan") },
                            placeholder = { Text("e.g. Fresh roasts & daily essentials") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Address, Phone, Email
                    item {
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Physical Address") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Tax System On/Off Toggle
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (enableTax)
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Tax Calculation (VAT / GST / Sales Tax)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (enableTax) StatusSuccess.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                if (enableTax) "TAXABLE" else "0% TAX-FREE",
                                                color = if (enableTax) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        if (enableTax) "System calculates and prints tax on items and receipts"
                                        else "Non-taxable store: all checkout totals operate with 0% tax",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = enableTax,
                                    onCheckedChange = { enableTax = it },
                                    modifier = Modifier.testTag("dialog_tax_switch")
                                )
                            }
                        }
                    }

                    // Tax ID, Tax Label & Rate
                    if (enableTax) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = taxId,
                                    onValueChange = { taxId = it },
                                    label = { Text("Tax Registration / GSTIN / VAT ID") },
                                    placeholder = { Text("e.g. GSTIN123456 / VAT-987654") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = currencySymbol,
                                    onValueChange = { currencySymbol = it },
                                    label = { Text("Currency Symbol") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.8f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = taxName,
                                    onValueChange = { taxName = it },
                                    label = { Text("Tax Label (e.g. GST, VAT)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.2f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = defaultTaxPercentStr,
                                    onValueChange = { defaultTaxPercentStr = it },
                                    label = { Text("Tax Rate %") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        item {
                            OutlinedTextField(
                                value = currencySymbol,
                                onValueChange = { currencySymbol = it },
                                label = { Text("Currency Symbol") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Receipt Templates
                    item {
                        OutlinedTextField(
                            value = receiptHeader,
                            onValueChange = { receiptHeader = it },
                            label = { Text("Receipt Header Text") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = receiptFooter,
                            onValueChange = { receiptFooter = it },
                            label = { Text("Receipt Footer Note") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Paper Width
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Thermal Paper Width:", fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = paperWidthMm == 58,
                                    onClick = { paperWidthMm = 58 },
                                    label = { Text("58mm (Compact)") }
                                )
                                FilterChip(
                                    selected = paperWidthMm == 80,
                                    onClick = { paperWidthMm = 80 },
                                    label = { Text("80mm (Standard)") }
                                )
                            }
                        }
                    }

                    // UPI & QR Code Payments
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Enable UPI Payment QR Code", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Prints QR on receipts for instant customer payments", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = enableUpiPayment, onCheckedChange = { enableUpiPayment = it })
                                }

                                if (enableUpiPayment) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = upiId,
                                        onValueChange = { upiId = it.trim() },
                                        label = { Text("UPI ID / VPA *") },
                                        placeholder = { Text("e.g. store@okaxis") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = upiPayeeName,
                                        onValueChange = { upiPayeeName = it },
                                        label = { Text("Payee Name (Optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // Denomination Logo
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Print Denomination Header", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text("Prints currency badge on thermal receipt", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = enableDenominationLogo, onCheckedChange = { enableDenominationLogo = it })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val taxVal = defaultTaxPercentStr.toDoubleOrNull() ?: 0.0
                            val updated = (initialBusiness ?: BusinessProfileEntity(businessName = businessName)).copy(
                                businessName = businessName.ifEmpty { "New Business" },
                                tagline = tagline,
                                address = address,
                                phone = phone,
                                email = email,
                                taxId = taxId,
                                countryCode = selectedCountryCode,
                                countryName = countryName,
                                currencyCode = currencyCode,
                                currencySymbol = currencySymbol.ifEmpty { "$" },
                                taxName = taxName.ifEmpty { "Tax" },
                                defaultTaxPercent = taxVal,
                                enableTax = enableTax,
                                receiptHeader = receiptHeader,
                                receiptFooter = receiptFooter,
                                paperWidthMm = paperWidthMm,
                                enableUpiPayment = enableUpiPayment,
                                upiId = upiId,
                                upiPayeeName = upiPayeeName,
                                enableDenominationLogo = enableDenominationLogo,
                                logoUri = logoUri
                            )
                            onSave(updated)
                        },
                        enabled = businessName.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_save_business_profile"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isEdit) "Save Changes" else "Create Business")
                    }
                }
            }
        }
    }

    if (showPresetSelector) {
        CountryCurrencyPresetDialog(
            currentCountryCode = selectedCountryCode,
            onSelectPreset = { preset ->
                selectedCountryCode = preset.countryCode
                countryName = preset.countryName
                currencyCode = preset.currencyCode
                currencySymbol = preset.currencySymbol
                taxName = preset.taxName
                defaultTaxPercentStr = preset.defaultTaxPercent.toString()
                receiptHeader = preset.defaultReceiptHeader
                receiptFooter = preset.defaultReceiptFooter
                showPresetSelector = false
            },
            onDismiss = { showPresetSelector = false }
        )
    }
}

@Composable
fun CountryCurrencyPresetDialog(
    currentCountryCode: String,
    onSelectPreset: (CountryCurrencyPreset) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredPresets = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            CurrencyCountryPresets.PRESETS
        } else {
            CurrencyCountryPresets.PRESETS.filter {
                it.countryName.contains(searchQuery, ignoreCase = true) ||
                        it.countryCode.contains(searchQuery, ignoreCase = true) ||
                        it.currencyCode.contains(searchQuery, ignoreCase = true) ||
                        it.currencySymbol.contains(searchQuery, ignoreCase = true) ||
                        it.taxName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select Country & Currency",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automatic tax input & currency symbol configuration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search country, currency (USD, INR, EUR, £)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_country_search")
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPresets) { preset ->
                        val isSelected = preset.countryCode.equals(currentCountryCode, ignoreCase = true)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectPreset(preset) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = preset.flagEmoji,
                                        fontSize = 28.sp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = preset.countryName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Currency: ${preset.currencyCode} (${preset.currencySymbol})",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${preset.defaultTaxPercent}% ${preset.taxName.substringBefore('(').trim()}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
