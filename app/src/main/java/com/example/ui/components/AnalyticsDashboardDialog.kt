package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.CartItem
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

/**
 * Data structure holding item-level financial & profit/loss metrics
 */
data class ItemProfitLossInsight(
    val productId: Long,
    val name: String,
    val category: String,
    val sku: String,
    val unitRetailPrice: Double, // Inclusive of tax
    val unitNetTaxablePrice: Double, // Exclusive of tax
    val unitCostPrice: Double, // COGS per unit
    val taxRatePercent: Double,
    val unitsSold: Int,
    val currentStock: Int,
    val totalGrossRevenue: Double,
    val totalNetRevenue: Double,
    val totalCogs: Double,
    val totalNetProfit: Double,
    val totalLoss: Double,
    val profitMarginPercent: Double, // (Net Profit / Net Revenue) * 100
    val markupPercent: Double, // (Net Profit / COGS) * 100
    val isLossMaking: Boolean,
    val isHighMargin: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDashboardDialog(
    catalogProducts: List<ProductEntity>,
    receipts: List<ReceiptEntity>,
    storeConfig: StoreConfigEntity,
    parseItemsJson: (String) -> List<CartItem>,
    onDismiss: () -> Unit
) {
    val currency = storeConfig.currencySymbol
    var selectedTab by remember { mutableStateOf(0) } // 0: Overview & P&L, 1: Item-Wise Insights, 2: Leaderboard & Alerts, 3: Category Matrix
    var itemSearchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var itemSortOption by remember { mutableStateOf("HIGHEST_PROFIT") } // HIGHEST_PROFIT, HIGHEST_MARGIN, MOST_SOLD, LOWEST_MARGIN, HIGHEST_REVENUE

    // -------------------------------------------------------------
    // 1. Core Financial & Profit / Loss Calculation Engine
    // -------------------------------------------------------------
    val productCostMap = remember(catalogProducts) {
        catalogProducts.associate { it.name.lowercase().trim() to it.costPrice }
    }
    val productIdCostMap = remember(catalogProducts) {
        catalogProducts.associate { it.id to it.costPrice }
    }
    val productEntityMap = remember(catalogProducts) {
        catalogProducts.associateBy { it.id }
    }

    // Aggregate sold items per product
    data class AggregatedSale(
        val name: String,
        val productId: Long,
        val category: String,
        val sku: String,
        var unitsSold: Int = 0,
        var grossRevenue: Double = 0.0,
        var netRevenue: Double = 0.0,
        var totalCogs: Double = 0.0,
        var totalTaxCollected: Double = 0.0,
        var totalDiscountsGiven: Double = 0.0,
        var unitPriceSample: Double = 0.0,
        var taxRateSample: Double = 0.0,
        var costPriceSample: Double = 0.0
    )

    val itemSalesAggregates = remember(receipts, catalogProducts) {
        val map = mutableMapOf<String, AggregatedSale>()

        receipts.forEach { receipt ->
            val items = parseItemsJson(receipt.itemsJson)
            val discountFactor = 1.0 - (receipt.discountPercent / 100.0).coerceIn(0.0, 1.0)

            items.forEach { item ->
                val key = if (item.productId != 0L) "ID_${item.productId}" else "NAME_${item.name.lowercase().trim()}"
                val agg = map.getOrPut(key) {
                    val fallbackCost = if (item.costPrice > 0) item.costPrice
                    else if (item.productId != 0L) productIdCostMap[item.productId] ?: 0.0
                    else productCostMap[item.name.lowercase().trim()] ?: 0.0

                    AggregatedSale(
                        name = item.name,
                        productId = item.productId,
                        category = item.category.ifBlank { "General" },
                        sku = item.sku,
                        unitPriceSample = item.unitPrice,
                        taxRateSample = item.taxRatePercent,
                        costPriceSample = fallbackCost
                    )
                }

                val itemGrossBeforeDiscount = item.unitPrice * item.quantity
                val itemGrossDiscounted = itemGrossBeforeDiscount * discountFactor
                val itemDiscountAmount = itemGrossBeforeDiscount - itemGrossDiscounted

                val itemNetBase = if (item.taxRatePercent > 0) {
                    itemGrossDiscounted / (1.0 + (item.taxRatePercent / 100.0))
                } else {
                    itemGrossDiscounted
                }
                val itemTax = itemGrossDiscounted - itemNetBase

                val unitEffectiveCost = if (item.costPrice > 0) item.costPrice
                else if (item.productId != 0L) productIdCostMap[item.productId] ?: 0.0
                else productCostMap[item.name.lowercase().trim()] ?: 0.0

                val itemCogs = unitEffectiveCost * item.quantity

                agg.unitsSold += item.quantity
                agg.grossRevenue += itemGrossDiscounted
                agg.netRevenue += itemNetBase
                agg.totalCogs += itemCogs
                agg.totalTaxCollected += itemTax
                agg.totalDiscountsGiven += itemDiscountAmount
                if (item.unitPrice > 0) agg.unitPriceSample = item.unitPrice
                if (unitEffectiveCost > 0) agg.costPriceSample = unitEffectiveCost
            }
        }
        map
    }

    // Build complete insights list for ALL catalog items + non-catalog sold items
    val itemInsightsList = remember(itemSalesAggregates, catalogProducts) {
        val processedKeys = mutableSetOf<String>()
        val list = mutableListOf<ItemProfitLossInsight>()

        // 1. Process sold items
        itemSalesAggregates.forEach { (key, sale) ->
            processedKeys.add(key)
            val matchingProduct = productEntityMap[sale.productId]
                ?: catalogProducts.find { it.name.equals(sale.name, ignoreCase = true) }

            val retailPrice = if (matchingProduct != null) matchingProduct.price else sale.unitPriceSample
            val costPrice = if (matchingProduct != null && matchingProduct.costPrice > 0) matchingProduct.costPrice else sale.costPriceSample
            val taxRate = if (matchingProduct != null) matchingProduct.taxRatePercent else sale.taxRateSample
            val netUnitPrice = if (taxRate > 0) retailPrice / (1.0 + (taxRate / 100.0)) else retailPrice
            val currentStock = matchingProduct?.stockQuantity ?: 0

            val rawProfit = sale.netRevenue - sale.totalCogs
            val profitAmount = if (rawProfit > 0) rawProfit else 0.0
            val lossAmount = if (rawProfit < 0) -rawProfit else 0.0

            val marginPercent = if (sale.netRevenue > 0) {
                (rawProfit / sale.netRevenue) * 100.0
            } else 0.0

            val markupPercent = if (sale.totalCogs > 0) {
                (rawProfit / sale.totalCogs) * 100.0
            } else 0.0

            list.add(
                ItemProfitLossInsight(
                    productId = sale.productId,
                    name = sale.name,
                    category = matchingProduct?.category ?: sale.category,
                    sku = matchingProduct?.sku ?: sale.sku,
                    unitRetailPrice = retailPrice,
                    unitNetTaxablePrice = netUnitPrice,
                    unitCostPrice = costPrice,
                    taxRatePercent = taxRate,
                    unitsSold = sale.unitsSold,
                    currentStock = currentStock,
                    totalGrossRevenue = sale.grossRevenue,
                    totalNetRevenue = sale.netRevenue,
                    totalCogs = sale.totalCogs,
                    totalNetProfit = profitAmount,
                    totalLoss = lossAmount,
                    profitMarginPercent = marginPercent,
                    markupPercent = markupPercent,
                    isLossMaking = rawProfit < 0,
                    isHighMargin = marginPercent >= 50.0
                )
            )
        }

        // 2. Include catalog items with 0 sales yet
        catalogProducts.forEach { product ->
            val keyId = "ID_${product.id}"
            val keyName = "NAME_${product.name.lowercase().trim()}"
            if (!processedKeys.contains(keyId) && !processedKeys.contains(keyName)) {
                val netUnitPrice = if (product.taxRatePercent > 0) product.price / (1.0 + (product.taxRatePercent / 100.0)) else product.price
                val unitProfitPotential = netUnitPrice - product.costPrice
                val potentialMargin = if (netUnitPrice > 0) (unitProfitPotential / netUnitPrice) * 100.0 else 0.0
                val potentialMarkup = if (product.costPrice > 0) (unitProfitPotential / product.costPrice) * 100.0 else 0.0

                list.add(
                    ItemProfitLossInsight(
                        productId = product.id,
                        name = product.name,
                        category = product.category.ifBlank { "General" },
                        sku = product.sku,
                        unitRetailPrice = product.price,
                        unitNetTaxablePrice = netUnitPrice,
                        unitCostPrice = product.costPrice,
                        taxRatePercent = product.taxRatePercent,
                        unitsSold = 0,
                        currentStock = product.stockQuantity,
                        totalGrossRevenue = 0.0,
                        totalNetRevenue = 0.0,
                        totalCogs = 0.0,
                        totalNetProfit = 0.0,
                        totalLoss = 0.0,
                        profitMarginPercent = potentialMargin,
                        markupPercent = potentialMarkup,
                        isLossMaking = unitProfitPotential < 0,
                        isHighMargin = potentialMargin >= 50.0
                    )
                )
            }
        }
        list
    }

    // High-level Aggregated Metrics
    val totalGrossRevenue = receipts.sumOf { it.totalAmount }
    val totalDiscounts = receipts.sumOf { it.discountAmount }
    val totalTips = receipts.sumOf { it.tipAmount }
    val totalTaxCollected = receipts.sumOf { it.taxAmount }
    val totalOrders = receipts.size

    val totalNetRevenue = itemInsightsList.sumOf { it.totalNetRevenue }
    val totalCogs = itemInsightsList.sumOf { it.totalCogs }
    val totalProfitAmount = itemInsightsList.sumOf { it.totalNetProfit }
    val totalLossAmount = itemInsightsList.sumOf { it.totalLoss }
    val overallNetProfit = totalProfitAmount - totalLossAmount

    val overallProfitMarginPercent = if (totalNetRevenue > 0) {
        (overallNetProfit / totalNetRevenue) * 100.0
    } else 0.0

    val overallMarkupPercent = if (totalCogs > 0) {
        (overallNetProfit / totalCogs) * 100.0
    } else 0.0

    val averageProfitPerOrder = if (totalOrders > 0) overallNetProfit / totalOrders else 0.0
    val averageTicket = if (totalOrders > 0) totalGrossRevenue / totalOrders else 0.0

    // Inventory Valuation & Unrealized Potential Profit
    val totalRetailAssetValue = catalogProducts.sumOf { it.price * it.stockQuantity }
    val totalStockCostValue = catalogProducts.sumOf { it.costPrice * it.stockQuantity }
    val totalPotentialInventoryProfit = (totalRetailAssetValue - totalStockCostValue).coerceAtLeast(0.0)
    val potentialStockMarginPercent = if (totalRetailAssetValue > 0) (totalPotentialInventoryProfit / totalRetailAssetValue) * 100.0 else 0.0
    val totalStockUnits = catalogProducts.sumOf { it.stockQuantity }

    // Categories
    val allCategories = remember(itemInsightsList) {
        listOf("ALL") + itemInsightsList.map { it.category }.distinct().sorted()
    }

    // Filtered & Sorted Item Insights
    val filteredItemInsights = remember(itemInsightsList, itemSearchQuery, selectedCategoryFilter, itemSortOption) {
        itemInsightsList.filter { item ->
            val matchQuery = itemSearchQuery.isEmpty() ||
                    item.name.contains(itemSearchQuery, ignoreCase = true) ||
                    item.sku.contains(itemSearchQuery, ignoreCase = true) ||
                    item.category.contains(itemSearchQuery, ignoreCase = true)
            val matchCat = selectedCategoryFilter == "ALL" || item.category.equals(selectedCategoryFilter, ignoreCase = true)
            matchQuery && matchCat
        }.sortedWith { a, b ->
            when (itemSortOption) {
                "HIGHEST_PROFIT" -> b.totalNetProfit.compareTo(a.totalNetProfit)
                "HIGHEST_MARGIN" -> b.profitMarginPercent.compareTo(a.profitMarginPercent)
                "MOST_SOLD" -> b.unitsSold.compareTo(a.unitsSold)
                "LOWEST_MARGIN" -> a.profitMarginPercent.compareTo(b.profitMarginPercent)
                "HIGHEST_REVENUE" -> b.totalGrossRevenue.compareTo(a.totalGrossRevenue)
                else -> b.totalNetProfit.compareTo(a.totalNetProfit)
            }
        }
    }

    // Top 5 Profitable Items
    val topProfitableItems = remember(itemInsightsList) {
        itemInsightsList.filter { it.unitsSold > 0 && it.totalNetProfit > 0 }
            .sortedByDescending { it.totalNetProfit }
            .take(5)
    }

    // Low Margin or Loss Alert Items
    val lowMarginOrLossItems = remember(itemInsightsList) {
        itemInsightsList.filter { it.isLossMaking || (it.profitMarginPercent < 15.0 && it.unitsSold > 0) }
    }

    // Category P&L Breakdown
    val categoryBreakdown = remember(itemInsightsList) {
        itemInsightsList.groupBy { it.category }
            .map { (cat, items) ->
                val catGrossRev = items.sumOf { it.totalGrossRevenue }
                val catNetRev = items.sumOf { it.totalNetRevenue }
                val catCogs = items.sumOf { it.totalCogs }
                val catProfit = items.sumOf { it.totalNetProfit } - items.sumOf { it.totalLoss }
                val catSold = items.sumOf { it.unitsSold }
                val catStockUnits = items.sumOf { it.currentStock }
                val catMargin = if (catNetRev > 0) (catProfit / catNetRev) * 100.0 else 0.0
                Triple(cat, items.size, mapOf(
                    "grossRev" to catGrossRev,
                    "netRev" to catNetRev,
                    "cogs" to catCogs,
                    "profit" to catProfit,
                    "sold" to catSold.toDouble(),
                    "stock" to catStockUnits.toDouble(),
                    "margin" to catMargin
                ))
            }.sortedByDescending { it.third["profit"] ?: 0.0 }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Analytics,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Analytical Profit & Loss Dashboard",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        "${storeConfig.businessName} • Real-time Profit, Loss & Item Insights",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Segmented Tabs
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        divider = { Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("📊 Overview & P&L", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📦 Item-Wise Insights", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            "${itemInsightsList.size}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🏆 Leaderboard & Alerts", fontWeight = FontWeight.Bold)
                                    if (lowMarginOrLossItems.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = StatusError.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                "${lowMarginOrLossItems.size} ⚠️",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = StatusError,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("🗂️ Category P&L", fontWeight = FontWeight.Bold) }
                        )
                    }

                    // Content based on Selected Tab
                    when (selectedTab) {
                        0 -> AnalyticsOverviewTab(
                            currency = currency,
                            totalProfitAmount = totalProfitAmount,
                            totalLossAmount = totalLossAmount,
                            overallNetProfit = overallNetProfit,
                            overallProfitMarginPercent = overallProfitMarginPercent,
                            overallMarkupPercent = overallMarkupPercent,
                            totalGrossRevenue = totalGrossRevenue,
                            totalNetRevenue = totalNetRevenue,
                            totalCogs = totalCogs,
                            totalTaxCollected = totalTaxCollected,
                            totalDiscounts = totalDiscounts,
                            totalTips = totalTips,
                            totalOrders = totalOrders,
                            averageProfitPerOrder = averageProfitPerOrder,
                            averageTicket = averageTicket,
                            totalRetailAssetValue = totalRetailAssetValue,
                            totalStockCostValue = totalStockCostValue,
                            totalPotentialInventoryProfit = totalPotentialInventoryProfit,
                            potentialStockMarginPercent = potentialStockMarginPercent,
                            totalStockUnits = totalStockUnits,
                            topProfitableItems = topProfitableItems,
                            enableTax = storeConfig.enableTax,
                            taxName = storeConfig.taxName,
                            onNavigateToItemInsights = { selectedTab = 1 }
                        )
                        1 -> AnalyticsItemWiseInsightsTab(
                            currency = currency,
                            searchQuery = itemSearchQuery,
                            onSearchQueryChange = { itemSearchQuery = it },
                            selectedCategory = selectedCategoryFilter,
                            onCategorySelect = { selectedCategoryFilter = it },
                            allCategories = allCategories,
                            sortOption = itemSortOption,
                            onSortOptionSelect = { itemSortOption = it },
                            items = filteredItemInsights,
                            enableTax = storeConfig.enableTax
                        )
                        2 -> AnalyticsLeaderboardAndAlertsTab(
                            currency = currency,
                            topProfitableItems = topProfitableItems,
                            lowMarginOrLossItems = lowMarginOrLossItems,
                            totalNetProfit = totalProfitAmount,
                            onNavigateToItemInsights = { selectedTab = 1 }
                        )
                        3 -> AnalyticsCategoryMatrixTab(
                            currency = currency,
                            categories = categoryBreakdown,
                            totalGrossRevenue = totalGrossRevenue
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 0: OVERVIEW & P&L SUMMARY
// -------------------------------------------------------------------------------------------------
@Composable
private fun AnalyticsOverviewTab(
    currency: String,
    totalProfitAmount: Double,
    totalLossAmount: Double,
    overallNetProfit: Double,
    overallProfitMarginPercent: Double,
    overallMarkupPercent: Double,
    totalGrossRevenue: Double,
    totalNetRevenue: Double,
    totalCogs: Double,
    totalTaxCollected: Double,
    totalDiscounts: Double,
    totalTips: Double,
    totalOrders: Int,
    averageProfitPerOrder: Double,
    averageTicket: Double,
    totalRetailAssetValue: Double,
    totalStockCostValue: Double,
    totalPotentialInventoryProfit: Double,
    potentialStockMarginPercent: Double,
    totalStockUnits: Int,
    topProfitableItems: List<ItemProfitLossInsight>,
    enableTax: Boolean = true,
    taxName: String = "Tax / VAT",
    onNavigateToItemInsights: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Hero Profit & Loss Summary Cards
        item {
            Text("Executive Profit & Loss Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // Primary P&L Row: Net Profit & Net Loss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Total Profit Amount
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldTertiary.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(EmeraldTertiary.copy(alpha = 0.4f)))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL PROFIT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldTertiary)
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = EmeraldTertiary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$currency${String.format("%.2f", totalProfitAmount)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = EmeraldTertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = EmeraldTertiary
                            ) {
                                Text(
                                    "+${String.format("%.1f", overallProfitMarginPercent)}% Margin",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Markup: +${String.format("%.1f", overallMarkupPercent)}%",
                                fontSize = 10.sp,
                                color = EmeraldTertiary
                            )
                        }
                    }
                }

                // Card 2: Total Loss Amount
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (totalLossAmount > 0) StatusError.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(if (totalLossAmount > 0) StatusError.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL LOSS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (totalLossAmount > 0) StatusError else MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(
                                if (totalLossAmount > 0) Icons.Default.TrendingDown else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (totalLossAmount > 0) StatusError else StatusSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (totalLossAmount > 0) "-$currency${String.format("%.2f", totalLossAmount)}" else "$currency 0.00",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = if (totalLossAmount > 0) StatusError else StatusSuccess
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (totalLossAmount > 0) "⚠️ Items sold below cost" else "✅ Zero loss recorded",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (totalLossAmount > 0) StatusError else StatusSuccess
                        )
                    }
                }
            }
        }

        // Section 2: Revenue, COGS, Tax & Margin KPI Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Total Gross Sales",
                    value = "$currency${String.format("%.2f", totalGrossRevenue)}",
                    subtitle = "$totalOrders completed sales",
                    icon = Icons.Default.AttachMoney,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Cost of Goods (COGS)",
                    value = "$currency${String.format("%.2f", totalCogs)}",
                    subtitle = "Inventory procurement",
                    icon = Icons.Default.ShoppingCart,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Profit / Order",
                    value = "$currency${String.format("%.2f", averageProfitPerOrder)}",
                    subtitle = "Avg Ticket: $currency${String.format("%.2f", averageTicket)}",
                    icon = Icons.Default.Receipt,
                    color = EmeraldTertiary,
                    modifier = Modifier.weight(1f)
                )
                if (enableTax) {
                    MetricCard(
                        title = "Included ${taxName.ifBlank { "Tax / VAT" }}",
                        value = "$currency${String.format("%.2f", totalTaxCollected)}",
                        subtitle = "Discounts: -$currency${String.format("%.2f", totalDiscounts)}",
                        icon = Icons.Default.AccountBalance,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    MetricCard(
                        title = "Total Discounts Given",
                        value = "-$currency${String.format("%.2f", totalDiscounts)}",
                        subtitle = if (totalTips > 0) "Tips: +$currency${String.format("%.2f", totalTips)}" else "$totalOrders sales completed",
                        icon = Icons.Default.Discount,
                        color = StatusWarning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section 3: Revenue Allocation & Flow Visual Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Revenue Distribution Breakdown", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    val safeGross = if (totalGrossRevenue > 0) totalGrossRevenue else 1.0
                    val profitRatio = ((overallNetProfit.coerceAtLeast(0.0) / safeGross)).toFloat()
                    val cogsRatio = ((totalCogs / safeGross)).toFloat()
                    val taxRatio = if (enableTax) ((totalTaxCollected / safeGross)).toFloat() else 0f
                    val discountRatio = ((totalDiscounts / safeGross)).toFloat()

                    // Multi-color segmented progress bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                    ) {
                        if (profitRatio > 0f) {
                            Box(modifier = Modifier.weight(profitRatio.coerceAtLeast(0.01f)).fillMaxHeight().background(EmeraldTertiary))
                        }
                        if (cogsRatio > 0f) {
                            Box(modifier = Modifier.weight(cogsRatio.coerceAtLeast(0.01f)).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
                        }
                        if (enableTax && taxRatio > 0f) {
                            Box(modifier = Modifier.weight(taxRatio.coerceAtLeast(0.01f)).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
                        }
                        if (discountRatio > 0f) {
                            Box(modifier = Modifier.weight(discountRatio.coerceAtLeast(0.01f)).fillMaxHeight().background(StatusWarning))
                        }
                        if (totalGrossRevenue <= 0) {
                            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Legend Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendItem("Net Profit (${String.format("%.1f", profitRatio * 100)}%)", EmeraldTertiary)
                        LegendItem("COGS (${String.format("%.1f", cogsRatio * 100)}%)", MaterialTheme.colorScheme.secondary)
                        if (enableTax) {
                            LegendItem("Tax (${String.format("%.1f", taxRatio * 100)}%)", MaterialTheme.colorScheme.tertiary)
                        }
                        LegendItem("Discounts (${String.format("%.1f", discountRatio * 100)}%)", StatusWarning)
                    }
                }
            }
        }

        // Section 4: Current Warehouse Potential Profit (Stock Valuation)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Current Stock Potential Profit", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "$totalStockUnits Units",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Stock Retail Value", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$currency${String.format("%.2f", totalRetailAssetValue)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column {
                            Text("Total Stock Purchase Cost", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$currency${String.format("%.2f", totalStockCostValue)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Unrealized Profit", fontSize = 11.sp, color = EmeraldTertiary, fontWeight = FontWeight.Bold)
                            Text(
                                "+$currency${String.format("%.2f", totalPotentialInventoryProfit)} (${String.format("%.1f", potentialStockMarginPercent)}%)",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = EmeraldTertiary
                            )
                        }
                    }
                }
            }
        }

        // Section 5: Top 3 Quick Preview & Action Button
        if (topProfitableItems.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔥 Top Profit Generating Items", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            TextButton(onClick = onNavigateToItemInsights) {
                                Text("View All Items →", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        topProfitableItems.take(3).forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        when (idx) {
                                            0 -> "🥇"
                                            1 -> "🥈"
                                            else -> "🥉"
                                        },
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(item.name, fontWeight = FontWeight.Medium, fontSize = 12.5.sp)
                                        Text("${item.unitsSold} sold • Margin: ${String.format("%.1f", item.profitMarginPercent)}%", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(
                                    "+$currency${String.format("%.2f", item.totalNetProfit)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = EmeraldTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 1: ITEM-WISE PROFIT & LOSS INSIGHTS
// -------------------------------------------------------------------------------------------------
@Composable
private fun AnalyticsItemWiseInsightsTab(
    currency: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    allCategories: List<String>,
    sortOption: String,
    onSortOptionSelect: (String) -> Unit,
    items: List<ItemProfitLossInsight>,
    enableTax: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Search & Filter Header
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by item name, SKU, or category...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(allCategories) { category ->
                FilterChip(
                    selected = selectedCategory.equals(category, ignoreCase = true),
                    onClick = { onCategorySelect(category) },
                    label = { Text(category, fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sorting Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sort:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    SortChip("💰 Highest Profit", "HIGHEST_PROFIT", sortOption, onSortOptionSelect)
                }
                item {
                    SortChip("📈 Highest Margin %", "HIGHEST_MARGIN", sortOption, onSortOptionSelect)
                }
                item {
                    SortChip("🔥 Most Sold", "MOST_SOLD", sortOption, onSortOptionSelect)
                }
                item {
                    SortChip("⚠️ Lowest Margin / Loss", "LOWEST_MARGIN", sortOption, onSortOptionSelect)
                }
                item {
                    SortChip("💵 Highest Revenue", "HIGHEST_REVENUE", sortOption, onSortOptionSelect)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Item List
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No products match the selected criteria.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.productId.toString() + "_" + it.name }) { itemInsight ->
                    ItemProfitLossCard(currency = currency, item = itemInsight, enableTax = enableTax)
                }
            }
        }
    }
}

@Composable
private fun ItemProfitLossCard(
    currency: String,
    item: ItemProfitLossInsight,
    enableTax: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isLossMaking) StatusError.copy(alpha = 0.08f)
            else if (item.isHighMargin && item.unitsSold > 0) EmeraldTertiary.copy(alpha = 0.06f)
            else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (item.isLossMaking) StatusError.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Item Name, Category, Profitability Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                item.category,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        if (item.sku.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SKU: ${item.sku}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Profit / Loss Status Pill
                if (item.isLossMaking) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = StatusError
                    ) {
                        Text(
                            "🚨 LOSS MAKING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                } else if (item.unitsSold == 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            "UNSOLD (Potential: ${String.format("%.1f", item.profitMarginPercent)}%)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (item.isHighMargin) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldTertiary
                    ) {
                        Text(
                            "⭐ HIGH MARGIN (${String.format("%.1f", item.profitMarginPercent)}%)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = StatusSuccess.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "PROFITABLE (${String.format("%.1f", item.profitMarginPercent)}%)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusSuccess,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            // Pricing & Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Column 1: Pricing Specs
                Column {
                    Text("Unit Pricing", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val retailLabel = if (enableTax) "Retail (Incl. Tax):" else "Retail Price:"
                    Text("$retailLabel $currency${String.format("%.2f", item.unitRetailPrice)}", fontSize = 11.sp)
                    Text("Cost (COGS): $currency${String.format("%.2f", item.unitCostPrice)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    if (enableTax) {
                        Text("Tax Rate: ${String.format("%.1f", item.taxRatePercent)}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Column 2: Sales & Volume
                Column {
                    Text("Volume & Stock", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Units Sold: ${item.unitsSold}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("In Stock: ${item.currentStock}", fontSize = 11.sp, color = if (item.currentStock <= 0) StatusError else MaterialTheme.colorScheme.onSurface)
                    Text("Revenue: $currency${String.format("%.2f", item.totalGrossRevenue)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Column 3: Net Profit / Loss Outcome
                Column(horizontalAlignment = Alignment.End) {
                    Text("Profit / Loss", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.isLossMaking) {
                        Text(
                            "-$currency${String.format("%.2f", item.totalLoss)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = StatusError
                        )
                        Text("Margin: ${String.format("%.1f", item.profitMarginPercent)}%", fontSize = 10.sp, color = StatusError, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            "+$currency${String.format("%.2f", item.totalNetProfit)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = EmeraldTertiary
                        )
                        Text(
                            "Margin: ${String.format("%.1f", item.profitMarginPercent)}%",
                            fontSize = 10.sp,
                            color = EmeraldTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text("COGS: $currency${String.format("%.2f", item.totalCogs)}", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Margin Progress Indicator
            if (item.unitsSold > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (item.profitMarginPercent.toFloat() / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp)),
                    color = if (item.isLossMaking) StatusError else if (item.isHighMargin) EmeraldTertiary else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 2: LEADERBOARD & STRATEGIC ALERTS
// -------------------------------------------------------------------------------------------------
@Composable
private fun AnalyticsLeaderboardAndAlertsTab(
    currency: String,
    topProfitableItems: List<ItemProfitLossInsight>,
    lowMarginOrLossItems: List<ItemProfitLossInsight>,
    totalNetProfit: Double,
    onNavigateToItemInsights: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section: Top 5 Profitable Champions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏆", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Top 5 Profit-Generating Items", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (topProfitableItems.isEmpty()) {
                        Text("No sales receipts recorded yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        topProfitableItems.forEachIndexed { index, item ->
                            val profitShare = if (totalNetProfit > 0) (item.totalNetProfit / totalNetProfit) * 100.0 else 0.0
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            when (index) {
                                                0 -> "🥇"
                                                1 -> "🥈"
                                                2 -> "🥉"
                                                else -> "#${index + 1}"
                                            },
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text("${item.unitsSold} units sold • ${String.format("%.1f", item.profitMarginPercent)}% margin", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("+$currency${String.format("%.2f", item.totalNetProfit)}", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = EmeraldTertiary)
                                        Text("${String.format("%.1f", profitShare)}% of profit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (profitShare.toFloat() / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(2.5.dp)),
                                    color = EmeraldTertiary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Low Margin & Loss Warning Alerts
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (lowMarginOrLossItems.isNotEmpty()) StatusError.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(if (lowMarginOrLossItems.isNotEmpty()) StatusError.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (lowMarginOrLossItems.isNotEmpty()) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (lowMarginOrLossItems.isNotEmpty()) StatusError else StatusSuccess
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (lowMarginOrLossItems.isNotEmpty()) "Margin & Pricing Alerts (${lowMarginOrLossItems.size} Items)" else "No Margin Warnings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (lowMarginOrLossItems.isNotEmpty()) StatusError else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (lowMarginOrLossItems.isEmpty()) {
                        Text(
                            "All products maintain healthy profit margins (>15%). Great pricing strategy!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "The following items have negative or razor-thin margins (<15%). Consider adjusting selling prices or negotiating cost with suppliers:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        lowMarginOrLossItems.take(5).forEach { alertItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(alertItem.name, fontWeight = FontWeight.Medium, fontSize = 12.5.sp)
                                    Text(
                                        "Retail: $currency${String.format("%.2f", alertItem.unitRetailPrice)} vs Cost: $currency${String.format("%.2f", alertItem.unitCostPrice)}",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (alertItem.isLossMaking) StatusError else StatusWarning
                                ) {
                                    Text(
                                        if (alertItem.isLossMaking) "LOSS (${String.format("%.1f", alertItem.profitMarginPercent)}%)" else "MARGIN: ${String.format("%.1f", alertItem.profitMarginPercent)}%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

// -------------------------------------------------------------------------------------------------
// TAB 3: CATEGORY PROFITABILITY MATRIX
// -------------------------------------------------------------------------------------------------
@Composable
private fun AnalyticsCategoryMatrixTab(
    currency: String,
    categories: List<Triple<String, Int, Map<String, Double>>>,
    totalGrossRevenue: Double
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Category Profitability & Margin Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Breakdown of turnover, procurement costs, and net earnings per product category.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (categories.isEmpty()) {
            item {
                Text("No categories recorded yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(categories) { (categoryName, itemCount, stats) ->
                val grossRev = stats["grossRev"] ?: 0.0
                val cogs = stats["cogs"] ?: 0.0
                val profit = stats["profit"] ?: 0.0
                val margin = stats["margin"] ?: 0.0
                val unitsSold = (stats["sold"] ?: 0.0).toInt()
                val stockUnits = (stats["stock"] ?: 0.0).toInt()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(categoryName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("$itemCount items • $unitsSold sold • $stockUnits in stock", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (profit >= 0) "+" else ""}$currency${String.format("%.2f", profit)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (profit >= 0) EmeraldTertiary else StatusError
                                )
                                Text(
                                    text = "Margin: ${String.format("%.1f", margin)}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (profit >= 0) EmeraldTertiary else StatusError
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Revenue: $currency${String.format("%.2f", grossRev)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("COGS: $currency${String.format("%.2f", cogs)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val share = if (totalGrossRevenue > 0) (grossRev / totalGrossRevenue).toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { share.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.5.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// UI Helper Components
// -------------------------------------------------------------------------------------------------
@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SortChip(
    label: String,
    optionKey: String,
    selectedOption: String,
    onSelect: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selectedOption == optionKey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .clickable { onSelect(optionKey) }
            .border(
                1.dp,
                if (selectedOption == optionKey) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selectedOption == optionKey) FontWeight.Bold else FontWeight.Normal,
            color = if (selectedOption == optionKey) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
