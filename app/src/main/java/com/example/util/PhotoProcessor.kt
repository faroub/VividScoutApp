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
 * Includes memory-efficient bounded stream reading, dimension subsampling, and decompression bomb protection.
 */
object PhotoProcessor {

    const val MAX_LONG_EDGE_PX = 1600
    const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB max output
    const val MAX_INPUT_STREAM_BYTES = 15 * 1024 * 1024 // 15 MB max input
    const val MAX_DIMENSION_PX = 12000 // 12,000px max width or height
    const val MAX_TOTAL_PIXELS = 50_000_000L // 50 Megapixels max
    const val REPORT_HIDE_THRESHOLD = 3

    /**
     * Bounded stream reader reading up to maxBytes + 1.
     * Throws IllegalArgumentException if stream length exceeds maxBytes.
     */
    fun readBoundedStream(inputStream: InputStream, maxBytes: Int = MAX_INPUT_STREAM_BYTES): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var totalRead = 0

        while (true) {
            val read = inputStream.read(chunk)
            if (read == -1) break
            totalRead += read
            if (totalRead > maxBytes) {
                throw IllegalArgumentException("Input stream exceeds maximum allowed ${maxBytes / (1024 * 1024)} MB size limit.")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    /**
     * Calculates inSampleSize power-of-two factor for memory-efficient subsampled decoding.
     */
    fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, reqMaxEdge: Int = MAX_LONG_EDGE_PX): Int {
        var inSampleSize = 1
        val maxEdge = max(rawWidth, rawHeight)
        if (maxEdge > reqMaxEdge) {
            val halfMaxEdge = maxEdge / 2
            while ((halfMaxEdge / inSampleSize) >= reqMaxEdge) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Safely decodes input image bytes with bounded reading, dimension subsampling, EXIF stripping,
     * and output size validation (< 5MB).
     */
    fun processImageStream(
        inputStream: InputStream,
        quality: Int = 85
    ): Result<ProcessedPhoto> {
        return try {
            val bytes = try {
                readBoundedStream(inputStream, MAX_INPUT_STREAM_BYTES)
            } catch (e: Exception) {
                return Result.failure(e)
            }

            if (bytes.size < 10) {
                return Result.failure(IllegalArgumentException("Image data too small or truncated."))
            }

            // Verify Magic Bytes for JPEG (0xFF, 0xD8) or PNG (0x89, 0x50, 0x4E, 0x47)
            val isJpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
            val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()

            if (!isJpeg && !isPng) {
                return Result.failure(IllegalArgumentException("Unsupported or malformed image format. Only JPEG and PNG are supported."))
            }

            // 1. Inspect image dimensions first without decoding full pixel array
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

            val rawWidth = boundsOptions.outWidth
            val rawHeight = boundsOptions.outHeight

            if (rawWidth <= 0 || rawHeight <= 0) {
                return Result.failure(IllegalArgumentException("Corrupt image header or invalid dimensions ($rawWidth x $rawHeight)."))
            }

            // 2. Decompression bomb protection
            if (rawWidth > MAX_DIMENSION_PX || rawHeight > MAX_DIMENSION_PX || (rawWidth.toLong() * rawHeight.toLong()) > MAX_TOTAL_PIXELS) {
                return Result.failure(IllegalArgumentException("Image dimensions ($rawWidth x $rawHeight) exceed maximum allowed safety bounds."))
            }

            // 3. Subsampled memory-efficient decoding
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(rawWidth, rawHeight, MAX_LONG_EDGE_PX)
            }

            val decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                ?: return Result.failure(IllegalArgumentException("Failed to decode image stream."))

            val result = processBitmap(decodedBitmap, quality)

            // Recycle internal decoded bitmap created from byte stream
            decodedBitmap.recycle()

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resizes bitmap to max 1600px on long edge, strips EXIF headers by re-compressing to JPEG,
     * validates output size (< 5MB), and manages temporary bitmap lifecycles.
     */
    fun processBitmap(
        bitmap: Bitmap,
        quality: Int = 85
    ): Result<ProcessedPhoto> {
        return try {
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                return Result.failure(IllegalArgumentException("Invalid or recycled bitmap dimensions."))
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

            // Safely recycle internal temporary scaled bitmap (do NOT recycle caller-owned bitmap)
            if (resizedBitmap != bitmap && !resizedBitmap.isRecycled) {
                resizedBitmap.recycle()
            }

            if (!compressed) {
                return Result.failure(IllegalStateException("Failed to compress bitmap to JPEG format."))
            }

            val jpegBytes = outputStream.toByteArray()

            if (jpegBytes.size > MAX_FILE_SIZE_BYTES) {
                return Result.failure(IllegalStateException("Processed image size (${jpegBytes.size / (1024 * 1024)} MB) exceeds 5 MB limit."))
            }

            val resultWidth = if (maxEdge > MAX_LONG_EDGE_PX) (width * (MAX_LONG_EDGE_PX.toFloat() / maxEdge)).toInt() else width
            val resultHeight = if (maxEdge > MAX_LONG_EDGE_PX) (height * (MAX_LONG_EDGE_PX.toFloat() / maxEdge)).toInt() else height

            val resultPhoto = ProcessedPhoto(
                jpegBytes = jpegBytes,
                width = max(1, resultWidth),
                height = max(1, resultHeight),
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
