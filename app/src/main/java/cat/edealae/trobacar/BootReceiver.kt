package cat.edealae.trobacar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                try {
                    CrashLogger.log(context, "BOOT", "Esdeveniment ${intent.action} - iniciant servei en segon pla")
                    LocationService.startService(context, "boot:${intent.action}")
                } catch (e: Exception) {
                    CrashLogger.logError(context, "BOOT", "Error iniciant servei després de ${intent.action}", e)
                }
            }
        }
    }
}
