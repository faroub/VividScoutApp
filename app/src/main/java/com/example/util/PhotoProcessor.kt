package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max

data class ProcessedPhoto(
    val jpegBytes: ByteArray,
    val width: Int,
    val height: Int,
    val mimeType: String = "image/jpeg"
) {
    val sizeBytes: Int get() = jpegBytes.size
    val sizeMB: Double get() = sizeBytes / (1024.0 * 1024.0)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProcessedPhoto) return false
        return jpegBytes.contentEquals(other.jpegBytes) && width == other.width && height == other.height
    }

    override fun hashCode(): Int {
        var result = jpegBytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

/**
 * Image processing utility for resizing photos to 1600px long edge, stripping EXIF location metadata,
 * re-encoding to JPEG bytes, and enforcing the 3-report privacy hiding rule.
 */
object PhotoProcessor {

    const val MAX_LONG_EDGE_PX = 1600
    const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB
    const val REPORT_HIDE_THRESHOLD = 3

    /**
     * Safely decodes input image bytes, resizes bitmap to max 1600px long edge,
     * strips EXIF metadata, validates output dimensions & size (< 5MB), and recycles bitmaps.
     */
    fun processImageStream(
        inputStream: InputStream,
        quality: Int = 85
    ): Result<ProcessedPhoto> {
        return try {
            val bytes = inputStream.readBytes()
            if (bytes.size < 10) {
                return Result.failure(IllegalArgumentException("Image data too small or truncated."))
            }

            // Verify Magic Bytes for JPEG (0xFF, 0xD8) or PNG (0x89, 0x50, 0x4E, 0x47)
            val isJpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
            val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()

            if (!isJpeg && !isPng) {
                return Result.failure(IllegalArgumentException("Unsupported or malformed image format. Only JPEG and PNG are supported."))
            }

            val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return Result.failure(IllegalArgumentException("Failed to decode image stream."))

            processBitmap(originalBitmap, quality)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resizes bitmap to max 1600px on long edge, strips EXIF headers by re-compressing to JPEG,
     * validates output size (< 5MB), and recycles temporary bitmaps.
     */
    fun processBitmap(
        bitmap: Bitmap,
        quality: Int = 85
    ): Result<ProcessedPhoto> {
        return try {
            if (bitmap.width <= 0 || bitmap.height <= 0) {
                return Result.failure(IllegalArgumentException("Invalid bitmap dimensions: ${bitmap.width}x${bitmap.height}"))
            }

            val width = bitmap.width
            val height = bitmap.height
            val maxEdge = max(width, height)

            val resizedBitmap = if (maxEdge > MAX_LONG_EDGE_PX) {
                val scale = MAX_LONG_EDGE_PX.toFloat() / maxEdge
                val targetWidth = max(1, (width * scale).toInt())
                val targetHeight = max(1, (height * scale).toInt())
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            val compressed = resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }

            if (!compressed) {
                return Result.failure(IllegalStateException("Failed to compress bitmap to JPEG format."))
            }

            val jpegBytes = outputStream.toByteArray()

            if (jpegBytes.size > MAX_FILE_SIZE_BYTES) {
                return Result.failure(IllegalStateException("Processed image size (${jpegBytes.size / (1024 * 1024)} MB) exceeds 5 MB limit."))
            }

            val resultPhoto = ProcessedPhoto(
                jpegBytes = jpegBytes,
                width = if (maxEdge > MAX_LONG_EDGE_PX) (width * (MAX_LONG_EDGE_PX.toFloat() / maxEdge)).toInt() else width,
                height = if (maxEdge > MAX_LONG_EDGE_PX) (height * (MAX_LONG_EDGE_PX.toFloat() / maxEdge)).toInt() else height,
                mimeType = "image/jpeg"
            )

            Result.success(resultPhoto)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Determines whether a photo should be hidden from the UI pending moderation review (>= 3 reports).
     */
    fun isPhotoHiddenByReports(reportCount: Int): Boolean {
        return reportCount >= REPORT_HIDE_THRESHOLD
    }
}
