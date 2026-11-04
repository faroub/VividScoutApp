package com.example.data.repository

import com.example.util.PhotoProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoModerationRepositoryTest {

    data class MockPhotoDocument(
        val photoId: String,
        var reportsCount: Int = 0,
        val userReports: MutableMap<String, Long> = mutableMapOf()
    )

    private fun simulateAtomicPhotoReport(
        photoDoc: MockPhotoDocument,
        reporterUid: String,
        nowMs: Long = System.currentTimeMillis()
    ): Result<Int> {
        if (reporterUid.isBlank()) {
            return Result.failure(IllegalStateException("Authentication required to report photos."))
        }

        // 1. Enforce per-user uniqueness (report ID = reporterUid)
        if (photoDoc.userReports.containsKey(reporterUid)) {
            return Result.failure(IllegalStateException("User $reporterUid has already reported photo ${photoDoc.photoId}."))
        }

        // 2. Atomic transaction write: create report document & increment reportsCount
        photoDoc.userReports[reporterUid] = nowMs
        photoDoc.reportsCount += 1

        return Result.success(photoDoc.reportsCount)
    }

    @Test
    fun photoReporting_userCanReportOnce_secondReportBySameUserIsRejected() {
        val photo = MockPhotoDocument("photo_100")
        val userA = "uid_alice"

        // First report by Alice -> Success
        val result1 = simulateAtomicPhotoReport(photo, userA)
        assertTrue("First report by Alice must succeed", result1.isSuccess)
        assertEquals(1, photo.reportsCount)

        // Second report by Alice -> Rejected
        val result2 = simulateAtomicPhotoReport(photo, userA)
        assertTrue("Second report by Alice MUST be rejected", result2.isFailure)
        assertEquals("Count must remain 1 after rejected duplicate report", 1, photo.reportsCount)
    }

    @Test
    fun photoReporting_multipleUniqueUsersCanReport_countsIncrementAccurately() {
        val photo = MockPhotoDocument("photo_200")

        simulateAtomicPhotoReport(photo, "uid_user1")
        assertEquals(1, photo.reportsCount)

        simulateAtomicPhotoReport(photo, "uid_user2")
        assertEquals(2, photo.reportsCount)

        simulateAtomicPhotoReport(photo, "uid_user3")
        assertEquals(3, photo.reportsCount)

        assertTrue("Photo with 3 reports MUST produce hidden moderation state", PhotoProcessor.isPhotoHiddenByReports(photo.reportsCount))
    }

    @Test
    fun photoModeration_threeUniqueReportsProduceHiddenState() {
        assertFalse("0 reports -> visible", PhotoProcessor.isPhotoHiddenByReports(0))
        assertFalse("1 report -> visible", PhotoProcessor.isPhotoHiddenByReports(1))
        assertFalse("2 reports -> visible", PhotoProcessor.isPhotoHiddenByReports(2))

        assertTrue("3 reports -> HIDDEN from UI", PhotoProcessor.isPhotoHiddenByReports(3))
        assertTrue("4 reports -> HIDDEN from UI", PhotoProcessor.isPhotoHiddenByReports(4))
    }
}
