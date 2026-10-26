package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

object BarcodeQrUtils {

    private val multiFormatReader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.ITF
            ),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    /**
     * Builds a standard NPCI compliant UPI Payment URI
     */
    fun buildUpiPaymentUri(
        upiId: String,
        payeeName: String,
        amount: Double? = null,
        invoiceNumber: String? = null
    ): String {
        val cleanUpi = upiId.trim()
        if (cleanUpi.isEmpty()) return ""

        val cleanName = if (payeeName.isNotBlank()) payeeName.trim() else "Merchant"
        val encodedName = URLEncoder.encode(cleanName, StandardCharsets.UTF_8.toString())

        val builder = StringBuilder("upi://pay?pa=$cleanUpi&pn=$encodedName")
        if (amount != null && amount > 0) {
            builder.append("&am=").append(String.format(Locale.US, "%.2f", amount))
        }
        builder.append("&cu=INR")
        if (!invoiceNumber.isNullOrBlank()) {
            val note = URLEncoder.encode("Inv: $invoiceNumber", StandardCharsets.UTF_8.toString())
            builder.append("&tn=").append(note)
        }
        return builder.toString()
    }

    /**
     * Generates a 100% compliant, scannable QR code Bitmap
     */
    fun generateQrCodeBitmap(content: String, size: Int = 300): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            bitMatrixToBitmap(bitMatrix)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a real 1D Barcode (Code128) Bitmap
     */
    fun generateBarcodeBitmap(
        content: String,
        width: Int = 400,
        height: Int = 100,
        format: BarcodeFormat = BarcodeFormat.CODE_128
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val clean = content.filter { it.code in 32..126 }.ifEmpty { "POS-1001" }
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 2
            )
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                clean,
                format,
                width,
                height,
                hints
            )
            bitMatrixToBitmap(bitMatrix)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun bitMatrixToBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Decodes 1D/2D Barcodes directly from a live CameraX ImageProxy
     */
    fun scanBarcodeFromImageProxy(image: ImageProxy): String? {
        return synchronized(multiFormatReader) {
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val width = image.width
                val height = image.height

                val source = PlanarYUVLuminanceSource(
                    bytes,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    false
                )
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                val result = multiFormatReader.decodeWithState(binaryBitmap)
                result.text
            } catch (e: NotFoundException) {
                null
            } catch (e: Exception) {
                null
            } finally {
                multiFormatReader.reset()
            }
        }
    }
}
