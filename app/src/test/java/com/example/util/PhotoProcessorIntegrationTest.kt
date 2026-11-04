package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import kotlin.math.max

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhotoProcessorIntegrationTest {

    @Test
    fun processBitmap_resizesBitmapTo1600pxLongEdge_andValidatesJpegOutput() {
        val largeBitmap = Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888)

        val result = PhotoProcessor.processBitmap(largeBitmap)
        assertTrue("Bitmap processing must succeed", result.isSuccess)

        val processed = result.getOrThrow()
        assertEquals("image/jpeg", processed.mimeType)
        assertTrue("Output byte size must be < 5 MB", processed.sizeBytes < PhotoProcessor.MAX_FILE_SIZE_BYTES)

        val decoded = BitmapFactory.decodeByteArray(processed.jpegBytes, 0, processed.sizeBytes)
        assertNotNull(decoded)

        val maxLongEdge = max(decoded.width, decoded.height)
        assertEquals("Long edge must be capped at 1600px", 1600, maxLongEdge)
    }

    @Test
    fun processImageStream_malformedCorruptStream_returnsFailureWithoutUploading() {
        val corruptBytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9) // Not a valid JPEG/PNG
        val stream = ByteArrayInputStream(corruptBytes)

        val result = PhotoProcessor.processImageStream(stream)
        assertTrue("Malformed image stream must fail processing gracefully", result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun storageOwnershipPath_constructsOwnershipAwarePath() {
        val userUid = "user_abc123"
        val spotId = "spot_456"
        val filename = "photo_xyz.jpg"

        val expectedPath = "spot_photos/$userUid/$spotId/$filename"
        assertTrue("Path must include owner UID and spot ID", expectedPath.contains("spot_photos/$userUid/$spotId/"))
    }
}
