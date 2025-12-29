package cat.edealae.trobacar

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.widget.Toast

class BluetoothReceiver : BroadcastReceiver() {

    companion object {
        const val CAR_BLUETOOTH_NAME = "MYCAR"
        private const val CAR_BLUETOOTH_PREF_KEY = "car_bluetooth_name"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        
        if (device == null) return

        val deviceName = device.name ?: return
        val prefs = context.getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val carBluetoothName = prefs.getString(CAR_BLUETOOTH_PREF_KEY, null)?.trim()

        // Només processar si és el nostre cotxe
        val targetName = if (!carBluetoothName.isNullOrEmpty()) carBluetoothName else CAR_BLUETOOTH_NAME
        if (deviceName == targetName) {
            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    // Connectat al Bluetooth del cotxe
                    prefs.edit().putBoolean("bluetooth_connected", true).apply()
                    Toast.makeText(context, "Connectat a $targetName", Toast.LENGTH_SHORT).show()
                }
                
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    // Desconnectat del Bluetooth del cotxe
                    prefs.edit().putBoolean("bluetooth_connected", false).apply()
                    
                    // Guardar ubicació
                    saveCurrentLocation(context, "Bluetooth")
                    Toast.makeText(context, "Ubicació guardada (Bluetooth)", Toast.LENGTH_LONG).show()
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
                
                // Afegir a l'historial amb el mètode (nom "Aparcament" per defecte a l'historial)
                LocationHistory.addLocation(context, location.latitude, location.longitude, method)
                
                // Notificació amb data i hora
                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val time = sdf.format(java.util.Date())
                Toast.makeText(context, "✓ Ubicació guardada ($method) - $time", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "No s'ha pogut obtenir la ubicació", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
