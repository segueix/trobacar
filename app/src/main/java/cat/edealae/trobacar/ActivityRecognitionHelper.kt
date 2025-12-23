package cat.edealae.trobacar

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

object ActivityRecognitionHelper {
    
    fun startActivityRecognition(context: Context) {
        val transitions = mutableListOf<ActivityTransition>()
        
        // Detectar quan ENTRES al vehicle
        transitions.add(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        )
        
        // Detectar quan SURTS del vehicle
        transitions.add(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build()
        )
        
        val request = ActivityTransitionRequest(transitions)
        
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        try {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(request, pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun stopActivityRecognition(context: Context) {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        try {
            ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class ActivityTransitionReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        if (com.google.android.gms.location.ActivityTransitionResult.hasResult(intent)) {
            val result = com.google.android.gms.location.ActivityTransitionResult.extractResult(intent!!)
            
            result?.transitionEvents?.forEach { event ->
                val prefs = context.getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
                
                when (event.activityType) {
                    DetectedActivity.IN_VEHICLE -> {
                        when (event.transitionType) {
                            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                                // Has entrat al cotxe
                                prefs.edit().putBoolean("in_vehicle", true).apply()
                            }
                            
                            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                                // Has sortit del cotxe
                                prefs.edit().putBoolean("in_vehicle", false).apply()
                                
                                // Comprovar si el Bluetooth del cotxe està connectat
                                val bluetoothConnected = prefs.getBoolean("bluetooth_connected", false)
                                
                                // NO guardar localització per moviment si el Bluetooth està connectat
                                // (deixar que sigui el Bluetooth qui gestioni el guardat)
                                if (!bluetoothConnected) {
                                    // Esperar 10 segons abans de guardar (per evitar falsos positius)
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        // Comprovar de nou per si s'ha connectat durant l'espera
                                        val stillNotConnected = !prefs.getBoolean("bluetooth_connected", false)
                                        if (stillNotConnected) {
                                            saveCurrentLocation(context, "Activity")
                                            Toast.makeText(context, "Ubicació guardada (Activity)", Toast.LENGTH_LONG).show()
                                        }
                                    }, 10000)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun saveCurrentLocation(context: Context, method: String) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                val prefs = context.getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
                
                // Guardar com a ubicació actual
                prefs.edit().apply {
                    putFloat("saved_latitude", location.latitude.toFloat())
                    putFloat("saved_longitude", location.longitude.toFloat())
                    putLong("saved_timestamp", System.currentTimeMillis())
                    putString("saved_method", method)
                    putString("location_name", "Aparcament actual") // Nom per defecte per pantalla principal
                    apply()
                }
                
                // Afegir a l'historial (nom "Aparcament" per defecte a l'historial)
                LocationHistory.addLocation(context, location.latitude, location.longitude, method)
                
                // Notificació amb data i hora
                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val time = sdf.format(java.util.Date())
                Toast.makeText(context, "✓ Ubicació guardada ($method) - $time", Toast.LENGTH_LONG).show()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
