package cat.edealae.trobacar

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var gpsIndicator: ImageView
    private lateinit var androidAutoIndicator: ImageView
    private lateinit var locationCard: CardView
    private lateinit var locationText: TextView
    private lateinit var locationName: TextView
    private lateinit var locationDateTime: TextView
    private lateinit var noLocationText: TextView
    private lateinit var historyButton: CardView
    private lateinit var openMapsButton: MaterialButton
    private lateinit var shareButton: MaterialButton

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val activityRecognitionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            permissions[Manifest.permission.ACTIVITY_RECOGNITION] != false

        if (hasLocationPermission) {
            startLocationService()
            updateUI()
        } else {
            Toast.makeText(this, "Permisos de localització necessaris", Toast.LENGTH_LONG).show()
        }

        if (activityRecognitionGranted) {
            ActivityRecognitionHelper.startActivityRecognition(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplicar tema guardat abans de setContentView
        applyTheme()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Ocultar ActionBar
        supportActionBar?.hide()

        // Inicialitzar vistes
        gpsIndicator = findViewById(R.id.gpsIndicator)
        androidAutoIndicator = findViewById(R.id.androidAutoIndicator)
        locationCard = findViewById(R.id.locationCard)
        locationText = findViewById(R.id.locationText)
        locationName = findViewById(R.id.locationName)
        locationDateTime = findViewById(R.id.locationDateTime)
        noLocationText = findViewById(R.id.noLocationText)
        historyButton = findViewById(R.id.historyButton)
        openMapsButton = findViewById(R.id.openMapsButton)
        shareButton = findViewById(R.id.shareButton)

        // Click listener per editar nom
        locationName.setOnClickListener {
            showEditNameDialog()
        }
        
        // Click listener per obrir mapa
        openMapsButton.setOnClickListener {
            openSavedLocationInMaps()
        }
        
        // Click listener per compartir
        shareButton.setOnClickListener {
            shareLocation()
        }
        
        // Click listener per veure historial
        historyButton.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        // Demanar permisos si cal
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        
        // Comprovar si s'ha canviat el tema
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val themeChanged = prefs.getBoolean("theme_changed", false)
        
        if (themeChanged) {
            prefs.edit().putBoolean("theme_changed", false).apply()
            recreate()
            return
        }
        
        updateUI()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startLocationService()
            ActivityRecognitionHelper.startActivityRecognition(this)
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateUI() {
        // Actualitzar indicador GPS
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        
        gpsIndicator.setImageResource(
            if (isGpsEnabled) R.drawable.ic_circle_green else R.drawable.ic_circle_red
        )

        // Actualitzar indicador (mostra Bluetooth o Activity o Android Auto)
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val bluetoothConnected = prefs.getBoolean("bluetooth_connected", false)
        val inVehicle = prefs.getBoolean("in_vehicle", false)
        val androidAutoConnected = prefs.getBoolean("android_auto_connected", false)
        
        // Mostrar verd si qualsevol està actiu
        val isConnected = bluetoothConnected || inVehicle || androidAutoConnected
        
        androidAutoIndicator.setImageResource(
            if (isConnected) R.drawable.ic_circle_green else R.drawable.ic_circle_red
        )

        // Actualitzar ubicació guardada
        val savedLat = prefs.getFloat("saved_latitude", 0f)
        val savedLon = prefs.getFloat("saved_longitude", 0f)
        val customName = prefs.getString("location_name", "Aparcament actual") ?: "Aparcament actual"
        val savedTimestamp = prefs.getLong("saved_timestamp", 0L)
        
        if (savedLat != 0f && savedLon != 0f) {
            locationCard.visibility = CardView.VISIBLE
            noLocationText.visibility = TextView.GONE
            
            // Mostrar nom personalitzat
            locationName.text = customName
            
            // Mostrar data i hora
            if (savedTimestamp > 0) {
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                locationDateTime.text = "📅 " + sdf.format(java.util.Date(savedTimestamp))
            } else {
                locationDateTime.text = ""
            }
            
            locationText.text = "Lat: ${String.format("%.6f", savedLat)}\n" +
                    "Lon: ${String.format("%.6f", savedLon)}"
        } else {
            locationCard.visibility = CardView.GONE
            noLocationText.visibility = TextView.VISIBLE
        }
    }

    private fun openSavedLocationInMaps() {
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val savedLat = prefs.getFloat("saved_latitude", 0f)
        val savedLon = prefs.getFloat("saved_longitude", 0f)
        val customName = prefs.getString("location_name", "El meu cotxe") ?: "El meu cotxe"
        
        if (savedLat != 0f && savedLon != 0f) {
            val uri = Uri.parse("geo:$savedLat,$savedLon?q=$savedLat,$savedLon($customName)")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            
            // Intentar obrir amb Google Maps primer
            intent.setPackage("com.google.android.apps.maps")
            
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Si Google Maps no està instal·lat, deixar que l'usuari esculli
                intent.setPackage(null)
                val chooser = Intent.createChooser(intent, getString(R.string.open_with))
                try {
                    startActivity(chooser)
                } catch (e2: Exception) {
                    Toast.makeText(this, R.string.no_map_apps, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun shareLocation() {
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val savedLat = prefs.getFloat("saved_latitude", 0f)
        val savedLon = prefs.getFloat("saved_longitude", 0f)
        val customName = prefs.getString("location_name", "El meu cotxe") ?: "El meu cotxe"
        
        if (savedLat != 0f && savedLon != 0f) {
            val shareText = "$customName\n" +
                    "Lat: ${String.format("%.6f", savedLat)}\n" +
                    "Lon: ${String.format("%.6f", savedLon)}\n" +
                    "https://maps.google.com/?q=$savedLat,$savedLon"
            
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            
            val shareIntent = Intent.createChooser(sendIntent, "Compartir ubicació")
            startActivity(shareIntent)
        }
    }
    
    private fun showEditNameDialog() {
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val currentName = prefs.getString("location_name", "Aparcament actual") ?: "Aparcament actual"
        
        val input = EditText(this)
        input.setText(currentName)
        input.setSelectAllOnFocus(true)
        
        AlertDialog.Builder(this)
            .setTitle("Editar nom")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    prefs.edit().putString("location_name", newName).apply()
                    updateUI()
                    Toast.makeText(this, getString(R.string.location_saved), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel·lar", null)
            .show()
    }
    
    private fun applyTheme() {
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        val themeColor = prefs.getString("theme_color", "blue")
        
        val themeId = when {
            isDarkMode && themeColor == "blue" -> R.style.Theme_TrobaCar_Dark_Blue
            isDarkMode && themeColor == "green" -> R.style.Theme_TrobaCar_Dark_Green
            isDarkMode && themeColor == "red" -> R.style.Theme_TrobaCar_Dark_Red
            isDarkMode && themeColor == "purple" -> R.style.Theme_TrobaCar_Dark_Purple
            !isDarkMode && themeColor == "green" -> R.style.Theme_TrobaCar_Light_Green
            !isDarkMode && themeColor == "red" -> R.style.Theme_TrobaCar_Light_Red
            !isDarkMode && themeColor == "purple" -> R.style.Theme_TrobaCar_Light_Purple
            else -> R.style.Theme_TrobaCar_Light_Blue
        }
        
        setTheme(themeId)
    }
}
