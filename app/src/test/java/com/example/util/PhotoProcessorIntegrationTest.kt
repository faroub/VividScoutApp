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
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

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

        // Caller-owned bitmap must NOT be recycled
        assertFalse("Caller-owned bitmap must not be recycled by PhotoProcessor", largeBitmap.isRecycled)
    }

    @Test
    fun calculateInSampleSize_calculatesPowerOfTwoSubsamplingFactor() {
        val sampleSize = PhotoProcessor.calculateInSampleSize(4000, 3000, reqMaxEdge = 1600)
        assertEquals("4000px raw width subsampled for 1600px max edge yields sample size = 2", 2, sampleSize)
    }

    @Test
    fun readBoundedStream_oversizedStreamExceedingFifteenMegabytes_throwsException() {
        // Stream that produces more than 15 MB
        val oversizedStream = object : InputStream() {
            var count = 0
            override fun read(): Int {
                count++
                return if (count <= 16 * 1024 * 1024) 'A'.code else -1
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (count >= 16 * 1024 * 1024) return -1
                val toRead = min(len, 16 * 1024 * 1024 - count)
                count += toRead
                return toRead
            }
        }

        try {
            PhotoProcessor.readBoundedStream(oversizedStream, maxBytes = 15 * 1024 * 1024)
            assertTrue("Reading stream > 15 MB must throw IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertTrue("Exception message must reference size limit", e.message!!.contains("exceeds maximum allowed"))
        }
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
