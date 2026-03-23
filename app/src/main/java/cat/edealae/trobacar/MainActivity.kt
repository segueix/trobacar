package cat.edealae.trobacar

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
    }

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "bluetooth_car_connected",
            "default_bluetooth_device_name",
            "saved_latitude",
            "saved_longitude",
            "saved_timestamp",
            "location_name",
            "disconnect_delay_seconds" -> runOnUiThread {
                updateUI()
                updateBluetoothSection()
            }
        }
    }

    private lateinit var statusCard: CardView
    private lateinit var gpsIndicator: ImageView
    private lateinit var androidAutoIndicator: ImageView
    private lateinit var locationCard: CardView
    private lateinit var locationText: TextView
    private lateinit var locationName: TextView
    private lateinit var locationDateTime: TextView
    private lateinit var noLocationText: TextView
    private lateinit var batteryOptimizationButton: CardView
    private lateinit var historyButton: CardView
    private lateinit var openMapsButton: MaterialButton
    private lateinit var shareButton: MaterialButton
    private lateinit var bluetoothSetupCard: CardView
    private lateinit var bluetoothSummaryCard: CardView
    private lateinit var bluetoothSummaryStatus: TextView
    private lateinit var delaySummaryStatus: TextView
    private lateinit var summaryChangeBluetoothButton: MaterialButton
    private lateinit var summaryChangeDelayButton: MaterialButton
    private lateinit var bluetoothStatusText: TextView
    private lateinit var bluetoothNameInput: TextInputEditText
    private lateinit var selectBluetoothButton: MaterialButton
    private lateinit var saveBluetoothButton: MaterialButton
    private lateinit var selectDelayButton: MaterialButton

    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            startLocationService()
            updateUI()
        } else {
            Toast.makeText(this, "Permisos de localització necessaris", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            applyTheme()
            super.onCreate(savedInstanceState)
            CrashLogger.log(this, "MAIN", "MainActivity onCreate iniciat")
            setContentView(R.layout.activity_main)
            supportActionBar?.hide()

            statusCard = findViewById(R.id.statusCard)
            gpsIndicator = findViewById(R.id.gpsIndicator)
            androidAutoIndicator = findViewById(R.id.androidAutoIndicator)
            locationCard = findViewById(R.id.locationCard)
            locationText = findViewById(R.id.locationText)
            locationName = findViewById(R.id.locationName)
            locationDateTime = findViewById(R.id.locationDateTime)
            noLocationText = findViewById(R.id.noLocationText)
            batteryOptimizationButton = findViewById(R.id.batteryOptimizationButton)
            historyButton = findViewById(R.id.historyButton)
            openMapsButton = findViewById(R.id.openMapsButton)
            shareButton = findViewById(R.id.shareButton)
            bluetoothSetupCard = findViewById(R.id.bluetoothSetupCard)
            bluetoothSummaryCard = findViewById(R.id.bluetoothSummaryCard)
            bluetoothSummaryStatus = findViewById(R.id.bluetoothSummaryStatus)
            delaySummaryStatus = findViewById(R.id.delaySummaryStatus)
            summaryChangeBluetoothButton = findViewById(R.id.summaryChangeBluetoothButton)
            summaryChangeDelayButton = findViewById(R.id.summaryChangeDelayButton)
            bluetoothStatusText = findViewById(R.id.bluetoothStatusText)
            bluetoothNameInput = findViewById(R.id.bluetoothNameInput)
            selectBluetoothButton = findViewById(R.id.selectBluetoothButton)
            saveBluetoothButton = findViewById(R.id.saveBluetoothButton)
            selectDelayButton = findViewById(R.id.selectDelayButton)

            locationName.setOnClickListener { showEditNameDialog() }
            openMapsButton.setOnClickListener { openSavedLocationInMaps() }
            shareButton.setOnClickListener { shareLocation() }
            batteryOptimizationButton.setOnClickListener { showBatteryOptimizationHelp() }
            historyButton.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
            findViewById<CardView>(R.id.errorLogButton).setOnClickListener { showErrorLogDialog() }
            selectBluetoothButton.setOnClickListener { showBondedBluetoothDevicesDialog() }
            saveBluetoothButton.setOnClickListener { saveBluetoothDeviceName() }
            selectDelayButton.setOnClickListener { showDelaySelectionDialog() }
            summaryChangeBluetoothButton.setOnClickListener { showBondedBluetoothDevicesDialog() }
            summaryChangeDelayButton.setOnClickListener { showDelaySelectionDialog() }

            checkAndRequestPermissions()
            CrashLogger.log(this, "MAIN", "MainActivity onCreate completat")
        } catch (e: Exception) {
            CrashLogger.logError(this, "MAIN", "Error a onCreate", e)
            throw e
        }
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()

        val themeChanged = prefs.getBoolean("theme_changed", false)
        if (themeChanged) {
            prefs.edit().putBoolean("theme_changed", false).apply()
            recreate()
            return
        }

        updateUI()
        updateBluetoothSection()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startLocationService()
            updateUI()
        }
    }

    private fun startLocationService() {
        if (!hasLocationPermission()) {
            CrashLogger.log(this, "MAIN", "No s'inicia el servei: falta permís de localització")
            return
        }

        try {
            LocationService.startService(this, "main_activity")
        } catch (e: RuntimeException) {
            CrashLogger.logError(this, "MAIN", "No s'ha pogut iniciar el servei de localització", e)
            Toast.makeText(this, R.string.location_service_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun updateUI() {
        applyStatusCardBackground()

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        gpsIndicator.setImageResource(
            if (isGpsEnabled) R.drawable.ic_circle_green else R.drawable.ic_circle_red
        )

        val bluetoothConnected = prefs.getBoolean("bluetooth_car_connected", false)
        androidAutoIndicator.setImageResource(
            if (bluetoothConnected) R.drawable.ic_circle_green else R.drawable.ic_circle_red
        )

        val savedLat = prefs.getFloat("saved_latitude", 0f)
        val savedLon = prefs.getFloat("saved_longitude", 0f)
        val customName = prefs.getString("location_name", getString(R.string.current_parking))
            ?: getString(R.string.current_parking)
        val savedTimestamp = prefs.getLong("saved_timestamp", 0L)

        if (savedLat != 0f && savedLon != 0f) {
            locationCard.visibility = CardView.VISIBLE
            noLocationText.visibility = TextView.GONE
            locationName.text = customName
            if (savedTimestamp > 0) {
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                locationDateTime.text = sdf.format(java.util.Date(savedTimestamp))
            } else {
                locationDateTime.text = ""
            }
            locationText.text = "Lat: ${String.format("%.6f", savedLat)}\nLon: ${String.format("%.6f", savedLon)}"
        } else {
            locationCard.visibility = CardView.GONE
            noLocationText.visibility = TextView.VISIBLE
        }
    }


    private fun applyStatusCardBackground() {
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        if (isDarkMode) {
            statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_elevated_dark))
            return
        }

        val baseBackground = ContextCompat.getColor(this, R.color.background_light)
        val accentColor = when (prefs.getString("theme_color", "blue")) {
            "green" -> ContextCompat.getColor(this, R.color.primary_green_light)
            "red" -> ContextCompat.getColor(this, R.color.primary_red_light)
            "purple" -> ContextCompat.getColor(this, R.color.primary_purple_light)
            else -> ContextCompat.getColor(this, R.color.primary_blue_light)
        }

        val tonedBackground = ColorUtils.blendARGB(baseBackground, accentColor, 0.30f)
        statusCard.setCardBackgroundColor(tonedBackground)
    }


    private fun showBatteryOptimizationHelp() {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }

        val manufacturerTip = when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> getString(R.string.battery_help_tip_xiaomi)
            "huawei", "honor" -> getString(R.string.battery_help_tip_huawei)
            "oppo", "realme", "oneplus", "vivo" -> getString(R.string.battery_help_tip_oppo)
            "samsung" -> getString(R.string.battery_help_tip_samsung)
            else -> getString(R.string.battery_help_tip_generic, manufacturer)
        }

        val statusText = if (isIgnoringOptimizations) {
            getString(R.string.battery_help_already_disabled)
        } else {
            getString(R.string.battery_help_currently_enabled)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.battery_help_title)
            .setMessage(getString(R.string.battery_help_message, statusText, manufacturerTip))
            .setPositiveButton(R.string.battery_help_open_settings) { _, _ ->
                openBatteryOptimizationSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openBatteryOptimizationSettings() {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                add(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            add(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        }

        intents.firstOrNull { intent ->
            intent.resolveActivity(packageManager) != null
        }?.let { startActivity(it) } ?: Toast.makeText(this, R.string.battery_help_open_failed, Toast.LENGTH_SHORT).show()
    }

    private fun updateBluetoothSection() {
        val savedName = prefs.getString("default_bluetooth_device_name", null)
        val isConfigured = !savedName.isNullOrEmpty()

        if (isConfigured) {
            // Show compact summary card
            bluetoothSetupCard.visibility = CardView.GONE
            bluetoothSummaryCard.visibility = CardView.VISIBLE

            val bluetoothConnected = prefs.getBoolean("bluetooth_car_connected", false)
            bluetoothSummaryStatus.text = if (bluetoothConnected) {
                getString(R.string.bluetooth_connected_to, savedName)
            } else {
                getString(R.string.bluetooth_configured, savedName)
            }

            val delaySec = prefs.getInt("disconnect_delay_seconds", 0)
            val delayText = if (delaySec == 0) {
                getString(R.string.disconnect_delay_immediate)
            } else {
                getString(R.string.disconnect_delay_seconds, delaySec)
            }
            delaySummaryStatus.text = getString(R.string.disconnect_delay_current, delayText)
        } else {
            // Show full setup card
            bluetoothSummaryCard.visibility = CardView.GONE
            bluetoothSetupCard.visibility = CardView.VISIBLE

            bluetoothStatusText.text = when {
                bluetoothAdapter == null -> getString(R.string.bluetooth_not_supported)
                bluetoothAdapter?.isEnabled != true -> getString(R.string.bluetooth_disabled)
                else -> getString(R.string.bluetooth_setup_needed)
            }

            updateDelayButtonText()
        }
    }

    private fun updateDelayButtonText() {
        val delaySec = prefs.getInt("disconnect_delay_seconds", 0)
        val delayText = if (delaySec == 0) {
            getString(R.string.disconnect_delay_immediate)
        } else {
            getString(R.string.disconnect_delay_seconds, delaySec)
        }
        selectDelayButton.text = getString(R.string.disconnect_delay_current, delayText)
    }

    private fun showDelaySelectionDialog() {
        val delayOptions = intArrayOf(0, 5, 10, 15, 20)
        val delayLabels = delayOptions.map { sec ->
            if (sec == 0) getString(R.string.disconnect_delay_immediate)
            else getString(R.string.disconnect_delay_seconds, sec)
        }.toTypedArray()

        val currentDelay = prefs.getInt("disconnect_delay_seconds", 0)
        val checkedItem = delayOptions.indexOf(currentDelay).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.disconnect_delay_title)
            .setSingleChoiceItems(delayLabels, checkedItem) { dialog, which ->
                prefs.edit().putInt("disconnect_delay_seconds", delayOptions[which]).apply()
                updateDelayButtonText()
                Toast.makeText(this, R.string.disconnect_delay_saved, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun getBondedBluetoothDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled || !hasBluetoothConnectPermission()) return emptyList()

        return adapter.bondedDevices
            ?.filter { !it.name.isNullOrBlank() }
            ?.sortedBy { it.name?.lowercase() }
            .orEmpty()
    }

    private fun showBondedBluetoothDevicesDialog() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Toast.makeText(this, R.string.bluetooth_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasBluetoothConnectPermission()) {
            Toast.makeText(this, R.string.bluetooth_permission_required, Toast.LENGTH_SHORT).show()
            return
        }

        val devices = getBondedBluetoothDevices()
        if (devices.isEmpty()) {
            Toast.makeText(this, R.string.bluetooth_no_paired_devices, Toast.LENGTH_SHORT).show()
            return
        }

        val deviceNames = devices.mapNotNull { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.bluetooth_select_dialog_title)
            .setItems(deviceNames) { _, which ->
                bluetoothNameInput.setText(deviceNames[which])
                saveBluetoothDeviceName()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveBluetoothDeviceName() {
        val name = bluetoothNameInput.text?.toString()?.trim()
        if (name.isNullOrEmpty()) {
            Toast.makeText(this, R.string.bluetooth_name_empty, Toast.LENGTH_SHORT).show()
            return
        }

        getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
            .edit()
            .putString("default_bluetooth_device_name", name)
            .apply()

        Toast.makeText(this, R.string.bluetooth_default_saved, Toast.LENGTH_SHORT).show()
        updateBluetoothSection()
    }

    private fun openSavedLocationInMaps() {
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val savedLat = prefs.getFloat("saved_latitude", 0f)
        val savedLon = prefs.getFloat("saved_longitude", 0f)
        val customName = prefs.getString("location_name", getString(R.string.current_parking))
            ?: getString(R.string.current_parking)

        if (savedLat != 0f && savedLon != 0f) {
            val uri = Uri.parse("geo:$savedLat,$savedLon?q=$savedLat,$savedLon($customName)")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")

            try {
                startActivity(intent)
            } catch (e: Exception) {
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
        val customName = prefs.getString("location_name", getString(R.string.current_parking))
            ?: getString(R.string.current_parking)

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

            val shareIntent = Intent.createChooser(sendIntent, getString(R.string.open_with))
            startActivity(shareIntent)
        }
    }

    private fun showEditNameDialog() {
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val currentName = prefs.getString("location_name", getString(R.string.current_parking))
            ?: getString(R.string.current_parking)

        val input = EditText(this)
        input.setText(currentName)
        input.setSelectAllOnFocus(true)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_name))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    prefs.edit().putString("location_name", newName).apply()
                    updateUI()
                    Toast.makeText(this, getString(R.string.location_name_updated), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showErrorLogDialog() {
        val logContent = CrashLogger.readLog(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_error_log, null)
        val logTextView = dialogView.findViewById<TextView>(R.id.errorLogText)
        val closeButton = dialogView.findViewById<MaterialButton>(R.id.closeErrorLogButton)
        val clearButton = dialogView.findViewById<MaterialButton>(R.id.clearErrorLogButton)
        val copyButton = dialogView.findViewById<ImageView>(R.id.copyErrorLogButton)

        logTextView.text = logContent

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.error_log_title))
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener { dialog.dismiss() }
        clearButton.setOnClickListener {
            CrashLogger.clearLog(this)
            logTextView.text = CrashLogger.readLog(this)
            Toast.makeText(this, getString(R.string.error_log_cleared), Toast.LENGTH_SHORT).show()
        }
        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.error_log_title), logTextView.text))
            Toast.makeText(this, getString(R.string.error_log_copied), Toast.LENGTH_SHORT).show()
        }

        dialog.show()
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
