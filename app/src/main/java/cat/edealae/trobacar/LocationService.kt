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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class LocationService : Service(), LocationListener {

    companion object {
        private const val RESTART_REQUEST_CODE = 1001
        private const val DISCONNECT_DEBOUNCE_MS = 15_000L
        private const val MIN_SAVE_INTERVAL_MS = 2 * 60 * 1000L

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
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "TrobaCarLocationChannel"
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
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (deviceName == savedName) {
                        CrashLogger.log(this@LocationService, "BT", "Bluetooth desconnectat: $deviceName - esperant debounce")
                        prefs.edit().putBoolean("bluetooth_car_connected", false).apply()
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
        createNotificationChannel()
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
            startForeground(NOTIFICATION_ID, createNotification())
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

            val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                CrashLogger.log(this, "SERVICE", "Guardant ubicació: ${location.latitude}, ${location.longitude}")
                prefs.edit()
                    .putFloat("saved_latitude", location.latitude.toFloat())
                    .putFloat("saved_longitude", location.longitude.toFloat())
                    .putLong("saved_timestamp", now)
                    .putString("saved_method", "Bluetooth")
                    .putString("location_name", getString(R.string.current_parking))
                    .apply()

                LocationHistory.addLocation(this, location.latitude, location.longitude, "Bluetooth")
            } else {
                CrashLogger.log(this, "SERVICE", "saveCurrentLocation: no hi ha ubicació disponible")
            }
        } catch (e: SecurityException) {
            CrashLogger.logError(this, "SERVICE", "SecurityException a saveCurrentLocation", e)
        } catch (e: IllegalArgumentException) {
            CrashLogger.logError(this, "SERVICE", "Provider de localització no disponible a saveCurrentLocation", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TrobaCar Localització",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servei per detectar la ubicació del cotxe"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("TrobaCar actiu")
        .setContentText("Esperant desconnexió del Bluetooth del cotxe")
        .setSmallIcon(R.drawable.ic_notification)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setOngoing(true)
        .build()

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
}
