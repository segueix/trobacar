package cat.edealae.trobacar

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class LocationEntry(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val method: String = "Manual", // Bluetooth, Activity, Android Auto, Manual
    var customName: String = "Aparcament" // Nom personalitzat
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun getFormattedLocation(): String {
        return "Lat: ${String.format("%.6f", latitude)}\nLon: ${String.format("%.6f", longitude)}"
    }
    
    fun getMethodIcon(): String {
        return when(method) {
            "Bluetooth" -> "🔵"
            "Activity" -> "🚶"
            "Android Auto" -> "🚗"
            else -> "📍"
        }
    }
}

object LocationHistory {
    private const val PREFS_NAME = "TrobaCar"
    private const val HISTORY_KEY = "location_history"
    private const val MAX_HISTORY = 50
    private val gson = Gson()
    
    fun addLocation(context: Context, latitude: Double, longitude: Double, method: String = "Manual") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val history = getHistory(context).toMutableList()
        
        // Comprovar si hi ha una ubicació recent molt propera (últims 10 minuts, menys de 50 metres)
        val currentTime = System.currentTimeMillis()
        val recentLocation = history.firstOrNull { 
            (currentTime - it.timestamp) < 10 * 60 * 1000 // 10 minuts
        }
        
        if (recentLocation != null) {
            val distance = calculateDistance(
                latitude, longitude,
                recentLocation.latitude, recentLocation.longitude
            )
            
            // Si està a menys de 50 metres d'una ubicació recent, no afegir duplicat
            if (distance < 50) {
                return
            }
        }
        
        val newEntry = LocationEntry(latitude, longitude, currentTime, method)
        history.add(0, newEntry) // Afegir al principi
        
        // Mantenir només les últimes MAX_HISTORY ubicacions
        if (history.size > MAX_HISTORY) {
            history.subList(MAX_HISTORY, history.size).clear()
        }
        
        saveHistory(prefs, history)
    }
    
    // Calcular distància entre dos punts en metres (fórmula Haversine)
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Radi de la Terra en metres
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
    
    fun getHistory(context: Context): List<LocationEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(HISTORY_KEY, null) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<LocationEntry>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(HISTORY_KEY).apply()
    }
    
    fun deleteEntry(context: Context, entry: LocationEntry) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val history = getHistory(context).toMutableList()
        history.removeAll { it.timestamp == entry.timestamp }
        saveHistory(prefs, history)
    }
    
    fun updateEntryName(context: Context, entry: LocationEntry, newName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val history = getHistory(context).toMutableList()
        val index = history.indexOfFirst { it.timestamp == entry.timestamp }
        if (index != -1) {
            history[index].customName = newName
            saveHistory(prefs, history)
        }
    }
    
    private fun saveHistory(prefs: SharedPreferences, history: List<LocationEntry>) {
        val json = gson.toJson(history)
        prefs.edit().putString(HISTORY_KEY, json).apply()
    }
}
