package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.network.GeminiQueryService
import com.example.util.AdaptivePrefetchPolicy
import com.example.util.BatteryStatus
import com.example.util.LocationBreadcrumb
import com.example.util.MovementVector
import com.example.util.MovementVectorUtils
import com.example.util.PrefetchPowerMode
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ProximityPrefetchSvc"
private const val NOTIFICATION_ID = 2001
private const val CHANNEL_ID = "proximity_prefetch_channel"

/**
 * Android Foreground Service (dataSync) that monitors user movement trajectory,
 * predicts the forward 5km movement vector corridor, and uses GeminiQueryService
 * to automatically pre-cache authentic Lisbon points of interest directly into the Room database.
 */
class ProximityPrefetchService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geminiQueryService: GeminiQueryService
    private lateinit var database: AppDatabase

    private val breadcrumbs = mutableListOf<LocationBreadcrumb>()
    private var lastPrefetchedOriginLat: Double? = null
    private var lastPrefetchedOriginLng: Double? = null
    private var lastPrefetchedBearing: Float? = null

    private var currentBatteryStatus = BatteryStatus()
    private var currentPowerMode = PrefetchPowerMode.OPTIMAL_WALKING

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { updateBatteryStatus(it) }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                handleNewLocation(loc)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        geminiQueryService = GeminiQueryService()
        database = AppDatabase.getDatabase(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()

        // Register broadcast receiver for battery level, plugged state, and power-save mode
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val stickyBattery = registerReceiver(batteryReceiver, filter)
        stickyBattery?.let { updateBatteryStatus(it) }

        _isServiceRunning.value = true
        _statusMessage.value = "Service active • Initializing movement vector"
    }

    private fun updateBatteryStatus(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else currentBatteryStatus.levelPercent
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode == true

        currentBatteryStatus = BatteryStatus(
            levelPercent = percent,
            isPlugged = plugged,
            isPowerSaveMode = isPowerSave
        )
        _batteryStatus.value = currentBatteryStatus

        // Re-evaluate policy with latest known speed
        reEvaluatePolicy(_currentSpeedKmh.value / 3.6f)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopPrefetchService()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_PREFETCH -> {
                val lat = intent?.getDoubleExtra(EXTRA_LAT, 38.7112) ?: 38.7112
                val lng = intent?.getDoubleExtra(EXTRA_LNG, -9.1423) ?: -9.1423
                val bearing = intent?.getFloatExtra(EXTRA_BEARING, 45f) ?: 45f
                val speed = intent?.getFloatExtra(EXTRA_SPEED, 1.4f) ?: 1.4f

                startForegroundServiceCompat(buildNotification("Manual 5km vector prefetch triggered..."))
                serviceScope.launch {
                    val vector = MovementVectorUtils.calculateMovementVector(
                        listOf(LocationBreadcrumb(lat, lng, speed, bearing)),
                        fallbackBearing = bearing
                    )
                    executePrefetchForVector(vector)
                }
            }
            else -> {
                startForegroundServiceCompat(buildNotification("Monitoring path & predicting 5km corridor..."))
                startLocationTracking()

                // Initial baseline vector prefetch
                serviceScope.launch {
                    val defaultLat = intent?.getDoubleExtra(EXTRA_LAT, 38.7112) ?: 38.7112
                    val defaultLng = intent?.getDoubleExtra(EXTRA_LNG, -9.1423) ?: -9.1423
                    val defaultBearing = intent?.getFloatExtra(EXTRA_BEARING, 45f) ?: 45f
                    val initialVector = MovementVectorUtils.calculateMovementVector(
                        listOf(LocationBreadcrumb(defaultLat, defaultLng, 1.4f, defaultBearing)),
                        fallbackBearing = defaultBearing
                    )
                    executePrefetchForVector(initialVector)
                }
            }
        }

        return START_STICKY
    }

    private fun startForegroundServiceCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed startForeground with type DATA_SYNC: ${e.message}")
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun reEvaluatePolicy(speedMps: Float) {
        val newMode = AdaptivePrefetchPolicy.evaluatePolicy(currentBatteryStatus, speedMps)
        val modeChanged = newMode != currentPowerMode

        if (modeChanged) {
            val oldMode = currentPowerMode
            currentPowerMode = newMode
            _activePowerMode.value = newMode
            Log.i(TAG, "Prefetch power mode transitioned from ${oldMode.title} to ${newMode.title} (Battery: ${currentBatteryStatus.levelPercent}%, Speed: ${speedMps * 3.6f} km/h)")

            reconfigureLocationTracking(newMode)

            when (newMode) {
                PrefetchPowerMode.VEHICLE_SPEED_PAUSED -> {
                    _statusMessage.value = "Transit speed (${(speedMps * 3.6f).toInt()} km/h) • Prefetch paused until walking resumes"
                    updateNotification("Transit speed detected • Prefetch paused")
                }
                PrefetchPowerMode.LOW_BATTERY_ECO -> {
                    _statusMessage.value = "Eco Mode active (${currentBatteryStatus.levelPercent}% Battery) • 3m Polling, 1km trigger"
                    updateNotification("Eco Mode: ${currentBatteryStatus.levelPercent}% Battery • 3m Polling")
                }
                PrefetchPowerMode.CHARGING_UNRESTRICTED -> {
                    _statusMessage.value = "Plugged in • High precision 5km vector prefetching"
                    updateNotification("Charging • Active 5km Vector Prefetch")
                }
                PrefetchPowerMode.OPTIMAL_WALKING -> {
                    _statusMessage.value = "Optimal Walking active • 5km Vector Corridor"
                    updateNotification("${newMode.badgeLabel} • ${_prefetchedCount.value} gems in Room")
                }
            }
        }
    }

    private fun reconfigureLocationTracking(mode: PrefetchPowerMode) {
        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return

        fusedLocationClient.removeLocationUpdates(locationCallback)

        val locationRequest = LocationRequest.Builder(
            if (mode == PrefetchPowerMode.LOW_BATTERY_ECO) Priority.PRIORITY_LOW_POWER else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            mode.locationIntervalMs
        ).setMinUpdateIntervalMillis((mode.locationIntervalMs / 2).coerceAtLeast(5_000L))
            .setMinUpdateDistanceMeters(mode.minUpdateDistanceMeters)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Reconfigured location updates for mode: ${mode.title} (Interval: ${mode.locationIntervalMs}ms, MinDistance: ${mode.minUpdateDistanceMeters}m)")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException reconfiguring location updates: ${e.message}")
        }
    }

    private fun startLocationTracking() {
        reconfigureLocationTracking(currentPowerMode)
    }

    private fun handleNewLocation(location: Location) {
        val speed = if (location.hasSpeed()) location.speed else 0f
        val speedKmh = speed * 3.6f
        _currentSpeedKmh.value = speedKmh

        // Dynamically re-evaluate policy against device battery and current movement velocity
        reEvaluatePolicy(speed)

        val breadcrumb = LocationBreadcrumb(
            latitude = location.latitude,
            longitude = location.longitude,
            speedMps = speed,
            bearingDegrees = if (location.hasBearing()) location.bearing else null,
            timestamp = System.currentTimeMillis()
        )

        synchronized(breadcrumbs) {
            breadcrumbs.add(breadcrumb)
            if (breadcrumbs.size > 20) {
                breadcrumbs.removeAt(0)
            }
        }

        val vector = MovementVectorUtils.calculateMovementVector(breadcrumbs)
        _activeVector.value = vector

        // If prefetching is paused by policy (e.g., vehicle speed > 25km/h), halt AI requests
        if (!currentPowerMode.isPrefetchAllowed) {
            _statusMessage.value = "Transit speed (${speedKmh.toInt()} km/h) • Prefetch paused until walking resumes"
            return
        }

        // Determine if movement is significant enough to warrant a new corridor prefetch based on active power policy
        val lastLat = lastPrefetchedOriginLat
        val lastLng = lastPrefetchedOriginLng
        val lastBearing = lastPrefetchedBearing

        val triggerDistance = currentPowerMode.triggerDisplacementMeters // 400m for Optimal, 1000m for Low Battery Eco

        val shouldFetch = if (lastLat == null || lastLng == null || lastBearing == null) {
            true
        } else {
            val dist = com.example.data.location.LocationTracker.distanceBetween(
                lastLat, lastLng, vector.originLat, vector.originLng
            )
            val bearingDelta = kotlin.math.abs(vector.bearingDegrees - lastBearing)
            val normalizedBearingDelta = if (bearingDelta > 180f) 360f - bearingDelta else bearingDelta

            dist > triggerDistance || (normalizedBearingDelta > 35f && dist > (triggerDistance * 0.4f))
        }

        if (shouldFetch) {
            serviceScope.launch {
                executePrefetchForVector(vector)
            }
        }
    }

    private suspend fun executePrefetchForVector(vector: MovementVector) = withContext(Dispatchers.IO) {
        _isPrefetching.value = true
        _statusMessage.value = "Prefetching gems 5km ${vector.cardinalDirection} (${vector.bearingDegrees.toInt()}°)..."
        updateNotification("Prefetching gems 5km ${vector.cardinalDirection}...")

        lastPrefetchedOriginLat = vector.originLat
        lastPrefetchedOriginLng = vector.originLng
        lastPrefetchedBearing = vector.bearingDegrees

        val result = geminiQueryService.queryCorridorGems(vector)
        result.fold(
            onSuccess = { spots ->
                database.scoutDao().insertSpots(spots)
                val totalPrefetched = database.scoutDao().getAllSpotsList()
                    .count { it.badgeLabel.contains("PREFETCH", ignoreCase = true) }

                _prefetchedCount.value = totalPrefetched
                _lastSyncTime.value = System.currentTimeMillis()
                _statusMessage.value = "Cached ${spots.size} gems along 5km ${vector.cardinalDirection} corridor"

                updateNotification(
                    "${totalPrefetched} gems cached 5km along your ${vector.cardinalDirection} trajectory"
                )
                Log.i(TAG, "Prefetch complete: inserted ${spots.size} gems along vector. Total prefetched in DB: $totalPrefetched")
            },
            onFailure = { err ->
                _statusMessage.value = "Prefetch standby: ${err.message ?: "Retrying on next movement"}"
                Log.w(TAG, "Prefetch query failed: ${err.message}")
            }
        )

        _isPrefetching.value = false
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Proximity Prefetch • [${currentPowerMode.badgeLabel}]")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Proximity Prefetch Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors user velocity vector and pre-caches POIs 5km ahead using Gemini AI"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopPrefetchService() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isServiceRunning.value = false
        _statusMessage.value = "Proximity prefetch stopped"
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Battery receiver unregister error: ${e.message}")
        }
        serviceScope.cancel()
        _isServiceRunning.value = false
    }

    companion object {
        const val ACTION_START = "com.example.service.action.START_PREFETCH"
        const val ACTION_STOP = "com.example.service.action.STOP_PREFETCH"
        const val ACTION_TRIGGER_PREFETCH = "com.example.service.action.TRIGGER_PREFETCH"

        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_BEARING = "extra_bearing"
        const val EXTRA_SPEED = "extra_speed"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _isPrefetching = MutableStateFlow(false)
        val isPrefetching: StateFlow<Boolean> = _isPrefetching.asStateFlow()

        private val _activeVector = MutableStateFlow<MovementVector?>(null)
        val activeVector: StateFlow<MovementVector?> = _activeVector.asStateFlow()

        private val _prefetchedCount = MutableStateFlow(0)
        val prefetchedCount: StateFlow<Int> = _prefetchedCount.asStateFlow()

        private val _lastSyncTime = MutableStateFlow(0L)
        val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

        private val _statusMessage = MutableStateFlow("Prefetch service ready")
        val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

        private val _activePowerMode = MutableStateFlow(PrefetchPowerMode.OPTIMAL_WALKING)
        val activePowerMode: StateFlow<PrefetchPowerMode> = _activePowerMode.asStateFlow()

        private val _batteryStatus = MutableStateFlow(BatteryStatus())
        val batteryStatus: StateFlow<BatteryStatus> = _batteryStatus.asStateFlow()

        private val _currentSpeedKmh = MutableStateFlow(0f)
        val currentSpeedKmh: StateFlow<Float> = _currentSpeedKmh.asStateFlow()

        fun start(context: Context, lat: Double = 38.7112, lng: Double = -9.1423, bearing: Float = 45f) {
            val intent = Intent(context, ProximityPrefetchService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
                putExtra(EXTRA_BEARING, bearing)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProximityPrefetchService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun triggerManualPrefetch(context: Context, lat: Double, lng: Double, bearing: Float) {
            val intent = Intent(context, ProximityPrefetchService::class.java).apply {
                action = ACTION_TRIGGER_PREFETCH
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
                putExtra(EXTRA_BEARING, bearing)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
