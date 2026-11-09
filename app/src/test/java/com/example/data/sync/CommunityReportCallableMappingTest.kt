package com.example.data.sync

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
 * Tests for the reportCommunityTip Cloud Function call-site mapping: the typed
 * exception mapping for every definitive backend rejection and the authoritative
 * reportsCount response parsing.
 *
 * Robolectric is required because FirebaseFunctionsException.Code's static initializer
 * touches android.util.SparseArray.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CommunityReportCallableMappingTest {

    @Test
    fun alreadyExistsCode_mapsToCommunityReportException() {
        val cause = Exception("duplicate report")
        val mapped = communityReportFailureFor(FirebaseFunctionsException.Code.ALREADY_EXISTS, cause)
        assertTrue("already-exists becomes a recoverable CommunityReportException", mapped is CommunityReportException)
        assertEquals(cause, mapped.cause)
    }

    @Test
    fun permissionDeniedCode_mapsToCommunityReportException() {
        // permission-denied is the callable's self-report rejection: owners cannot
        // report their own tip, mirroring the self-vote refusal.
        val cause = Exception("owner cannot report own tip")
        val mapped = communityReportFailureFor(FirebaseFunctionsException.Code.PERMISSION_DENIED, cause)
        assertTrue(mapped is CommunityReportException)
        assertEquals(cause, mapped.cause)
    }

    @Test
    fun failedPreconditionCode_mapsToCommunityReportException() {
        // failed-precondition is the callable's "tip already hidden or removed" signal.
        val mapped = communityReportFailureFor(FirebaseFunctionsException.Code.FAILED_PRECONDITION, Exception("moderated"))
        assertTrue(mapped is CommunityReportException)
    }

    @Test
    fun unauthenticatedCode_mapsToCommunityReportException() {
        // unauthenticated is the callable's "anonymous sign-in cannot report" signal.
        val mapped = communityReportFailureFor(FirebaseFunctionsException.Code.UNAUTHENTICATED, Exception("anonymous"))
        assertTrue(mapped is CommunityReportException)
    }

    @Test
    fun resourceExhaustedCode_mapsToCommunityReportException() {
        // resource-exhausted is the callable's per-UID hourly report-limit signal.
        val mapped = communityReportFailureFor(FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED, Exception("limit"))
        assertTrue(mapped is CommunityReportException)
    }

    @Test
    fun notFoundCode_mapsToCommunityReportException() {
        val mapped = communityReportFailureFor(FirebaseFunctionsException.Code.NOT_FOUND, Exception("tip gone"))
        assertTrue(mapped is CommunityReportException)
    }

    @Test
    fun unexpectedCode_passesTheOriginalExceptionThrough() {
        val cause = Exception("backend hiccup")
        val mapped = communityReportFailureFor(FirebaseFunctionsException.Code.INTERNAL, cause)
        assertEquals("unexpected codes keep their generic shape", cause, mapped)
        assertFalse(mapped is CommunityReportException)
    }

    @Test
    fun reportsCountResponse_parsesTheCount() {
        assertEquals(3, communityReportCountFromResponse(mapOf("tipId" to "t1", "reportsCount" to 3)))
        assertEquals(0, communityReportCountFromResponse(mapOf("reportsCount" to 0)))
    }

    @Test
    fun reportsCountResponse_parsesNumericTypesOtherThanInt() {
        // The callable runs on Node: numbers arrive as Long/double, not Kotlin Int.
        assertEquals(7, communityReportCountFromResponse(mapOf("reportsCount" to 7L)))
        assertEquals(7, communityReportCountFromResponse(mapOf("reportsCount" to 7.0)))
    }

    @Test
    fun reportsCountResponse_rejectsMalformedShapes() {
        assertThrows(IllegalStateException::class.java) {
            communityReportCountFromResponse(mapOf("tipId" to "t1"))
        }
        assertThrows(IllegalStateException::class.java) {
            communityReportCountFromResponse(null)
        }
        assertThrows(IllegalStateException::class.java) {
            communityReportCountFromResponse("not-a-map")
        }
        assertThrows(IllegalStateException::class.java) {
            communityReportCountFromResponse(mapOf("reportsCount" to "not-a-number"))
        }
    }
}
