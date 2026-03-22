package cat.edealae.trobacar

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
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
    private lateinit var bluetoothStatusText: TextView
    private lateinit var bluetoothDefaultText: TextView
    private lateinit var bluetoothListView: ListView
    private lateinit var scanBluetoothButton: MaterialButton
    private lateinit var saveBluetoothButton: MaterialButton

    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private val discoveredDevices = linkedMapOf<String, BluetoothDevice>()
    private val bluetoothItems = mutableListOf<String>()
    private lateinit var bluetoothListAdapter: ArrayAdapter<String>
    private var selectedBluetoothAddress: String? = null
    private var isBluetoothReceiverRegistered = false

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

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasBluetoothPermissions()) {
            startBluetoothScan()
            updateBluetoothSection()
        } else {
            Toast.makeText(this, R.string.bluetooth_permissions_needed, Toast.LENGTH_LONG).show()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { addBluetoothDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanBluetoothButton.isEnabled = true
                    scanBluetoothButton.text = getString(R.string.scan_bluetooth)
                    updateBluetoothSection()
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothA2dpCompat.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadsetCompat.ACTION_CONNECTION_STATE_CHANGED -> {
                    updateBluetoothSection()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

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
        bluetoothStatusText = findViewById(R.id.bluetoothStatusText)
        bluetoothDefaultText = findViewById(R.id.bluetoothDefaultText)
        bluetoothListView = findViewById(R.id.bluetoothListView)
        scanBluetoothButton = findViewById(R.id.scanBluetoothButton)
        saveBluetoothButton = findViewById(R.id.saveBluetoothButton)

        setupBluetoothList()

        locationName.setOnClickListener { showEditNameDialog() }
        openMapsButton.setOnClickListener { openSavedLocationInMaps() }
        shareButton.setOnClickListener { shareLocation() }
        historyButton.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        scanBluetoothButton.setOnClickListener { ensureBluetoothPermissionsAndScan() }
        saveBluetoothButton.setOnClickListener { saveSelectedBluetoothDevice() }

        registerBluetoothReceiver()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()

        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val themeChanged = prefs.getBoolean("theme_changed", false)
        if (themeChanged) {
            prefs.edit().putBoolean("theme_changed", false).apply()
            recreate()
            return
        }

        updateUI()
        loadKnownBluetoothDevices()
        updateBluetoothSection()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
        if (isBluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver)
            isBluetoothReceiverRegistered = false
        }
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

        if (permissionsToRequest.isNotEmpty()) {
            locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startLocationService()
            updateUI()
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
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        gpsIndicator.setImageResource(
            if (isGpsEnabled) R.drawable.ic_circle_green else R.drawable.ic_circle_red
        )

        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val androidAutoConnected = prefs.getBoolean("android_auto_connected", false)
        androidAutoIndicator.setImageResource(
            if (androidAutoConnected) R.drawable.ic_circle_green else R.drawable.ic_circle_red
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
                locationDateTime.text = "📅 " + sdf.format(java.util.Date(savedTimestamp))
            } else {
                locationDateTime.text = ""
            }
            locationText.text = "Lat: ${String.format("%.6f", savedLat)}\nLon: ${String.format("%.6f", savedLon)}"
        } else {
            locationCard.visibility = CardView.GONE
            noLocationText.visibility = TextView.VISIBLE
        }
    }

    private fun setupBluetoothList() {
        bluetoothListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, bluetoothItems)
        bluetoothListView.adapter = bluetoothListAdapter
        bluetoothListView.choiceMode = ListView.CHOICE_MODE_SINGLE
        bluetoothListView.setOnItemClickListener { _, _, position, _ ->
            val item = bluetoothItems.getOrNull(position) ?: return@setOnItemClickListener
            selectedBluetoothAddress = item.substringAfterLast(" • ", "")
            updateBluetoothSection()
        }
    }

    private fun registerBluetoothReceiver() {
        if (isBluetoothReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dpCompat.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadsetCompat.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter)
        isBluetoothReceiverRegistered = true
    }

    private fun ensureBluetoothPermissionsAndScan() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_LONG).show()
            return
        }

        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startBluetoothScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothScan() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            Toast.makeText(this, R.string.bluetooth_enable_first, Toast.LENGTH_LONG).show()
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        if (!hasBluetoothPermissions()) {
            Toast.makeText(this, R.string.bluetooth_permissions_needed, Toast.LENGTH_LONG).show()
            return
        }

        discoveredDevices.clear()
        bluetoothItems.clear()
        bluetoothListAdapter.notifyDataSetChanged()
        selectedBluetoothAddress = null
        loadKnownBluetoothDevices()

        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }

        scanBluetoothButton.isEnabled = false
        scanBluetoothButton.text = getString(R.string.scanning_bluetooth)

        val started = adapter.startDiscovery()
        if (!started) {
            scanBluetoothButton.isEnabled = true
            scanBluetoothButton.text = getString(R.string.scan_bluetooth)
            Toast.makeText(this, R.string.bluetooth_scan_failed, Toast.LENGTH_LONG).show()
        }
        updateBluetoothSection()
    }

    @SuppressLint("MissingPermission")
    private fun loadKnownBluetoothDevices() {
        val adapter = bluetoothAdapter ?: return
        if (!hasBluetoothPermissions()) return

        adapter.bondedDevices.orEmpty().forEach { addBluetoothDevice(it) }
        getConnectedBluetoothDevices().forEach { addBluetoothDevice(it) }
    }

    @SuppressLint("MissingPermission")
    private fun addBluetoothDevice(device: BluetoothDevice) {
        val address = device.address ?: return
        if (discoveredDevices.containsKey(address)) return
        val name = device.name?.takeIf { it.isNotBlank() } ?: getString(R.string.unnamed_bluetooth_device)
        discoveredDevices[address] = device
        bluetoothItems.add("$name • $address")
        bluetoothListAdapter.notifyDataSetChanged()
    }

    @SuppressLint("MissingPermission")
    private fun updateBluetoothSection() {
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val defaultAddress = prefs.getString("default_bluetooth_device_address", null)
        val defaultName = prefs.getString("default_bluetooth_device_name", null)

        val connectedDevices = if (hasBluetoothPermissions()) getConnectedBluetoothDevices() else emptyList()
        val connectedPreferred = connectedDevices.firstOrNull { it.address == defaultAddress }
        val activeDevice = connectedPreferred ?: connectedDevices.firstOrNull()

        bluetoothStatusText.text = when {
            bluetoothAdapter == null -> getString(R.string.bluetooth_not_supported)
            bluetoothAdapter?.isEnabled != true -> getString(R.string.bluetooth_disabled)
            activeDevice != null -> getString(
                R.string.bluetooth_connected_to,
                activeDevice.name?.takeIf { it.isNotBlank() } ?: activeDevice.address
            )
            connectedDevices.isNotEmpty() -> getString(R.string.bluetooth_connected_generic)
            else -> getString(R.string.bluetooth_not_connected)
        }

        bluetoothDefaultText.text = if (defaultAddress != null) {
            getString(R.string.bluetooth_default_device, defaultName ?: defaultAddress)
        } else {
            getString(R.string.bluetooth_default_not_set)
        }

        val selectedIndex = bluetoothItems.indexOfFirst { it.endsWith(selectedBluetoothAddress ?: "") }
        if (selectedIndex >= 0) {
            bluetoothListView.setItemChecked(selectedIndex, true)
        } else {
            bluetoothListView.clearChoices()
        }

        saveBluetoothButton.isEnabled = connectedDevices.any { it.address == selectedBluetoothAddress }
        if (!saveBluetoothButton.isEnabled) {
            saveBluetoothButton.text = getString(R.string.connect_before_saving)
        } else {
            saveBluetoothButton.text = getString(R.string.save_default_bluetooth)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getConnectedBluetoothDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions()) return emptyList()

        val devices = linkedMapOf<String, BluetoothDevice>()
        listOf(
            BluetoothProfile.HEADSET,
            BluetoothProfile.A2DP,
            BluetoothProfile.GATT,
            BluetoothProfile.GATT_SERVER
        ).forEach { profile ->
            bluetoothManager.getConnectedDevices(profile).forEach { device ->
                devices[device.address] = device
            }
        }
        return devices.values.toList()
    }

    private fun saveSelectedBluetoothDevice() {
        val address = selectedBluetoothAddress
        if (address.isNullOrBlank()) {
            Toast.makeText(this, R.string.select_bluetooth_device, Toast.LENGTH_LONG).show()
            return
        }

        val device = discoveredDevices[address]
        val connected = hasBluetoothPermissions() && getConnectedBluetoothDevices().any { it.address == address }
        if (!connected) {
            Toast.makeText(this, R.string.bluetooth_must_be_connected, Toast.LENGTH_LONG).show()
            updateBluetoothSection()
            return
        }

        getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
            .edit()
            .putString("default_bluetooth_device_address", address)
            .putString("default_bluetooth_device_name", device?.name ?: address)
            .apply()

        Toast.makeText(this, R.string.bluetooth_default_saved, Toast.LENGTH_SHORT).show()
        updateBluetoothSection()
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
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

private object BluetoothA2dpCompat {
    const val ACTION_CONNECTION_STATE_CHANGED = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
}

private object BluetoothHeadsetCompat {
    const val ACTION_CONNECTION_STATE_CHANGED = "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"
}
