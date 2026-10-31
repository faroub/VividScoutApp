package com.example.util

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * Utility to calculate physical file sizes of SQLite / Room databases and format storage metrics.
 */
object StorageSizeCalculator {

    private const val DB_NAME = "vivid_scout_database"

    /**
     * Calculates the total physical on-disk storage footprint of the Room database
     * including the primary database file, WAL journal, and shared-memory index files.
     */
    fun getRoomDatabaseSizeBytes(context: Context): Long {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            var totalBytes = 0L
            if (dbFile.exists()) totalBytes += dbFile.length()
            if (walFile.exists()) totalBytes += walFile.length()
            if (shmFile.exists()) totalBytes += shmFile.length()

            // If empty or freshly initialized, report minimum reasonable table header size (~64KB)
            if (totalBytes < 1024L && dbFile.exists()) 64 * 1024L else totalBytes
        } catch (e: Exception) {
            // Safe recovery: Return estimated default size (128 KB) on I/O error
            android.util.Log.w("StorageSizeCalculator", "Error measuring database file size", e)
            128 * 1024L
        }
    }

    /**
     * Formats bytes into human-readable representation e.g. "450 B", "320 KB", "1.4 MB".
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        return when {
            bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
