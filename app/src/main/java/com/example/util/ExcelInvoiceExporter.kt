package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.CartItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

object ExcelInvoiceExporter {

    private const val TAG = "ExcelInvoiceExporter"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val itemsType = Types.newParameterizedType(List::class.java, CartItem::class.java)
    private val itemsAdapter = moshi.adapter<List<CartItem>>(itemsType)

    /**
     * Exports a list of receipts to an Excel-compatible CSV file with UTF-8 BOM encoding.
     * The file opens seamlessly in Microsoft Excel, Google Sheets, Apple Numbers, and LibreOffice.
     */
    fun exportInvoicesToExcel(
        context: Context,
        receipts: List<ReceiptEntity>,
        config: StoreConfigEntity
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Invoices_Export_$timestamp.csv"

        // 1. Output file in app storage for safe FileProvider access
        val cacheFolder = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(cacheFolder, fileName)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        FileOutputStream(exportFile).use { fos ->
            // Write UTF-8 BOM so Microsoft Excel correctly recognises Unicode characters & currency symbols
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                // Summary Meta Header
                writer.append(escapeCsv("Business Name: ${config.businessName}")).append("\n")
                writer.append(escapeCsv("Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")).append("\n")
                writer.append(escapeCsv("Total Invoices: ${receipts.size}")).append("\n")
                writer.append(escapeCsv("Currency: ${config.currencySymbol} (${config.currencyCode})")).append("\n\n")

                // Column Headers
                val headers = mutableListOf(
                    "Invoice No",
                    "Date",
                    "Time",
                    "Customer Name",
                    "Customer Phone",
                    "Cashier",
                    "Status",
                    "Payment Method",
                    "Payment Details",
                    "Total Qty",
                    "Items Detail",
                    if (config.enableTax) "Subtotal (Net)" else "Subtotal"
                )
                if (config.enableTax) {
                    headers.add("Tax Name")
                    headers.add("Tax Rate (%)")
                    headers.add("Tax Amount")
                }
                headers.addAll(
                    listOf(
                        "Discount (%)",
                        "Discount Amount",
                        "Tip Amount",
                        "Total Amount (Gross)",
                        "Amount Tendered",
                        "Change Due",
                        "Cloud Sync Status",
                        "Notes",
                        "Security Hash"
                    )
                )
                writer.append(headers.joinToString(",") { escapeCsv(it) }).append("\n")

                // Data Rows
                for (receipt in receipts) {
                    val dateObj = Date(receipt.createdAt)
                    val dateStr = dateFormat.format(dateObj)
                    val timeStr = timeFormat.format(dateObj)

                    val items: List<CartItem> = try {
                        itemsAdapter.fromJson(receipt.itemsJson) ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val totalQty = items.sumOf { it.quantity }
                    val itemsDetail = items.joinToString(" | ") { item ->
                        "${item.name} (x${item.quantity} @ ${config.currencySymbol}${String.format(Locale.US, "%.2f", item.unitPrice)})"
                    }

                    val row = mutableListOf(
                        receipt.invoiceNumber,
                        dateStr,
                        timeStr,
                        receipt.customerName,
                        receipt.customerPhone,
                        receipt.cashierName,
                        receipt.status.label,
                        receipt.paymentMethod.label,
                        receipt.paymentDetails,
                        totalQty.toString(),
                        itemsDetail,
                        String.format(Locale.US, "%.2f", receipt.subtotal)
                    )
                    if (config.enableTax) {
                        row.add(config.taxName)
                        row.add(String.format(Locale.US, "%.2f", config.defaultTaxPercent))
                        row.add(String.format(Locale.US, "%.2f", receipt.taxAmount))
                    }
                    row.addAll(
                        listOf(
                            String.format(Locale.US, "%.2f", receipt.discountPercent),
                            String.format(Locale.US, "%.2f", receipt.discountAmount),
                            String.format(Locale.US, "%.2f", receipt.tipAmount),
                            String.format(Locale.US, "%.2f", receipt.totalAmount),
                            String.format(Locale.US, "%.2f", receipt.amountTendered),
                            String.format(Locale.US, "%.2f", receipt.changeDue),
                            receipt.syncStatus.label,
                            receipt.notes,
                            receipt.securityHash
                        )
                    )
                    writer.append(row.joinToString(",") { escapeCsv(it) }).append("\n")
                }

                // Summary Row
                writer.append("\n")
                val totalSales = receipts.filter { it.status.name == "PAID" }.sumOf { it.totalAmount }
                val totalTax = receipts.filter { it.status.name == "PAID" }.sumOf { it.taxAmount }
                val totalDiscounts = receipts.sumOf { it.discountAmount }
                val totalTips = receipts.sumOf { it.tipAmount }

                val summaryRow = mutableListOf(
                    "TOTALS (PAID)",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    ""
                )
                if (config.enableTax) {
                    summaryRow.add("")
                    summaryRow.add("")
                    summaryRow.add(String.format(Locale.US, "%.2f", totalTax))
                }
                summaryRow.addAll(
                    listOf(
                        "",
                        String.format(Locale.US, "%.2f", totalDiscounts),
                        String.format(Locale.US, "%.2f", totalTips),
                        String.format(Locale.US, "%.2f", totalSales),
                        "",
                        "",
                        "",
                        "",
                        ""
                    )
                )
                writer.append(summaryRow.joinToString(",") { escapeCsv(it) }).append("\n")
                writer.flush()
            }
        }

        // 2. Also copy to public Downloads/Invoices folder for easy file manager access
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val invoicesFolder = File(downloadsDir, "Invoices").apply { mkdirs() }
            val publicFile = File(invoicesFolder, fileName)
            exportFile.copyTo(publicFile, overwrite = true)
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy to public Downloads: ${e.message}")
        }

        return exportFile
    }

    /**
     * Opens the Android system share sheet with the generated Excel/CSV file.
     */
    fun shareExcelFile(context: Context, file: File, title: String = "Export Invoices to Excel") {
        try {
            if (!file.exists() || file.length() == 0L) {
                Toast.makeText(context, "Export file not found", Toast.LENGTH_SHORT).show()
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Exported invoice records Excel/CSV spreadsheet attached.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing Excel file", e)
            Toast.makeText(context, "Could not share Excel file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun escapeCsv(value: String): String {
        val containsSpecial = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        return if (containsSpecial) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
