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
                    if (!LocationPermissionHelper.hasLocationPermission(context)) {
                        CrashLogger.log(context, "BOOT", "No s'inicia el servei: falta permís de localització")
                        return
                    }

                    CrashLogger.log(
                        context,
                        "BOOT",
                        "Esdeveniment ${intent.action} - Android no permet obrir la pantalla principal automàticament; iniciant només el servei en segon pla"
                    )
                    LocationService.startService(context, "boot:${intent.action}")
                } catch (e: RuntimeException) {
                    CrashLogger.logError(context, "BOOT", "Android ha bloquejat l'inici en segon pla després de ${intent.action}", e)
                } catch (e: Exception) {
                    CrashLogger.logError(context, "BOOT", "Error iniciant servei després de ${intent.action}", e)
                }
            }
        }
    }
}
