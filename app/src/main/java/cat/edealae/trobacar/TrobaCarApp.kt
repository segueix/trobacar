package cat.edealae.trobacar

import android.app.Application

class TrobaCarApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Instal·la un handler global per capturar crashes fatals
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLogger.logCrash(this, thread, throwable)
            // Delega al handler per defecte perquè Android mostri el diàleg de crash
            defaultHandler?.uncaughtException(thread, throwable)
        }

        CrashLogger.log(this, "APP", "Aplicació iniciada")
    }
}
