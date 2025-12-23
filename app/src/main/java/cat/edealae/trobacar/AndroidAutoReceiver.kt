package cat.edealae.trobacar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.widget.Toast

class AndroidAutoReceiver : BroadcastReceiver() {

    companion object {
        // Estat de connexió d'Android Auto (constants d'Android)
        const val CAR_CONNECTION_STATE = "CarConnectionState"
        const val CONNECTION_TYPE_PROJECTION = 1
        const val STATE_CONNECTED = "connected"
        const val STATE_DISCONNECTED = "disconnected"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        if (action == "android.car.action.CAR_CONNECTION_STATUS") {
            handleCarConnection(context, intent)
        }
    }

    private fun handleCarConnection(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        
        // Obtenir l'estat de connexió
        val carConnectionState = intent.getStringExtra(CAR_CONNECTION_STATE)
        val connectionType = intent.getIntExtra("connection_type", -1)

        when (carConnectionState) {
            STATE_CONNECTED -> {
                if (connectionType == CONNECTION_TYPE_PROJECTION) {
                    // Connectat a Android Auto
                    prefs.edit().apply {
                        putBoolean("android_auto_connected", true)
                        // Esborrar ubicació anterior
                        putFloat("saved_latitude", 0f)
                        putFloat("saved_longitude", 0f)
                        apply()
                    }
                    Toast.makeText(context, "Connectat a Android Auto", Toast.LENGTH_SHORT).show()
                }
            }
            
            STATE_DISCONNECTED -> {
                // Desconnectat d'Android Auto
                prefs.edit().putBoolean("android_auto_connected", false).apply()
                
                // Guardar ubicació actual
                saveCurrentLocation(context)
                Toast.makeText(context, "Ubicació del cotxe guardada", Toast.LENGTH_LONG).show()
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
                prefs.edit().apply {
                    putFloat("saved_latitude", location.latitude.toFloat())
                    putFloat("saved_longitude", location.longitude.toFloat())
                    putLong("saved_timestamp", System.currentTimeMillis())
                    putString("saved_method", "Android Auto")
                    putString("location_name", "Aparcament actual") // Nom per defecte per pantalla principal
                    apply()
                }
                
                // Afegir a l'historial (nom "Aparcament" per defecte a l'historial)
                LocationHistory.addLocation(context, location.latitude, location.longitude, "Android Auto")
                
                // Notificació amb data i hora
                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val time = sdf.format(java.util.Date())
                Toast.makeText(context, "✓ Ubicació guardada (Android Auto) - $time", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "No s'ha pogut obtenir la ubicació", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
