package com.example.data.sync

import android.content.Context
import com.example.R
import com.example.util.SafeLog
import com.example.util.configureAppCheck
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal const val TAG = "FirestoreSyncManager"
internal const val SUBMISSIONS_COLLECTION = "community_submissions"
internal const val TIPS_COLLECTION = "community_tips"
internal const val USERS_COLLECTION = "users"
internal const val PRIVATE_NOTES_COLLECTION = "private_spot_notes"

/**
 * Firebase initialization, Firestore instance resolution (honoring the configured
 * database id), and anonymous-auth bootstrapping. Shared by [FirestoreSyncManager]
 * and its community write/download helpers so every sync path goes through the same
 * trusted-boundary setup. All operations fail soft (null/false) and log at debug
 * level; callers treat a null Firestore as "sync unavailable", never as success.
 */
internal object FirebaseAccess {

    internal fun getFirebaseApp(context: Context): FirebaseApp? {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                FirebaseApp.initializeApp(context)
            }
        } catch (e: Exception) {
            SafeLog.d(TAG, "Failed to init Firebase app: ${e.message}")
            null
        }
    }

    internal fun getFirestore(context: Context): FirebaseFirestore? {
        return try {
            configureAppCheck(context)
            val app = getFirebaseApp(context) ?: return null

            val databaseId = try {
                context.getString(R.string.firestore_database_id)
            } catch (_: Exception) {
                ""
            }

            if (databaseId.isNotBlank()) {
                FirebaseFirestore.getInstance(app, databaseId)
            } else {
                FirebaseFirestore.getInstance(app)
            }
        } catch (e: Exception) {
            SafeLog.d(TAG, "Failed to init Firestore instance: ${e.message}")
            null
        }
    }

    internal suspend fun ensureFirebaseAuth(context: Context): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val app = if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                FirebaseApp.initializeApp(context)
            } ?: return@withContext false

            val auth = com.google.firebase.auth.FirebaseAuth.getInstance(app)
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
                SafeLog.d(TAG, "Successfully authenticated Firebase user anonymously (${SafeLog.sanitizeUid(auth.currentUser?.uid)})")
            }
            auth.currentUser != null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.d(TAG, "Firebase Auth sign-in skipped: ${e.message}")
            false
        }
    }
}
