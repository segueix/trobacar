package cat.edealae.trobacar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var clearButton: TextView
    private lateinit var adapter: HistoryAdapter
    private lateinit var darkModeSwitch: com.google.android.material.switchmaterial.SwitchMaterial

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

        recyclerView.layoutManager = LinearLayoutManager(this)
        
        clearButton.setOnClickListener {
            showClearConfirmDialog()
        }
        
        setupThemeSelectors()

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
        AlertDialog.Builder(this)
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
        
        AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
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
