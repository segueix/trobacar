package cat.edealae.trobacar

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Escriu errors i events importants a un fitxer de log intern de l'app.
 * El fitxer es pot consultar des de la pantalla principal per diagnosticar problemes.
 */
object CrashLogger {

    private const val LOG_FILE = "trobacar_errors.log"
    private const val MAX_LOG_SIZE = 256 * 1024 // 256 KB max

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun log(context: Context, tag: String, message: String) {
        try {
            val file = getLogFile(context)
            trimIfNeeded(file)
            val timestamp = dateFormat.format(Date())
            file.appendText("[$timestamp] [$tag] $message\n")
        } catch (_: Exception) {
            // No podem fer res si el logging falla
        }
    }

    fun logError(context: Context, tag: String, message: String, throwable: Throwable) {
        try {
            val file = getLogFile(context)
            trimIfNeeded(file)
            val timestamp = dateFormat.format(Date())
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            file.appendText("[$timestamp] [$tag] ERROR: $message\n$sw\n")
        } catch (_: Exception) {
            // No podem fer res si el logging falla
        }
    }

    fun logCrash(context: Context, thread: Thread, throwable: Throwable) {
        try {
            val file = getLogFile(context)
            trimIfNeeded(file)
            val timestamp = dateFormat.format(Date())
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            file.appendText(
                "[$timestamp] [CRASH] *** CRASH FATAL al thread '${thread.name}' ***\n$sw\n" +
                "---\n"
            )
        } catch (_: Exception) {
            // No podem fer res si el logging falla
        }
    }

    fun readLog(context: Context): String {
        return try {
            val file = getLogFile(context)
            if (file.exists()) file.readText() else "(Sense errors registrats)"
        } catch (_: Exception) {
            "(Error llegint el fitxer de log)"
        }
    }

    fun clearLog(context: Context) {
        try {
            val file = getLogFile(context)
            if (file.exists()) file.writeText("")
        } catch (_: Exception) {
            // Ignora
        }
    }

    private fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE)
    }

    private fun trimIfNeeded(file: File) {
        if (file.exists() && file.length() > MAX_LOG_SIZE) {
            // Manté només l'última meitat del fitxer
            val content = file.readText()
            val half = content.length / 2
            val trimmed = content.substring(half)
            file.writeText("...(log truncat)...\n$trimmed")
        }
    }
}
