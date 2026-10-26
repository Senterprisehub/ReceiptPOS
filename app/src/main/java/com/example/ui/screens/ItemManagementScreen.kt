package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.CartItem
import com.example.ui.components.AnalyticsDashboardDialog
import com.example.ui.POSUiState
import com.example.ui.POSViewModel
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

enum class ItemSortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    PRICE_LOW_HIGH("Price (Low-High)"),
    PRICE_HIGH_LOW("Price (High-Low)"),
    STOCK_LOW_HIGH("Stock (Lowest First)"),
    STOCK_HIGH_LOW("Stock (Highest First)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemManagementScreen(
    viewModel: POSViewModel,
    uiState: POSUiState,
    catalogProducts: List<ProductEntity>,
    categories: List<String>,
    storeConfig: StoreConfigEntity,
    modifier: Modifier = Modifier
) {
    val currency = storeConfig.currencySymbol
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var sortOption by remember { mutableStateOf(ItemSortOption.NAME_ASC) }
    var showOnlyLowStock by remember { mutableStateOf(false) }
    var showAnalyticsDashboard by remember { mutableStateOf(false) }

    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }

    // Filtered and sorted products
    val filteredProducts = remember(catalogProducts, searchQuery, selectedCategory, sortOption, showOnlyLowStock) {
        val list = catalogProducts.filter { prod ->
            val matchCat = selectedCategory == "All" || prod.category.equals(selectedCategory, ignoreCase = true)
            val matchSearch = searchQuery.isEmpty() ||
                    prod.name.contains(searchQuery, ignoreCase = true) ||
                    prod.sku.contains(searchQuery, ignoreCase = true) ||
                    prod.barcode.contains(searchQuery, ignoreCase = true) ||
                    prod.category.contains(searchQuery, ignoreCase = true)
            val matchLowStock = !showOnlyLowStock || prod.stockQuantity <= 10
            matchCat && matchSearch && matchLowStock
        }

        when (sortOption) {
            ItemSortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            ItemSortOption.PRICE_LOW_HIGH -> list.sortedBy { it.price }
            ItemSortOption.PRICE_HIGH_LOW -> list.sortedByDescending { it.price }
            ItemSortOption.STOCK_LOW_HIGH -> list.sortedBy { it.stockQuantity }
            ItemSortOption.STOCK_HIGH_LOW -> list.sortedByDescending { it.stockQuantity }
        }
    }

    // Inventory Metrics
    val totalItems = catalogProducts.size
    val totalStockUnits = catalogProducts.sumOf { it.stockQuantity }
    val totalInventoryValue = catalogProducts.sumOf { it.price * it.stockQuantity }
    val lowStockCount = catalogProducts.count { it.stockQuantity in 1..10 }
    val outOfStockCount = catalogProducts.count { it.stockQuantity <= 0 }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.setShowAddProductDialog(true) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Item", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_item")
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            val isWide = maxWidth >= 540.dp

            LazyVerticalGrid(
                columns = if (isWide) GridCells.Adaptive(minSize = 280.dp) else GridCells.Fixed(1),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Analytical Dashboard Button
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        onClick = { showAnalyticsDashboard = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_analytical_dashboard"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Analytics,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Business Analytical Dashboard",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Click for detailed business insights & reports",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Button(
                                onClick = { showAnalyticsDashboard = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.QueryStats, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Insights", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Inventory KPI Metric Cards
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Products", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$totalItems", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("$totalStockUnits units total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Retail Value", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format("%s%.2f", currency, totalInventoryValue), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                Text("in stock inventory", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showOnlyLowStock = !showOnlyLowStock },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (showOnlyLowStock) StatusWarning.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = if (showOnlyLowStock) androidx.compose.foundation.BorderStroke(1.5.dp, StatusWarning) else null
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Alerts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (outOfStockCount > 0 || lowStockCount > 0) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(StatusWarning, CircleShape)
                                        )
                                    }
                                }
                                Text("${lowStockCount + outOfStockCount}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (outOfStockCount > 0) StatusError else StatusWarning)
                                Text("$outOfStockCount out of stock", fontSize = 10.sp, color = if (outOfStockCount > 0) StatusError else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Search and Filter Bar
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search catalog items, SKU, barcode...") },
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
                                .weight(1f)
                                .testTag("item_mgmt_search")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                ItemSortOption.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                option.label,
                                                fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            sortOption = option
                                            showSortMenu = false
                                        },
                                        trailingIcon = {
                                            if (sortOption == option) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Categories horizontal chip row
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 12.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // Item Results Count
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredProducts.size} Items Found",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (showOnlyLowStock) {
                            TextButton(onClick = { showOnlyLowStock = false }) {
                                Text("Show All Items", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Product List
                if (filteredProducts.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "No catalog items match your filter",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { viewModel.setShowAddProductDialog(true) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add First Item")
                                }
                            }
                        }
                    }
                } else {
                    items(filteredProducts, key = { it.id }) { product ->
                        ProductItemCard(
                            product = product,
                            currency = currency,
                            onEdit = { viewModel.openEditProductDialog(product) },
                            onAdjustStock = { viewModel.openAdjustStockDialog(product) },
                            onQuickIncrement = { delta ->
                                viewModel.adjustProductStock(product.id, (product.stockQuantity + delta).coerceAtLeast(0))
                            },
                            onToggleActive = { isActive ->
                                viewModel.toggleProductActive(product.id, isActive)
                            },
                            onDelete = { productToDelete = product }
                        )
                    }
                }
            }
        }
    }

    // Add Product Dialog (New Item)
    if (uiState.showAddProductDialog) {
        AddEditProductDialog(
            initialProduct = null,
            currency = currency,
            defaultTaxRate = storeConfig.defaultTaxPercent,
            existingCategories = categories.filter { it != "All" },
            enableTax = storeConfig.enableTax,
            onSave = { newProd ->
                viewModel.addNewProduct(newProd)
            },
            onDismiss = { viewModel.setShowAddProductDialog(false) }
        )
    }

    // Edit Product Dialog
    if (uiState.showEditProductDialog && uiState.productToEdit != null) {
        AddEditProductDialog(
            initialProduct = uiState.productToEdit,
            currency = currency,
            defaultTaxRate = storeConfig.defaultTaxPercent,
            existingCategories = categories.filter { it != "All" },
            enableTax = storeConfig.enableTax,
            onSave = { updatedProd ->
                viewModel.updateProduct(updatedProd)
            },
            onDismiss = { viewModel.closeEditProductDialog() }
        )
    }

    // Quick Adjust Stock Dialog
    if (uiState.showAdjustStockDialog && uiState.productForStockAdjust != null) {
        AdjustStockDialog(
            product = uiState.productForStockAdjust,
            onSaveStock = { newStock ->
                viewModel.adjustProductStock(uiState.productForStockAdjust.id, newStock)
            },
            onDismiss = { viewModel.closeAdjustStockDialog() }
        )
    }

    // Delete Product Confirmation
    if (productToDelete != null) {
        val prod = productToDelete!!
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Delete Product", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to permanently delete '${prod.name}' from your store catalog?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProduct(prod)
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Business Analytics Insights Dialog
    if (showAnalyticsDashboard) {
        BusinessAnalyticsInsightsDialog(
            catalogProducts = catalogProducts,
            receipts = receipts,
            storeConfig = storeConfig,
            parseItemsJson = { json -> viewModel.parseItemsJson(json) },
            onDismiss = { showAnalyticsDashboard = false }
        )
    }
}

@Composable
fun ProductItemCard(
    product: ProductEntity,
    currency: String,
    onEdit: () -> Unit,
    onAdjustStock: () -> Unit,
    onQuickIncrement: (Int) -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val stockColor = when {
        product.stockQuantity <= 0 -> StatusError
        product.stockQuantity <= 10 -> StatusWarning
        else -> StatusSuccess
    }

    val stockText = when {
        product.stockQuantity <= 0 -> "OUT OF STOCK"
        product.stockQuantity <= 10 -> "LOW (${product.stockQuantity})"
        else -> "${product.stockQuantity} in stock"
    }

    val marginPercent = if (product.price > 0 && product.costPrice > 0) {
        ((product.price - product.costPrice) / product.price) * 100.0
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (product.isActive)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (product.isActive) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Product Name, Category & Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = try {
                                    Color(android.graphics.Color.parseColor(product.colorHex))
                                } catch (e: Exception) {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = product.name.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = product.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (product.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!product.isActive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "ARCHIVED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = product.category,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (product.sku.isNotEmpty()) {
                                Text(
                                    text = "• SKU: ${product.sku}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (product.barcode.isNotEmpty()) {
                                Text(
                                    text = "• ▌▌ ${product.barcode}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Price and stock status
                Column(horizontalAlignment = Alignment.End) {
                    val hasDiscount = product.discountType != "NONE" && product.discountValue > 0.0
                    val discountAmount = when (product.discountType) {
                        "PERCENTAGE" -> product.price * (product.discountValue.coerceIn(0.0, 100.0) / 100.0)
                        "FLAT" -> product.discountValue.coerceAtMost(product.price)
                        else -> 0.0
                    }
                    val finalPrice = (product.price - discountAmount).coerceAtLeast(0.0)

                    if (hasDiscount) {
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = if (product.discountType == "PERCENTAGE") "-${product.discountValue.toInt()}% OFF" else "-${currency}${String.format("%.2f", product.discountValue)} OFF",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = String.format("%s%.2f", currency, product.price),
                            style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = String.format("%s%.2f", currency, if (hasDiscount) finalPrice else product.price),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (hasDiscount) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = stockColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, stockColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = stockText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = stockColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action toolbar & Quick stock adjusters
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Stock quick increment pills
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Stock: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(
                        onClick = { onQuickIncrement(-1) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("-1", fontSize = 11.sp)
                    }
                    FilledTonalButton(
                        onClick = { onQuickIncrement(1) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("+1", fontSize = 11.sp)
                    }
                    FilledTonalButton(
                        onClick = { onQuickIncrement(10) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("+10", fontSize = 11.sp)
                    }
                    IconButton(
                        onClick = onAdjustStock,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Adjust Stock", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Edit, Toggle Active, and Delete action buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Product",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { onToggleActive(!product.isActive) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (product.isActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (product.isActive) "Archive Item" else "Activate Item",
                            tint = if (product.isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Product",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditProductDialog(
    initialProduct: ProductEntity?,
    currency: String,
    defaultTaxRate: Double,
    existingCategories: List<String>,
    enableTax: Boolean = true,
    onSave: (ProductEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val isNew = initialProduct == null
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Details, 1: Taxable Amount Tab

    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var category by remember { mutableStateOf(initialProduct?.category ?: (existingCategories.firstOrNull() ?: "General")) }
    var newCategoryInput by remember { mutableStateOf("") }
    var isAddingNewCategory by remember { mutableStateOf(false) }
    var priceStr by remember { mutableStateOf(if (initialProduct != null) String.format(java.util.Locale.US, "%.2f", initialProduct.price) else "") }
    var costPriceStr by remember { mutableStateOf(if (initialProduct != null) String.format(java.util.Locale.US, "%.2f", initialProduct.costPrice) else "0.0") }
    var stockStr by remember { mutableStateOf(initialProduct?.stockQuantity?.toString() ?: "100") }
    var sku by remember { mutableStateOf(initialProduct?.sku ?: "") }
    var barcode by remember { mutableStateOf(initialProduct?.barcode ?: "") }
    var taxRateStr by remember { mutableStateOf(if (enableTax) (initialProduct?.taxRatePercent?.toString() ?: defaultTaxRate.toString()) else "0.0") }
    var isTaxInclusive by remember { mutableStateOf(true) } // Tax inclusive (MRP) vs exclusive (Net + Tax)
    var discountType by remember { mutableStateOf(initialProduct?.discountType ?: "NONE") }
    var discountValueStr by remember {
        mutableStateOf(
            if (initialProduct != null && initialProduct.discountValue > 0.0 && initialProduct.discountType != "NONE") {
                if (initialProduct.discountType == "PERCENTAGE") initialProduct.discountValue.toInt().toString()
                else String.format(java.util.Locale.US, "%.2f", initialProduct.discountValue)
            } else ""
        )
    }
    var isActive by remember { mutableStateOf(initialProduct?.isActive ?: true) }
    var colorHex by remember { mutableStateOf(initialProduct?.colorHex ?: "#1E88E5") }

    val availableColors = listOf(
        "#1E88E5", "#43A047", "#E53935", "#FB8C00", "#8E24AA",
        "#00ACC1", "#3949AB", "#D81B60", "#FDD835", "#546E7A"
    )

    val quickTaxPresets = listOf(0.0, 5.0, 8.25, 12.0, 18.0, 28.0)

    // Dynamic Reactive Tax & Taxable calculations
    val parsedPrice = priceStr.toDoubleOrNull() ?: 0.0
    val parsedCost = costPriceStr.toDoubleOrNull() ?: 0.0
    val parsedTaxRate = if (enableTax) (taxRateStr.toDoubleOrNull() ?: defaultTaxRate) else 0.0
    val parsedDiscountValue = discountValueStr.toDoubleOrNull() ?: 0.0

    val itemDiscountAmount = when (discountType) {
        "PERCENTAGE" -> parsedPrice * (parsedDiscountValue.coerceIn(0.0, 100.0) / 100.0)
        "FLAT" -> parsedDiscountValue.coerceAtMost(parsedPrice)
        else -> 0.0
    }
    val priceAfterDiscount = (parsedPrice - itemDiscountAmount).coerceAtLeast(0.0)

    val taxableBaseAmount = if (parsedPrice > 0.0) {
        if (isTaxInclusive) {
            if (parsedTaxRate > 0.0) parsedPrice / (1.0 + (parsedTaxRate / 100.0)) else parsedPrice
        } else {
            parsedPrice
        }
    } else 0.0

    val calculatedTaxAmount = if (parsedPrice > 0.0) {
        if (isTaxInclusive) {
            parsedPrice - taxableBaseAmount
        } else {
            parsedPrice * (parsedTaxRate / 100.0)
        }
    } else 0.0

    val finalTotalCustomerPrice = if (isTaxInclusive) parsedPrice else (taxableBaseAmount + calculatedTaxAmount)

    val netProfit = if (taxableBaseAmount > 0.0 && parsedCost > 0.0) taxableBaseAmount - parsedCost else null
    val profitMarginPercent = if (taxableBaseAmount > 0.0 && parsedCost > 0.0 && netProfit != null) {
        (netProfit / taxableBaseAmount) * 100.0
    } else null

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
                // Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isNew) Icons.Default.AddShoppingCart else Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isNew) "Add New Product" else "Edit Product",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Header Navigation (Product Details vs Taxable Amount) - only show Taxable tab if enableTax is true
                if (enableTax) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Product Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            },
                            modifier = Modifier.testTag("tab_product_details")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Taxable Amount", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            },
                            modifier = Modifier.testTag("tab_taxable_amount")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Scrollable Content Area based on Selected Tab
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!enableTax || selectedTab == 0) {
                        // --- TAB 0: PRODUCT DETAILS ---

                        // 1. Product Name
                        item {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Product Name *") },
                                placeholder = { Text("e.g. Espresso Coffee, Organic Apples") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("input_product_name")
                            )
                        }

                        // 2. Category Selection & Quick Add
                        item {
                            Column {
                                Text("Category:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(existingCategories) { cat ->
                                        val isSelected = !isAddingNewCategory && category.equals(cat, ignoreCase = true)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                category = cat
                                                isAddingNewCategory = false
                                            },
                                            label = { Text(cat, fontSize = 12.sp) },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                    item {
                                        FilterChip(
                                            selected = isAddingNewCategory,
                                            onClick = { isAddingNewCategory = true },
                                            label = { Text("+ Custom", fontSize = 12.sp) },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                                if (isAddingNewCategory) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = newCategoryInput,
                                        onValueChange = {
                                            newCategoryInput = it
                                            category = it
                                        },
                                        label = { Text("Custom Category Name") },
                                        placeholder = { Text("e.g. Bakery, Beverages, Electronics") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // 3. Price & Cost Price
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = priceStr,
                                    onValueChange = { priceStr = it },
                                    label = { Text(if (enableTax) "Retail Price (Incl. Tax) *" else "Retail Price ($currency) *") },
                                    placeholder = { Text("0.00") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f).testTag("input_product_price")
                                )
                                OutlinedTextField(
                                    value = costPriceStr,
                                    onValueChange = { costPriceStr = it },
                                    label = { Text("Cost Price ($currency)") },
                                    placeholder = { Text("0.00") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 4. Initial Stock & Tax Rate
                        item {
                            if (enableTax) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = stockStr,
                                        onValueChange = { stockStr = it },
                                        label = { Text("Stock Quantity Units") },
                                        placeholder = { Text("100") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f).testTag("input_product_stock")
                                    )
                                    OutlinedTextField(
                                        value = taxRateStr,
                                        onValueChange = { taxRateStr = it },
                                        label = { Text("Tax Rate %") },
                                        placeholder = { Text(defaultTaxRate.toString()) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                OutlinedTextField(
                                    value = stockStr,
                                    onValueChange = { stockStr = it },
                                    label = { Text("Stock Quantity Units") },
                                    placeholder = { Text("100") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("input_product_stock")
                                )
                            }
                        }

                        // 5. Item Discount Section (Flat / Percentage)
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (discountType != "NONE") Color(0xFF16A34A).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth().testTag("item_discount_section")
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.LocalOffer,
                                                contentDescription = null,
                                                tint = if (discountType != "NONE") Color(0xFF15803D) else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Item Discount (Flat / %)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (discountType != "NONE") Color(0xFF15803D) else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (discountType != "NONE" && parsedDiscountValue > 0.0) {
                                            Surface(
                                                color = Color(0xFFDCFCE7),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (discountType == "PERCENTAGE") "${parsedDiscountValue.toInt()}% OFF" else "$currency${String.format("%.2f", parsedDiscountValue)} OFF",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF15803D),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Discount Type Selector
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = discountType == "NONE",
                                            onClick = {
                                                discountType = "NONE"
                                                discountValueStr = ""
                                            },
                                            label = { Text("No Discount", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f).testTag("chip_no_discount")
                                        )
                                        FilterChip(
                                            selected = discountType == "PERCENTAGE",
                                            onClick = { discountType = "PERCENTAGE" },
                                            label = { Text("Percentage (%)", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1.1f).testTag("chip_percent_discount")
                                        )
                                        FilterChip(
                                            selected = discountType == "FLAT",
                                            onClick = { discountType = "FLAT" },
                                            label = { Text("Flat ($currency)", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f).testTag("chip_flat_discount")
                                        )
                                    }

                                    if (discountType != "NONE") {
                                        Spacer(modifier = Modifier.height(8.dp))

                                        if (discountType == "PERCENTAGE") {
                                            // Preset % Chips
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                listOf(5.0, 10.0, 15.0, 20.0, 25.0, 50.0).forEach { p ->
                                                    SuggestionChip(
                                                        onClick = { discountValueStr = p.toInt().toString() },
                                                        label = { Text("${p.toInt()}%", fontSize = 10.5.sp) }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            OutlinedTextField(
                                                value = discountValueStr,
                                                onValueChange = { discountValueStr = it },
                                                label = { Text("Discount Percentage (%)") },
                                                placeholder = { Text("e.g. 10 or 25") },
                                                leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.fillMaxWidth().testTag("input_product_discount_percent")
                                            )
                                        } else {
                                            // Preset Flat Chips
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                listOf(1.0, 2.0, 5.0, 10.0).filter { it < parsedPrice || parsedPrice == 0.0 }.forEach { f ->
                                                    SuggestionChip(
                                                        onClick = { discountValueStr = String.format(java.util.Locale.US, "%.2f", f) },
                                                        label = { Text("$currency${String.format(java.util.Locale.US, "%.2f", f)}", fontSize = 10.5.sp) }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            OutlinedTextField(
                                                value = discountValueStr,
                                                onValueChange = { discountValueStr = it },
                                                label = { Text("Flat Discount per Unit ($currency)") },
                                                placeholder = { Text("e.g. 1.50 or 5.00") },
                                                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.fillMaxWidth().testTag("input_product_discount_flat")
                                            )
                                        }

                                        if (itemDiscountAmount > 0.0 && parsedPrice > 0.0) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                color = Color(0xFFDCFCE7).copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "Price after Discount: $currency${String.format("%.2f", priceAfterDiscount)}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF15803D)
                                                    )
                                                    Text(
                                                        "Saved: -$currency${String.format("%.2f", itemDiscountAmount)}",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF15803D)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Interactive Taxable Amount Quick Banner (Links to Taxable Amount Tab)
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTab = 1 }
                                    .testTag("taxable_amount_preview_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Calculated Taxable Amount", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Base: $currency${String.format("%.2f", taxableBaseAmount)} • Tax (${String.format("%.1f", parsedTaxRate)}%): $currency${String.format("%.2f", calculatedTaxAmount)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { selectedTab = 1 },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("View Tab →", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // 5. SKU & Barcode
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = sku,
                                    onValueChange = { sku = it },
                                    label = { Text("SKU Code") },
                                    placeholder = { Text("e.g. BEV-001") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = barcode,
                                    onValueChange = { barcode = it },
                                    label = { Text("Barcode / EAN") },
                                    placeholder = { Text("e.g. 890123456789") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 6. Color Tag Picker
                        item {
                            Column {
                                Text("Card Badge Color:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(availableColors) { hex ->
                                        val isSelected = colorHex.equals(hex, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    color = try {
                                                        Color(android.graphics.Color.parseColor(hex))
                                                    } catch (e: Exception) {
                                                        MaterialTheme.colorScheme.primary
                                                    }
                                                )
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable { colorHex = hex },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 7. Active Status Toggle
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Active in Register POS", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Display and sell this item on the main register grid", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = isActive,
                                        onCheckedChange = { isActive = it }
                                    )
                                }
                            }
                        }
                    } else {
                        // --- TAB 1: TAXABLE AMOUNT CALCULATION TAB ---

                        // Header Explanation
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Calculate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "Taxable Amount & Tax Engine",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            "Dynamically calculates the exact taxable base amount and tax component based on the Retail Selling Price and Selected Tax %.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }

                        // Interactive Inputs for Price & Tax Rate
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Calculation Inputs", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = priceStr,
                                            onValueChange = { priceStr = it },
                                            label = { Text("Retail Price ($currency) *") },
                                            placeholder = { Text("0.00") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f).testTag("tab_tax_input_price")
                                        )

                                        OutlinedTextField(
                                            value = taxRateStr,
                                            onValueChange = { taxRateStr = it },
                                            label = { Text("Tax Percentage %") },
                                            placeholder = { Text(defaultTaxRate.toString()) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f).testTag("tab_tax_input_rate")
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Quick Tax Preset Rate Chips
                                    Text("Quick Tax Presets:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(quickTaxPresets) { preset ->
                                            val isSelected = parsedTaxRate == preset
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { taxRateStr = preset.toString() },
                                                label = { Text("${preset}%", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Tax Mode Selector (Inclusive vs Exclusive)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isTaxInclusive) "Retail Price Includes Tax (MRP/Gross)" else "Retail Price Excludes Tax (Net + Tax)",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = if (isTaxInclusive) "Taxable amount extracted from retail price" else "Tax calculated and added on top of retail price",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isTaxInclusive,
                                            onCheckedChange = { isTaxInclusive = it },
                                            modifier = Modifier.testTag("tax_inclusive_switch")
                                        )
                                    }
                                }
                            }
                        }

                        // Calculated Breakdown Result Card
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().testTag("taxable_amount_results_card")
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Tax Breakdown Results", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (isTaxInclusive) "INCLUSIVE TAX" else "EXCLUSIVE TAX",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Key Metric 1: Net Taxable Base Amount (Prominent)
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Net Taxable Amount (Base Price)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                                Text("Amount subject to tax/accounting", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(
                                                text = "$currency${String.format("%.2f", taxableBaseAmount)}",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Metric 2: Tax Component
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = EmeraldTertiary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Tax Component (${String.format("%.2f", parsedTaxRate)}%)", fontSize = 13.sp)
                                        }
                                        Text(
                                            text = "$currency${String.format("%.2f", calculatedTaxAmount)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = EmeraldTertiary
                                        )
                                    }

                                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    // Metric 3: Total Customer Selling Price
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Total Customer Billed Price", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            text = "$currency${String.format("%.2f", finalTotalCustomerPrice)}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Cost & Profitability Analysis if Cost Price is provided
                                    if (parsedCost > 0.0) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Product Cost Price:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("$currency${String.format("%.2f", parsedCost)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Net Profit (Taxable - Cost):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(
                                                        "$currency${String.format("%.2f", netProfit ?: 0.0)}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if ((netProfit ?: 0.0) >= 0) StatusSuccess else StatusError
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Gross Profit Margin:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(
                                                        "${String.format("%.1f", profitMarginPercent ?: 0.0)}%",
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
                        }

                        // Formula Breakdown Box
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Mathematical Formula Applied", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (isTaxInclusive) {
                                        Text(
                                            text = "• Taxable Amount = Retail Price ÷ (1 + Tax Rate / 100)\n  = $currency${String.format("%.2f", parsedPrice)} ÷ (1 + ${parsedTaxRate} / 100) = $currency${String.format("%.2f", taxableBaseAmount)}",
                                            fontSize = 11.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• Tax Amount = Retail Price - Taxable Amount\n  = $currency${String.format("%.2f", parsedPrice)} - $currency${String.format("%.2f", taxableBaseAmount)} = $currency${String.format("%.2f", calculatedTaxAmount)}",
                                            fontSize = 11.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = "• Taxable Amount = Retail Price = $currency${String.format("%.2f", taxableBaseAmount)}",
                                            fontSize = 11.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• Tax Amount = Retail Price × (Tax Rate / 100)\n  = $currency${String.format("%.2f", parsedPrice)} × ${parsedTaxRate}% = $currency${String.format("%.2f", calculatedTaxAmount)}",
                                            fontSize = 11.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                val isValid = name.isNotBlank() && parsedPrice > 0.0

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val price = priceStr.toDoubleOrNull() ?: 0.0
                            val cost = costPriceStr.toDoubleOrNull() ?: 0.0
                            val stock = stockStr.toIntOrNull() ?: 100
                            val tax = if (enableTax) (taxRateStr.toDoubleOrNull() ?: defaultTaxRate) else 0.0
                            val finalCat = if (isAddingNewCategory && newCategoryInput.isNotBlank()) newCategoryInput.trim() else category.ifBlank { "General" }
                            val finalSku = sku.ifBlank { "SKU-" + System.currentTimeMillis().toString().takeLast(4) }

                            val prod = initialProduct?.copy(
                                name = name.trim(),
                                category = finalCat,
                                price = price,
                                costPrice = cost,
                                stockQuantity = stock,
                                sku = finalSku,
                                barcode = barcode.trim(),
                                taxRatePercent = tax,
                                colorHex = colorHex,
                                discountType = discountType,
                                discountValue = if (discountType != "NONE") parsedDiscountValue else 0.0,
                                isActive = isActive,
                                updatedAt = System.currentTimeMillis()
                            ) ?: ProductEntity(
                                name = name.trim(),
                                category = finalCat,
                                price = price,
                                costPrice = cost,
                                stockQuantity = stock,
                                sku = finalSku,
                                barcode = barcode.trim(),
                                taxRatePercent = tax,
                                colorHex = colorHex,
                                discountType = discountType,
                                discountValue = if (discountType != "NONE") parsedDiscountValue else 0.0,
                                isActive = isActive,
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(prod)
                        },
                        enabled = isValid,
                        modifier = Modifier.weight(1.2f).testTag("save_product_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isNew) "Add to Catalog" else "Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AdjustStockDialog(
    product: ProductEntity,
    onSaveStock: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var stockCount by remember { mutableIntStateOf(product.stockQuantity) }
    var stockInput by remember { mutableStateOf(product.stockQuantity.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Adjust Stock Inventory", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Category: ${product.category} • SKU: ${product.sku.ifEmpty { "N/A" }}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = stockInput,
                    onValueChange = {
                        stockInput = it
                        val v = it.toIntOrNull()
                        if (v != null) stockCount = v
                    },
                    label = { Text("Exact Stock Count") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Quick Adjust:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(-5, +1, +5, +10, +50).forEach { delta ->
                        FilledTonalButton(
                            onClick = {
                                val updated = (stockCount + delta).coerceAtLeast(0)
                                stockCount = updated
                                stockInput = updated.toString()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (delta > 0) "+$delta" else "$delta", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalStock = stockInput.toIntOrNull() ?: stockCount
                    onSaveStock(finalStock.coerceAtLeast(0))
                }
            ) {
                Text("Update Stock")
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
fun BusinessAnalyticsInsightsDialog(
    catalogProducts: List<ProductEntity>,
    receipts: List<ReceiptEntity>,
    storeConfig: StoreConfigEntity,
    parseItemsJson: (String) -> List<CartItem>,
    onDismiss: () -> Unit
) {
    AnalyticsDashboardDialog(
        catalogProducts = catalogProducts,
        receipts = receipts,
        storeConfig = storeConfig,
        parseItemsJson = parseItemsJson,
        onDismiss = onDismiss
    )
}
