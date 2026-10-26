package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.CartItem
import com.example.data.model.ReceiptStatus
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

object PdfInvoiceGenerator {
    private const val TAG = "PdfInvoiceGenerator"
    private const val PAGE_WIDTH = 595 // A4 standard width in points
    private const val PAGE_HEIGHT = 842 // A4 standard height in points
    private const val MARGIN = 36f // 0.5 inch margins

    /**
     * Generates a high-quality vector PDF Invoice file for a receipt.
     */
    fun generatePdfFile(
        context: Context,
        receipt: ReceiptEntity,
        config: StoreConfigEntity,
        items: List<CartItem>
    ): File {
        val invoicesDir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val cleanInvoiceNo = receipt.invoiceNumber.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val outputFile = File(invoicesDir, "Invoice_$cleanInvoiceNo.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawInvoiceCanvas(context, canvas, receipt, config, items)

        document.finishPage(page)

        try {
            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing PDF to file", e)
        } finally {
            document.close()
        }

        return outputFile
    }

    private fun drawInvoiceCanvas(
        context: Context,
        canvas: Canvas,
        receipt: ReceiptEntity,
        config: StoreConfigEntity,
        items: List<CartItem>
    ) {
        val currency = config.currencySymbol
        val dateStr = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(receipt.createdAt))

        // Paints
        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F172A") // Slate 900
            isAntiAlias = true
        }
        val subHeaderPaint = Paint().apply {
            color = Color.parseColor("#475569") // Slate 600
            textSize = 9.5f
            isAntiAlias = true
        }
        val titlePaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val invoiceTitlePaint = Paint().apply {
            color = Color.parseColor("#2563EB") // Blue 600
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 10f
            isAntiAlias = true
        }
        val boldTextPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0") // Slate 200
            strokeWidth = 1f
            isAntiAlias = true
        }
        val tableHeaderBgPaint = Paint().apply {
            color = Color.parseColor("#F1F5F9") // Slate 100
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val zebraBgPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val totalCardBgPaint = Paint().apply {
            color = Color.parseColor("#EFF6FF") // Blue 50
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val totalCardBorderPaint = Paint().apply {
            color = Color.parseColor("#BFDBFE") // Blue 200
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }

        // Top decorative accent line
        val accentPaint = Paint().apply {
            color = Color.parseColor("#2563EB")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 6f, accentPaint)

        // 0. DIAGONAL WATERMARK (Generated invoice number centered diagonally across the invoice)
        canvas.save()
        canvas.translate(PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
        canvas.rotate(-35f)
        val watermarkPaint = Paint().apply {
            color = Color.parseColor("#334155")
            alpha = 32 // ~13% opacity for watermark elegance & background readability
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            letterSpacing = 0.05f
        }
        canvas.drawText(receipt.invoiceNumber, 0f, 0f, watermarkPaint)
        canvas.restore()

        var y = MARGIN + 10f

        // 1. HEADER: Business Info (with optional Color Logo) & INVOICE Heading (Right)
        val logoBitmap = com.example.util.LogoImageUtils.loadLogoBitmap(context, config.logoUri, maxDimension = 300)
        val hasLogo = logoBitmap != null
        val logoSize = 56f
        val textStartX = if (hasLogo) MARGIN + logoSize + 14f else MARGIN

        if (hasLogo && logoBitmap != null) {
            val logoRect = RectF(MARGIN, y, MARGIN + logoSize, y + logoSize)
            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(logoBitmap, null, logoRect, logoPaint)
        }

        canvas.drawText(config.businessName.ifEmpty { "RETAIL STORE" }.uppercase(), textStartX, y + 14f, titlePaint)
        val invoiceHeading = if (config.enableTax) "TAX INVOICE" else "INVOICE"
        canvas.drawText(invoiceHeading, PAGE_WIDTH - MARGIN, y + 14f, invoiceTitlePaint)

        y += 24f
        if (config.tagline.isNotEmpty()) {
            canvas.drawText(config.tagline, textStartX, y + 8f, subHeaderPaint)
            y += 12f
        }
        if (config.address.isNotEmpty()) {
            canvas.drawText(config.address, textStartX, y + 8f, subHeaderPaint)
            y += 12f
        }
        val contactLine = listOfNotNull(
            config.phone.takeIf { it.isNotEmpty() }?.let { "Tel: $it" },
            config.email.takeIf { it.isNotEmpty() }?.let { "Email: $it" },
            (if (config.enableTax) config.taxId else "").takeIf { it.isNotEmpty() }?.let { "Tax ID: $it" }
        ).joinToString("  •  ")

        if (contactLine.isNotEmpty()) {
            canvas.drawText(contactLine, textStartX, y + 8f, subHeaderPaint)
            y += 14f
        }

        if (config.enableDenominationLogo) {
            val currCode = config.currencyCode.ifBlank { "CURRENCY" }
            val denomText = "DENOMINATION / BASE CURRENCY: $currCode ($currency)"
            val denomPaint = Paint(boldTextPaint).apply { textSize = 8.5f; color = Color.parseColor("#2563EB") }
            canvas.drawText(denomText, textStartX, y + 8f, denomPaint)
            y += 14f
        }

        // Ensure y is past the logo box
        if (hasLogo && y < MARGIN + logoSize + 18f) {
            y = MARGIN + logoSize + 18f
        }

        y += 10f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 16f

        // 2. INVOICE META GRID (2 Columns)
        val col1X = MARGIN
        val col2X = PAGE_WIDTH / 2f + 20f

        // Left box: Customer / Bill To
        canvas.drawText("BILL TO:", col1X, y, boldTextPaint)
        val custName = receipt.customerName.ifEmpty { "Walk-in Customer" }
        canvas.drawText(custName, col1X, y + 14f, boldTextPaint)
        if (receipt.customerPhone.isNotEmpty()) {
            canvas.drawText("Phone: ${receipt.customerPhone}", col1X, y + 26f, textPaint)
        }

        // Right box: Invoice Details
        val metaRightAlignPaint = Paint(textPaint).apply { textAlign = Paint.Align.RIGHT }
        val metaRightBoldPaint = Paint(boldTextPaint).apply { textAlign = Paint.Align.RIGHT }

        val metaLeft = PAGE_WIDTH - MARGIN - 160f
        canvas.drawText("Invoice Number:", metaLeft, y, textPaint)
        canvas.drawText(receipt.invoiceNumber, PAGE_WIDTH - MARGIN, y, metaRightBoldPaint)

        canvas.drawText("Date & Time:", metaLeft, y + 14f, textPaint)
        canvas.drawText(dateStr, PAGE_WIDTH - MARGIN, y + 14f, metaRightAlignPaint)

        canvas.drawText("Cashier:", metaLeft, y + 28f, textPaint)
        canvas.drawText(receipt.cashierName, PAGE_WIDTH - MARGIN, y + 28f, metaRightAlignPaint)

        canvas.drawText("Payment Method:", metaLeft, y + 42f, textPaint)
        canvas.drawText(receipt.paymentMethod.label, PAGE_WIDTH - MARGIN, y + 42f, metaRightBoldPaint)

        // Status badge
        val statusBgPaint = Paint().apply {
            color = when (receipt.status) {
                ReceiptStatus.PAID -> Color.parseColor("#DCFCE7") // Light green
                ReceiptStatus.REFUNDED -> Color.parseColor("#FEE2E2") // Light red
                ReceiptStatus.VOIDED -> Color.parseColor("#F1F5F9") // Light gray
            }
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val statusTextPaint = Paint().apply {
            color = when (receipt.status) {
                ReceiptStatus.PAID -> Color.parseColor("#166534") // Dark green
                ReceiptStatus.REFUNDED -> Color.parseColor("#991B1B") // Dark red
                ReceiptStatus.VOIDED -> Color.parseColor("#475569") // Dark gray
            }
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val badgeRect = RectF(PAGE_WIDTH - MARGIN - 80f, y + 50f, PAGE_WIDTH - MARGIN, y + 66f)
        canvas.drawRoundRect(badgeRect, 8f, 8f, statusBgPaint)
        canvas.drawText(receipt.status.label.uppercase(), badgeRect.centerX(), badgeRect.centerY() + 3f, statusTextPaint)

        y += 76f

        // 3. ITEMIZED TABLE
        val tableLeft = MARGIN
        val tableRight = PAGE_WIDTH - MARGIN
        val tableWidth = tableRight - tableLeft

        val colDescX = tableLeft + 8f
        val colQtyX = tableLeft + tableWidth * 0.55f
        val colUnitX = tableLeft + tableWidth * 0.72f
        val colTotalX = tableRight - 8f

        // Table Header
        val headerRect = RectF(tableLeft, y, tableRight, y + 22f)
        canvas.drawRoundRect(headerRect, 4f, 4f, tableHeaderBgPaint)

        val thPaint = Paint(boldTextPaint).apply { textSize = 9f; color = Color.parseColor("#475569") }
        val thRightPaint = Paint(thPaint).apply { textAlign = Paint.Align.RIGHT }
        val thCenterPaint = Paint(thPaint).apply { textAlign = Paint.Align.CENTER }

        canvas.drawText("ITEM DESCRIPTION", colDescX, y + 14f, thPaint)
        canvas.drawText("QTY", colQtyX, y + 14f, thCenterPaint)
        canvas.drawText("UNIT PRICE", colUnitX + 20f, y + 14f, thRightPaint)
        canvas.drawText("AMOUNT", colTotalX, y + 14f, thRightPaint)

        y += 24f

        // Table Rows
        val rowTextPaint = Paint(textPaint).apply { textSize = 9.5f }
        val rowRightPaint = Paint(rowTextPaint).apply { textAlign = Paint.Align.RIGHT }
        val rowCenterPaint = Paint(rowTextPaint).apply { textAlign = Paint.Align.CENTER }

        val itemDiscPaint = Paint(textPaint).apply {
            textSize = 8f
            color = Color.parseColor("#16A34A") // Emerald Green
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        for ((index, item) in items.withIndex()) {
            val hasItemDiscount = item.itemDiscountAmount > 0.0 && item.discountType != com.example.data.model.ItemDiscountType.NONE
            val rowHeight = if (hasItemDiscount) 30f else 22f

            if (index % 2 == 1) {
                canvas.drawRect(tableLeft, y, tableRight, y + rowHeight, zebraBgPaint)
            }

            // Description
            val itemName = if (item.name.length > 38) item.name.take(35) + "..." else item.name
            canvas.drawText(itemName, colDescX, y + 14f, rowTextPaint)

            // Conditional Item Discount Subtitle: Only appear if discount details added for the item
            if (hasItemDiscount) {
                val discLabel = when (item.discountType) {
                    com.example.data.model.ItemDiscountType.PERCENTAGE -> "Discount: -${item.discountValue.toInt()}% (-$currency${String.format("%.2f", item.itemDiscountAmount)})"
                    com.example.data.model.ItemDiscountType.FLAT -> "Discount (Flat): -$currency${String.format("%.2f", item.itemDiscountAmount)}"
                    com.example.data.model.ItemDiscountType.NONE -> ""
                }
                canvas.drawText(discLabel, colDescX, y + 25f, itemDiscPaint)
            }

            // Qty
            canvas.drawText("${item.quantity}", colQtyX, y + 14f, rowCenterPaint)

            // Unit Price
            canvas.drawText(String.format("%s%.2f", currency, item.unitPrice), colUnitX + 20f, y + 14f, rowRightPaint)

            // Line Total
            canvas.drawText(String.format("%s%.2f", currency, item.total), colTotalX, y + 14f, rowRightPaint)

            canvas.drawLine(tableLeft, y + rowHeight, tableRight, y + rowHeight, linePaint)
            y += rowHeight
        }

        y += 12f

        // 4. FINANCIAL SUMMARY & TOTAL CARD (Right aligned)
        val summaryLeft = PAGE_WIDTH - MARGIN - 230f
        val summaryRight = PAGE_WIDTH - MARGIN
        val labelX = summaryLeft + 10f
        val valueX = summaryRight - 10f

        val sumLabelPaint = Paint(textPaint).apply { textSize = 9.5f }
        val sumValPaint = Paint(textPaint).apply { textSize = 9.5f; textAlign = Paint.Align.RIGHT }
        val grandTotalLabelPaint = Paint(boldTextPaint).apply { textSize = 13f; color = Color.parseColor("#1E3A8A") }
        val grandTotalValPaint = Paint(boldTextPaint).apply { textSize = 14f; color = Color.parseColor("#1E3A8A"); textAlign = Paint.Align.RIGHT }

        // Subtotal (Net Taxable Base or Standard Subtotal)
        val subtotalTitle = if (config.enableTax && receipt.taxAmount > 0) "Net Taxable Amount:" else "Subtotal:"
        canvas.drawText(subtotalTitle, labelX, y + 10f, sumLabelPaint)
        canvas.drawText(String.format("%s%.2f", currency, receipt.subtotal), valueX, y + 10f, sumValPaint)
        y += 16f

        // Conditional Total Discount Section: Only appear if discount details added
        val totalDiscounts = receipt.discountAmount + items.sumOf { it.itemDiscountAmount }
        if (totalDiscounts > 0.0) {
            val discLabel = if (receipt.discountPercent > 0.0) {
                "Total Discount (${receipt.discountPercent.toInt()}%):"
            } else {
                "Total Discount:"
            }
            canvas.drawText(discLabel, labelX, y + 10f, sumLabelPaint)
            val discPaint = Paint(sumValPaint).apply { color = Color.parseColor("#16A34A") }
            canvas.drawText(String.format("-%s%.2f", currency, totalDiscounts), valueX, y + 10f, discPaint)
            y += 16f
        }

        // Tax
        if (config.enableTax && receipt.taxAmount > 0) {
            val taxLabel = if (config.taxName.isNotEmpty()) config.taxName else "Tax / VAT"
            canvas.drawText("$taxLabel (Included):", labelX, y + 10f, sumLabelPaint)
            canvas.drawText(String.format("%s%.2f", currency, receipt.taxAmount), valueX, y + 10f, sumValPaint)
            y += 16f
        }

        // Tip
        if (receipt.tipAmount > 0) {
            canvas.drawText("Tip / Gratuity:", labelX, y + 10f, sumLabelPaint)
            canvas.drawText(String.format("%s%.2f", currency, receipt.tipAmount), valueX, y + 10f, sumValPaint)
            y += 16f
        }

        // Credit Note / Trade Note Adjustment
        if (receipt.creditNoteAppliedAmount > 0.0 && receipt.appliedCreditNoteNumber.isNotEmpty()) {
            val cnPaint = Paint(sumLabelPaint).apply {
                color = Color.parseColor("#0F766E")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val cnValPaint = Paint(sumValPaint).apply {
                color = Color.parseColor("#0F766E")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("Credit Note Adj (${receipt.appliedCreditNoteNumber}):", labelX, y + 10f, cnPaint)
            canvas.drawText(String.format("-%s%.2f", currency, receipt.creditNoteAppliedAmount), valueX, y + 10f, cnValPaint)
            y += 13f
            if (receipt.appliedCreditOriginalInvoice.isNotEmpty()) {
                val origPaint = Paint(subHeaderPaint).apply { textSize = 8f; color = Color.parseColor("#0F766E") }
                canvas.drawText("Adjusted from Bill #${receipt.appliedCreditOriginalInvoice}", labelX, y + 8f, origPaint)
                y += 14f
            } else {
                y += 4f
            }
        }

        y += 6f

        // Grand Total Box
        val totalCardRect = RectF(summaryLeft, y, summaryRight, y + 36f)
        canvas.drawRoundRect(totalCardRect, 6f, 6f, totalCardBgPaint)
        canvas.drawRoundRect(totalCardRect, 6f, 6f, totalCardBorderPaint)

        canvas.drawText("TOTAL DUE:", labelX + 4f, y + 23f, grandTotalLabelPaint)
        canvas.drawText(String.format("%s%.2f", currency, receipt.totalAmount), valueX - 4f, y + 23f, grandTotalValPaint)

        y += 44f

        // Tendered
        if (receipt.amountTendered > 0) {
            canvas.drawText("Amount Tendered: ${String.format("%s%.2f", currency, receipt.amountTendered)}", labelX, y + 10f, subHeaderPaint)
            y += 16f
        }

        // 5. SECURITY HASH & QR VERIFICATION / UPI (Left side of footer)
        val footerY = PAGE_HEIGHT - MARGIN - 80f

        // Draw UPI Payment QR or Verification QR if enabled
        if (config.enableUpiPayment && config.upiId.isNotBlank()) {
            val upiUri = BarcodeQrUtils.buildUpiPaymentUri(
                upiId = config.upiId,
                payeeName = config.upiPayeeName.ifBlank { config.businessName },
                amount = receipt.totalAmount,
                invoiceNumber = receipt.invoiceNumber
            )
            val upiQrBitmap = BarcodeQrUtils.generateQrCodeBitmap(upiUri, size = 90)
            if (upiQrBitmap != null) {
                canvas.drawBitmap(upiQrBitmap, MARGIN, footerY - 20f, null)
                val upiTextPaint = Paint(boldTextPaint).apply { textSize = 7.5f; color = Color.parseColor("#2563EB") }
                val upiSubPaint = Paint(textPaint).apply { textSize = 6.5f; color = Color.parseColor("#475569") }
                canvas.drawText("SCAN TO PAY (UPI)", MARGIN + 100f, footerY - 8f, upiTextPaint)
                canvas.drawText("UPI ID: ${config.upiId}", MARGIN + 100f, footerY + 4f, upiSubPaint)
                canvas.drawText("Apps: GPay, PhonePe, Paytm, BHIM", MARGIN + 100f, footerY + 16f, upiSubPaint)
            }
        }

        // Draw Barcode on the right side if showBarcode is true
        if (config.showBarcode) {
            val barcodeData = receipt.invoiceNumber.replace("-", "").take(16).ifEmpty { "POS1001" }
            val barcodeBitmap = BarcodeQrUtils.generateBarcodeBitmap(barcodeData, width = 160, height = 40)
            if (barcodeBitmap != null) {
                val barcodeX = PAGE_WIDTH - MARGIN - 160f
                canvas.drawBitmap(barcodeBitmap, barcodeX, footerY - 10f, null)
                val bcTextPaint = Paint(textPaint).apply { textSize = 7f; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }
                canvas.drawText(barcodeData, barcodeX + 80f, footerY + 38f, bcTextPaint)
            }
        }

        // Security stamp removed per user request

        // 6. BOTTOM FOOTER & POLICY
        val footerTextPaint = Paint(subHeaderPaint).apply {
            textAlign = Paint.Align.CENTER
            textSize = 8.5f
        }
        val footerMsg = config.receiptFooter.ifEmpty { "Thank you for your business! Please retain this invoice for your records." }
        canvas.drawText(footerMsg, PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN - 6f, footerTextPaint)

        // Decorative bottom border
        canvas.drawRect(0f, PAGE_HEIGHT - 4f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), accentPaint)
    }

    /**
     * Generates a standalone vector PDF file for a Credit Note ("Trade Note").
     */
    fun generateCreditNotePdfFile(
        context: Context,
        creditNote: com.example.data.local.entity.CreditNoteEntity,
        config: StoreConfigEntity,
        items: List<CartItem>
    ): File {
        val invoicesDir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val cleanNo = creditNote.creditNoteNumber.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val outputFile = File(invoicesDir, "CreditNote_$cleanNo.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val currency = config.currencySymbol
        val dateStr = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(creditNote.createdAt))

        val titlePaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val cnHeadingPaint = Paint().apply {
            color = Color.parseColor("#0F766E")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val subHeaderPaint = Paint().apply {
            color = Color.parseColor("#475569")
            textSize = 9.5f
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 10f
            isAntiAlias = true
        }
        val boldTextPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
            isAntiAlias = true
        }
        val accentPaint = Paint().apply {
            color = Color.parseColor("#0F766E")
            style = Paint.Style.FILL
        }

        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 6f, accentPaint)

        // Watermark
        canvas.save()
        canvas.translate(PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
        canvas.rotate(-35f)
        val watermarkPaint = Paint().apply {
            color = Color.parseColor("#0F766E")
            alpha = 26
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(creditNote.creditNoteNumber, 0f, 0f, watermarkPaint)
        canvas.restore()

        var y = MARGIN + 16f
        canvas.drawText(config.businessName.ifEmpty { "RETAIL STORE" }.uppercase(), MARGIN, y, titlePaint)
        canvas.drawText("CREDIT / TRADE NOTE", PAGE_WIDTH - MARGIN, y, cnHeadingPaint)

        y += 18f
        if (config.address.isNotEmpty()) {
            canvas.drawText(config.address, MARGIN, y, subHeaderPaint)
            y += 12f
        }
        if (config.phone.isNotEmpty()) {
            canvas.drawText("Tel: ${config.phone}", MARGIN, y, subHeaderPaint)
            y += 12f
        }

        y += 10f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 18f

        canvas.drawText("ISSUED TO CUSTOMER:", MARGIN, y, boldTextPaint)
        canvas.drawText(creditNote.customerName.ifEmpty { "Customer" }, MARGIN, y + 14f, boldTextPaint)
        canvas.drawText("Mobile: ${creditNote.customerPhone}", MARGIN, y + 28f, textPaint)

        val metaRightBold = Paint(boldTextPaint).apply { textAlign = Paint.Align.RIGHT }
        val metaRight = Paint(textPaint).apply { textAlign = Paint.Align.RIGHT }
        val metaLeft = PAGE_WIDTH - MARGIN - 180f

        canvas.drawText("Credit Note #:", metaLeft, y, textPaint)
        canvas.drawText(creditNote.creditNoteNumber, PAGE_WIDTH - MARGIN, y, metaRightBold)

        canvas.drawText("Original Invoice #:", metaLeft, y + 14f, textPaint)
        canvas.drawText(creditNote.originalInvoiceNumber, PAGE_WIDTH - MARGIN, y + 14f, metaRightBold)

        canvas.drawText("Issued Date:", metaLeft, y + 28f, textPaint)
        canvas.drawText(dateStr, PAGE_WIDTH - MARGIN, y + 28f, metaRight)

        canvas.drawText("Status:", metaLeft, y + 42f, textPaint)
        canvas.drawText(creditNote.status.label.uppercase(), PAGE_WIDTH - MARGIN, y + 42f, metaRightBold)

        y += 62f

        val tableLeft = MARGIN
        val tableRight = PAGE_WIDTH - MARGIN
        val tableWidth = tableRight - tableLeft
        val colDescX = tableLeft + 8f
        val colQtyX = tableLeft + tableWidth * 0.55f
        val colUnitX = tableLeft + tableWidth * 0.72f
        val colTotalX = tableRight - 8f

        val headerBg = Paint().apply { color = Color.parseColor("#F0FDFA"); style = Paint.Style.FILL }
        canvas.drawRoundRect(RectF(tableLeft, y, tableRight, y + 22f), 4f, 4f, headerBg)

        val thPaint = Paint(boldTextPaint).apply { textSize = 9f; color = Color.parseColor("#0F766E") }
        val thRight = Paint(thPaint).apply { textAlign = Paint.Align.RIGHT }
        val thCenter = Paint(thPaint).apply { textAlign = Paint.Align.CENTER }

        canvas.drawText("RETURNED ITEM DESCRIPTION", colDescX, y + 14f, thPaint)
        canvas.drawText("RETURN QTY", colQtyX, y + 14f, thCenter)
        canvas.drawText("UNIT PRICE", colUnitX + 20f, y + 14f, thRight)
        canvas.drawText("CREDIT AMOUNT", colTotalX, y + 14f, thRight)

        y += 24f

        val rowRight = Paint(textPaint).apply { textAlign = Paint.Align.RIGHT }
        val rowCenter = Paint(textPaint).apply { textAlign = Paint.Align.CENTER }

        for (item in items) {
            val rowH = 22f
            canvas.drawText(item.name.take(40), colDescX, y + 14f, textPaint)
            canvas.drawText("${item.quantity}", colQtyX, y + 14f, rowCenter)
            canvas.drawText(String.format("%s%.2f", currency, item.unitPrice), colUnitX + 20f, y + 14f, rowRight)
            canvas.drawText(String.format("%s%.2f", currency, item.total), colTotalX, y + 14f, rowRight)
            canvas.drawLine(tableLeft, y + rowH, tableRight, y + rowH, linePaint)
            y += rowH
        }

        y += 18f

        val summaryLeft = PAGE_WIDTH - MARGIN - 240f
        val summaryRight = PAGE_WIDTH - MARGIN
        val cardBg = Paint().apply { color = Color.parseColor("#F0FDFA"); style = Paint.Style.FILL }
        val cardBorder = Paint().apply { color = Color.parseColor("#99F6E4"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
        val cardRect = RectF(summaryLeft, y, summaryRight, y + 52f)
        canvas.drawRoundRect(cardRect, 6f, 6f, cardBg)
        canvas.drawRoundRect(cardRect, 6f, 6f, cardBorder)

        canvas.drawText("Total Credit Value:", summaryLeft + 10f, y + 18f, boldTextPaint)
        canvas.drawText(String.format("%s%.2f", currency, creditNote.totalCreditAmount), summaryRight - 10f, y + 18f, metaRightBold)

        val balPaint = Paint(boldTextPaint).apply { textSize = 13f; color = Color.parseColor("#0F766E") }
        val balRightPaint = Paint(balPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("AVAILABLE CREDIT:", summaryLeft + 10f, y + 40f, balPaint)
        canvas.drawText(String.format("%s%.2f", currency, creditNote.remainingBalance), summaryRight - 10f, y + 40f, balRightPaint)

        y += 70f
        if (creditNote.notes.isNotBlank()) {
            canvas.drawText("Return Reason / Notes: ${creditNote.notes}", MARGIN, y, subHeaderPaint)
        }

        val footerTextPaint = Paint(subHeaderPaint).apply { textAlign = Paint.Align.CENTER; textSize = 9f }
        canvas.drawText(
            "Present this Credit Note (#${creditNote.creditNoteNumber}) or mobile number (${creditNote.customerPhone}) on your next purchase to adjust your bill.",
            PAGE_WIDTH / 2f,
            PAGE_HEIGHT - MARGIN - 12f,
            footerTextPaint
        )
        canvas.drawRect(0f, PAGE_HEIGHT - 4f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), accentPaint)

        document.finishPage(page)
        try {
            FileOutputStream(outputFile).use { out -> document.writeTo(out) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing Credit Note PDF", e)
        } finally {
            document.close()
        }
        return outputFile
    }

    /**
     * Shares generated PDF invoice via Android Share Sheet (Email, WhatsApp, Drive, etc.)
     */
    fun sharePdf(context: Context, file: File, invoiceNumber: String) {
        try {
            if (!file.exists() || file.length() == 0L) {
                Toast.makeText(context, "Error: PDF file generation failed.", Toast.LENGTH_LONG).show()
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Invoice $invoiceNumber")
                putExtra(Intent.EXTRA_TEXT, "Attached is your Tax Invoice #$invoiceNumber.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Invoice PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing PDF invoice", e)
            Toast.makeText(context, "Could not share PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shares any file (e.g., zip archive of all invoices) via Android Share Sheet
     */
    fun shareFile(context: Context, file: File, title: String) {
        try {
            if (!file.exists() || file.length() == 0L) return
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = if (file.name.endsWith(".zip", ignoreCase = true)) "application/zip" else "application/pdf"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Exported invoice document archive attached.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing file", e)
        }
    }

    /**
     * Sends the PDF to Android System Print Manager for direct A4 or thermal printing
     */
    fun printPdf(context: Context, file: File, jobName: String = "Print Invoice") {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, "Print service not available on this device", Toast.LENGTH_SHORT).show()
                return
            }

            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }

                    val info = PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()

                    callback?.onLayoutFinished(info, newAttributes != oldAttributes)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    var input: FileInputStream? = null
                    var output: FileOutputStream? = null

                    try {
                        input = FileInputStream(file)
                        output = FileOutputStream(destination?.fileDescriptor)

                        val buffer = ByteArray(1024)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } >= 0) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback?.onWriteCancelled()
                                return
                            }
                            output.write(buffer, 0, bytesRead)
                        }

                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing print document", e)
                        callback?.onWriteFailed(e.message)
                    } finally {
                        try {
                            input?.close()
                            output?.close()
                        } catch (ignored: IOException) {}
                    }
                }
            }

            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()

            printManager.print(jobName, printAdapter, printAttributes)
        } catch (e: Exception) {
            Log.e(TAG, "Error printing PDF document", e)
            Toast.makeText(context, "Print failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
