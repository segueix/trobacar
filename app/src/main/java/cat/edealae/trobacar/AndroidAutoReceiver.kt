package cat.edealae.trobacar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager

class AndroidAutoReceiver : BroadcastReceiver() {

    companion object {
        const val CAR_CONNECTION_STATE = "CarConnectionState"
        const val CONNECTION_TYPE_PROJECTION = 1
        const val STATE_CONNECTED = "connected"
        const val STATE_DISCONNECTED = "disconnected"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == "android.car.action.CAR_CONNECTION_STATUS") {
            handleCarConnection(context, intent)
        }
    }

    private fun handleCarConnection(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val carConnectionState = intent.getStringExtra(CAR_CONNECTION_STATE)
        val connectionType = intent.getIntExtra("connection_type", -1)

        when (carConnectionState) {
            STATE_CONNECTED -> {
                if (connectionType == CONNECTION_TYPE_PROJECTION) {
                    prefs.edit()
                        .putBoolean("bluetooth_car_connected", true)
                        .apply()
                }
            }

            STATE_DISCONNECTED -> {
                prefs.edit().putBoolean("bluetooth_car_connected", false).apply()
                saveCurrentLocation(context)
            }
        }
    }

    private fun saveCurrentLocation(context: Context) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                val prefs = context.getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
                prefs.edit()
                    .putFloat("saved_latitude", location.latitude.toFloat())
                    .putFloat("saved_longitude", location.longitude.toFloat())
                    .putLong("saved_timestamp", System.currentTimeMillis())
                    .putString("saved_method", "Android Auto")
                    .putString("location_name", context.getString(R.string.current_parking))
                    .apply()

                LocationHistory.addLocation(context, location.latitude, location.longitude, "Android Auto")
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
