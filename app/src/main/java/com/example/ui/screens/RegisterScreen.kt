package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.CartItem
import com.example.ui.POSUiState
import com.example.ui.POSViewModel
import com.example.ui.components.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: POSViewModel,
    uiState: POSUiState,
    products: List<ProductEntity>,
    categories: List<String>,
    storeConfig: StoreConfigEntity,
    modifier: Modifier = Modifier
) {
    val currency = storeConfig.currencySymbol
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val availableCreditNotes by viewModel.availableCreditNotes.collectAsStateWithLifecycle()
    var isCartExpanded by remember { mutableStateOf(false) }
    var itemToDiscount by remember { mutableStateOf<CartItem?>(null) }

    // Filtered products
    val filteredProducts = remember(products, uiState.searchQuery, uiState.selectedCategory) {
        products.filter { prod ->
            val matchCategory = uiState.selectedCategory == "All" || prod.category.equals(uiState.selectedCategory, ignoreCase = true)
            val matchSearch = uiState.searchQuery.isEmpty() ||
                    prod.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    prod.sku.contains(uiState.searchQuery, ignoreCase = true) ||
                    prod.barcode.contains(uiState.searchQuery, ignoreCase = true)
            matchCategory && matchSearch
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isDualPane = maxWidth >= 600.dp
        val isCompactHeight = maxHeight < 500.dp

        if (isDualPane) {
            // ==========================================
            // TABLET / WIDE POS DUAL-PANE WORKSTATION
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isCompactHeight) 8.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 10.dp else 14.dp)
            ) {
                // LEFT PANE: Unified scrollable Catalog, Search, Categories & Product Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (isCompactHeight) 115.dp else 135.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                ) {
                    // Search Bar & Quick Actions
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search item or SKU...", fontSize = if (isCompactHeight) 12.sp else 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(if (isCompactHeight) 18.dp else 22.dp)) },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(if (isCompactHeight) 16.dp else 20.dp))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pos_search_input")
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            FilledIconButton(
                                onClick = { viewModel.setShowBarcodeScannerDialog(true) },
                                modifier = Modifier
                                    .size(if (isCompactHeight) 38.dp else 44.dp)
                                    .testTag("open_barcode_scanner_btn")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", modifier = Modifier.size(if (isCompactHeight) 18.dp else 22.dp))
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            FilledTonalIconButton(
                                onClick = { viewModel.setShowCustomItemDialog(true) },
                                modifier = Modifier
                                    .size(if (isCompactHeight) 38.dp else 44.dp)
                                    .testTag("open_custom_item_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Custom Item", modifier = Modifier.size(if (isCompactHeight) 18.dp else 22.dp))
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            FilledTonalIconButton(
                                onClick = { viewModel.setShowAddProductDialog(true) },
                                modifier = Modifier
                                    .size(if (isCompactHeight) 38.dp else 44.dp)
                                    .testTag("open_add_product_btn")
                            ) {
                                Icon(Icons.Default.AddBusiness, contentDescription = "Add New Product to Catalog", modifier = Modifier.size(if (isCompactHeight) 18.dp else 22.dp))
                            }
                        }
                    }

                    // Category Chips
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(categories) { category ->
                                val isSelected = uiState.selectedCategory == category
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setSelectedCategory(category) },
                                    label = { Text(category, fontSize = if (isCompactHeight) 11.sp else 13.sp) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // Products Grid
                    if (filteredProducts.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.Inventory2,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (isCompactHeight) 40.dp else 56.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("No products found", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    } else {
                        items(filteredProducts, key = { it.id }) { product ->
                            ProductCard(
                                product = product,
                                currency = currency,
                                cartQuantity = uiState.cartItems.find { it.productId == product.id }?.quantity ?: 0,
                                onAddToCart = { viewModel.addToCart(product) }
                            )
                        }
                    }
                }

                // RIGHT PANE: Persistent POS Terminal Order Summary (Scrollable in horizontal/compact height)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val isWalkInWide = uiState.selectedCustomer == null && (uiState.customerName == "Walk-in Customer" || uiState.customerName.isBlank())

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isCompactHeight) 8.dp else 14.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 4.dp else 8.dp)
                    ) {
                        // Header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (isCompactHeight) 18.dp else 22.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Current Order", style = if (isCompactHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text("${uiState.totalItemsCount}")
                                    }
                                }

                                if (uiState.cartItems.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearCart() }, modifier = Modifier.size(if (isCompactHeight) 24.dp else 30.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(if (isCompactHeight) 16.dp else 20.dp))
                                    }
                                }
                            }
                        }

                        // Customer Strip
                        item {
                            Surface(
                                onClick = { viewModel.setShowCustomerSelectionDialog(true) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isWalkInWide) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = if (isCompactHeight) 4.dp else 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(if (isWalkInWide) Icons.Default.DirectionsWalk else Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isWalkInWide) "Walk-in Customer" else uiState.customerName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(if (isWalkInWide) "Attach" else "Change", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        item {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }

                        // Cart Items List
                        if (uiState.cartItems.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = if (isCompactHeight) 16.dp else 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Outlined.ShoppingCart, contentDescription = null, modifier = Modifier.size(if (isCompactHeight) 36.dp else 48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Order is empty", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Tap products on the left to add", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        } else {
                            items(uiState.cartItems) { item ->
                                CartItemRow(
                                    item = item,
                                    currency = currency,
                                    onIncrease = { viewModel.updateCartItemQuantity(item, item.quantity + 1) },
                                    onDecrease = { viewModel.updateCartItemQuantity(item, item.quantity - 1) },
                                    onEditDiscount = { itemToDiscount = item },
                                    onRemove = { viewModel.removeCartItem(item) }
                                )
                            }
                        }

                        item {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }

                        // Totals Summary
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Total Payable", style = if (isCompactHeight) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = String.format("%s%.2f", currency, uiState.totalAmount),
                                    style = if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Charge / Checkout Button
                        item {
                            Button(
                                onClick = { viewModel.openCheckout() },
                                enabled = uiState.cartItems.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isCompactHeight) 40.dp else 48.dp)
                                    .testTag("cart_checkout_btn")
                            ) {
                                Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(if (isCompactHeight) 18.dp else 22.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Charge ${String.format("%s%.2f", currency, uiState.totalAmount)}", fontWeight = FontWeight.Bold, fontSize = if (isCompactHeight) 13.sp else 15.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // ==========================================
            // COMPACT MOBILE PHONE LAYOUT (Unified Scroll)
            // ==========================================
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(
                        top = 10.dp,
                        bottom = if (uiState.cartItems.isNotEmpty()) 150.dp else 16.dp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp)
                ) {
                    // Search Bar & Quick Scan Actions
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search item, SKU or barcode...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pos_search_input")
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Barcode Scanner button
                            FilledIconButton(
                                onClick = { viewModel.setShowBarcodeScannerDialog(true) },
                                modifier = Modifier.testTag("open_barcode_scanner_btn")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Quick Add Custom Item to Cart button
                            FilledTonalIconButton(
                                onClick = { viewModel.setShowCustomItemDialog(true) },
                                modifier = Modifier.testTag("open_custom_item_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Custom Item")
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Quick Add New Product to Inventory Catalog button
                            FilledTonalIconButton(
                                onClick = { viewModel.setShowAddProductDialog(true) },
                                modifier = Modifier.testTag("open_add_product_btn")
                            ) {
                                Icon(Icons.Default.AddBusiness, contentDescription = "Add New Product to Catalog")
                            }
                        }
                    }

                    // Category Chips Row
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories) { category ->
                                val isSelected = uiState.selectedCategory == category
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setSelectedCategory(category) },
                                    label = { Text(category) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    // Customer Selector Strip (Walk-in vs Attached Customer)
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val isWalkIn = uiState.selectedCustomer == null && (uiState.customerName == "Walk-in Customer" || uiState.customerName.isBlank())
                        Surface(
                            onClick = { viewModel.setShowCustomerSelectionDialog(true) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isWalkIn) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isWalkIn) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_quick_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isWalkIn) Icons.Default.DirectionsWalk else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isWalkIn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isWalkIn) "Walk-in Customer" else uiState.customerName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isWalkIn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!isWalkIn && uiState.customerPhone.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "• ${uiState.customerPhone}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (isWalkIn) "Tap to attach customer or loyalty profile" else "Profile linked to this receipt",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!isWalkIn) {
                                        IconButton(
                                            onClick = { viewModel.selectWalkInCustomer() },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Switch to Walk-in",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }

                                    FilledTonalButton(
                                        onClick = { viewModel.setShowCustomerSelectionDialog(true) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text(if (isWalkIn) "Select / Add" else "Change", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Products Grid
                    if (filteredProducts.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.Inventory2,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No products found",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Try searching another keyword or tap '+' for a custom item",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredProducts, key = { it.id }) { product ->
                            ProductCard(
                                product = product,
                                currency = currency,
                                cartQuantity = uiState.cartItems.find { it.productId == product.id }?.quantity ?: 0,
                                onAddToCart = { viewModel.addToCart(product) }
                            )
                        }
                    }
                }

                // Floating Cart Bar (Bottom)
                if (uiState.cartItems.isNotEmpty()) {
                    Surface(
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .testTag("floating_cart_bar")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header row with Expand toggle and Clear button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isCartExpanded = !isCartExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text("${uiState.totalItemsCount}")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Current Order",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Icon(
                                        if (isCartExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                        contentDescription = "Toggle Cart"
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = String.format("%s%.2f", currency, uiState.totalAmount),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { viewModel.clearCart() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Clear Cart",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            // Expanded Cart Details
                            AnimatedVisibility(visible = isCartExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = if (isCompactHeight) 120.dp else 240.dp)
                                        .padding(top = 8.dp)
                                ) {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    ) {
                                        items(uiState.cartItems) { item ->
                                            CartItemRow(
                                                item = item,
                                                currency = currency,
                                                onIncrease = { viewModel.updateCartItemQuantity(item, item.quantity + 1) },
                                                onDecrease = { viewModel.updateCartItemQuantity(item, item.quantity - 1) },
                                                onEditDiscount = { itemToDiscount = item },
                                                onRemove = { viewModel.removeCartItem(item) }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Checkout Button
                            Button(
                                onClick = { viewModel.openCheckout() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("cart_checkout_btn"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.PointOfSale, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Charge ${String.format("%s%.2f", currency, uiState.totalAmount)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. Payment Dialog
    if (uiState.showCheckoutDialog) {
        PaymentDialog(
            uiState = uiState,
            config = storeConfig,
            availableCreditNotes = availableCreditNotes,
            onDismiss = { viewModel.closeCheckout() },
            onPaymentMethodSelected = { viewModel.setPaymentMethod(it) },
            onAmountTenderedChanged = { viewModel.setAmountTendered(it) },
            onDiscountPercentChanged = { viewModel.setDiscountPercent(it) },
            onTipAmountChanged = { viewModel.setTipAmount(it) },
            onCustomerInfoChanged = { name, phone -> viewModel.setCustomerInfo(name, phone) },
            onAutoPrintToggled = { viewModel.setAutoPrintOnSale(it) },
            onOpenCustomerDirectory = { viewModel.setShowCustomerSelectionDialog(true) },
            onApplyCreditNote = { viewModel.applyCreditNote(it) },
            onConfirmCheckout = { viewModel.processCheckout() }
        )
    }

    // 2. Customer Selection Directory Dialog
    if (uiState.showCustomerSelectionDialog) {
        CustomerSelectionDialog(
            customers = customers,
            selectedCustomer = uiState.selectedCustomer,
            currentCustomerName = uiState.customerName,
            currencySymbol = currency,
            onSelectWalkIn = { viewModel.selectWalkInCustomer() },
            onSelectCustomer = { viewModel.selectCustomer(it) },
            onAddNewCustomer = {
                viewModel.setShowCustomerSelectionDialog(false)
                viewModel.setShowAddCustomerDialog(true)
            },
            onEditCustomer = { cust ->
                viewModel.setShowCustomerSelectionDialog(false)
                viewModel.openEditCustomerDialog(cust)
            },
            onDeleteCustomer = { cust ->
                viewModel.deleteCustomer(cust)
            },
            onDismiss = { viewModel.setShowCustomerSelectionDialog(false) }
        )
    }

    // 3. Add Customer Dialog
    if (uiState.showAddCustomerDialog) {
        AddEditCustomerDialog(
            customer = null,
            onSave = { newCust ->
                viewModel.saveCustomer(newCust)
                viewModel.selectCustomer(newCust)
            },
            onDismiss = { viewModel.setShowAddCustomerDialog(false) }
        )
    }

    // 4. Edit Customer Dialog
    if (uiState.showEditCustomerDialog && uiState.customerToEdit != null) {
        AddEditCustomerDialog(
            customer = uiState.customerToEdit,
            onSave = { updatedCust ->
                viewModel.saveCustomer(updatedCust)
            },
            onDelete = { toDelete ->
                viewModel.deleteCustomer(toDelete)
            },
            onDismiss = { viewModel.closeEditCustomerDialog() }
        )
    }

    // 5. Custom Quick Item Dialog
    if (uiState.showCustomItemDialog) {
        CustomItemDialog(
            currency = currency,
            defaultTaxRate = storeConfig.defaultTaxPercent,
            enableTax = storeConfig.enableTax,
            onDismiss = { viewModel.setShowCustomItemDialog(false) },
            onAdd = { name, price, qty, tax, discType, discVal ->
                viewModel.addCustomItem(name, price, qty, if (storeConfig.enableTax) tax else 0.0, discType, discVal)
            }
        )
    }

    // 6. Barcode Scanner Dialog
    if (uiState.showBarcodeScannerDialog) {
        BarcodeScannerDialog(
            products = products,
            onDismiss = { viewModel.setShowBarcodeScannerDialog(false) },
            onProductScanned = { prod ->
                viewModel.addToCart(prod)
                viewModel.setShowBarcodeScannerDialog(false)
                viewModel.showToast("Added '${prod.name}' to cart!")
            }
        )
    }

    // 7. Add Product Dialog (New Inventory Item)
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

    // 8. Item-Level Discount Dialog
    itemToDiscount?.let { currentItem ->
        ItemDiscountDialog(
            item = currentItem,
            currency = currency,
            onDismiss = { itemToDiscount = null },
            onApplyDiscount = { type, value ->
                viewModel.updateCartItemDiscount(currentItem, type, value)
                itemToDiscount = null
                val desc = when (type) {
                    com.example.data.model.ItemDiscountType.PERCENTAGE -> "${value.toInt()}% discount applied to '${currentItem.name}'"
                    com.example.data.model.ItemDiscountType.FLAT -> "$currency${String.format("%.2f", value)} flat discount applied to '${currentItem.name}'"
                    com.example.data.model.ItemDiscountType.NONE -> "Discount removed from '${currentItem.name}'"
                }
                viewModel.showToast(desc)
            }
        )
    }
}

@Composable
fun ProductCard(
    product: ProductEntity,
    currency: String,
    cartQuantity: Int,
    onAddToCart: () -> Unit
) {
    Surface(
        onClick = onAddToCart,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = if (cartQuantity > 0) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Category mini pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = product.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }

                if (cartQuantity > 0) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$cartQuantity",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "SKU: ${product.sku.ifEmpty { "N/A" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasProductDiscount = product.discountType != "NONE" && product.discountValue > 0.0
                val productDiscountAmount = when (product.discountType) {
                    "PERCENTAGE" -> product.price * (product.discountValue.coerceIn(0.0, 100.0) / 100.0)
                    "FLAT" -> product.discountValue.coerceAtMost(product.price)
                    else -> 0.0
                }
                val effectivePrice = (product.price - productDiscountAmount).coerceAtLeast(0.0)

                Column {
                    if (hasProductDiscount) {
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = if (product.discountType == "PERCENTAGE") "-${product.discountValue.toInt()}% OFF" else "-${currency}${String.format("%.2f", product.discountValue)}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = String.format("%s%.2f", currency, product.price),
                            style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = String.format("%s%.2f", currency, if (hasProductDiscount) effectivePrice else product.price),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = if (hasProductDiscount) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    currency: String,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onEditDiscount: () -> Unit,
    onRemove: () -> Unit
) {
    val hasItemDiscount = item.itemDiscountAmount > 0.0 && item.discountType != com.example.data.model.ItemDiscountType.NONE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        String.format("%s%.2f each", currency, item.unitPrice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Discount trigger pill/chip button
                    Surface(
                        onClick = onEditDiscount,
                        shape = RoundedCornerShape(4.dp),
                        color = if (hasItemDiscount) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.testTag("item_discount_btn_${item.name.replace(" ", "_")}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocalOffer,
                                contentDescription = "Item Discount",
                                modifier = Modifier.size(10.dp),
                                tint = if (hasItemDiscount) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (hasItemDiscount) {
                                    when (item.discountType) {
                                        com.example.data.model.ItemDiscountType.PERCENTAGE -> "-${item.discountValue.toInt()}%"
                                        com.example.data.model.ItemDiscountType.FLAT -> "-${currency}${String.format("%.2f", item.discountValue)}"
                                        com.example.data.model.ItemDiscountType.NONE -> "Discount"
                                    }
                                } else "Discount",
                                fontSize = 9.5.sp,
                                fontWeight = if (hasItemDiscount) FontWeight.Bold else FontWeight.Medium,
                                color = if (hasItemDiscount) Color(0xFF15803D) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Stepper
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrease, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                }
                Text(
                    "${item.quantity}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(onClick = onIncrease, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                }
            }

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(65.dp).padding(start = 4.dp)) {
                if (hasItemDiscount) {
                    Text(
                        text = String.format("%s%.2f", currency, item.grossTotal),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                    )
                }
                Text(
                    text = String.format("%s%.2f", currency, item.total),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (hasItemDiscount) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Dialog to add Flat Discount or Discount Percentage for an individual cart item.
 */
@Composable
fun ItemDiscountDialog(
    item: CartItem,
    currency: String,
    onDismiss: () -> Unit,
    onApplyDiscount: (com.example.data.model.ItemDiscountType, Double) -> Unit
) {
    var selectedType by remember { mutableStateOf(item.discountType) }
    var discountValueStr by remember {
        mutableStateOf(
            if (item.discountValue > 0.0 && item.discountType != com.example.data.model.ItemDiscountType.NONE) {
                if (item.discountType == com.example.data.model.ItemDiscountType.PERCENTAGE) {
                    item.discountValue.toInt().toString()
                } else {
                    String.format(Locale.US, "%.2f", item.discountValue)
                }
            } else ""
        )
    }

    val parsedValue = discountValueStr.toDoubleOrNull() ?: 0.0

    // Compute live discount & new price preview
    val calculatedDiscountAmount = when (selectedType) {
        com.example.data.model.ItemDiscountType.PERCENTAGE -> (item.grossTotal * (parsedValue.coerceIn(0.0, 100.0) / 100.0))
        com.example.data.model.ItemDiscountType.FLAT -> (parsedValue * item.quantity).coerceAtMost(item.grossTotal)
        com.example.data.model.ItemDiscountType.NONE -> 0.0
    }
    val newTotal = (item.grossTotal - calculatedDiscountAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Item Discount", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        item.name,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Info Summary Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Original Item Total (${item.quantity}x @ $currency${String.format("%.2f", item.unitPrice)}):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "$currency${String.format("%.2f", item.grossTotal)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        if (calculatedDiscountAmount > 0.0 && selectedType != com.example.data.model.ItemDiscountType.NONE) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("After Item Discount:", fontSize = 11.sp, color = Color(0xFF16A34A))
                                Text(
                                    "$currency${String.format("%.2f", newTotal)}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = Color(0xFF16A34A)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Discount Type Selector Tabs (No Discount / Percentage % / Flat Amount)
                Text("Select Discount Type:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedType == com.example.data.model.ItemDiscountType.NONE,
                        onClick = {
                            selectedType = com.example.data.model.ItemDiscountType.NONE
                            discountValueStr = ""
                        },
                        label = { Text("No Discount", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).testTag("discount_type_none")
                    )
                    FilterChip(
                        selected = selectedType == com.example.data.model.ItemDiscountType.PERCENTAGE,
                        onClick = { selectedType = com.example.data.model.ItemDiscountType.PERCENTAGE },
                        leadingIcon = {
                            if (selectedType == com.example.data.model.ItemDiscountType.PERCENTAGE) {
                                Icon(Icons.Default.Percent, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        },
                        label = { Text("Percentage (%)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).testTag("discount_type_percentage")
                    )
                    FilterChip(
                        selected = selectedType == com.example.data.model.ItemDiscountType.FLAT,
                        onClick = { selectedType = com.example.data.model.ItemDiscountType.FLAT },
                        leadingIcon = {
                            if (selectedType == com.example.data.model.ItemDiscountType.FLAT) {
                                Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        },
                        label = { Text("Flat ($currency)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).testTag("discount_type_flat")
                    )
                }

                if (selectedType != com.example.data.model.ItemDiscountType.NONE) {
                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedType == com.example.data.model.ItemDiscountType.PERCENTAGE) {
                        // Quick percentage preset chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(5.0, 10.0, 15.0, 20.0, 25.0, 50.0).forEach { p ->
                                SuggestionChip(
                                    onClick = { discountValueStr = p.toInt().toString() },
                                    label = { Text("${p.toInt()}%", fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = discountValueStr,
                            onValueChange = { discountValueStr = it },
                            label = { Text("Discount Percentage (%)") },
                            placeholder = { Text("e.g. 10 or 25") },
                            leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag("item_discount_percent_input")
                        )
                    } else if (selectedType == com.example.data.model.ItemDiscountType.FLAT) {
                        // Quick flat preset chips
                        val unitP = item.unitPrice
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(1.0, 2.0, 5.0, 10.0, unitP * 0.1, unitP * 0.2).distinct().filter { it < item.unitPrice && it > 0 }.take(5).forEach { f ->
                                SuggestionChip(
                                    onClick = { discountValueStr = String.format(Locale.US, "%.2f", f) },
                                    label = { Text("$currency${String.format(Locale.US, "%.2f", f)}", fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = discountValueStr,
                            onValueChange = { discountValueStr = it },
                            label = { Text("Flat Discount per Unit ($currency)") },
                            placeholder = { Text("e.g. 1.50 or 5.00") },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag("item_discount_flat_input")
                        )
                    }

                    if (calculatedDiscountAmount > 0.0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Item Discount Total: -$currency${String.format("%.2f", calculatedDiscountAmount)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedType == com.example.data.model.ItemDiscountType.NONE) {
                        onApplyDiscount(com.example.data.model.ItemDiscountType.NONE, 0.0)
                    } else {
                        onApplyDiscount(selectedType, parsedValue)
                    }
                },
                modifier = Modifier.testTag("save_item_discount_btn")
            ) {
                Text("Apply Discount")
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
fun CustomItemDialog(
    currency: String,
    defaultTaxRate: Double = 0.0,
    enableTax: Boolean = true,
    onDismiss: () -> Unit,
    onAdd: (name: String, price: Double, qty: Int, tax: Double, discountType: com.example.data.model.ItemDiscountType, discountValue: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var priceStr by remember { mutableStateOf("") }
    var qtyStr by remember { mutableStateOf("1") }
    var taxStr by remember { mutableStateOf(if (enableTax) defaultTaxRate.toString() else "0.0") }
    var discountType by remember { mutableStateOf(com.example.data.model.ItemDiscountType.NONE) }
    var discountValStr by remember { mutableStateOf("") }

    val parsedPrice = priceStr.toDoubleOrNull() ?: 0.0
    val parsedDiscVal = discountValStr.toDoubleOrNull() ?: 0.0
    val discAmt = when (discountType) {
        com.example.data.model.ItemDiscountType.PERCENTAGE -> parsedPrice * (parsedDiscVal.coerceIn(0.0, 100.0) / 100.0)
        com.example.data.model.ItemDiscountType.FLAT -> parsedDiscVal.coerceAtMost(parsedPrice)
        com.example.data.model.ItemDiscountType.NONE -> 0.0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Custom Item", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    placeholder = { Text("e.g. Special Order / Repair") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Price ($currency)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (enableTax) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = qtyStr,
                            onValueChange = { qtyStr = it },
                            label = { Text("Quantity") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = taxStr,
                            onValueChange = { taxStr = it },
                            label = { Text("Tax %") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = qtyStr,
                        onValueChange = { qtyStr = it },
                        label = { Text("Quantity") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Discount Selector
                Text("Item Discount:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = discountType == com.example.data.model.ItemDiscountType.NONE,
                        onClick = {
                            discountType = com.example.data.model.ItemDiscountType.NONE
                            discountValStr = ""
                        },
                        label = { Text("None", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = discountType == com.example.data.model.ItemDiscountType.PERCENTAGE,
                        onClick = { discountType = com.example.data.model.ItemDiscountType.PERCENTAGE },
                        label = { Text("%", fontSize = 11.sp) },
                        modifier = Modifier.weight(0.8f)
                    )
                    FilterChip(
                        selected = discountType == com.example.data.model.ItemDiscountType.FLAT,
                        onClick = { discountType = com.example.data.model.ItemDiscountType.FLAT },
                        label = { Text("Flat ($currency)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1.2f)
                    )
                }

                if (discountType != com.example.data.model.ItemDiscountType.NONE) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = discountValStr,
                        onValueChange = { discountValStr = it },
                        label = { Text(if (discountType == com.example.data.model.ItemDiscountType.PERCENTAGE) "Discount %" else "Discount Amount ($currency)") },
                        placeholder = { Text(if (discountType == com.example.data.model.ItemDiscountType.PERCENTAGE) "e.g. 10" else "e.g. 2.00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (discAmt > 0.0 && parsedPrice > 0.0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Net Price: $currency${String.format("%.2f", (parsedPrice - discAmt).coerceAtLeast(0.0))}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceStr.toDoubleOrNull() ?: 0.0
                    val qty = qtyStr.toIntOrNull() ?: 1
                    val tax = if (enableTax) (taxStr.toDoubleOrNull() ?: defaultTaxRate) else 0.0
                    val discVal = discountValStr.toDoubleOrNull() ?: 0.0
                    if (price > 0) {
                        onAdd(name.ifEmpty { "Custom Item" }, price, qty, tax, discountType, if (discountType != com.example.data.model.ItemDiscountType.NONE) discVal else 0.0)
                    }
                },
                enabled = (priceStr.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text("Add to Cart")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
