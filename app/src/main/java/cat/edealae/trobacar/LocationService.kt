package cat.edealae.trobacar

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class LocationService : Service(), LocationListener {

    companion object {
        private const val RESTART_REQUEST_CODE = 1001
        private const val DISCONNECT_DEBOUNCE_MS = 15_000L
        private const val MIN_SAVE_INTERVAL_MS = 2 * 60 * 1000L
        private const val SERVICE_CHANNEL_ID = "TrobaCarLocationChannel"
        private const val STATUS_CHANNEL_ID = "TrobaCarStatusChannel"
        private const val NOTIFICATION_ID_FOREGROUND = 1
        private const val NOTIFICATION_ID_BT_CONNECTED = 2
        private const val NOTIFICATION_ID_BT_DISCONNECTED = 3
        private const val MAX_LOCATION_AGE_MS = 2 * 60 * 1000L
        private const val MAX_LOCATION_ACCURACY_METERS = 60f
        private const val FALLBACK_LOCATION_AGE_MS = 5 * 60 * 1000L
        private const val FALLBACK_LOCATION_ACCURACY_METERS = 150f
        private const val FRESH_FIX_TIMEOUT_MS = 10_000L

        fun startService(context: Context, reason: String) {
            val intent = Intent(context, LocationService::class.java).apply {
                putExtra("start_reason", reason)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    @Suppress("DEPRECATION")
                    context.startService(intent)
                }
                CrashLogger.log(context, "SERVICE", "Sol·licitat inici del servei: $reason")
            } catch (e: RuntimeException) {
                CrashLogger.logError(context, "SERVICE", "No s'ha pogut iniciar el servei: $reason", e)
            }
        }
    }

    private lateinit var locationManager: LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isBluetoothReceiverRegistered = false
    private var isRunningInForeground = false
    private var shouldRestartService = false

    private val delayedDisconnectSaves = mutableMapOf<String, Runnable>()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            val deviceName = if (hasBluetoothConnectPermission()) device?.name else null
            val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
            val savedName = prefs.getString("default_bluetooth_device_name", null)

            CrashLogger.log(this@LocationService, "BT", "Event BT: action=${intent.action}, device=$deviceName, saved=$savedName")

            if (savedName.isNullOrEmpty() || deviceName == null) return

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (deviceName == savedName) {
                        cancelPendingDisconnectSave(deviceName, "reconnexió detectada")
                        CrashLogger.log(this@LocationService, "BT", "Bluetooth connectat: $deviceName")
                        prefs.edit().putBoolean("bluetooth_car_connected", true).apply()
                        showBluetoothStatusNotification(deviceName, true)
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (deviceName == savedName) {
                        CrashLogger.log(this@LocationService, "BT", "Bluetooth desconnectat: $deviceName - esperant debounce")
                        prefs.edit().putBoolean("bluetooth_car_connected", false).apply()
                        showBluetoothStatusNotification(deviceName, false)
                        scheduleDisconnectSave(deviceName)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        CrashLogger.log(this, "SERVICE", "LocationService onCreate iniciat")
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannels()
        registerBluetoothReceiver()
        cancelScheduledServiceRestart("servei creat")
        CrashLogger.log(this, "SERVICE", "LocationService onCreate completat")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra("start_reason") ?: "sense motiu"
        CrashLogger.log(this, "SERVICE", "onStartCommand cridat ($reason)")

        if (!hasLocationPermission()) {
            shouldRestartService = false
            CrashLogger.log(this, "SERVICE", "Aturant servei: sense permís de localització")
            stopSelf()
            return START_STICKY
        }

        if (!ensureForegroundStarted()) {
            scheduleServiceRestart("foreground no disponible")
            stopSelf()
            return START_STICKY
        }

        startLocationUpdates()
        return START_STICKY
    }

    private fun ensureForegroundStarted(): Boolean {
        if (isRunningInForeground) return true

        return try {
            startForeground(NOTIFICATION_ID_FOREGROUND, createForegroundNotification())
            isRunningInForeground = true
            shouldRestartService = true
            true
        } catch (e: SecurityException) {
            CrashLogger.logError(this, "SERVICE", "No es pot iniciar el foreground service per permisos/estat", e)
            false
        } catch (e: RuntimeException) {
            CrashLogger.logError(this, "SERVICE", "No es pot iniciar el foreground service ara mateix", e)
            false
        }
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                10f,
                this
            )

            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                5000L,
                10f,
                this
            )
        } catch (e: SecurityException) {
            CrashLogger.logError(this, "SERVICE", "SecurityException a startLocationUpdates", e)
        } catch (e: IllegalArgumentException) {
            CrashLogger.logError(this, "SERVICE", "Provider de localització no disponible", e)
        }
    }

    override fun onLocationChanged(location: Location) {
        // Location is tracked continuously; saved only when Bluetooth car disconnects
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // Deprecated callback kept for backward compatibility
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerBluetoothReceiver() {
        if (isBluetoothReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
        }
        isBluetoothReceiverRegistered = true
    }

    private fun scheduleDisconnectSave(deviceName: String) {
        cancelPendingDisconnectSave(deviceName, "nou esdeveniment de desconnexió")
        val saveRunnable = Runnable {
            delayedDisconnectSaves.remove(deviceName)
            CrashLogger.log(this, "BT", "Debounce completat per $deviceName - intentant guardar ubicació")
            saveCurrentLocation()
        }
        delayedDisconnectSaves[deviceName] = saveRunnable
        mainHandler.postDelayed(saveRunnable, DISCONNECT_DEBOUNCE_MS)
    }

    private fun cancelPendingDisconnectSave(deviceName: String, reason: String) {
        val runnable = delayedDisconnectSaves.remove(deviceName) ?: return
        mainHandler.removeCallbacks(runnable)
        CrashLogger.log(this, "BT", "Cancel·lat guardat pendent de $deviceName: $reason")
    }

    @SuppressLint("MissingPermission")
    private fun saveCurrentLocation() {
        if (!hasLocationPermission()) return

        try {
            val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
            val lastSavedTimestamp = prefs.getLong("saved_timestamp", 0L)
            val now = System.currentTimeMillis()
            if (now - lastSavedTimestamp < MIN_SAVE_INTERVAL_MS) {
                CrashLogger.log(this, "SERVICE", "Guardat omès: ja s'ha desat una ubicació fa menys de 2 minuts")
                return
            }

            resolveBestLocationForSave { location ->
                if (location != null) {
                    persistLocation(location, now)
                } else {
                    CrashLogger.log(this, "SERVICE", "saveCurrentLocation: no hi ha ubicació prou bona disponible")
                }
            }
        } catch (e: SecurityException) {
            CrashLogger.logError(this, "SERVICE", "SecurityException a saveCurrentLocation", e)
        } catch (e: IllegalArgumentException) {
            CrashLogger.logError(this, "SERVICE", "Provider de localització no disponible a saveCurrentLocation", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveBestLocationForSave(callback: (Location?) -> Unit) {
        val gpsLocation = getLastKnownLocationSafely(LocationManager.GPS_PROVIDER)
        val networkLocation = getLastKnownLocationSafely(LocationManager.NETWORK_PROVIDER)
        val bestLastKnown = selectBestLocation(listOfNotNull(gpsLocation, networkLocation))

        if (bestLastKnown != null && isLocationGoodEnough(bestLastKnown)) {
            CrashLogger.log(this, "SERVICE", "S'utilitza lastKnownLocation vàlida (${bestLastKnown.provider})")
            callback(bestLastKnown)
            return
        }

        CrashLogger.log(this, "SERVICE", "Cercant un fix més recent després de la desconnexió")
        requestFreshLocationFix(bestLastKnown, callback)
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocationFix(fallback: Location?, callback: (Location?) -> Unit) {
        val candidateProviders = buildList {
            if (isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }

        if (candidateProviders.isEmpty()) {
            callback(fallback?.takeIf(::isLocationFallbackUsable))
            return
        }

        var completed = false
        var bestLocation: Location? = fallback?.takeIf(::isLocationFallbackUsable)
        lateinit var freshLocationListener: LocationListener

        fun finish(result: Location?) {
            if (completed) return
            completed = true
            mainHandler.removeCallbacksAndMessages(freshLocationListener)
            try {
                locationManager.removeUpdates(freshLocationListener)
            } catch (_: Exception) {
            }
            callback(result)
        }

        val timeoutRunnable = Runnable {
            val timeoutResult = bestLocation?.takeIf(::isLocationFallbackUsable)
            if (timeoutResult != null) {
                CrashLogger.log(this, "SERVICE", "Timeout obtenint fix recent; s'utilitza el millor candidat disponible")
            } else {
                CrashLogger.log(this, "SERVICE", "Timeout obtenint fix recent i sense candidat prou fiable")
            }
            finish(timeoutResult)
        }

        freshLocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (bestLocation == null || isBetterLocation(location, bestLocation)) {
                    bestLocation = location
                }
                if (isLocationGoodEnough(location)) {
                    CrashLogger.log(this@LocationService, "SERVICE", "Fix recent acceptat (${location.provider}, ${location.accuracy}m)")
                    finish(location)
                }
            }

            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        candidateProviders.forEach { provider ->
            try {
                locationManager.requestLocationUpdates(provider, 0L, 0f, freshLocationListener, Looper.getMainLooper())
            } catch (e: Exception) {
                CrashLogger.logError(this, "SERVICE", "No s'ha pogut demanar un fix recent de $provider", e)
            }
        }
        mainHandler.postAtTime(timeoutRunnable, freshLocationListener, System.currentTimeMillis() + FRESH_FIX_TIMEOUT_MS)
    }

    private fun getLastKnownLocationSafely(provider: String): Location? {
        return try {
            locationManager.getLastKnownLocation(provider)
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun persistLocation(location: Location, timestamp: Long) {
        CrashLogger.log(this, "SERVICE", "Guardant ubicació: ${location.latitude}, ${location.longitude} (${location.provider}, ${location.accuracy}m)")
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("saved_latitude", location.latitude.toFloat())
            .putFloat("saved_longitude", location.longitude.toFloat())
            .putLong("saved_timestamp", timestamp)
            .putString("saved_method", "Bluetooth")
            .putString("location_name", getString(R.string.current_parking))
            .apply()

        LocationHistory.addLocation(this, location.latitude, location.longitude, "Bluetooth")
    }

    private fun selectBestLocation(locations: List<Location>): Location? {
        return locations.maxByOrNull { locationScore(it) }
    }

    private fun locationScore(location: Location): Double {
        val agePenalty = locationAgeMs(location) / 1000.0
        val accuracyPenalty = if (location.hasAccuracy()) location.accuracy.toDouble() else 25.0
        val gpsBonus = if (location.provider == LocationManager.GPS_PROVIDER) 30.0 else 0.0
        return gpsBonus - agePenalty - accuracyPenalty
    }

    private fun isBetterLocation(candidate: Location, currentBest: Location?): Boolean {
        if (currentBest == null) return true
        return locationScore(candidate) > locationScore(currentBest)
    }

    private fun isLocationGoodEnough(location: Location): Boolean {
        val age = locationAgeMs(location)
        val accuracy = if (location.hasAccuracy()) location.accuracy else MAX_LOCATION_ACCURACY_METERS
        return age in 0..MAX_LOCATION_AGE_MS && accuracy <= MAX_LOCATION_ACCURACY_METERS
    }

    private fun isLocationFallbackUsable(location: Location): Boolean {
        val age = locationAgeMs(location)
        val accuracy = if (location.hasAccuracy()) location.accuracy else FALLBACK_LOCATION_ACCURACY_METERS
        return age in 0..FALLBACK_LOCATION_AGE_MS && accuracy <= FALLBACK_LOCATION_ACCURACY_METERS
    }

    private fun locationAgeMs(location: Location): Long {
        return System.currentTimeMillis() - location.time
    }

    private fun isProviderEnabled(provider: String): Boolean {
        return try {
            locationManager.isProviderEnabled(provider)
        } catch (_: Exception) {
            false
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "TrobaCar Localització",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servei per detectar la ubicació del cotxe"
            }

            val statusChannel = NotificationChannel(
                STATUS_CHANNEL_ID,
                "TrobaCar Bluetooth",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificacions de connexió i desconnexió del Bluetooth del cotxe"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(statusChannel)
        }
    }

    private fun createForegroundNotification() = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
        .setContentTitle("TrobaCar actiu")
        .setContentText("Esperant desconnexió del Bluetooth del cotxe")
        .setSmallIcon(R.drawable.ic_notification)
        .setContentIntent(createMainActivityPendingIntent())
        .setOngoing(true)
        .build()

    private fun showBluetoothStatusNotification(deviceName: String, connected: Boolean) {
        if (!canPostNotifications()) return

        val titleRes = if (connected) R.string.bluetooth_notification_connected_title else R.string.bluetooth_notification_disconnected_title
        val textRes = if (connected) R.string.bluetooth_notification_connected_text else R.string.bluetooth_notification_disconnected_text
        val notificationId = if (connected) NOTIFICATION_ID_BT_CONNECTED else NOTIFICATION_ID_BT_DISCONNECTED

        val notification = NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(titleRes))
            .setContentText(getString(textRes, deviceName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMainActivityPendingIntent())
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    private fun createMainActivityPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onDestroy() {
        CrashLogger.log(this, "SERVICE", "LocationService onDestroy")
        cancelAllPendingDisconnectSaves()
        super.onDestroy()
        isRunningInForeground = false
        try {
            locationManager.removeUpdates(this)
        } catch (_: Exception) {
        }
        if (isBluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver)
            isBluetoothReceiverRegistered = false
        }
        if (shouldRestartService) {
            scheduleServiceRestart("onDestroy")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (shouldRestartService) {
            scheduleServiceRestart("task removed")
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun scheduleServiceRestart(reason: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val restartIntent = Intent(this, ServiceRestartReceiver::class.java).apply {
            action = ServiceRestartReceiver.ACTION_RESTART_LOCATION_SERVICE
            putExtra("restart_reason", reason)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            RESTART_REQUEST_CODE,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAtMillis = System.currentTimeMillis() + 10_000L
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        CrashLogger.log(this, "SERVICE", "Reinici del servei programat en 10s: $reason")
    }

    private fun cancelScheduledServiceRestart(reason: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val restartIntent = Intent(this, ServiceRestartReceiver::class.java).apply {
            action = ServiceRestartReceiver.ACTION_RESTART_LOCATION_SERVICE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            RESTART_REQUEST_CODE,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        CrashLogger.log(this, "SERVICE", "Reinici programat cancel·lat: $reason")
    }

    private fun cancelAllPendingDisconnectSaves() {
        delayedDisconnectSaves.values.forEach(mainHandler::removeCallbacks)
        delayedDisconnectSaves.clear()
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
