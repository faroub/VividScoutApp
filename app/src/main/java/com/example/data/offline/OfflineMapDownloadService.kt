package com.example.data.offline

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.repository.ScoutGemRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class OfflineMapDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val downloadChannel = Channel<DownloadTask>(Channel.UNLIMITED)

    private val isProcessing = AtomicInteger(0)

    data class DownloadTask(
        val packId: String,
        val cityName: String,
        val lat: Double,
        val lng: Double,
        val radiusMeters: Int
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startQueueProcessor()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START_DOWNLOAD) {
            val packId = intent.getStringExtra(EXTRA_PACK_ID) ?: return START_NOT_STICKY
            val cityName = intent.getStringExtra(EXTRA_CITY_NAME) ?: "Vector Map"
            val lat = intent.getDoubleExtra(EXTRA_LAT, 38.7112)
            val lng = intent.getDoubleExtra(EXTRA_LNG, -9.1423)
            val radius = intent.getIntExtra(EXTRA_RADIUS, 3500)

            // Immediately start foreground service with initial notification
            val notification = buildProgressNotification(cityName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start foreground with type DATA_SYNC: ${e.message}")
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Enqueue task
            serviceScope.launch {
                downloadChannel.send(DownloadTask(packId, cityName, lat, lng, radius))
            }
        } else if (action == ACTION_STOP_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startQueueProcessor() {
        serviceScope.launch {
            for (task in downloadChannel) {
                isProcessing.incrementAndGet()
                try {
                    processDownload(task)
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading ${task.cityName}", e)
                } finally {
                    if (isProcessing.decrementAndGet() == 0 && downloadChannel.isEmpty) {
                        Log.i(TAG, "All downloads finished. Stopping foreground service.")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private suspend fun processDownload(task: DownloadTask) {
        val db = AppDatabase.getDatabase(applicationContext)
        val gemRepo = ScoutGemRepository(db.scoutDao(), db.discoveredGemDao())
        val mapManager = OfflineMapManager(applicationContext, gemRepo)

        val notificationManager = NotificationManagerCompat.from(applicationContext)

        // Stream progress updates from 0 to 100%
        mapManager.downloadPack(task.packId, task.lat, task.lng)

        // Show completion notification in status bar that persists until tapped
        showCompletionNotification(task.cityName, notificationManager)
    }

    private fun buildProgressNotification(cityName: String, progress: Int): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading $cityName Vector Map")
            .setContentText(if (progress > 0) "$progress% completed" else "Connecting to Overpass API...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun showCompletionNotification(cityName: String, notificationManager: NotificationManagerCompat) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completionNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Offline Map Ready")
            .setContentText("$cityName vector map downloaded for zero-data offline scouting.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager.notify(COMPLETION_NOTIFICATION_ID_BASE + cityName.hashCode(), completionNotification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing for completion notification: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Offline Map Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time status for offline vector map pack downloads"
                setShowBadge(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val TAG = "OfflineMapDownloadSvc"
        const val CHANNEL_ID = "offline_map_downloads"
        const val NOTIFICATION_ID = 4001
        const val COMPLETION_NOTIFICATION_ID_BASE = 5000

        const val ACTION_START_DOWNLOAD = "com.example.action.START_DOWNLOAD"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"

        const val EXTRA_PACK_ID = "extra_pack_id"
        const val EXTRA_CITY_NAME = "extra_city_name"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_RADIUS = "extra_radius"

        fun startDownload(
            context: Context,
            packId: String,
            cityName: String,
            lat: Double,
            lng: Double,
            radiusMeters: Int = 3500
        ) {
            val intent = Intent(context, OfflineMapDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_PACK_ID, packId)
                putExtra(EXTRA_CITY_NAME, cityName)
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
                putExtra(EXTRA_RADIUS, radiusMeters)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground download service: ${e.message}")
            }
        }
    }
}
