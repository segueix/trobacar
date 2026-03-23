package cat.edealae.trobacar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    CrashLogger.log(context, "BOOT", "S'omet l'inici automàtic del servei al boot a Android 12+")
                    return
                }

                CrashLogger.log(context, "BOOT", "Boot completat - iniciant servei")
                val serviceIntent = Intent(context, LocationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                CrashLogger.log(context, "BOOT", "Servei iniciat correctament")
            } catch (e: Exception) {
                CrashLogger.logError(context, "BOOT", "Error iniciant servei al boot", e)
            }
        }
    }
}
