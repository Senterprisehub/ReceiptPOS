package com.example.printer

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

object EscPosCommands {
    val ESC: Byte = 0x1B
    val GS: Byte = 0x1D
    val LF: Byte = 0x0A

    // Printer initialization
    val INIT_PRINTER = byteArrayOf(ESC, 0x40)

    // Text Alignment
    val ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(ESC, 0x61, 0x01)
    val ALIGN_RIGHT = byteArrayOf(ESC, 0x61, 0x02)

    // Bold
    val BOLD_ON = byteArrayOf(ESC, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(ESC, 0x45, 0x00)

    // Underline
    val UNDERLINE_OFF = byteArrayOf(ESC, 0x2D, 0x00)
    val UNDERLINE_1DOT = byteArrayOf(ESC, 0x2D, 0x01)
    val UNDERLINE_2DOT = byteArrayOf(ESC, 0x2D, 0x02)

    // Character Sizes
    val TEXT_SIZE_NORMAL = byteArrayOf(GS, 0x21, 0x00)
    val TEXT_SIZE_DOUBLE_HEIGHT = byteArrayOf(GS, 0x21, 0x01)
    val TEXT_SIZE_DOUBLE_WIDTH = byteArrayOf(GS, 0x21, 0x10)
    val TEXT_SIZE_DOUBLE_BOTH = byteArrayOf(GS, 0x21, 0x11)
    val TEXT_SIZE_LARGE = byteArrayOf(GS, 0x21, 0x22)

    // Inverse Print Mode (White on Black)
    val INVERSE_ON = byteArrayOf(GS, 0x42, 0x01)
    val INVERSE_OFF = byteArrayOf(GS, 0x42, 0x00)

    // Paper Cut
    val FULL_CUT = byteArrayOf(GS, 0x56, 0x00)
    val PARTIAL_CUT = byteArrayOf(GS, 0x56, 0x01)
    val FEED_AND_CUT = byteArrayOf(GS, 0x56, 0x42, 0x00)

    // Cash Drawer Kick
    val OPEN_CASH_DRAWER_PIN2 = byteArrayOf(ESC, 0x70, 0x00, 0x19, 0xFA.toByte())
    val OPEN_CASH_DRAWER_PIN5 = byteArrayOf(ESC, 0x70, 0x01, 0x19, 0xFA.toByte())

    fun feedLines(lines: Int): ByteArray {
        val count = lines.coerceIn(1, 255)
        return byteArrayOf(ESC, 0x64, count.toByte())
    }

    /**
     * Converts non-ASCII currency denomination symbols (like ₹, €, £, ¥)
     * to clean, reliable thermal printer characters that won't print as '?'
     */
    fun safeCurrencyForThermal(currencySymbol: String, currencyCode: String = ""): String {
        return when {
            currencySymbol == "₹" || currencyCode.equals("INR", ignoreCase = true) || currencySymbol.contains("₹") -> "Rs. "
            currencySymbol == "€" || currencyCode.equals("EUR", ignoreCase = true) -> "EUR "
            currencySymbol == "£" || currencyCode.equals("GBP", ignoreCase = true) -> "GBP "
            currencySymbol == "¥" || currencyCode.equals("JPY", ignoreCase = true) -> "JPY "
            currencySymbol == "$" -> "$"
            currencySymbol.isBlank() && currencyCode.isNotBlank() -> "$currencyCode "
            else -> {
                // If it contains non-ASCII characters, fallback to currencyCode or sanitized symbol
                val hasNonAscii = currencySymbol.any { it.code > 127 }
                if (hasNonAscii) {
                    if (currencyCode.isNotBlank()) "$currencyCode " else "Rs. "
                } else {
                    currencySymbol
                }
            }
        }
    }

    /**
     * Builds ESC/POS 2D QR Code sequence (standard Model 2)
     */
    fun qrCode(data: String, moduleSize: Int = 6): ByteArray {
        val stream = ByteArrayOutputStream()
        val dataBytes = data.toByteArray(Charset.forName("UTF-8"))
        val length = dataBytes.size + 3
        val pL = (length % 256).toByte()
        val pH = (length / 256).toByte()

        // 1. Select QR Model (Model 2)
        stream.write(byteArrayOf(GS, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))

        // 2. Set module size (1-16 dots)
        val size = moduleSize.coerceIn(3, 12).toByte()
        stream.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, size))

        // 3. Set error correction level (49 = '1' -> 15% Error correction Level M)
        stream.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31))

        // 4. Store the QR data in symbol storage area
        stream.write(byteArrayOf(GS, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        stream.write(dataBytes)

        // 5. Print the QR symbol
        stream.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))

        return stream.toByteArray()
    }

    /**
     * Builds standard Code128 Barcode sequence
     */
    fun barcode128(data: String, height: Int = 64): ByteArray {
        val stream = ByteArrayOutputStream()
        val cleanData = data.filter { it.code in 32..126 }
        val dataBytes = cleanData.toByteArray(Charset.forName("ASCII"))

        // Set barcode height
        stream.write(byteArrayOf(GS, 0x68, height.coerceIn(20, 200).toByte()))
        // Set barcode width (2 dots)
        stream.write(byteArrayOf(GS, 0x77, 0x02))
        // Set HRI characters position (0x02 = below barcode)
        stream.write(byteArrayOf(GS, 0x48, 0x02))
        // Select HRI font (0 = Font A)
        stream.write(byteArrayOf(GS, 0x66, 0x00))

        // Print Code128 (Format B: 0x1D 0x6B 0x49 length {B data...})
        val subsetB = "{B".toByteArray(Charset.forName("ASCII"))
        val totalLength = (subsetB.size + dataBytes.size).toByte()

        stream.write(byteArrayOf(GS, 0x6B, 0x49, totalLength))
        stream.write(subsetB)
        stream.write(dataBytes)

        return stream.toByteArray()
    }

    /**
     * Centers a single string line within a specified character column width (32 chars for 58mm, 48 chars for 80mm)
     */
    fun centerText(text: String, totalWidth: Int = 32): String {
        val trimmed = text.trim()
        if (trimmed.length >= totalWidth) return trimmed.take(totalWidth)
        val leftPadding = (totalWidth - trimmed.length) / 2
        return " ".repeat(leftPadding) + trimmed
    }

    /**
     * Converts a monochrome/greyscale Android Bitmap into standard ESC/POS Raster Bit Image (GS v 0)
     * This prints on 100% of thermal printers even if native vector barcode is unsupported!
     * Optional totalPaperDots (e.g. 384 for 58mm, 576 for 80mm) auto-centers smaller bitmaps on paper.
     */
    fun rasterBitmap(bitmap: Bitmap, totalPaperDots: Int = 0): ByteArray {
        val targetBmp = if (totalPaperDots > 0 && bitmap.width < totalPaperDots) {
            val fullCanvas = Bitmap.createBitmap(totalPaperDots, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(fullCanvas)
            canvas.drawColor(Color.WHITE)
            val leftOffset = ((totalPaperDots - bitmap.width) / 2f).coerceAtLeast(0f)
            canvas.drawBitmap(bitmap, leftOffset, 0f, null)
            fullCanvas
        } else {
            bitmap
        }

        val stream = ByteArrayOutputStream()
        val width = targetBmp.width
        val height = targetBmp.height

        // Width in bytes (must be multiple of 8 dots)
        val widthBytes = (width + 7) / 8
        val xL = (widthBytes % 256).toByte()
        val xH = (widthBytes / 256).toByte()
        val yL = (height % 256).toByte()
        val yH = (height / 256).toByte()

        // GS v 0 0 xL xH yL yH
        stream.write(byteArrayOf(GS, 0x76, 0x30, 0x00, xL, xH, yL, yH))

        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var byteVal = 0
                for (b in 0 until 8) {
                    val x = xByte * 8 + b
                    if (x < width) {
                        val pixel = targetBmp.getPixel(x, y)
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val blue = Color.blue(pixel)
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * blue).toInt()
                        // If dark, set bit to 1 (print dot)
                        if (luminance < 128 && Color.alpha(pixel) > 50) {
                            byteVal = byteVal or (1 shl (7 - b))
                        }
                    }
                }
                stream.write(byteVal)
            }
        }

        if (targetBmp != bitmap) {
            targetBmp.recycle()
        }

        return stream.toByteArray()
    }

    /**
     * Formats 2-column row justified to line width (32 chars for 58mm, 48 chars for 80mm)
     */
    fun formatRowTwoColumn(leftText: String, rightText: String, totalWidth: Int = 32): String {
        val rightLen = rightText.length
        val maxLeftLen = totalWidth - rightLen - 1
        val safeLeft = if (leftText.length > maxLeftLen && maxLeftLen > 0) {
            leftText.take(maxLeftLen)
        } else {
            leftText
        }
        val spacesCount = (totalWidth - safeLeft.length - rightLen).coerceAtLeast(1)
        return safeLeft + " ".repeat(spacesCount) + rightText + "\n"
    }

    /**
     * Formats 3-column table row for 58mm (32 chars)
     */
    fun formatItemRow(name: String, qty: Int, unitPrice: Double, totalPrice: Double, currency: String, totalWidth: Int = 32): String {
        if (totalWidth >= 48) {
            return formatItemRow80mm(name, qty, unitPrice, totalPrice, currency)
        }
        val sb = StringBuilder()
        val totalStr = String.format("%s%.2f", currency, totalPrice)
        val qtyPriceStr = if (qty > 1) String.format(" (%dx %s%.2f)", qty, currency, unitPrice) else ""
        val fullName = name + qtyPriceStr

        if (fullName.length + totalStr.length + 1 <= totalWidth) {
            val spaces = totalWidth - fullName.length - totalStr.length
            sb.append(fullName).append(" ".repeat(spaces)).append(totalStr).append("\n")
        } else {
            if (name.length > totalWidth) {
                sb.append(name.take(totalWidth)).append("\n")
                val subline = if (qty > 1) String.format(" %dx %s%.2f", qty, currency, unitPrice) else ""
                val spaces = (totalWidth - subline.length - totalStr.length).coerceAtLeast(1)
                sb.append(subline).append(" ".repeat(spaces)).append(totalStr).append("\n")
            } else {
                val spaces = (totalWidth - name.length - totalStr.length).coerceAtLeast(1)
                sb.append(name).append(" ".repeat(spaces)).append(totalStr).append("\n")
                if (qty > 1) {
                    sb.append(String.format("  @ %s%.2f each\n", currency, unitPrice))
                }
            }
        }
        return sb.toString()
    }

    /**
     * Formats 4-column structured table row for 80mm wide printers (48 chars)
     */
    fun formatItemRow80mm(name: String, qty: Int, unitPrice: Double, totalPrice: Double, currency: String): String {
        val totalStr = String.format("%s%.2f", currency, totalPrice)
        val unitStr = String.format("%s%.2f", currency, unitPrice)
        val qtyStr = qty.toString()

        val cleanName = if (name.length > 20) name.take(18) + ".." else name
        val namePad = cleanName.padEnd(20)
        val qtyPad = qtyStr.padStart(4)
        val unitPad = unitStr.padStart(11)
        val totalPad = totalStr.padStart(11)

        return "$namePad $qtyPad $unitPad $totalPad\n"
    }

    /**
     * Formats item discount details line for physical thermal print bill
     */
    fun formatItemDiscountRow(discountType: com.example.data.model.ItemDiscountType, discountValue: Double, discountAmount: Double, currency: String, totalWidth: Int = 32): String {
        if (discountAmount <= 0.0 || discountType == com.example.data.model.ItemDiscountType.NONE) return ""
        val typeLabel = when (discountType) {
            com.example.data.model.ItemDiscountType.PERCENTAGE -> "-${discountValue.toInt()}%"
            com.example.data.model.ItemDiscountType.FLAT -> "Flat"
            com.example.data.model.ItemDiscountType.NONE -> ""
        }
        val left = "  * Disc ($typeLabel):"
        val right = String.format("-%s%.2f", currency, discountAmount)
        val spaces = (totalWidth - left.length - right.length).coerceAtLeast(1)
        return left + " ".repeat(spaces) + right + "\n"
    }

    fun divider(char: Char = '-', totalWidth: Int = 32): String {
        return char.toString().repeat(totalWidth) + "\n"
    }
}
