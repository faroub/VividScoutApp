/**
 * Firestore database configuration — the single Cloud Functions end of the
 * Android/Functions database contract.
 *
 * The Android app reads R.string.firestore_database_id (SharedTripManager and
 * FirestoreSyncManager) and selects FirebaseFirestore.getInstance(app, databaseId)
 * when it is non-blank, defaulting to the standard database otherwise. This module
 * resolves exactly the same convention on the backend: the empty string and
 * "(default)" both canonicalize to "(default)" (verified to resolve the same
 * Firestore instance), and any other value selects that named database. If the two
 * ends ever diverge, shared-trip publishing and every other trusted write would land
 * in a different Firestore database than the app reads — silent data loss.
 */

const { defineString } = require("firebase-functions/params");

const FIRESTORE_DATABASE_ID = defineString("FIRESTORE_DATABASE_ID", {
  default: "(default)",
  label: "Firestore database ID — must match the Android app's firestore_database_id string resource"
});

/**
 * Resolves the configured Firestore database ID to the canonical "(default)" when
 * unset or blank, mirroring the Android side's blank fallback so both ends always
 * agree on which database they address.
 */
function firestoreDatabaseId() {
  const databaseId = FIRESTORE_DATABASE_ID.value().trim();
  return databaseId === "" ? "(default)" : databaseId;
}

module.exports = { FIRESTORE_DATABASE_ID, firestoreDatabaseId };
