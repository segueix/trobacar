package cat.edealae.trobacar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var clearButton: TextView
    private lateinit var adapter: HistoryAdapter
    private lateinit var darkModeSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var bluetoothConfigCard: CardView
    private lateinit var historyBluetoothStatus: TextView
    private lateinit var historyChangeBluetoothButton: MaterialButton
    private lateinit var historyChangeDelayButton: MaterialButton

    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplicar tema guardat abans de setContentView
        applyTheme()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Historial d'ubicacions"

        recyclerView = findViewById(R.id.historyRecyclerView)
        emptyView = findViewById(R.id.emptyHistoryText)
        clearButton = findViewById(R.id.clearHistoryButton)
        darkModeSwitch = findViewById(R.id.darkModeSwitch)
        bluetoothConfigCard = findViewById(R.id.bluetoothConfigCard)
        historyBluetoothStatus = findViewById(R.id.historyBluetoothStatus)
        historyChangeBluetoothButton = findViewById(R.id.historyChangeBluetoothButton)
        historyChangeDelayButton = findViewById(R.id.historyChangeDelayButton)

        recyclerView.layoutManager = LinearLayoutManager(this)

        clearButton.setOnClickListener {
            showClearConfirmDialog()
        }

        setupThemeSelectors()
        setupBluetoothConfig()
        loadHistory()
    }
    
    private fun setupThemeSelectors() {
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        
        darkModeSwitch.isChecked = isDarkMode
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean("dark_mode", isChecked)
                .putBoolean("theme_changed", true)
                .apply()
            recreate() // Recrear activity per aplicar el tema
        }
        
        // Colors
        findViewById<View>(R.id.colorBlue).setOnClickListener {
            changeThemeColor("blue")
        }
        findViewById<View>(R.id.colorGreen).setOnClickListener {
            changeThemeColor("green")
        }
        findViewById<View>(R.id.colorRed).setOnClickListener {
            changeThemeColor("red")
        }
        findViewById<View>(R.id.colorPurple).setOnClickListener {
            changeThemeColor("purple")
        }
    }
    
    private fun changeThemeColor(color: String) {
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("theme_color", color)
            .putBoolean("theme_changed", true)
            .apply()
        recreate()
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

    private fun setupBluetoothConfig() {
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val savedName = prefs.getString("default_bluetooth_device_name", null)

        if (!savedName.isNullOrEmpty()) {
            bluetoothConfigCard.visibility = View.VISIBLE
            updateBluetoothConfigStatus()

            historyChangeBluetoothButton.setOnClickListener { showBondedBluetoothDevicesDialog() }
            historyChangeDelayButton.setOnClickListener { showDelaySelectionDialog() }
        } else {
            bluetoothConfigCard.visibility = View.GONE
        }
    }

    private fun updateBluetoothConfigStatus() {
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val savedName = prefs.getString("default_bluetooth_device_name", null) ?: return
        val delaySec = prefs.getInt("disconnect_delay_seconds", 0)
        val delayText = if (delaySec == 0) {
            getString(R.string.disconnect_delay_immediate)
        } else {
            getString(R.string.disconnect_delay_seconds, delaySec)
        }

        historyBluetoothStatus.text = getString(R.string.bluetooth_configured, savedName) +
            "\n" + getString(R.string.disconnect_delay_current, delayText)
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
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
        MaterialAlertDialogBuilder(this, R.style.Theme_TrobaCar_Dialog)
            .setTitle(R.string.bluetooth_select_dialog_title)
            .setItems(deviceNames) { _, which ->
                val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
                prefs.edit().putString("default_bluetooth_device_name", deviceNames[which]).apply()
                Toast.makeText(this, R.string.bluetooth_default_saved, Toast.LENGTH_SHORT).show()
                updateBluetoothConfigStatus()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDelaySelectionDialog() {
        val prefs = getSharedPreferences("TrobaCar", Context.MODE_PRIVATE)
        val delayOptions = intArrayOf(0, 5, 10, 15, 20)
        val delayLabels = delayOptions.map { sec ->
            if (sec == 0) getString(R.string.disconnect_delay_immediate)
            else getString(R.string.disconnect_delay_seconds, sec)
        }.toTypedArray()

        val currentDelay = prefs.getInt("disconnect_delay_seconds", 0)
        val checkedItem = delayOptions.indexOf(currentDelay).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this, R.style.Theme_TrobaCar_Dialog)
            .setTitle(R.string.disconnect_delay_title)
            .setSingleChoiceItems(delayLabels, checkedItem) { dialog, which ->
                prefs.edit().putInt("disconnect_delay_seconds", delayOptions[which]).apply()
                Toast.makeText(this, R.string.disconnect_delay_saved, Toast.LENGTH_SHORT).show()
                updateBluetoothConfigStatus()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadHistory() {
        val history = LocationHistory.getHistory(this)
        
        if (history.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            clearButton.visibility = View.GONE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            clearButton.visibility = View.VISIBLE
            
            adapter = HistoryAdapter(
                items = history,
                onItemClick = { entry -> openLocationInMaps(entry) },
                onDeleteClick = { entry -> deleteEntry(entry) },
                onEditNameClick = { entry -> showEditNameDialog(entry) }
            )
            recyclerView.adapter = adapter
        }
    }
    
    private fun deleteEntry(entry: LocationEntry) {
        MaterialAlertDialogBuilder(this, R.style.Theme_TrobaCar_Dialog)
            .setMessage("Vols esborrar aquesta ubicació?")
            .setPositiveButton(R.string.delete) { _, _ ->
                LocationHistory.deleteEntry(this, entry)
                loadHistory()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showEditNameDialog(entry: LocationEntry) {
        val input = android.widget.EditText(this)
        input.setText(entry.customName)
        input.setSelectAllOnFocus(true)
        
        MaterialAlertDialogBuilder(this, R.style.Theme_TrobaCar_Dialog)
            .setTitle("Editar nom")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    LocationHistory.updateEntryName(this, entry, newName)
                    loadHistory()
                    Toast.makeText(this, "Nom actualitzat", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel·lar", null)
            .show()
    }

    private fun showClearConfirmDialog() {
        MaterialAlertDialogBuilder(this, R.style.Theme_TrobaCar_Dialog)
            .setMessage(R.string.delete_confirmation)
            .setPositiveButton(R.string.delete) { _, _ ->
                LocationHistory.clearHistory(this)
                loadHistory()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openLocationInMaps(entry: LocationEntry) {
        val uri = Uri.parse("geo:${entry.latitude},${entry.longitude}?q=${entry.latitude},${entry.longitude}(${entry.customName})")
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

class HistoryAdapter(
    private val items: List<LocationEntry>,
    private val onItemClick: (LocationEntry) -> Unit,
    private val onDeleteClick: (LocationEntry) -> Unit,
    private val onEditNameClick: (LocationEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.nameText)
        val dateTimeText: TextView = view.findViewById(R.id.dateTimeText)
        val locationText: TextView = view.findViewById(R.id.locationText)
        val methodIcon: ImageView = view.findViewById(R.id.methodIcon)
        val deleteButton: ImageView = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        
        // Mostrar nom (sense "actual" si és el per defecte)
        val displayName = if (entry.customName == "Aparcament actual") "Aparcament" else entry.customName
        holder.nameText.text = displayName
        
        // Mostrar data i hora
        holder.dateTimeText.text = "📅 " + entry.getFormattedDate()
        
        holder.locationText.text = entry.getFormattedLocation()
        
        holder.itemView.setOnClickListener {
            onItemClick(entry)
        }
        
        holder.nameText.setOnClickListener {
            onEditNameClick(entry)
        }
        
        holder.deleteButton.setOnClickListener {
            onDeleteClick(entry)
        }
    }

    override fun getItemCount() = items.size
}
