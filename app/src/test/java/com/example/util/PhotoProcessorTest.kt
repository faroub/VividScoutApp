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
import kotlin.math.max

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhotoProcessorTest {

    @Test
    fun processAndStripExif_resizesBitmapTo1600pxLongEdge_andStripsExifMetadata() {
        // Create 2400 x 1800 test bitmap
        val originalBitmap = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)

        val processedBytes = PhotoProcessor.processAndStripExif(originalBitmap)
        assertNotNull(processedBytes)
        assertTrue(processedBytes.isNotEmpty())

        val decodedBitmap = BitmapFactory.decodeByteArray(processedBytes, 0, processedBytes.size)
        assertNotNull(decodedBitmap)

        val maxLongEdge = max(decodedBitmap.width, decodedBitmap.height)
        assertEquals("Long edge of resized photo must be capped at 1600px", 1600, maxLongEdge)
    }

    @Test
    fun isPhotoHiddenByReports_hidesPhotoWhenReportCountIsThreeOrGreater() {
        assertFalse("0 reports should not hide photo", PhotoProcessor.isPhotoHiddenByReports(0))
        assertFalse("1 report should not hide photo", PhotoProcessor.isPhotoHiddenByReports(1))
        assertFalse("2 reports should not hide photo", PhotoProcessor.isPhotoHiddenByReports(2))

        assertTrue("3 reports MUST hide photo from UI pending review", PhotoProcessor.isPhotoHiddenByReports(3))
        assertTrue("5 reports MUST hide photo from UI pending review", PhotoProcessor.isPhotoHiddenByReports(5))
    }
}
