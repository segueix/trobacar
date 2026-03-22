package cat.edealae.trobacar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            // Sense permisos no podem continuar; esperarà a que l'Activity els demani de nou
            return
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L, // Actualitzar cada 5 segons
                10f,   // O cada 10 metres
                this
            )

            // Fallback a xarxa per mantenir l'actualització quan el GPS no està disponible
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                5000L,
                10f,
                this
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        // La ubicació actual està disponible però només es guarda quan es desconnecta d'Android Auto
        // Això es gestiona al AndroidAutoReceiver
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        .setContentText("Esperant desconnexió d'Android Auto")
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
        super.onDestroy()
        locationManager.removeUpdates(this)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // En Android recents, aquest reinici pot llençar excepcions de restricció de FGS.
        // El servei ja retorna START_STICKY a onStartCommand, així que evitem un crash extra.
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
            // Ignorar: Android 12+ pot bloquejar aquest reinici quan l'usuari tanca l'app.
        }

        super.onTaskRemoved(rootIntent)
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }
}
