package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.CreditNoteEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.CreditNoteStatus
import com.example.data.model.ReceiptStatus
import com.example.data.model.SyncStatus
import com.example.ui.POSUiState
import com.example.ui.POSViewModel
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReceiptsScreen(
    viewModel: POSViewModel,
    uiState: POSUiState,
    receipts: List<ReceiptEntity>,
    totalSales: Double?,
    unsyncedCount: Int,
    storeConfig: StoreConfigEntity,
    modifier: Modifier = Modifier
) {
    val currency = storeConfig.currencySymbol
    val creditNotes by viewModel.creditNotes.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredReceipts = remember(receipts, searchQuery, selectedFilter) {
        receipts.filter { rec ->
            val matchSearch = searchQuery.isEmpty() ||
                    rec.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                    rec.customerName.contains(searchQuery, ignoreCase = true) ||
                    rec.customerPhone.contains(searchQuery, ignoreCase = true) ||
                    rec.cashierName.contains(searchQuery, ignoreCase = true) ||
                    rec.appliedCreditNoteNumber.contains(searchQuery, ignoreCase = true)

            val matchFilter = when (selectedFilter) {
                "PAID" -> rec.status == ReceiptStatus.PAID
                "REFUNDED" -> rec.status == ReceiptStatus.REFUNDED
                "VOIDED" -> rec.status == ReceiptStatus.VOIDED
                else -> true
            }
            matchSearch && matchFilter
        }
    }

    val filteredCreditNotes = remember(creditNotes, searchQuery) {
        creditNotes.filter { cn ->
            searchQuery.isEmpty() ||
                    cn.creditNoteNumber.contains(searchQuery, ignoreCase = true) ||
                    cn.originalInvoiceNumber.contains(searchQuery, ignoreCase = true) ||
                    cn.customerPhone.contains(searchQuery, ignoreCase = true) ||
                    cn.customerName.contains(searchQuery, ignoreCase = true)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        val isWide = maxWidth >= 540.dp

        LazyVerticalGrid(
            columns = if (isWide && selectedFilter != "CREDIT NOTES") GridCells.Adaptive(minSize = 280.dp) else GridCells.Fixed(1),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Summary KPI Cards Row
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total Gross Sales
                    KpiCard(
                        title = "Total Sales",
                        value = String.format("%s%.2f", currency, totalSales ?: 0.0),
                        icon = Icons.Default.MonetizationOn,
                        iconColor = EmeraldTertiary,
                        modifier = Modifier.weight(1f)
                    )

                    // Total Invoices
                    KpiCard(
                        title = "Total Invoices",
                        value = "${receipts.size}",
                        icon = Icons.Default.ReceiptLong,
                        iconColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )

                    // Active Credit Notes
                    val activeCnCount = creditNotes.count { it.remainingBalance > 0.0 }
                    KpiCard(
                        title = "Credit Notes",
                        value = "$activeCnCount Active",
                        icon = Icons.Default.AssignmentReturn,
                        iconColor = StatusWarning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Prominent Item Return / Credit Note by Customer Phone Banner
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    onClick = { viewModel.openItemReturnDialog(null) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_item_return_by_phone_btn")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AssignmentReturn,
                                    contentDescription = "Item Return",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Partial Item Return & Trade / Credit Note",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Lookup bill by Customer Mobile # • Return selected item(s) • Issue Credit Note",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.openItemReturnDialog(null) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Item Return", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Action Banners Row: PDF Invoice Export & Excel Spreadsheet Export
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Download All Invoices PDF Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("download_all_invoices_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "PDF Invoices",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${filteredReceipts.size} file(s)",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { viewModel.downloadAllPdfInvoices(context, filteredReceipts) },
                                enabled = filteredReceipts.isNotEmpty() && !viewModel.isBatchPdfDownloading,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                if (viewModel.isBatchPdfDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Export All Invoices to Excel Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = EmeraldTertiary.copy(alpha = 0.12f),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("download_all_excel_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = EmeraldTertiary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Excel Export",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = EmeraldTertiary
                                    )
                                    Text(
                                        text = "${filteredReceipts.size} record(s)",
                                        fontSize = 10.sp,
                                        color = EmeraldTertiary.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.exportAllInvoicesToExcel(context, filteredReceipts) },
                                enabled = filteredReceipts.isNotEmpty() && !viewModel.isBatchExcelExporting,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldTertiary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                if (viewModel.isBatchExcelExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Excel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            if (selectedFilter == "CREDIT NOTES")
                                "Search Credit Note #, Mobile #, or Bill #..."
                            else
                                "Search by invoice #, customer phone or name..."
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receipts_search_bar")
                )
            }

            // Filter Chips
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterOptions = listOf("ALL", "PAID", "CREDIT NOTES", "REFUNDED", "VOIDED")
                    items(filterOptions) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = {
                                if (filter == "CREDIT NOTES") {
                                    Text("CREDIT NOTES (${creditNotes.size})")
                                } else {
                                    Text(filter)
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            if (selectedFilter == "CREDIT NOTES") {
                if (filteredCreditNotes.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.AssignmentReturn,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No Credit Notes issued yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredCreditNotes, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { cn ->
                        CreditNoteCard(
                            creditNote = cn,
                            currency = currency,
                            onClick = { viewModel.selectCreditNote(cn) },
                            onQuickPrint = { viewModel.printCreditNote(cn.id) },
                            onQuickPdf = { viewModel.shareCreditNotePdf(context, cn) }
                        )
                    }
                }
            } else {
                // Receipts List
                if (filteredReceipts.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.Receipt,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No receipts match your search",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredReceipts, key = { it.id }) { receipt ->
                        ReceiptCard(
                            receipt = receipt,
                            currency = currency,
                            onClick = { viewModel.selectReceipt(receipt) },
                            onQuickPrint = { viewModel.printCurrentReceipt(receipt.id) },
                            onQuickPdf = { viewModel.sharePdfInvoice(context, receipt) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreditNoteCard(
    creditNote: CreditNoteEntity,
    currency: String,
    onClick: () -> Unit,
    onQuickPrint: () -> Unit,
    onQuickPdf: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(creditNote.createdAt))

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("credit_note_card_${creditNote.id}")
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
                        text = creditNote.creditNoteNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (creditNote.status) {
                            CreditNoteStatus.ACTIVE -> StatusSuccess.copy(alpha = 0.15f)
                            CreditNoteStatus.PARTIALLY_USED -> StatusWarning.copy(alpha = 0.15f)
                            CreditNoteStatus.REDEEMED -> Color.Gray.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = creditNote.status.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (creditNote.status) {
                                CreditNoteStatus.ACTIVE -> StatusSuccess
                                CreditNoteStatus.PARTIALLY_USED -> StatusWarning
                                CreditNoteStatus.REDEEMED -> Color.Gray
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "${creditNote.customerName.ifBlank { "Customer" }} (${creditNote.customerPhone}) • From Bill #${creditNote.originalInvoiceNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (creditNote.notes.isNotBlank()) {
                    Text(
                        text = creditNote.notes,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("%s%.2f", currency, creditNote.remainingBalance),
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = if (creditNote.remainingBalance > 0.0) EmeraldTertiary else Color.Gray
                )
                Text(
                    text = "Total: ${String.format("%s%.2f", currency, creditNote.totalCreditAmount)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onQuickPdf,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = "Credit Note PDF",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onQuickPrint,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = "Print Credit Note",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ReceiptCard(
    receipt: ReceiptEntity,
    currency: String,
    onClick: () -> Unit,
    onQuickPrint: () -> Unit,
    onQuickPdf: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(receipt.createdAt))

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("receipt_card_${receipt.id}")
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
                        text = receipt.invoiceNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status Pill
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (receipt.status) {
                            ReceiptStatus.PAID -> StatusSuccess.copy(alpha = 0.15f)
                            ReceiptStatus.REFUNDED -> StatusError.copy(alpha = 0.15f)
                            ReceiptStatus.VOIDED -> Color.Gray.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = receipt.status.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (receipt.status) {
                                ReceiptStatus.PAID -> StatusSuccess
                                ReceiptStatus.REFUNDED -> StatusError
                                ReceiptStatus.VOIDED -> Color.Gray
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (receipt.creditNoteAppliedAmount > 0.0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = EmeraldTertiary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "CN Adj: -${String.format("%s%.2f", currency, receipt.creditNoteAppliedAmount)}",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldTertiary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                val phoneSuffix = if (receipt.customerPhone.isNotBlank()) " (${receipt.customerPhone})" else ""
                Text(
                    text = "${receipt.customerName}$phoneSuffix • ${receipt.paymentMethod.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (receipt.creditNoteAppliedAmount > 0.0 && receipt.appliedCreditOriginalInvoice.isNotBlank()) {
                    Text(
                        text = "Trade Note ${receipt.appliedCreditNoteNumber} adjusted from Bill #${receipt.appliedCreditOriginalInvoice}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldTertiary
                    )
                }

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("%s%.2f", currency, receipt.totalAmount),
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = if (receipt.status == ReceiptStatus.PAID) MaterialTheme.colorScheme.primary else StatusError
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick PDF Invoice Icon
                    IconButton(
                        onClick = onQuickPdf,
                        modifier = Modifier.size(32.dp).testTag("quick_pdf_btn_${receipt.id}")
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = "PDF Invoice",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Quick Thermal Print Icon
                    IconButton(
                        onClick = onQuickPrint,
                        modifier = Modifier.size(32.dp).testTag("quick_print_btn_${receipt.id}")
                    ) {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = "Quick Print",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
