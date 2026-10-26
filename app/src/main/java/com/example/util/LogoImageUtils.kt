package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min
import kotlin.math.roundToInt

object LogoImageUtils {
    private const val TAG = "LogoImageUtils"

    /**
     * Copies and persists an uploaded logo from any image URI into internal app storage.
     * Handles any image size / format safely without memory limits.
     * @return Absolute file path of the permanently saved logo
     */
    fun saveLogoFromUri(context: Context, sourceUri: Uri, businessId: Long): String? {
        return try {
            val logosDir = File(context.filesDir, "business_logos").apply { mkdirs() }
            val destFile = File(logosDir, "logo_business_${businessId}_${System.currentTimeMillis()}.png")

            // 1. Decode bounds to compute safe inSampleSize for huge images
            var inSampleSize = 1
            var input: InputStream? = context.contentResolver.openInputStream(sourceUri)
            if (input != null) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                input.close()

                val maxDimension = 1600 // Maximum resolution to store for pristine clarity
                if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                        inSampleSize *= 2
                    }
                }
            }

            // 2. Decode actual bitmap with sample size
            input = context.contentResolver.openInputStream(sourceUri) ?: return null
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeStream(input, null, decodeOptions)
            input.close()

            if (bitmap != null) {
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()
                destFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving business logo", e)
            null
        }
    }

    /**
     * Deletes a previously uploaded logo file from internal storage
     */
    fun deleteLogoFile(logoPathOrUri: String?) {
        if (logoPathOrUri.isNullOrBlank()) return
        try {
            val cleanPath = when {
                logoPathOrUri.startsWith("file://") -> logoPathOrUri.removePrefix("file://")
                else -> logoPathOrUri
            }
            val file = File(cleanPath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting logo file: $logoPathOrUri", e)
        }
    }

    /**
     * Loads a logo Bitmap from file path or content URI
     */
    fun loadLogoBitmap(context: Context, logoPathOrUri: String?, maxDimension: Int = 800): Bitmap? {
        if (logoPathOrUri.isNullOrBlank()) return null
        return try {
            if (logoPathOrUri.startsWith("content://")) {
                val uri = Uri.parse(logoPathOrUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                val cleanPath = when {
                    logoPathOrUri.startsWith("file://") -> logoPathOrUri.removePrefix("file://")
                    else -> logoPathOrUri
                }
                val file = File(cleanPath)
                if (file.exists()) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    var sampleSize = 1
                    while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                        sampleSize *= 2
                    }
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading logo bitmap: $logoPathOrUri", e)
            null
        }
    }

    /**
     * Loads and auto-adjusts logo for Thermal POS printers:
     * - Scales to target logo width (e.g. 240px for 58mm, 420px for 80mm)
     * - Converts to monochrome 1-bit bitmap using Floyd-Steinberg error diffusion dithering
     * - Center-pads logo on totalPaperDots wide bitmap (384 dots for 58mm, 576 dots for 80mm) for dead-center alignment across all printers
     */
    fun loadAndScaleLogoForThermal(
        context: Context,
        logoPathOrUri: String?,
        targetWidth: Int = 240,
        totalPaperDots: Int = 384
    ): Bitmap? {
        val original = loadLogoBitmap(context, logoPathOrUri, maxDimension = 1000) ?: return null
        return try {
            val aspect = original.height.toFloat() / original.width.toFloat()
            val safeWidth = targetWidth.coerceAtMost(totalPaperDots - 16)
            val targetHeight = (safeWidth * aspect).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(original, safeWidth, targetHeight, true)

            // Convert to Floyd-Steinberg dithered monochrome bitmap for ESC/POS raster image
            val monoBitmap = convertToMonochromeDithered(scaled)
            if (scaled != original) scaled.recycle()
            original.recycle()

            // Center monoBitmap on full totalPaperDots width canvas (384 dots for 58mm, 576 dots for 80mm)
            // This guarantees dead-center alignment on ALL 58mm & 80mm thermal hardware
            if (totalPaperDots > safeWidth) {
                val fullCanvas = Bitmap.createBitmap(totalPaperDots, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(fullCanvas)
                canvas.drawColor(Color.WHITE) // White paper background
                val leftOffset = ((totalPaperDots - safeWidth) / 2f).coerceAtLeast(0f)
                canvas.drawBitmap(monoBitmap, leftOffset, 0f, null)
                monoBitmap.recycle()
                fullCanvas
            } else {
                monoBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing thermal logo bitmap", e)
            original
        }
    }

    /**
     * High quality Floyd-Steinberg error diffusion dithering for crystal clear thermal receipt prints
     */
    private fun convertToMonochromeDithered(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = Array(height) { FloatArray(width) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                val alpha = (p shr 24) and 0xFF
                if (alpha < 128) {
                    gray[y][x] = 255f // Transparent is treated as white paper
                } else {
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    gray[y][x] = (0.299f * r + 0.587f * g + 0.114f * b)
                }
            }
        }

        val outPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val oldVal = gray[y][x]
                val newVal = if (oldVal < 128f) 0f else 255f
                val error = oldVal - newVal

                outPixels[y * width + x] = if (newVal == 0f) Color.BLACK else Color.WHITE

                if (x + 1 < width) gray[y][x + 1] += error * 7f / 16f
                if (y + 1 < height) {
                    if (x > 0) gray[y + 1][x - 1] += error * 3f / 16f
                    gray[y + 1][x] += error * 5f / 16f
                    if (x + 1 < width) gray[y + 1][x + 1] += error * 1f / 16f
                }
            }
        }

        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }
}
