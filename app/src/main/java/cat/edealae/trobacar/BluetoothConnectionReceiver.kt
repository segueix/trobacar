package cat.edealae.trobacar

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

/**
 * Receiver declarat al manifest que actua de xarxa de seguretat: si el cotxe es
 * connecta o desconnecta mentre el [LocationService] està aturat, desperta el
 * servei i li reenvia l'esdeveniment perquè el processi. Mentre el servei viu,
 * el seu receiver dinàmic ja gestiona l'esdeveniment i la deduplicació evita
 * processar-lo dues vegades.
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED &&
            action != BluetoothDevice.ACTION_ACL_DISCONNECTED
        ) {
            return
        }

        // Sense permís de localització no podem fer res d'útil amb l'esdeveniment.
        if (!LocationPermissionHelper.hasLocationPermission(context)) return

        val prefs = context.getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val savedName = prefs.getString("default_bluetooth_device_name", null)
        // Si l'usuari no ha configurat cap cotxe, no cal despertar res.
        if (savedName.isNullOrEmpty()) return

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val deviceName = if (hasBluetoothConnectPermission(context)) device?.name else null

        // Si podem llegir el nom i no és el cotxe desat, no despertem el servei.
        if (deviceName != null && deviceName != savedName) return

        CrashLogger.log(context, "BT", "Receiver del manifest: action=$action, device=$deviceName")

        try {
            LocationService.startService(context, "bt:$action", action, deviceName)
        } catch (e: RuntimeException) {
            CrashLogger.logError(context, "BT", "No s'ha pogut despertar el servei des del receiver del manifest", e)
        }
    }

    private fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
