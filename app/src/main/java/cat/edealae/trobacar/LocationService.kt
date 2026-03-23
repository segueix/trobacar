package cat.edealae.trobacar

import android.Manifest
import android.annotation.SuppressLint
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
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class LocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "TrobaCarLocationChannel"
    private var isBluetoothReceiverRegistered = false
    private var isRunningInForeground = false

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
                        CrashLogger.log(this@LocationService, "BT", "Bluetooth connectat: $deviceName")
                        prefs.edit().putBoolean("bluetooth_car_connected", true).apply()
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (deviceName == savedName) {
                        CrashLogger.log(this@LocationService, "BT", "Bluetooth desconnectat: $deviceName - guardant ubicació")
                        prefs.edit().putBoolean("bluetooth_car_connected", false).apply()
                        saveCurrentLocation()
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
        CrashLogger.log(this, "SERVICE", "LocationService onCreate completat")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CrashLogger.log(this, "SERVICE", "onStartCommand cridat")

        if (!hasLocationPermission()) {
            CrashLogger.log(this, "SERVICE", "Aturant servei: sense permís de localització")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!ensureForegroundStarted()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startLocationUpdates()
        return START_STICKY
    }


    private fun ensureForegroundStarted(): Boolean {
        if (isRunningInForeground) return true

        return try {
            startForeground(NOTIFICATION_ID, createNotification())
            isRunningInForeground = true
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

    @SuppressLint("MissingPermission")
    private fun saveCurrentLocation() {
        if (!hasLocationPermission()) return

        try {
            val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                CrashLogger.log(this, "SERVICE", "Guardant ubicació: ${location.latitude}, ${location.longitude}")
                val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
                prefs.edit()
                    .putFloat("saved_latitude", location.latitude.toFloat())
                    .putFloat("saved_longitude", location.longitude.toFloat())
                    .putLong("saved_timestamp", System.currentTimeMillis())
                    .putString("saved_method", "Bluetooth")
                    .putString("location_name", getString(R.string.current_parking))
                    .apply()

                LocationHistory.addLocation(this, location.latitude, location.longitude, "Bluetooth")
            } else {
                CrashLogger.log(this, "SERVICE", "saveCurrentLocation: no hi ha ubicació disponible")
            }
        } catch (e: SecurityException) {
            CrashLogger.logError(this, "SERVICE", "SecurityException a saveCurrentLocation", e)
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
        super.onDestroy()
        isRunningInForeground = false
        locationManager.removeUpdates(this)
        if (isBluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver)
            isBluetoothReceiverRegistered = false
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            CrashLogger.log(this, "SERVICE", "No es reinicia el servei en segon pla a Android 12+")
            super.onTaskRemoved(rootIntent)
            return
        }

        val restartIntent = Intent(applicationContext, LocationService::class.java).apply {
            `package` = packageName
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restartIntent)
            } else {
                @Suppress("DEPRECATION")
                applicationContext.startService(restartIntent)
            }
        } catch (exception: RuntimeException) {
            CrashLogger.logError(this, "SERVICE", "No s'ha pogut reiniciar el servei", exception)
        }

        super.onTaskRemoved(rootIntent)
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
