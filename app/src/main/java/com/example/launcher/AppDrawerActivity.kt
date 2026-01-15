package com.example.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu as AppCompatPopupMenu
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.min

data class DrawerFolderInfo(
    var name: String = "Pasta",
    val apps: MutableList<String> = mutableListOf()
)

class AppDrawerActivity : AppCompatActivity() {

    private lateinit var appDrawerContainer: FrameLayout
    private lateinit var drawerContent: LinearLayout
    private lateinit var appGrid: GridView
    private lateinit var searchBar: EditText
    private lateinit var btnSortMenu: ImageView
    
    private lateinit var customIconManager: CustomIconManager
    
    private var allApps: MutableList<AppInfo> = mutableListOf()
    private var filteredApps: MutableList<AppInfo> = mutableListOf()
    private var currentSortMode: String = "name_asc"
    
    private var pendingIconChangePackage: String? = null
    private val PICK_ICON_REQUEST = 1001

    private var drawerFolders: MutableMap<Int, DrawerFolderInfo> = mutableMapOf()
    private var currentDragPackage: String? = null
    private var currentDragPosition: Int = -1
    private var isDragging = false
    private var dragDelayTime = 0L
    private val dragDelayDuration = 300L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val dragThreshold = 20f
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    private var packageChangeReceiver: BroadcastReceiver? = null

    companion object {
        const val LAUNCHER_SETTINGS_PACKAGE = "com.example.launcher.settings"
    }

    data class AppInfo(
        val packageName: String,
        val name: String,
        val icon: Drawable
    )

    sealed class DrawerItem {
        data class App(val appInfo: AppInfo) : DrawerItem()
        data class Folder(val folderInfo: DrawerFolderInfo, val position: Int) : DrawerItem()
    }

    private var displayItems: MutableList<DrawerItem> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)

        customIconManager = CustomIconManager(this)

        appDrawerContainer = findViewById(R.id.appDrawerOverlayContainer)
        drawerContent = findViewById(R.id.drawerContent)
        appGrid = findViewById(R.id.overlayAppGrid)
        searchBar = findViewById(R.id.overlaySearchBar)
        btnSortMenu = findViewById(R.id.overlayBtnSortMenu)

        loadSortPreference()
        loadDrawerFolders() // Load folders
        loadInstalledApps()
        setupSearchBar()
        setupSortMenu()
        setupGridTouchListener() // Changed from setupGridClickListener to support drag

        registerPackageChangeReceiver()

        // Animate entrance
        appDrawerContainer.setOnClickListener {
            closeDrawer()
        }

        // Prevent clicks on content from closing
        drawerContent.setOnClickListener { /* consume click */ }

        val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_left)
        drawerContent.startAnimation(slideIn)
    }

    private fun registerPackageChangeReceiver() {
        packageChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == PackageChangeReceiver.ACTION_APPS_CHANGED) {
                    val packageName = intent.getStringExtra(PackageChangeReceiver.EXTRA_PACKAGE_NAME)
                    val changeType = intent.getStringExtra(PackageChangeReceiver.EXTRA_CHANGE_TYPE)
                    
                    // Atualiza a lista de apps na UI thread
                    runOnUiThread {
                        when (changeType) {
                            PackageChangeReceiver.TYPE_INSTALLED -> {
                                Toast.makeText(this@AppDrawerActivity, "App instalado: $packageName", Toast.LENGTH_SHORT).show()
                            }
                            PackageChangeReceiver.TYPE_UNINSTALLED -> {
                                // Remove o app das pastas se estiver em alguma
                                drawerFolders.values.forEach { folder ->
                                    folder.apps.remove(packageName)
                                }
                                saveDrawerFolders()
                            }
                        }
                        
                        // Recarrega a lista de apps
                        loadInstalledApps()
                        filterApps(searchBar.text.toString())
                    }
                }
            }
        }
        
        val filter = IntentFilter(PackageChangeReceiver.ACTION_APPS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageChangeReceiver, filter)
        }
    }

    private fun loadSortPreference() {
        val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        currentSortMode = prefs.getString("sort_mode", "name_asc") ?: "name_asc"
    }

    private fun loadDrawerFolders() {
        val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val foldersJson = prefs.getString("drawer_folders", null)
        drawerFolders.clear()
        
        if (foldersJson != null) {
            try {
                val json = JSONObject(foldersJson)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val folderObj = json.getJSONObject(key)
                    val name = folderObj.getString("name")
                    val appsArray = folderObj.getJSONArray("apps")
                    val apps = mutableListOf<String>()
                    for (i in 0 until appsArray.length()) {
                        apps.add(appsArray.getString(i))
                    }
                    drawerFolders[key.toInt()] = DrawerFolderInfo(name, apps)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveDrawerFolders() {
        val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val json = JSONObject()
        
        for ((position, folder) in drawerFolders) {
            val folderObj = JSONObject()
            folderObj.put("name", folder.name)
            val appsArray = JSONArray()
            for (app in folder.apps) {
                appsArray.put(app)
            }
            folderObj.put("apps", appsArray)
            json.put(position.toString(), folderObj)
        }
        
        prefs.edit().putString("drawer_folders", json.toString()).apply()
    }

    private fun loadInstalledApps() {
        allApps.clear()
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfoList = pm.queryIntentActivities(intent, 0)

        val appsInFolders = drawerFolders.values.flatMap { it.apps }.toSet()

        for (resolveInfo in resolveInfoList) {
            val pkgName = resolveInfo.activityInfo.packageName
            
            if (appsInFolders.contains(pkgName)) continue
            
            val defaultIcon = resolveInfo.loadIcon(pm)
            val icon = customIconManager.getIconForApp(pkgName, defaultIcon)
            
            val appInfo = AppInfo(
                packageName = pkgName,
                name = resolveInfo.loadLabel(pm).toString(),
                icon = icon
            )
            allApps.add(appInfo)
        }

        addLauncherSettingsApp()

        sortApps()
        filteredApps.clear()
        filteredApps.addAll(allApps)
        buildDisplayItems() // Build display items including folders
    }

    private fun addLauncherSettingsApp() {
        val settingsIcon = ContextCompat.getDrawable(this, R.drawable.ic_launcher_settings)
        if (settingsIcon != null) {
            val settingsApp = AppInfo(
                packageName = LAUNCHER_SETTINGS_PACKAGE,
                name = "Configurações do Launcher",
                icon = settingsIcon
            )
            allApps.add(settingsApp)
        }
    }

    private fun buildDisplayItems() {
        displayItems.clear()
        
        // Add folders first (sorted by position)
        val sortedFolders = drawerFolders.entries.sortedBy { it.key }
        for ((position, folder) in sortedFolders) {
            if (folder.apps.isNotEmpty()) {
                displayItems.add(DrawerItem.Folder(folder, position))
            }
        }
        
        // Add apps
        for (app in filteredApps) {
            displayItems.add(DrawerItem.App(app))
        }
        
        updateGrid()
    }

    private fun sortApps() {
        when (currentSortMode) {
            "name_asc" -> allApps.sortBy { it.name.lowercase() }
            "name_desc" -> allApps.sortByDescending { it.name.lowercase() }
            "recent" -> {
                val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
                val recentString = prefs.getString("recent_apps", "") ?: ""
                val recentList = if (recentString.isNotEmpty()) recentString.split(",") else emptyList()
                
                allApps.sortWith { a, b ->
                    val indexA = recentList.indexOf(a.packageName)
                    val indexB = recentList.indexOf(b.packageName)
                    when {
                        indexA == -1 && indexB == -1 -> a.name.lowercase().compareTo(b.name.lowercase())
                        indexA == -1 -> 1
                        indexB == -1 -> -1
                        else -> indexA - indexB
                    }
                }
            }
        }
    }

    private fun setupSearchBar() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApps(s.toString())
            }
        })
    }

    private fun filterApps(query: String) {
        filteredApps.clear()
        if (query.isEmpty()) {
            filteredApps.addAll(allApps)
        } else {
            filteredApps.addAll(allApps.filter { 
                it.name.contains(query, ignoreCase = true) 
            })
        }
        buildDisplayItems() // Rebuild display items
    }

    private fun setupSortMenu() {
        btnSortMenu.setOnClickListener { view ->
            val popup = AppCompatPopupMenu(this, view)
            popup.menu.add(0, 1, 0, "Nome (A-Z)")
            popup.menu.add(0, 2, 1, "Nome (Z-A)")
            popup.menu.add(0, 3, 2, "Recentes")

            popup.setOnMenuItemClickListener { item ->
                currentSortMode = when (item.itemId) {
                    1 -> "name_asc"
                    2 -> "name_desc"
                    3 -> "recent"
                    else -> "name_asc"
                }
                
                val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("sort_mode", currentSortMode).apply()
                
                sortApps()
                filterApps(searchBar.text.toString())
                true
            }
            popup.show()
        }
    }

    private var highlightedPosition = -1
    private var dragWasInitiated = false

    private fun setupGridTouchListener() {
        appGrid.setOnItemClickListener { _, _, position, _ ->
            if (position < displayItems.size && !dragWasInitiated) {
                when (val item = displayItems[position]) {
                    is DrawerItem.App -> launchApp(item.appInfo.packageName)
                    is DrawerItem.Folder -> showFolderDialog(item.folderInfo, item.position)
                }
            }
            dragWasInitiated = false
        }
        
        appGrid.setOnItemLongClickListener { _, view, position, _ ->
            if (position < displayItems.size) {
                when (val item = displayItems[position]) {
                    is DrawerItem.App -> {
                        if (item.appInfo.packageName == LAUNCHER_SETTINGS_PACKAGE) {
                            return@setOnItemLongClickListener true
                        }
                        
                        // Start drag operation
                        currentDragPackage = item.appInfo.packageName
                        currentDragPosition = position
                        isDragging = true
                        dragWasInitiated = true
                        
                        val clipData = ClipData.newPlainText("drawer_folder_app", item.appInfo.packageName)
                        val shadowBuilder = View.DragShadowBuilder(view)
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            view.startDragAndDrop(clipData, shadowBuilder, item.appInfo, 0)
                        } else {
                            @Suppress("DEPRECATION")
                            view.startDrag(clipData, shadowBuilder, item.appInfo, 0)
                        }
                    }
                    is DrawerItem.Folder -> {
                        showFolderOptionsDialog(item.folderInfo, item.position)
                    }
                }
            }
            true
        }

        appGrid.setOnDragListener { v, event ->
            handleGridDrag(v, event)
        }
    }

    private fun launchApp(packageName: String) {
        // Handle launcher settings app
        if (packageName == LAUNCHER_SETTINGS_PACKAGE) {
            val intent = Intent(this, LauncherSettingsActivity::class.java)
            startActivity(intent)
            closeDrawer()
            return
        }
        
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                // Track recent apps
                trackRecentApp(packageName)
                startActivity(launchIntent)
                closeDrawer()
            } else {
                Toast.makeText(this, "Não foi possível abrir o aplicativo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao abrir aplicativo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun trackRecentApp(packageName: String) {
        val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val recentString = prefs.getString("recent_apps", "") ?: ""
        val recentList = if (recentString.isNotEmpty()) {
            recentString.split(",").toMutableList()
        } else {
            mutableListOf()
        }
        
        // Remove if exists and add to front
        recentList.remove(packageName)
        recentList.add(0, packageName)
        
        // Keep only last 20
        val trimmedList = recentList.take(20)
        prefs.edit().putString("recent_apps", trimmedList.joinToString(",")).apply()
    }

    private fun closeDrawer() {
        sendBroadcast(Intent("com.example.launcher.CLOSE_DRAWER"))
        
        val slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
        slideOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                finish()
                overridePendingTransition(0, 0)
            }
        })
        drawerContent.startAnimation(slideOut)
    }

    private fun updateGrid() {
        appGrid.adapter = DrawerGridAdapter()
    }

    inner class DrawerGridAdapter : BaseAdapter() {
        override fun getCount(): Int = displayItems.size

        override fun getItem(position: Int): Any = displayItems[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@AppDrawerActivity)
                .inflate(R.layout.item_app, parent, false)

            val item = displayItems[position]
            val iconView = view.findViewById<ImageView>(R.id.appIcon)
            val labelView = view.findViewById<TextView>(R.id.appLabel)
            val notificationBadge = view.findViewById<TextView>(R.id.notificationBadge)

            when (item) {
                is DrawerItem.App -> {
                    iconView.setImageDrawable(item.appInfo.icon)
                    labelView.text = item.appInfo.name
                    notificationBadge.visibility = View.GONE
                }
                is DrawerItem.Folder -> {
                    // Create folder icon
                    iconView.setImageResource(android.R.drawable.ic_menu_agenda)
                    iconView.setColorFilter(Color.parseColor("#DD4814"))
                    labelView.text = item.folderInfo.name
                    // Show app count as badge
                    if (item.folderInfo.apps.size > 0) {
                        notificationBadge.visibility = View.VISIBLE
                        notificationBadge.text = item.folderInfo.apps.size.toString()
                    } else {
                        notificationBadge.visibility = View.GONE
                    }
                }
            }

            // Highlight drop target
            if (position == highlightedPosition) {
                view.setBackgroundColor(Color.parseColor("#44DD4814"))
            } else {
                view.setBackgroundColor(Color.TRANSPARENT)
            }

            return view
        }
    }

    private fun handleGridDrag(view: View, event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
            }
            
            DragEvent.ACTION_DRAG_ENTERED -> {
                return true
            }
            
            DragEvent.ACTION_DRAG_LOCATION -> {
                val position = appGrid.pointToPosition(event.x.toInt(), event.y.toInt())
                highlightDropTarget(position)
                return true
            }
            
            DragEvent.ACTION_DRAG_EXITED -> {
                clearDropHighlights()
                return true
            }
            
            DragEvent.ACTION_DROP -> {
                clearDropHighlights()
                
                val draggedAppInfo = event.localState as? AppInfo
                
                if (draggedAppInfo != null) {
                    val draggedPackage = draggedAppInfo.packageName
                    val targetPosition = appGrid.pointToPosition(event.x.toInt(), event.y.toInt())
                    
                    val originalPosition = displayItems.indexOfFirst { 
                        it is DrawerItem.App && it.appInfo.packageName == draggedPackage 
                    }
                    
                    if (targetPosition != GridView.INVALID_POSITION && 
                        targetPosition < displayItems.size && 
                        targetPosition != originalPosition) {
                        
                        val targetItem = displayItems[targetPosition]
                        
                        when (targetItem) {
                            is DrawerItem.Folder -> {
                                // Add to existing folder
                                if (!targetItem.folderInfo.apps.contains(draggedPackage)) {
                                    targetItem.folderInfo.apps.add(draggedPackage)
                                    
                                    // Remove from allApps
                                    allApps.removeAll { it.packageName == draggedPackage }
                                    filteredApps.removeAll { it.packageName == draggedPackage }
                                    
                                    saveDrawerFolders()
                                    buildDisplayItems()
                                    Toast.makeText(this, "App adicionado à pasta", Toast.LENGTH_SHORT).show()
                                }
                            }
                            
                            is DrawerItem.App -> {
                                if (targetItem.appInfo.packageName == LAUNCHER_SETTINGS_PACKAGE) {
                                    return true
                                }
                                
                                // Create new folder with both apps
                                if (targetItem.appInfo.packageName != draggedPackage) {
                                    val newFolderPosition = (drawerFolders.keys.maxOrNull() ?: -1) + 1
                                    val newFolder = DrawerFolderInfo(
                                        "Pasta", 
                                        mutableListOf(targetItem.appInfo.packageName, draggedPackage)
                                    )
                                    drawerFolders[newFolderPosition] = newFolder
                                    
                                    // Remove both apps from allApps
                                    allApps.removeAll { 
                                        it.packageName == draggedPackage || 
                                        it.packageName == targetItem.appInfo.packageName 
                                    }
                                    filteredApps.removeAll { 
                                        it.packageName == draggedPackage || 
                                        it.packageName == targetItem.appInfo.packageName 
                                    }
                                    
                                    saveDrawerFolders()
                                    buildDisplayItems()
                                    Toast.makeText(this, "Pasta criada", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                return true
            }
            
            DragEvent.ACTION_DRAG_ENDED -> {
                clearDropHighlights()
                isDragging = false
                dragWasInitiated = false
                currentDragPackage = null
                currentDragPosition = -1
                return true
            }
            
            else -> return true
        }
    }

    private fun highlightDropTarget(position: Int) {
        if (position != highlightedPosition) {
            highlightedPosition = position
            (appGrid.adapter as? DrawerGridAdapter)?.notifyDataSetChanged()
        }
    }

    private fun clearDropHighlights() {
        if (highlightedPosition != -1) {
            highlightedPosition = -1
            (appGrid.adapter as? DrawerGridAdapter)?.notifyDataSetChanged()
        }
    }

    private fun showFolderDialog(folder: DrawerFolderInfo, position: Int) {
        val pm = packageManager
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        
        // Folder name header (editable on click)
        val nameView = TextView(this).apply {
            text = folder.name
            textSize = 20f
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogView.addView(nameView)
        
        // Grid for folder apps
        val gridContainer = GridLayout(this).apply {
            columnCount = 3
            setPadding(0, 16, 0, 16)
        }
        
        val iconSize = (64 * resources.displayMetrics.density).toInt()
        
        for (appPackage in folder.apps) {
            try {
                val appInfo = pm.getApplicationInfo(appPackage, 0)
                val defaultIcon = appInfo.loadIcon(pm)
                val icon = customIconManager.getIconForApp(appPackage, defaultIcon)
                val label = appInfo.loadLabel(pm).toString()
                
                val itemView = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding(16, 16, 16, 16)
                }
                
                val iconView = ImageView(this).apply {
                    setImageDrawable(icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                }
                
                val labelView = TextView(this).apply {
                    text = label
                    textSize = 11f
                    setTextColor(Color.DKGRAY)
                    gravity = android.view.Gravity.CENTER
                    maxLines = 2
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 8
                    }
                }
                
                itemView.addView(iconView)
                itemView.addView(labelView)
                
                itemView.setOnClickListener {
                    launchApp(appPackage)
                }
                
                itemView.setOnLongClickListener {
                    showRemoveFromFolderDialog(folder, position, appPackage)
                    true
                }
                
                gridContainer.addView(itemView)
            } catch (e: Exception) {
                // App not found
            }
        }
        
        dialogView.addView(gridContainer)
        
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Fechar", null)
            .setNeutralButton("Renomear") { _, _ ->
                showRenameFolderDialog(folder, position)
            }
            .show()
    }

    private fun showRemoveFromFolderDialog(folder: DrawerFolderInfo, folderPosition: Int, appPackage: String) {
        AlertDialog.Builder(this)
            .setTitle("Remover da pasta")
            .setMessage("Deseja remover este app da pasta?")
            .setPositiveButton("Remover") { _, _ ->
                folder.apps.remove(appPackage)
                
                // If folder is empty, remove it
                if (folder.apps.isEmpty()) {
                    drawerFolders.remove(folderPosition)
                }
                
                saveDrawerFolders()
                loadInstalledApps() // Reload to add app back to main list
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showRenameFolderDialog(folder: DrawerFolderInfo, position: Int) {
        val input = EditText(this).apply {
            setText(folder.name)
            setSelectAllOnFocus(true)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Renomear pasta")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    folder.name = newName
                    saveDrawerFolders()
                    buildDisplayItems()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showFolderOptionsDialog(folder: DrawerFolderInfo, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(folder.name)
            .setItems(arrayOf("Abrir", "Renomear", "Excluir pasta")) { _, which ->
                when (which) {
                    0 -> showFolderDialog(folder, position)
                    1 -> showRenameFolderDialog(folder, position)
                    2 -> showDeleteFolderDialog(folder, position)
                }
            }
            .show()
    }

    private fun showDeleteFolderDialog(folder: DrawerFolderInfo, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Excluir pasta")
            .setMessage("Os apps da pasta voltarão para a gaveta. Deseja continuar?")
            .setPositiveButton("Excluir") { _, _ ->
                drawerFolders.remove(position)
                saveDrawerFolders()
                loadInstalledApps() // Reload to add apps back to main list
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onBackPressed() {
        closeDrawer()
    }
    
    private fun pickIconForApp(packageName: String) {
        pendingIconChangePackage = packageName
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        startActivityForResult(intent, PICK_ICON_REQUEST)
    }
    
    private fun restoreOriginalIcon(packageName: String) {
        AlertDialog.Builder(this)
            .setTitle("Restaurar ícone")
            .setMessage("Deseja restaurar o ícone original deste app?")
            .setPositiveButton("Sim") { _, _ ->
                if (customIconManager.removeCustomIcon(packageName)) {
                    Toast.makeText(this, "Ícone restaurado", Toast.LENGTH_SHORT).show()
                    loadInstalledApps()
                    // Notifica a MainActivity para atualizar o dock
                    sendBroadcast(Intent("com.example.launcher.REFRESH_DOCK"))
                } else {
                    Toast.makeText(this, "Erro ao restaurar ícone", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PICK_ICON_REQUEST && resultCode == Activity.RESULT_OK) {
            val imageUri = data?.data
            val packageName = pendingIconChangePackage
            
            if (imageUri != null && packageName != null) {
                if (customIconManager.saveCustomIcon(packageName, imageUri)) {
                    Toast.makeText(this, "Ícone alterado com sucesso", Toast.LENGTH_SHORT).show()
                    loadInstalledApps()
                    // Notifica a MainActivity para atualizar o dock
                    sendBroadcast(Intent("com.example.launcher.REFRESH_DOCK"))
                } else {
                    Toast.makeText(this, "Erro ao salvar ícone", Toast.LENGTH_SHORT).show()
                }
            }
            pendingIconChangePackage = null
        }
    }

    private fun showAppContextMenu(view: View?, app: AppInfo) {
        val options = mutableListOf(
            "Fixar no dock",
            "Informações do app",
            "Trocar ícone"
        )
        if (customIconManager.hasCustomIcon(app.packageName)) {
            options.add("Restaurar ícone original")
        }
        options.add("Desinstalar")
        
        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Fixar no dock" -> pinAppToDock(app.packageName)
                    "Informações do app" -> openAppInfo(app.packageName)
                    "Trocar ícone" -> pickIconForApp(app.packageName)
                    "Restaurar ícone original" -> restoreOriginalIcon(app.packageName)
                    "Desinstalar" -> uninstallApp(app.packageName)
                }
            }
            .show()
    }

    private fun pinAppToDock(packageName: String) {
        val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val pinnedString = prefs.getString("pinned_apps_ordered", null)
        val pinnedList = if (pinnedString != null && pinnedString.isNotEmpty()) {
            pinnedString.split(",").toMutableList()
        } else {
            mutableListOf()
        }

        if (!pinnedList.contains(packageName)) {
            pinnedList.add(packageName)
            prefs.edit().putString("pinned_apps_ordered", pinnedList.joinToString(",")).apply()
            Toast.makeText(this, "App fixado no dock", Toast.LENGTH_SHORT).show()
            
            // Notify MainActivity to refresh dock
            val intent = Intent("com.example.launcher.REFRESH_DOCK")
            sendBroadcast(intent)
        } else {
            Toast.makeText(this, "App já está no dock", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppInfo(packageName: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao abrir informações", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uninstallApp(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao desinstalar", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        packageChangeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

