package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BusinessProfileEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.CountryCurrencyPreset
import com.example.data.model.CurrencyCountryPresets
import com.example.ui.POSUiState
import com.example.ui.POSViewModel
import com.example.ui.components.AddEditBusinessDialog
import com.example.ui.components.BusinessSelectorDialog
import com.example.ui.components.CountryCurrencyPresetDialog
import com.example.ui.components.UserManualDialog
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusSuccess
import com.example.util.BarcodeQrUtils

@Composable
fun SettingsScreen(
    viewModel: POSViewModel,
    uiState: POSUiState,
    storeConfig: StoreConfigEntity,
    businesses: List<BusinessProfileEntity>,
    products: List<ProductEntity>,
    modifier: Modifier = Modifier
) {
    var showUserManualDialog by remember { mutableStateOf(false) }

    var businessName by remember(storeConfig) { mutableStateOf(storeConfig.businessName) }
    var tagline by remember(storeConfig) { mutableStateOf(storeConfig.tagline) }
    var address by remember(storeConfig) { mutableStateOf(storeConfig.address) }
    var phone by remember(storeConfig) { mutableStateOf(storeConfig.phone) }
    var email by remember(storeConfig) { mutableStateOf(storeConfig.email) }
    var taxId by remember(storeConfig) { mutableStateOf(storeConfig.taxId) }
    var countryCode by remember(storeConfig) { mutableStateOf(storeConfig.countryCode) }
    var countryName by remember(storeConfig) { mutableStateOf(storeConfig.countryName) }
    var currencyCode by remember(storeConfig) { mutableStateOf(storeConfig.currencyCode) }
    var currencySymbol by remember(storeConfig) { mutableStateOf(storeConfig.currencySymbol) }
    var taxName by remember(storeConfig) { mutableStateOf(storeConfig.taxName) }
    var defaultTaxRateStr by remember(storeConfig) { mutableStateOf("${storeConfig.defaultTaxPercent}") }
    var enableTax by remember(storeConfig) { mutableStateOf(storeConfig.enableTax) }
    var paperWidthMm by remember(storeConfig) { mutableStateOf(storeConfig.paperWidthMm) }
    var receiptHeader by remember(storeConfig) { mutableStateOf(storeConfig.receiptHeader) }
    var receiptFooter by remember(storeConfig) { mutableStateOf(storeConfig.receiptFooter) }
    var showQr by remember(storeConfig) { mutableStateOf(storeConfig.showQrVerification) }
    var showBarcode by remember(storeConfig) { mutableStateOf(storeConfig.showBarcode) }
    var masterPin by remember { mutableStateOf(storeConfig.masterPin) }
    var isPinUnlocked by remember { mutableStateOf(storeConfig.masterPin.isEmpty()) }
    var showPinUnlockDialog by remember { mutableStateOf(false) }
    var pinUnlockInput by remember { mutableStateOf("") }
    var isPinRequiredForRefund by remember(storeConfig) { mutableStateOf(storeConfig.isPinRequiredForRefund) }
    var enableUpiPayment by remember(storeConfig) { mutableStateOf(storeConfig.enableUpiPayment) }
    var upiId by remember(storeConfig) { mutableStateOf(storeConfig.upiId) }
    var upiPayeeName by remember(storeConfig) { mutableStateOf(storeConfig.upiPayeeName) }
    var enableDenominationLogo by remember(storeConfig) { mutableStateOf(storeConfig.enableDenominationLogo) }
    var syncEndpointUrl by remember(storeConfig) { mutableStateOf(storeConfig.syncEndpointUrl) }

    val activePreset = CurrencyCountryPresets.findPresetByCountry(countryCode)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // User Manual & Step-by-Step Guide Quick Banner Card
        item {
            Card(
                onClick = { showUserManualDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_user_manual_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "User Manual & Guide",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    Text(
                                        "HELP",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                "Step-by-step instructions for register, printers, backups & setup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = { showUserManualDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_open_user_manual")
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Multi-Business Switcher & Management Card
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                activePreset?.flagEmoji ?: "🏪",
                                fontSize = 22.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Multiple Business Profiles",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${businesses.size} Registered Profile${if (businesses.size > 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = { viewModel.setShowBusinessSelectorDialog(true) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Switch Store", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        businessName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = StatusSuccess.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "ACTIVE",
                                            color = StatusSuccess,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    if (enableTax) "$countryName • Currency: $currencyCode ($currencySymbol) • $defaultTaxRateStr% $taxName"
                                    else "$countryName • Currency: $currencyCode ($currencySymbol) • 0% Tax-Free Store",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    val currentBusiness = businesses.find { it.id == storeConfig.activeBusinessId }
                                        ?: BusinessProfileEntity(
                                            businessName = businessName,
                                            tagline = tagline,
                                            address = address,
                                            phone = phone,
                                            email = email,
                                            taxId = taxId,
                                            countryCode = countryCode,
                                            countryName = countryName,
                                            currencyCode = currencyCode,
                                            currencySymbol = currencySymbol,
                                            taxName = taxName,
                                            defaultTaxPercent = defaultTaxRateStr.toDoubleOrNull() ?: 8.25
                                        )
                                    viewModel.openEditBusinessDialog(currentBusiness)
                                }
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Business",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.setShowAddBusinessDialog(true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Business", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.setShowBusinessSelectorDialog(true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All Stores (${businesses.size})", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Multi-Currency & Automatic Tax Configuration Card
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CurrencyExchange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Currency & Automatic Tax",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Presets for 20+ countries with statutory tax rates",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // TAX CALCULATION ON / OFF SWITCH
                    Surface(
                        color = if (enableTax)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (enableTax) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Automatic Tax Calculation",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = if (enableTax) StatusSuccess.copy(alpha = 0.18f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (enableTax) StatusSuccess else MaterialTheme.colorScheme.outline
                                        )
                                    ) {
                                        Text(
                                            if (enableTax) "ACTIVE (TAXABLE)" else "DISABLED (0% TAX)",
                                            color = if (enableTax) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (enableTax)
                                        "Taxable Business: System computes and prints tax (${taxName} • ${defaultTaxRateStr}%) across cart, checkout, receipts & invoices."
                                    else
                                        "Tax-Free Business: Entire system operates without tax calculation (0% tax mode). No tax is charged or printed.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Switch(
                                checked = enableTax,
                                onCheckedChange = { isChecked ->
                                    enableTax = isChecked
                                    viewModel.toggleTaxEnabled(isChecked)
                                },
                                modifier = Modifier.testTag("tax_system_toggle_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset Country / Currency Button
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setShowCurrencyCountryDialog(true) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    activePreset?.flagEmoji ?: "🌐",
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        countryName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        if (enableTax)
                                            "Currency: $currencyCode ($currencySymbol) • Tax: $defaultTaxRateStr% $taxName"
                                        else
                                            "Currency: $currencyCode ($currencySymbol) • Tax: 0.0% (Disabled)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.setShowCurrencyCountryDialog(true) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Change Preset", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Currency Symbol & Tax Fields
                    if (enableTax) {
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
                                label = { Text("Tax Label (VAT/GST)") },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = defaultTaxRateStr,
                                onValueChange = { defaultTaxRateStr = it },
                                label = { Text("Tax Rate %") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = currencySymbol,
                            onValueChange = { currencySymbol = it },
                            label = { Text("Currency Symbol") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (enableTax)
                            "💡 Tax is automatically calculated at checkout according to this percentage."
                        else
                            "💡 Tax is turned OFF. Entire application operates in 0% tax-free mode.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Theme & Display Mode Card
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (uiState.isDarkTheme == true) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "App Theme & Appearance",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    when (uiState.isDarkTheme) {
                                        true -> "Dark Theme Active"
                                        false -> "Light Theme Active"
                                        null -> "Following System Mode"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Light Mode Button
                        OutlinedButton(
                            onClick = { viewModel.setThemeMode(false) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_btn_light"),
                            shape = RoundedCornerShape(10.dp),
                            colors = if (uiState.isDarkTheme == false) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Icon(Icons.Filled.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Light", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Dark Mode Button
                        OutlinedButton(
                            onClick = { viewModel.setThemeMode(true) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_btn_dark"),
                            shape = RoundedCornerShape(10.dp),
                            colors = if (uiState.isDarkTheme == true) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Icon(Icons.Filled.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dark", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // System Default Button
                        OutlinedButton(
                            onClick = { viewModel.setThemeMode(null) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_btn_system"),
                            shape = RoundedCornerShape(10.dp),
                            colors = if (uiState.isDarkTheme == null) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Icon(Icons.Filled.BrightnessAuto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("System", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Store Identity Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Store Identity & Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("Store / Business Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tagline,
                        onValueChange = { tagline = it },
                        label = { Text("Tagline / Subtitle") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Physical Store Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Store Phone") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Billing Email") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (enableTax) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = taxId,
                            onValueChange = { taxId = it },
                            label = { Text("Tax Registration / GSTIN / VAT ID") },
                            placeholder = { Text("e.g. GSTIN123456 / VAT-987654") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Thermal Receipt Configuration Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Thermal Printer & Receipt Format",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Paper Width Switch
                    Text("Paper Roll Width", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = paperWidthMm == 58,
                            onClick = { paperWidthMm = 58 },
                            label = { Text("58mm Standard (32 Chars)") }
                        )
                        FilterChip(
                            selected = paperWidthMm == 80,
                            onClick = { paperWidthMm = 80 },
                            label = { Text("80mm Large POS (48 Chars)") }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = receiptHeader,
                        onValueChange = { receiptHeader = it },
                        label = { Text("Custom Receipt Header Message") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = receiptFooter,
                        onValueChange = { receiptFooter = it },
                        label = { Text("Receipt Footer & Return Policy") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Print Denominations & Base Currency Badge", fontWeight = FontWeight.Medium)
                            Text("Includes currency symbol ($currencySymbol) and store denomination identifier on receipts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enableDenominationLogo, onCheckedChange = { enableDenominationLogo = it })
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Print QR Code Verification")
                        Switch(checked = showQr, onCheckedChange = { showQr = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Print Invoice Barcode")
                        Switch(checked = showBarcode, onCheckedChange = { showBarcode = it })
                    }
                }
            }
        }

        // UPI & Digital Payment QR Code Card
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "UPI & Digital QR Payments",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Prints customer payment QR on thermal receipts",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = enableUpiPayment,
                            onCheckedChange = { enableUpiPayment = it }
                        )
                    }

                    if (enableUpiPayment) {
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = upiId,
                            onValueChange = { upiId = it.trim() },
                            label = { Text("Merchant UPI ID / VPA *") },
                            placeholder = { Text("e.g. yourstore@okaxis, 9876543210@paytm") },
                            leadingIcon = { Icon(Icons.Default.Payment, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = upiPayeeName,
                            onValueChange = { upiPayeeName = it },
                            label = { Text("Payee Display Name (Optional)") },
                            placeholder = { Text(businessName.ifBlank { "Store Merchant Name" }) },
                            leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Dynamic QR Code Preview
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Live UPI QR Code Preview",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Compatible with GPay, PhonePe, Paytm, BHIM & all UPI apps",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (upiId.isNotBlank()) {
                                    val previewUri = remember(upiId, upiPayeeName, businessName) {
                                        BarcodeQrUtils.buildUpiPaymentUri(
                                            upiId = upiId,
                                            payeeName = upiPayeeName.ifBlank { businessName.ifBlank { "POS Store" } }
                                        )
                                    }
                                    val qrBmp = remember(previewUri) {
                                        BarcodeQrUtils.generateQrCodeBitmap(previewUri, size = 160)
                                    }
                                    if (qrBmp != null) {
                                        Image(
                                            bitmap = qrBmp.asImageBitmap(),
                                            contentDescription = "UPI QR Code Preview",
                                            modifier = Modifier
                                                .size(130.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(androidx.compose.ui.graphics.Color.White)
                                                .padding(6.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "UPI: $upiId",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    val displayName = upiPayeeName.ifBlank { businessName.ifBlank { "POS Store" } }
                                    Text(
                                        displayName,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        "Enter your UPI ID above to generate and test your QR code",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Security & Access Control
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Security & Supervisor Access",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = if (isPinUnlocked) masterPin else "••••",
                        onValueChange = { 
                            if (isPinUnlocked && it.length <= 4 && it.all { c -> c.isDigit() }) {
                                masterPin = it
                                // Auto update in system when pin changes
                                val updated = storeConfig.copy(masterPin = it)
                                viewModel.updateStoreConfig(updated)
                            }
                        },
                        label = { Text("Master 4-Digit Supervisor PIN") },
                        singleLine = true,
                        enabled = isPinUnlocked,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (storeConfig.masterPin.isNotEmpty()) {
                                IconButton(onClick = {
                                    if (isPinUnlocked) {
                                        isPinUnlocked = false
                                        masterPin = storeConfig.masterPin
                                    } else {
                                        showPinUnlockDialog = true
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (isPinUnlocked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle PIN visibility"
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Require PIN for Refunds & Voids")
                        Switch(
                            checked = isPinRequiredForRefund,
                            onCheckedChange = { isPinRequiredForRefund = it }
                        )
                    }
                }
            }
        }

        // Save Settings & User Manual Action Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val updated = storeConfig.copy(
                            businessName = businessName,
                            tagline = tagline,
                            address = address,
                            phone = phone,
                            email = email,
                            taxId = taxId,
                            countryCode = countryCode,
                            countryName = countryName,
                            currencyCode = currencyCode,
                            currencySymbol = currencySymbol,
                            taxName = taxName,
                            defaultTaxPercent = defaultTaxRateStr.toDoubleOrNull() ?: 8.5,
                            enableTax = enableTax,
                            paperWidthMm = paperWidthMm,
                            receiptHeader = receiptHeader,
                            receiptFooter = receiptFooter,
                            showQrVerification = showQr,
                            showBarcode = showBarcode,
                            masterPin = masterPin,
                            isPinRequiredForRefund = isPinRequiredForRefund,
                            enableUpiPayment = enableUpiPayment,
                            upiId = upiId,
                            upiPayeeName = upiPayeeName,
                            enableDenominationLogo = enableDenominationLogo,
                            syncEndpointUrl = syncEndpointUrl
                        )
                        viewModel.updateStoreConfig(updated)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_settings_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Configuration Changes", fontWeight = FontWeight.Bold)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // User Manual & Guide Modal Dialog
    if (showUserManualDialog) {
        UserManualDialog(
            onDismiss = { showUserManualDialog = false }
        )
    }

    // Business Selector Dialog
    if (showPinUnlockDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPinUnlockDialog = false
                pinUnlockInput = ""
            },
            title = { Text("Enter Supervisor PIN") },
            text = {
                OutlinedTextField(
                    value = pinUnlockInput,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinUnlockInput = it },
                    label = { Text("Enter PIN to unlock") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinUnlockInput == storeConfig.masterPin) {
                        isPinUnlocked = true
                        showPinUnlockDialog = false
                    }
                    pinUnlockInput = ""
                }) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showPinUnlockDialog = false 
                    pinUnlockInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showBusinessSelectorDialog) {
        BusinessSelectorDialog(
            businesses = businesses,
            activeBusinessId = storeConfig.activeBusinessId,
            onSelectBusiness = { businessId ->
                viewModel.switchBusiness(businessId)
            },
            onAddNewBusiness = {
                viewModel.setShowBusinessSelectorDialog(false)
                viewModel.setShowAddBusinessDialog(true)
            },
            onEditBusiness = { business ->
                viewModel.setShowBusinessSelectorDialog(false)
                viewModel.openEditBusinessDialog(business)
            },
            onDeleteBusiness = { business ->
                viewModel.deleteBusiness(business)
            },
            onDismiss = { viewModel.setShowBusinessSelectorDialog(false) }
        )
    }

    // Add New Business Dialog
    if (uiState.showAddBusinessDialog) {
        AddEditBusinessDialog(
            initialBusiness = null,
            onSave = { newBusiness ->
                viewModel.addNewBusiness(newBusiness)
            },
            onDismiss = { viewModel.setShowAddBusinessDialog(false) }
        )
    }

    // Edit Existing Business Dialog
    if (uiState.showEditBusinessDialog && uiState.businessToEdit != null) {
        AddEditBusinessDialog(
            initialBusiness = uiState.businessToEdit,
            onSave = { updatedBusiness ->
                viewModel.updateBusiness(updatedBusiness)
            },
            onDismiss = { viewModel.closeEditBusinessDialog() }
        )
    }

    // Currency & Country Preset Selector Dialog
    if (uiState.showCurrencyCountryDialog) {
        CountryCurrencyPresetDialog(
            currentCountryCode = countryCode,
            onSelectPreset = { preset ->
                countryCode = preset.countryCode
                countryName = preset.countryName
                currencyCode = preset.currencyCode
                currencySymbol = preset.currencySymbol
                taxName = preset.taxName
                defaultTaxRateStr = preset.defaultTaxPercent.toString()
                receiptHeader = preset.defaultReceiptHeader
                receiptFooter = preset.defaultReceiptFooter

                viewModel.applyCountryPreset(preset)
                viewModel.setShowCurrencyCountryDialog(false)
            },
            onDismiss = { viewModel.setShowCurrencyCountryDialog(false) }
        )
    }
}
