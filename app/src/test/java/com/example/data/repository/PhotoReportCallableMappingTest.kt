package com.example.data.repository

import com.google.firebase.functions.FirebaseFunctionsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the reportPhoto Cloud Function call-site mapping: the typed exception
 * mapping for every definitive backend rejection, the authoritative reportsCount
 * response parsing, and the [SpotPhotoData.isHidden] states (report threshold, and
 * the trusted backend's "hidden"/"removed" moderation states).
 *
 * Robolectric is required because FirebaseFunctionsException.Code's static initializer
 * touches android.util.SparseArray.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhotoReportCallableMappingTest {

    @Test
    fun alreadyExistsCode_mapsToPhotoReportException() {
        val cause = Exception("duplicate report")
        val mapped = photoReportFailureFor(FirebaseFunctionsException.Code.ALREADY_EXISTS, cause)
        assertTrue("already-exists becomes a recoverable PhotoReportException", mapped is PhotoReportException)
        assertEquals(cause, mapped.cause)
    }

    @Test
    fun notFoundCode_mapsToPhotoNotFoundException() {
        assertTrue(
            photoReportFailureFor(FirebaseFunctionsException.Code.NOT_FOUND, Exception("photo gone"))
                is PhotoNotFoundException
        )
    }

    @Test
    fun failedPreconditionCode_mapsToPhotoReportException() {
        // failed-precondition is the callable's "photo already hidden or removed" signal.
        val mapped = photoReportFailureFor(FirebaseFunctionsException.Code.FAILED_PRECONDITION, Exception("moderated"))
        assertTrue(mapped is PhotoReportException)
    }

    @Test
    fun unauthenticatedCode_mapsToPhotoReportException() {
        // unauthenticated is the callable's "anonymous sign-in cannot report" signal.
        val mapped = photoReportFailureFor(FirebaseFunctionsException.Code.UNAUTHENTICATED, Exception("anonymous"))
        assertTrue(mapped is PhotoReportException)
    }

    @Test
    fun unexpectedCode_passesTheOriginalExceptionThrough() {
        val cause = Exception("backend hiccup")
        val mapped = photoReportFailureFor(FirebaseFunctionsException.Code.INTERNAL, cause)
        assertEquals("unexpected codes keep their generic shape", cause, mapped)
    }

    @Test
    fun reportsCountResponse_parsesTheCount() {
        assertEquals(3, reportPhotoCountFromResponse(mapOf("photoId" to "p1", "reportsCount" to 3)))
        assertEquals(0, reportPhotoCountFromResponse(mapOf("reportsCount" to 0)))
    }

    @Test
    fun reportsCountResponse_parsesNumericTypesOtherThanInt() {
        // The callable runs on Node: numbers arrive as Long/double, not Kotlin Int.
        assertEquals(7, reportPhotoCountFromResponse(mapOf("reportsCount" to 7L)))
        assertEquals(7, reportPhotoCountFromResponse(mapOf("reportsCount" to 7.0)))
    }

    @Test
    fun reportsCountResponse_rejectsMalformedShapes() {
        assertThrows(IllegalStateException::class.java) {
            reportPhotoCountFromResponse(mapOf("photoId" to "p1"))
        }
        assertThrows(IllegalStateException::class.java) {
            reportPhotoCountFromResponse(null)
        }
        assertThrows(IllegalStateException::class.java) {
            reportPhotoCountFromResponse("not-a-map")
        }
        assertThrows(IllegalStateException::class.java) {
            reportPhotoCountFromResponse(mapOf("reportsCount" to "not-a-number"))
        }
    }

    @Test
    fun spotPhotoData_isHidden_reportsThresholdStillApplies() {
        assertFalse(SpotPhotoData(id = "p", spotId = "s", photoUrl = "u", reportsCount = 2).isHidden)
        assertTrue(SpotPhotoData(id = "p", spotId = "s", photoUrl = "u", reportsCount = 3).isHidden)
    }

    @Test
    fun spotPhotoData_isHidden_backendHiddenStateHidesEvenWithoutReports() {
        assertTrue(
            SpotPhotoData(id = "p", spotId = "s", photoUrl = "u", reportsCount = 0, moderationState = "hidden").isHidden
        )
    }

    @Test
    fun spotPhotoData_isHidden_backendRemovedStateHidesEvenWithoutReports() {
        assertTrue(
            SpotPhotoData(id = "p", spotId = "s", photoUrl = "u", reportsCount = 0, moderationState = "removed").isHidden
        )
    }

    @Test
    fun spotPhotoData_isHidden_unknownModerationStateStaysVisible() {
        assertFalse(
            SpotPhotoData(id = "p", spotId = "s", photoUrl = "u", reportsCount = 0, moderationState = "unexpected").isHidden
        )
    }
}
