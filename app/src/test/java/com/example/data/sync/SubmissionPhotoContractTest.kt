package com.example.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The submission photoUri security boundary, unit-tested at its pure decision points
 * (the rules side is covered by the Firebase emulator suite):
 *
 * Write side — a public submission document may carry only '' or an owned Storage
 * path; a locally selected photo (content://) must go through the trusted
 * finalizePhotoUpload pipeline before the draft's cloud write; legacy/fabricated
 * external URLs are never rewritten into the document.
 *
 * Display side — a document's photoUri is either a Storage path (resolved to a
 * download URL at display time) or it is not rendered at all.
 */
class SubmissionPhotoContractTest {

    @Test
    fun classifySubmissionPhoto_emptyAndPlaceholderReferences_areNoPhoto() {
        assertEquals(SubmissionPhotoKind.None, classifySubmissionPhoto(null))
        assertEquals(SubmissionPhotoKind.None, classifySubmissionPhoto(""))
        assertEquals(SubmissionPhotoKind.None, classifySubmissionPhoto("ic_cat_sights"))
    }

    @Test
    fun classifySubmissionPhoto_localContentReference_needsTrustedUpload() {
        assertEquals(
            SubmissionPhotoKind.NeedsUpload,
            classifySubmissionPhoto("content://media/picker/1000001234.jpg")
        )
    }

    @Test
    fun classifySubmissionPhoto_canonicalStoragePath_isAlreadyUploaded() {
        assertEquals(
            SubmissionPhotoKind.AlreadyUploaded,
            classifySubmissionPhoto("spot_photos/alice-uid/spot-1/abc123.jpg")
        )
    }

    @Test
    fun classifySubmissionPhoto_externalAndFabricatedUrls_areRejected() {
        assertEquals(
            "Every external URL (including the legacy hardcoded googleusercontent " +
                "fabrication) must be rejected from the public document",
            SubmissionPhotoKind.Rejected,
            classifySubmissionPhoto("https://example.com/x.jpg")
        )
        assertEquals(
            SubmissionPhotoKind.Rejected,
            classifySubmissionPhoto("https://lh3.googleusercontent.com/aida-public/AB6AXuB7")
        )
    }

    @Test
    fun classifySubmissionPhotoForDisplay_emptyOrNull_isNoPhoto() {
        assertEquals(SubmissionDisplayPhoto.NoPhoto, classifySubmissionPhotoForDisplay(null))
        assertEquals(SubmissionDisplayPhoto.NoPhoto, classifySubmissionPhotoForDisplay(""))
    }

    @Test
    fun classifySubmissionPhotoForDisplay_storagePath_resolvableAtDisplayTime() {
        assertEquals(
            SubmissionDisplayPhoto.StoragePath,
            classifySubmissionPhotoForDisplay("spot_photos/alice-uid/spot-1/abc123.jpg")
        )
    }

    @Test
    fun classifySubmissionPhotoForDisplay_externalUrl_isNeverRendered() {
        assertEquals(
            "A legacy external URL must be classifiable so viewers' devices never " +
                "fetch a third-party host",
            SubmissionDisplayPhoto.ExternalRejected,
            classifySubmissionPhotoForDisplay("https://example.com/x.jpg")
        )
        assertEquals(
            SubmissionDisplayPhoto.ExternalRejected,
            classifySubmissionPhotoForDisplay("ftp://files/spot.jpg")
        )
    }
}
