package cat.edealae.trobacar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RESTART_LOCATION_SERVICE = "cat.edealae.trobacar.action.RESTART_LOCATION_SERVICE"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != ACTION_RESTART_LOCATION_SERVICE) return

        val reason = intent.getStringExtra("restart_reason") ?: "alarm"
        CrashLogger.log(context, "SERVICE", "Receiver de reinici activat: $reason")
        LocationService.startService(context, "restart:$reason")
    }
}
