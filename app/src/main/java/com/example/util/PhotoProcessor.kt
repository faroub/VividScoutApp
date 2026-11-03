package com.example.util

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Image processing utility for resizing photos to 1600px long edge, stripping EXIF location metadata,
 * and enforcing the 3-report privacy hiding rule.
 */
object PhotoProcessor {

    const val MAX_LONG_EDGE_PX = 1600
    const val REPORT_HIDE_THRESHOLD = 3

    /**
     * Resizes a bitmap to at most 1600px on its longest edge while preserving aspect ratio,
     * re-compressing to JPEG bytes without any EXIF metadata headers.
     */
    fun processAndStripExif(bitmap: Bitmap, quality: Int = 85): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val maxEdge = max(width, height)

        val resizedBitmap = if (maxEdge > MAX_LONG_EDGE_PX) {
            val scale = MAX_LONG_EDGE_PX.toFloat() / maxEdge
            val targetWidth = (width * scale).toInt()
            val targetHeight = (height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        // re-compressing to JPEG creates a fresh byte array free of original EXIF metadata tags
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Determines whether a photo should be hidden from the UI pending moderation review (>= 3 reports).
     */
    fun isPhotoHiddenByReports(reportCount: Int): Boolean {
        return reportCount >= REPORT_HIDE_THRESHOLD
    }
}
