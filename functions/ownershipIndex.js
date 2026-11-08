/**
 * Ownership index — trusted production helper module.
 *
 * Public community documents (community_tips, community_submissions, spot_photos) carry
 * NO ownerUid: anonymous Firebase UIDs are stable identifiers, so publishing them would
 * let anyone link one person's contributions across every public collection. Ownership is
 * instead recorded in this server-side index, written only by the trusted Cloud Functions
 * through the Admin SDK (firestore.rules denies all client access, and the publicly
 * readable documents have no owner field at all).
 *
 * firestore.rules reads the index through its get()/exists() functions to enforce
 * ownership at the trusted boundary. A Firestore rules get() bypasses the referenced
 * document's read permission (verified against the emulator), so the index can stay
 * client-unreadable while the rules still validate ownership from it — and a missing
 * index record makes every ownership check fail closed.
 *
 * Document shape: { ownerUid: string, createdAt: number }.
 *
 * Tests must import this module (not copy its logic).
 */

const OWNERSHIP_INDEX_COLLECTION = "ownership_index";
const OWNERSHIP_INDEX_RECORDS_COLLECTION = "records";

/**
 * The index document address for a public document: ownership_index/{collectionId}/records/{docId}.
 * The collection id is part of the path (not a field) so index records of different
 * collections can never collide and a single rules match governs the whole index.
 */
const ownershipIndexRef = (db, collectionId, docId) =>
  db
    .collection(OWNERSHIP_INDEX_COLLECTION)
    .doc(collectionId)
    .collection(OWNERSHIP_INDEX_RECORDS_COLLECTION)
    .doc(docId);

module.exports = {
  OWNERSHIP_INDEX_COLLECTION,
  OWNERSHIP_INDEX_RECORDS_COLLECTION,
  ownershipIndexRef,
};
