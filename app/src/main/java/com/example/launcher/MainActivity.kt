package com.example.launcher

import android.Manifest
import android.app.WallpaperManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.MotionEvent
import android.view.DragEvent
import android.widget.GridView
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.content.ClipData
import android.content.ClipDescription // Added for ClipDescription
import android.graphics.Canvas
import android.graphics.Point
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Vibrator
import android.provider.Settings
import android.widget.SeekBar
import android.widget.PopupMenu
import android.view.Gravity
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.graphics.Color
import kotlin.math.abs
import android.app.NotificationManager
import androidx.activity.OnBackPressedCallback
import android.widget.TextView // Added for TextView
import android.widget.GridLayout // Added for GridLayout
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.util.Calendar
import android.graphics.Typeface
import android.graphics.Paint // Added for Paint
import android.widget.ScrollView
import android.graphics.drawable.GradientDrawable // Added for GradientDrawable
import androidx.core.app.NotificationManagerCompat // Added for notification listener check
import android.widget.Button // Added for Button
import android.text.TextUtils // Added import for TextUtils
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager

data class FolderInfo(
    var name: String = "Pasta",
    val apps: MutableList<String> = mutableListOf()
)

class MainActivity : AppCompatActivity() {

    private lateinit var btnAppDrawer: ImageView
    private lateinit var appDrawerContainer: FrameLayout
    private lateinit var appGrid: GridView
    private lateinit var searchBar: EditText
    private lateinit var btnSortMenu: ImageView
    private lateinit var appList: List<AppInfo>
    private lateinit var filteredAppList: MutableList<AppInfo>
    private lateinit var dockAppsContainer: LinearLayout
    private lateinit var dockContainer: LinearLayout
    private lateinit var dockScrollView: ScrollView
    private lateinit var pinnedApps: MutableList<String>
    
    private lateinit var hiddenApps: MutableList<String>
    private var showHiddenApps = false
    
    private lateinit var desktopPinnedApps: MutableMap<Int, String>
    private lateinit var desktopFolders: MutableMap<Int, FolderInfo>
    
    private lateinit var dropZoneContainer: FrameLayout
    private lateinit var dropZoneArc: View
    private lateinit var dropZoneOptions: LinearLayout
    private lateinit var dropZoneUnpin: LinearLayout
    private lateinit var dropZoneCircle: View // Simplificado para apenas o círculo
    private var isOverDropZone = false
    private var currentDragPackage: String? = null
    private var currentDragPosition: Int = -1 // Para rastrear a posição de origem da tela inicial
    
    private val DESKTOP_COLUMNS = 5 // Changed from 4 to 5
    private val DESKTOP_ROWS = 11
    private val DESKTOP_MAX_APPS = DESKTOP_COLUMNS * DESKTOP_ROWS
    
    private lateinit var adapter: AppGridAdapter
    private lateinit var homeBackground: FrameLayout
    private lateinit var wallpaperView: ImageView
    private lateinit var desktopGrid: GridLayout // Added for desktop grid
    
    private lateinit var controlPanelContainer: FrameLayout
    private lateinit var wifiToggle: LinearLayout
    private lateinit var bluetoothToggle: LinearLayout
    private lateinit var flashlightToggle: LinearLayout
    private lateinit var airplaneModeToggle: LinearLayout
    private lateinit var calculatorToggle: LinearLayout
    private lateinit var locationToggle: LinearLayout
    private lateinit var brightnessSeekBar: SeekBar

    private lateinit var wifiIcon: ImageView
    private lateinit var bluetoothIcon: ImageView
    private lateinit var flashlightIcon: ImageView
    private lateinit var airplaneModeIcon: ImageView
    private lateinit var calculatorIcon: ImageView
    private lateinit var locationIcon: ImageView

    private lateinit var widgetContainer: FrameLayout
    data class WidgetInfo(
        val id: String = java.util.UUID.randomUUID().toString(),
        var style: Int,
        var x: Float = 16f,
        var y: Float = 16f,
        var scale: Float = 1.0f
    )
    private val widgets = mutableListOf<WidgetInfo>()
    private var editingWidgetId: String? = null // Track which widget is being edited
    private val clockHandler = Handler(Looper.getMainLooper())
    private lateinit var clockRunnable: Runnable

    private var isWidgetEditMode: Boolean = false

    private var isDragging = false
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var dragDelayRunnable: Runnable? = null
    private var dragDelayTime = 0L // milliseconds before drag can start
    private val dragDelayDuration = 300L // milliseconds before drag can start
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var currentTouchView: View? = null
    private var currentAppInfo: AppInfo? = null
    private val dragThreshold = 20f // pixels to consider as drag movement
    private var isDraggingFromDock = false
    private var draggedDockPackageName: String? = null
    private var draggedDockIndex = -1
    private var isFlashlightOn = false
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var dockSwipeStartX = 0f
    private var dockSwipeStartY = 0f
    private var isDockSwiping = false
    private var isDockHidden = false

    private var pinnedAppsReceiver: BroadcastReceiver? = null
    private var notificationReceiver: BroadcastReceiver? = null
    
    private var wallpaperReceiver: BroadcastReceiver? = null
    
    private var packageChangeReceiver: BroadcastReceiver? = null
    
    private var pendingIconChangePackage: String? = null
    private val PICK_ICON_REQUEST = 1001
    
    private val packageRemovedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_REMOVED) {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName != null) {
                    onAppUninstalled(packageName)
                }
            }
        }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            pickImageLauncher.launch("image/*")
        } else {
            Toast.makeText(this, "Permissão necessária para escolher imagem", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { setWallpaperFromUri(it) }
    }

    private lateinit var customIconManager: CustomIconManager

    private var gestureServiceWatcher: GestureServiceWatcher? = null

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 101
        private const val REQUEST_CAMERA_PERMISSION = 102
        private const val REQUEST_WRITE_SETTINGS = 103
        private const val REQUEST_OVERLAY_PERMISSION = 104 // Adicionada constante para permissão de overlay
        private const val REQUEST_NOTIFICATION_LISTENER = 105 // Added constant for notification listener
        private const val REQUEST_ACCESSIBILITY_PERMISSION = 106 // Added constant for accessibility permission
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        if (!isPermissionsSetupComplete()) {
            startActivity(Intent(this, PermissionsSetupActivity::class.java))
            finish() // Finish MainActivity so it doesn't show until setup is done
            return
        }
        
        customIconManager = CustomIconManager(this)
        
        // Initialize GestureServiceWatcher
        gestureServiceWatcher = GestureServiceWatcher(this) {
            checkAccessibilityPermission()
        }
        // </CHANGE>
        
        initViews()
        setupBackPressedHandler() // Renamed to setupBackPressedHandler based on update
        setupControlPanel()
        setupDropZones() // Renamed to setupDropZones based on update
        loadPinnedApps()
        loadHiddenApps()
        loadDesktopPinnedApps() // Changed to loadDesktopApps()
        loadSavedWallpaper()
        loadWidgets() // Changed to loadWidgets() for widget loading
        loadInstalledApps()
        
        filteredAppList = appList.toMutableList()
        adapter = AppGridAdapter(this, filteredAppList)
        appGrid.adapter = adapter

        val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED)
        filter.addDataScheme("package")
        registerReceiver(packageRemovedReceiver, filter)
        
        registerWallpaperReceiver() // Call the newly added receiver registration
        
        registerPackageChangeReceiver()
        
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })

        btnAppDrawer.setOnClickListener {
            toggleAppDrawer()
        }
        
        btnAppDrawer.setOnLongClickListener {
            toggleControlPanel()
            true
        }

        btnSortMenu.setOnClickListener { view ->
            showSortMenu(view)
        }

        appDrawerContainer.setOnClickListener {
            closeAppDrawer()
        }

        setupGridTouchListener()
        setupDrawerDragListener()

        appGrid.setOnItemClickListener { _, _, position, _ ->
            if (!isDragging) {
                val appInfo = filteredAppList[position]
                launchApp(appInfo.packageName)
            }
        }
        
        appGrid.setOnItemLongClickListener { _, _, _, _ ->
            // Long click handling is done in touch listener
            true
        }
        
        setupDockDragListener()
        
        homeBackground.setOnLongClickListener {
            showHomeScreenOptions()
            true
        }

        renderPinnedApps()
        
        setupControlPanel()
        setupDockSwipeListener()
        setupDropZones() // Inicializar drop zones
        requestControlPanelPermissions()
        
        setupBackPressedHandler()
        
        checkOverlayPermissionAndStartService()
        handleIntent(intent)
        
        initControlPanelViews() // Call the new function to initialize control panel views
        
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager // Corrected service name
        try {
            cameraId = cameraManager?.cameraIdList?.get(0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Start clock updates
        startClockUpdates()
        
        registerPinnedAppsReceiver()
        registerNotificationReceiver()
        
        checkNotificationListenerPermission()
        
        gestureServiceWatcher = GestureServiceWatcher(this) {
            checkAccessibilityPermission()
        }
        gestureServiceWatcher?.startWatching()
    }
    
    private fun initViews() {
        btnAppDrawer = findViewById(R.id.btnAppDrawer)
        appDrawerContainer = findViewById(R.id.appDrawerContainer)
        appGrid = findViewById(R.id.appGrid)
        searchBar = findViewById(R.id.searchBar)
        dockAppsContainer = findViewById(R.id.dockAppsContainer)
        dockContainer = findViewById(R.id.dockContainer)
        dockScrollView = findViewById(R.id.dockScrollView)
        controlPanelContainer = findViewById(R.id.controlPanelContainer)
        wallpaperView = findViewById(R.id.wallpaperView)
        desktopGrid = findViewById(R.id.desktopGrid)
        btnSortMenu = findViewById(R.id.btnSortMenu)
        homeBackground = findViewById(R.id.homeBackground)
        
        dropZoneCircle = findViewById(R.id.dropZoneCircle)
        
        wifiToggle = findViewById(R.id.wifiToggle)
        bluetoothToggle = findViewById(R.id.bluetoothToggle)
        flashlightToggle = findViewById(R.id.flashlightToggle)
        airplaneModeToggle = findViewById(R.id.airplaneModeToggle)
        calculatorToggle = findViewById(R.id.calculatorToggle)
        locationToggle = findViewById(R.id.locationToggle)
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar)
        
        wifiIcon = findViewById(R.id.wifiIcon)
        bluetoothIcon = findViewById(R.id.bluetoothIcon)
        flashlightIcon = findViewById(R.id.flashlightIcon)
        airplaneModeIcon = findViewById(R.id.airplaneModeIcon)
        calculatorIcon = findViewById(R.id.calculatorIcon)
        locationIcon = findViewById(R.id.locationIcon)

        widgetContainer = findViewById(R.id.widgetContainer)
    }

    private fun initControlPanelViews() {
        controlPanelContainer = findViewById(R.id.controlPanelContainer)
        
        wifiToggle = findViewById(R.id.wifiToggle)
        bluetoothToggle = findViewById(R.id.bluetoothToggle)
        flashlightToggle = findViewById(R.id.flashlightToggle)
        airplaneModeToggle = findViewById(R.id.airplaneModeToggle)
        calculatorToggle = findViewById(R.id.calculatorToggle)
        locationToggle = findViewById(R.id.locationToggle)
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar)
        
        wifiIcon = findViewById(R.id.wifiIcon)
        bluetoothIcon = findViewById(R.id.bluetoothIcon)
        flashlightIcon = findViewById(R.id.flashlightIcon)
        airplaneModeIcon = findViewById(R.id.airplaneModeIcon)
        calculatorIcon = findViewById(R.id.calculatorIcon)
        locationIcon = findViewById(R.id.locationIcon)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }
    
    override fun onStart() {
        super.onStart()
        // Start the watcher
        gestureServiceWatcher?.startWatching()
        // </CHANGE>
    }

    override fun onStop() {
        super.onStop()
        // Stop the watcher
        gestureServiceWatcher?.stopWatching()
        // </CHANGE>
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "OPEN_APP_DRAWER") {
            if (appDrawerContainer.visibility != View.VISIBLE) {
                toggleAppDrawer()
            }
        }
    }
    
    private fun checkOverlayPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                startEdgeSwipeService()
            } else {
                // Request overlay permission
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
            }
        } else {
            startEdgeSwipeService()
        }
    }
    
    private fun startEdgeSwipeService() {
        val serviceIntent = Intent(this, EdgeSwipeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_OVERLAY_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        startEdgeSwipeService()
                        checkAccessibilityPermission() // Check accessibility after overlay permission
                    } else {
                        Toast.makeText(this, "Permissão de overlay necessária para edge swipe", Toast.LENGTH_LONG).show()
                    }
                }
            }
            REQUEST_ACCESSIBILITY_PERMISSION -> {
                if (isAccessibilityServiceEnabled()) {
                    Toast.makeText(this, "Gestos de navegação ativados!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Gestos de navegação não foram ativados", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_WRITE_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.System.canWrite(this)) {
                        Toast.makeText(this, "Permissão para modificar configurações concedida", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Permissão para modificar configurações negada", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            PICK_ICON_REQUEST -> {
                if (resultCode == RESULT_OK) {
                    val imageUri = data?.data
                    val packageName = pendingIconChangePackage
                    
                    if (imageUri != null && packageName != null) {
                        if (customIconManager.saveCustomIcon(packageName, imageUri)) {
                            Toast.makeText(this, "Ícone alterado com sucesso", Toast.LENGTH_SHORT).show()
                            // Atualiza a UI
                            loadInstalledApps()
                            filterApps(searchBar.text.toString())
                            renderPinnedApps()
                            setupDesktopGrid()
                            // Notifica o serviço para atualizar
                            sendBroadcast(Intent("com.example.launcher.REFRESH_DOCK"))
                        } else {
                            Toast.makeText(this, "Erro ao salvar ícone", Toast.LENGTH_SHORT).show()
                        }
                    }
                    pendingIconChangePackage = null
                }
            }
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) {
            0
        }
        
        if (accessibilityEnabled == 1) {
            val serviceString = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (!TextUtils.isEmpty(serviceString)) {
                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(serviceString)
                while (colonSplitter.hasNext()) {
                    val componentName = colonSplitter.next()
                    if (componentName.equals("$packageName/${NavigationGestureService::class.java.canonicalName}", ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }
    
    private fun checkAccessibilityPermission() {
        if (isAccessibilityServiceEnabled()) {
            // Service is enabled, no action needed
        } else {
            // If not enabled, prompt user
            AlertDialog.Builder(this)
                .setTitle("Permissão de Acessibilidade")
                .setMessage("Para habilitar os gestos de navegação, por favor, ative o serviço 'Navigation Gesture Service' nas configurações de acessibilidade.")
                .setPositiveButton("Abrir Configurações") { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivityForResult(intent, REQUEST_ACCESSIBILITY_PERMISSION)
                }
                .setNegativeButton("Agora não", null)
                .show()
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    appDrawerContainer.visibility == View.VISIBLE -> {
                        closeAppDrawer()
                    }
                    controlPanelContainer.visibility == View.VISIBLE -> {
                        closeControlPanel()
                    }
                    isWidgetEditMode -> {
                        exitWidgetEditMode()
                    }
                    else -> {
                        // Stay on home screen, don't exit the launcher
                    }
                }
            }
        })
    }

    private fun setupGridTouchListener() {
        val grid = appGrid ?: return
        
        grid.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    isDragging = false
                    dragDelayTime = 0L
                    
                    val position = grid.pointToPosition(event.x.toInt(), event.y.toInt())
                    if (position != GridView.INVALID_POSITION) {
                        currentAppInfo = filteredAppList.getOrNull(position)
                        currentTouchView = grid.getChildAt(position - grid.firstVisiblePosition)
                        
                        longPressRunnable = Runnable {
                            if (!isDragging && currentAppInfo != null) {
                                showAppOptionsDialog(currentAppInfo!!)
                                currentAppInfo = null
                                currentTouchView = null
                            }
                        }
                        longPressRunnable?.let { longPressHandler.postDelayed(it, 600) }
                    }
                }
                
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(event.x - touchDownX)
                    val deltaY = abs(event.y - touchDownY)
                    
                    if ((deltaX > dragThreshold || deltaY > dragThreshold) && 
                        !isDragging && currentAppInfo != null && currentTouchView != null) {
                        
                        if (dragDelayTime == 0L) {
                            dragDelayTime = System.currentTimeMillis() + dragDelayDuration
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        } else if (System.currentTimeMillis() >= dragDelayTime) {
                            isDragging = true
                            // Set clip data for drawer apps
                            val clipData = ClipData.newPlainText("drawer_app:${currentAppInfo!!.packageName}", "drawer_app:${currentAppInfo!!.packageName}")
                            val shadowBuilder = View.DragShadowBuilder(currentTouchView)
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                currentTouchView!!.startDragAndDrop(clipData, shadowBuilder, currentAppInfo, 0)
                            } else {
                                currentTouchView!!.startDrag(clipData, shadowBuilder, currentAppInfo, 0)
                            }
                            
                            dragDelayTime = 0L
                        }
                    }
                }
                
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    dragDelayTime = 0L
                    if (!isDragging) {
                        currentAppInfo = null
                        currentTouchView = null
                    }
                }
            }
            false
        }
    }
    
    private fun setupDockDragListener() {
        dockContainer.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    if (!isDraggingFromDock) {
                        dockContainer.alpha = 0.7f
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (!isDraggingFromDock) {
                        dockContainer.alpha = 1.0f
                    }
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    if (!isDraggingFromDock) {
                        dockContainer.alpha = 0.7f
                    }
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val clipData = event.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        val itemText = clipData.getItemAt(0).text.toString()
                        
                        val dropY = event.y
                        val targetPosition = calculateDockDropPosition(dropY)
                        
                        if (isDraggingFromDock) {
                            // Reordering within dock - do nothing here as it's handled per icon
                        } else if (itemText.startsWith("drawer_app:")) {
                            val packageName = itemText.replace("drawer_app:", "")
                            val appInfo = filteredAppList.find { app -> app.packageName == packageName }
                            if (appInfo != null && !pinnedApps.contains(packageName)) {
                                pinAppAtPosition(appInfo, targetPosition)
                            }
                        } else if (itemText.startsWith("desktop_app:")) {
                            val packageName = itemText.replace("desktop_app:", "")
                            val appInfo = filteredAppList.find { app -> app.packageName == packageName }
                            if (appInfo != null && !pinnedApps.contains(packageName)) {
                                // Remove from desktop
                                val positionToRemove = desktopPinnedApps.entries.find { it.value == packageName }?.key
                                if (positionToRemove != null) {
                                    desktopPinnedApps.remove(positionToRemove)
                                    saveDesktopPinnedApps()
                                    setupDesktopGrid()
                                }
                                pinAppAtPosition(appInfo, targetPosition)
                            }
                        } else {
                            // Legacy format without prefix
                            val appInfo = filteredAppList.find { app -> app.packageName == itemText }
                            if (appInfo != null) {
                                pinAppAtPosition(appInfo, targetPosition)
                            }
                        }
                    }
                    dockContainer.alpha = 1.0f
                    isDragging = false
                    isDraggingFromDock = false
                    currentAppInfo = null
                    currentTouchView = null
                    draggedDockPackageName = null
                    draggedDockIndex = -1
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    dockContainer.alpha = 1.0f
                    isDragging = false
                    isDraggingFromDock = false
                    currentAppInfo = null
                    currentTouchView = null
                    draggedDockPackageName = null
                    draggedDockIndex = -1
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupDrawerDragListener() {
        appDrawerContainer.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    val clipDesc = event.clipDescription
                    val hasText = clipDesc?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                    if (hasText && isDraggingFromDock) {
                        appDrawerContainer.setBackgroundColor(Color.parseColor("#99000000"))
                    }
                    hasText
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (isDraggingFromDock) {
                        appDrawerContainer.setBackgroundColor(Color.parseColor("#66FFFFFF"))
                    }
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    if (isDraggingFromDock) {
                        appDrawerContainer.setBackgroundColor(Color.parseColor("#99000000"))
                    }
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val clipData = event.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        val itemText = clipData.getItemAt(0).text.toString()
                        
                        if (itemText.startsWith("dock_app:")) {
                            val packageName = itemText.replace("dock_app:", "")
                            unpinApp(packageName)
                            filterApps(searchBar.text.toString())
                        }
                    }
                    appDrawerContainer.setBackgroundColor(Color.parseColor("#B3000000"))
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    appDrawerContainer.setBackgroundColor(Color.parseColor("#B3000000"))
                    true
                }
                else -> true
            }
        }
    }
    
    private fun startDragForApp(view: View, appInfo: AppInfo) {
        val clipData = ClipData.newPlainText(appInfo.label, "drawer_app:${appInfo.packageName}")
        val shadowBuilder = View.DragShadowBuilder(view)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(clipData, shadowBuilder, appInfo, 0)
        } else {
            view.startDrag(clipData, shadowBuilder, appInfo, 0)
        }
    }

    private fun onAppUninstalled(packageName: String) {
        if (pinnedApps.contains(packageName)) {
            pinnedApps.remove(packageName)
            savePinnedApps()
            renderPinnedApps()
            notifyPinnedAppsChanged() // Notify service about dock change
        }
        if (desktopPinnedApps.containsValue(packageName)) { // Changed to check value in map
            unpinAppFromDesktop(packageName) // Use existing method to handle map removal
        }
        // Also remove from folders
        desktopFolders.values.forEach { folder ->
            if (folder.apps.remove(packageName)) {
                saveDesktopFolders()
                setupDesktopGrid()
            }
        }
        
        loadInstalledApps()
        filterApps(searchBar.text.toString()) // Re-filter to update the visible list
        
        Toast.makeText(this, "App desinstalado", Toast.LENGTH_SHORT).show()
    }
    
    private fun showHomeScreenOptions() {
        val options = arrayOf(
            "Alterar papel de parede",
            "Adicionar Widget",
            "Remover Widget"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Opções da Tela Inicial")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showWallpaperOptions()
                    1 -> showWidgetSelector()
                    2 -> removeWidget()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showWallpaperOptions() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_wallpaper_picker, null)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        dialogView.findViewById<LinearLayout>(R.id.option_gallery).setOnClickListener {
            dialog.dismiss()
            checkPermissionAndPickImage()
        }
        
        dialogView.findViewById<FrameLayout>(R.id.option_default).setOnClickListener {
            dialog.dismiss()
            setDefaultWallpaper()
        }
        
        dialogView.findViewById<FrameLayout>(R.id.option_ubuntu_gradient).setOnClickListener {
            dialog.dismiss()
            setPresetWallpaper(R.drawable.wallpaper_ubuntu_gradient, "ubuntu_gradient")
        }
        
        dialogView.findViewById<FrameLayout>(R.id.option_ubuntu_logo).setOnClickListener {
            dialog.dismiss()
            setPresetWallpaper(R.drawable.wallpaper_ubuntu_logo, "ubuntu_logo")
        }
        
        dialogView.findViewById<FrameLayout>(R.id.option_focal_fossa).setOnClickListener {
            dialog.dismiss()
            setPresetWallpaper(R.drawable.wallpaper_cat, "focal_fossa")
        }
        
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun checkPermissionAndPickImage() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                pickImageLauncher.launch("image/*")
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                AlertDialog.Builder(this)
                    .setTitle("Permissão de Armazenamento")
                    .setMessage("Precisamos de permissão para acessar suas fotos para definir o papel de parede.")
                    .setPositiveButton("Ok") { _, _ ->
                        requestPermissionLauncher.launch(permission)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            else -> {
                // No explanation needed, we can request the permission.
                requestPermissionLauncher.launch(permission)
            }
        }
    }
    
    private fun setWallpaperFromUri(uri: Uri) {
        try {
            wallpaperView.setImageURI(uri)
            
            val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            prefs.edit().putString("custom_wallpaper_uri", uri.toString()).apply()
            
            Toast.makeText(this, "Papel de parede alterado com sucesso!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao alterar papel de parede", Toast.LENGTH_SHORT).show()
            setDefaultWallpaper()
        }
    }
    
    private fun setDefaultWallpaper() {
        try {
            wallpaperView.setImageResource(R.drawable.default_wallpaper)
            
            val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            prefs.edit()
                .remove("custom_wallpaper_uri")
                .remove("preset_wallpaper")  // Also clear preset wallpaper
                .apply()
            
            Toast.makeText(this, "Papel de parede restaurado para o padrão", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setPresetWallpaper(resourceId: Int, presetName: String) {
        try {
            wallpaperView.setImageResource(resourceId)
            
            val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            prefs.edit()
                .remove("custom_wallpaper_uri")
                .putString("preset_wallpaper", presetName)
                .apply()
            
            Toast.makeText(this, "Papel de parede alterado com sucesso!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao alterar papel de parede", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadSavedWallpaper() {
        try {
            val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            val savedUri = prefs.getString("custom_wallpaper_uri", null)
            val presetWallpaper = prefs.getString("preset_wallpaper", null)
            
            when {
                savedUri != null && savedUri.isNotEmpty() -> {
                    val uri = Uri.parse(savedUri)
                    try {
                        wallpaperView.setImageURI(uri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        wallpaperView.setImageResource(R.drawable.default_wallpaper)
                        prefs.edit().remove("custom_wallpaper_uri").apply()
                    }
                }
                presetWallpaper != null -> {
                    val resourceId = when (presetWallpaper) {
                        "ubuntu_gradient" -> R.drawable.wallpaper_ubuntu_gradient
                        "ubuntu_logo" -> R.drawable.wallpaper_ubuntu_logo
                        "focal_fossa" -> R.drawable.wallpaper_cat
                        else -> R.drawable.default_wallpaper
                    }
                    wallpaperView.setImageResource(resourceId)
                }
                else -> {
                    wallpaperView.setImageResource(R.drawable.default_wallpaper)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                wallpaperView.setImageResource(R.drawable.default_wallpaper)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(packageRemovedReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        longPressHandler.removeCallbacksAndMessages(null)
        stopClockUpdates()
        pinnedAppsReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        notificationReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        packageChangeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        wallpaperReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        gestureServiceWatcher?.stopWatching()
        gestureServiceWatcher = null
    }

    private fun filterApps(query: String) {
        filteredAppList.clear()
        
        val visibleApps = appList.filter { app ->
            val isHidden = hiddenApps.contains(app.packageName)
            val isPinnedToDock = pinnedApps.contains(app.packageName)
            
            // Show app if: not hidden OR we're showing hidden apps
            // AND not pinned to dock (since it's already in the dock)
            (showHiddenApps || !isHidden) && !isPinnedToDock
        }
        
        if (query.isEmpty()) {
            filteredAppList.addAll(visibleApps)
        } else {
            val lowerQuery = query.lowercase()
            filteredAppList.addAll(
                visibleApps.filter { it.label.lowercase().contains(lowerQuery) }
            )
        }
        
        adapter.notifyDataSetChanged()
    }

    private fun loadPinnedApps() {
        try {
            val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            val pinnedString = prefs.getString("pinned_apps_ordered", null)
            pinnedApps = if (pinnedString != null && pinnedString.isNotEmpty()) {
                pinnedString.split(",").toMutableList()
            } else {
                // Migração de dados antigos (se existir)
                val pinnedSet = prefs.getStringSet("pinned_apps", null)
                val list = pinnedSet?.toMutableList() ?: mutableListOf()
                if (list.isNotEmpty()) {
                    // Salvar no novo formato
                    prefs.edit().putString("pinned_apps_ordered", list.joinToString(",")).apply()
                    prefs.edit().remove("pinned_apps").apply()
                }
                list
            }
        } catch (e: Exception) {
            e.printStackTrace()
            pinnedApps = mutableListOf()
        }
    }

    private fun savePinnedApps() {
        try {
            val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            prefs.edit().putString("pinned_apps_ordered", pinnedApps.joinToString(",")).apply()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao salvar apps fixados", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadHiddenApps() {
        try {
            val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            val hiddenString = prefs.getString("hidden_apps", null)
            hiddenApps = if (hiddenString != null && hiddenString.isNotEmpty()) {
                hiddenString.split(",").toMutableList()
            } else {
                mutableListOf()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            hiddenApps = mutableListOf()
        }
    }

    private fun saveHiddenApps() {
        try {
            val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            prefs.edit().putString("hidden_apps", hiddenApps.joinToString(",")).apply()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao salvar apps ocultos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideApp(packageName: String, label: String) {
        if (!hiddenApps.contains(packageName)) {
            hiddenApps.add(packageName)
            saveHiddenApps()
            filterApps(searchBar.text.toString())
            Toast.makeText(this, "$label oculto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showApp(packageName: String, label: String) {
        if (hiddenApps.contains(packageName)) {
            hiddenApps.remove(packageName)
            saveHiddenApps()
            filterApps(searchBar.text.toString())
            Toast.makeText(this, "$label visível novamente", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDesktopPinnedApps() {
        try {
            val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            val pinnedString = prefs.getString("desktop_pinned_apps_v2", null)
            desktopPinnedApps = mutableMapOf()
            
            if (pinnedString != null && pinnedString.isNotEmpty()) {
                // Formato: "posição:packageName,posição:packageName,..."
                pinnedString.split(",").forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        val position = parts[0].toIntOrNull()
                        val packageName = parts[1]
                        if (position != null && position in 0 until DESKTOP_MAX_APPS) {
                            desktopPinnedApps[position] = packageName
                        }
                    }
                }
            }
            
            loadDesktopFolders()
        } catch (e: Exception) {
            e.printStackTrace()
            desktopPinnedApps = mutableMapOf()
            desktopFolders = mutableMapOf()
        }
    }

    private fun saveDesktopPinnedApps() {
        try {
            val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            val pinnedString = desktopPinnedApps.entries.joinToString(",") { "${it.key}:${it.value}" }
            prefs.edit().putString("desktop_pinned_apps_v2", pinnedString).apply()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao salvar apps da tela inicial", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDesktopFolders() {
        try {
            val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            val foldersString = prefs.getString("desktop_folders_v1", null)
            desktopFolders = mutableMapOf()
            
            if (foldersString != null && foldersString.isNotEmpty()) {
                // Formato: "posição|nome|pkg1;pkg2;pkg3,posição|nome|pkg1;pkg2"
                foldersString.split(",").forEach { entry ->
                    val parts = entry.split("|")
                    if (parts.size == 3) {
                        val position = parts[0].toIntOrNull()
                        val name = parts[1]
                        val apps = parts[2].split(";").filter { it.isNotEmpty() }.toMutableList()
                        if (position != null && position in 0 until DESKTOP_MAX_APPS && apps.isNotEmpty()) {
                            desktopFolders[position] = FolderInfo(name, apps)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            desktopFolders = mutableMapOf()
        }
    }
    
    private fun saveDesktopFolders() {
        try {
            val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
            val foldersString = desktopFolders.entries.joinToString(",") { entry ->
                "${entry.key}|${entry.value.name}|${entry.value.apps.joinToString(";")}"
            }
            prefs.edit().putString("desktop_folders_v1", foldersString).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pinAppToDesktop(appInfo: AppInfo) {
        // Verifica se já está fixado
        if (desktopPinnedApps.containsValue(appInfo.packageName)) {
            Toast.makeText(this, "${appInfo.label} já está na tela inicial", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Encontra a primeira posição livre
        var freePosition = -1
        for (i in 0 until DESKTOP_MAX_APPS) {
            if (!desktopPinnedApps.containsKey(i) && !desktopFolders.containsKey(i)) { // Also check if position is not used by a folder
                freePosition = i
                break
            }
        }
        
        if (freePosition == -1) {
            Toast.makeText(this, "Tela inicial cheia (máx. $DESKTOP_MAX_APPS apps)", Toast.LENGTH_SHORT).show()
            return
        }
        
        desktopPinnedApps[freePosition] = appInfo.packageName
        saveDesktopPinnedApps()
        setupDesktopGrid()
        Toast.makeText(this, "${appInfo.label} fixado na tela inicial", Toast.LENGTH_SHORT).show()
    }

    private fun unpinAppFromDesktop(packageName: String) {
        val position = desktopPinnedApps.entries.find { it.value == packageName }?.key
        if (position != null) {
            desktopPinnedApps.remove(position)
            saveDesktopPinnedApps()
            setupDesktopGrid()
            Toast.makeText(this, "App removido da tela inicial", Toast.LENGTH_SHORT).show()
        }
    }

    // Renamed from renderDesktopApps to setupDesktopGrid for clarity
    private fun setupDesktopGrid() {
        desktopGrid.removeAllViews()
        desktopGrid.columnCount = DESKTOP_COLUMNS
        desktopGrid.rowCount = DESKTOP_ROWS
        val pm = packageManager
        val iconSize = (64 * resources.displayMetrics.density).toInt()
        
        // Cria todas as 44 células (4 colunas x 11 linhas)
        for (position in 0 until DESKTOP_MAX_APPS) {
            val row = position / DESKTOP_COLUMNS
            val col = position % DESKTOP_COLUMNS
            
            val cellView = FrameLayout(this).apply {
                tag = position // Armazena a posição na tag
            }
            
            val gridParams = GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(row, 1f)
                columnSpec = GridLayout.spec(col, 1f)
                width = 0 // Will be expanded by weight
                height = 0 // Will be expanded by weight
                setMargins(4, 4, 4, 4)
            }
            cellView.layoutParams = gridParams
            
            val folder = desktopFolders[position]
            val packageName = desktopPinnedApps[position]
            
            cellView.setOnDragListener { v, event ->
                handleDesktopDrag(v, event, position)
            }
            
            if (folder != null && folder.apps.isNotEmpty()) {
                renderFolderCell(cellView, folder, position, iconSize, pm)
            } else if (packageName != null) {
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val defaultIcon = appInfo.loadIcon(pm)
                    val icon = customIconManager.getIconForApp(packageName, defaultIcon)
                    val label = appInfo.loadLabel(pm).toString()
                    
                    val itemView = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = android.view.Gravity.CENTER
                        setPadding(4, 8, 4, 8)
                    }
                    
                    val iconContainer = FrameLayout(this).apply {
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                    }
                    
                    val iconView = ImageView(this).apply {
                        setImageDrawable(icon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        layoutParams = FrameLayout.LayoutParams(iconSize, iconSize)
                    }
                    iconContainer.addView(iconView)
                    
                    val notificationCount = NotificationListener.getNotificationCount(packageName)
                    if (notificationCount > 0) {
                        val badgeSize = (20 * resources.displayMetrics.density).toInt()
                        val badge = TextView(this).apply {
                            text = if (notificationCount > 99) "99+" else notificationCount.toString()
                            setTextColor(Color.WHITE)
                            textSize = 10f
                            setTypeface(typeface, Typeface.BOLD)
                            gravity = Gravity.CENTER
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.parseColor("#DD6200"))
                            }
                            layoutParams = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                                gravity = Gravity.TOP or Gravity.START
                            }
                        }
                        iconContainer.addView(badge)
                    }
                    
                    val labelView = TextView(this).apply {
                        text = label
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 10f
                        gravity = android.view.Gravity.CENTER
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = (2 * resources.displayMetrics.density).toInt()
                        }
                    }
                    
                    itemView.addView(iconContainer)
                    itemView.addView(labelView)
                    
                    val frameParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    itemView.layoutParams = frameParams
                    
                    cellView.addView(itemView)
                    
                    // Click para abrir app
                    cellView.setOnClickListener {
                        launchApp(packageName)
                    }
                    
                    // Long click inicia drag direto
                    if (desktopPinnedApps.containsKey(position)) {
                        val appPackageName = desktopPinnedApps[position]!!
                        
                        cellView.setOnLongClickListener { v ->
                            currentDragPackage = appPackageName
                            currentDragPosition = position // Armazena posição de origem
                            showDropZone()

                            val clipData = ClipData.newPlainText("desktop_$position", "desktop_app:$appPackageName") // Changed prefix to desktop_app
                            val shadow = View.DragShadowBuilder(v)
                            v.startDragAndDrop(clipData, shadow, null, 0)
                            true
                        }
                    }
                    
                } catch (e: Exception) {
                    // App não existe mais, remove da lista
                    desktopPinnedApps.remove(position)
                    saveDesktopPinnedApps()
                }
            }
            
            desktopGrid.addView(cellView)
        }
    }
    
    private fun renderFolderCell(cellView: FrameLayout, folder: FolderInfo, position: Int, iconSize: Int, pm: PackageManager) {
        val itemView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(4, 8, 4, 8)
        }
        
        // Container for folder icons (2x2 grid)
        val folderIconContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * resources.displayMetrics.density
                setColor(android.graphics.Color.parseColor("#40FFFFFF"))
            }
        }
        
        val gridContainer = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(4, 4, 4, 4)
        }
        
        val smallIconSize = (iconSize - 16) / 2
        
        // Show up to 4 app icons in the folder preview
        for (i in 0 until minOf(4, folder.apps.size)) {
            val appPackage = folder.apps[i]
            try {
                val appInfo = pm.getApplicationInfo(appPackage, 0)
                val defaultIcon = appInfo.loadIcon(pm)
                val icon = customIconManager.getIconForApp(appPackage, defaultIcon)
                
                val miniIcon = ImageView(this).apply {
                    setImageDrawable(icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = smallIconSize
                        height = smallIconSize
                        setMargins(2, 2, 2, 2)
                    }
                }
                gridContainer.addView(miniIcon)
            } catch (e: Exception) {
                // App not found, skip
            }
        }
        
        folderIconContainer.addView(gridContainer)
        
        val labelView = TextView(this).apply {
            text = folder.name
            setTextColor(android.graphics.Color.WHITE)
            textSize = 10f
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (2 * resources.displayMetrics.density).toInt()
            }
        }
        
        itemView.addView(folderIconContainer)
        itemView.addView(labelView)
        
        val frameParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        itemView.layoutParams = frameParams
        
        cellView.addView(itemView)
        
        // Click to open folder
        cellView.setOnClickListener {
            showFolderDialog(folder, position)
        }
        
        // Long click to edit folder
        cellView.setOnLongClickListener {
            showFolderOptionsDialog(folder, position)
            true
        }
    }
    
    private fun showFolderDialog(folder: FolderInfo, position: Int) {
        val pm = packageManager
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * resources.displayMetrics.density
                setColor(android.graphics.Color.parseColor("#E0111111"))
            }
        }
        
        // Folder name (editable on click)
        val nameView = TextView(this).apply {
            text = folder.name
            setTextColor(android.graphics.Color.WHITE)
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setPadding(16, 16, 16, 24)
        }
        
        nameView.setOnClickListener {
            showEditFolderNameDialog(folder, position, nameView)
        }
        
        dialogView.addView(nameView)
        
        // Grid of apps
        val gridView = GridLayout(this).apply {
            columnCount = 3
            // Calculate row count dynamically
            rowCount = (folder.apps.size + 2) / 3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        val iconSize = (64 * resources.displayMetrics.density).toInt()
        
        for (appPackage in folder.apps) {
            try {
                val appInfo = pm.getApplicationInfo(appPackage, 0)
                val defaultIcon = appInfo.loadIcon(pm)
                val icon = customIconManager.getIconForApp(appPackage, defaultIcon)
                val label = appInfo.loadLabel(pm).toString()
                
                val appView = LinearLayout(this).apply {
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
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 11f
                    gravity = android.view.Gravity.CENTER
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        (iconSize * 1.2).toInt(),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                
                appView.addView(iconView)
                appView.addView(labelView)
                
                appView.setOnClickListener {
                    dialog.dismiss()
                    launchApp(appPackage)
                }
                
                appView.setOnLongClickListener {
                    showRemoveFromFolderDialog(folder, position, appPackage, dialog)
                    true
                }
                
                gridView.addView(appView)
            } catch (e: Exception) {
                // App not found, remove from folder
                folder.apps.remove(appPackage)
                saveDesktopFolders()
            }
        }
        
        dialogView.addView(gridView)
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    private fun showEditFolderNameDialog(folder: FolderInfo, position: Int, nameView: TextView) {
        val editText = EditText(this).apply {
            setText(folder.name)
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            hint = "Nome da pasta"
            setPadding(32, 32, 32, 32)
            setSelection(text.length)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Renomear pasta")
            .setView(editText)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    folder.name = newName
                    saveDesktopFolders()
                    nameView.text = newName
                    setupDesktopGrid()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun showRemoveFromFolderDialog(folder: FolderInfo, position: Int, appPackage: String, parentDialog: AlertDialog) {
        val pm = packageManager
        val appLabel = try {
            pm.getApplicationInfo(appPackage, 0).loadLabel(pm).toString()
        } catch (e: Exception) {
            appPackage
        }
        
        AlertDialog.Builder(this)
            .setTitle(appLabel)
            .setItems(arrayOf("Remover da pasta", "Mover para tela inicial")) { _, which ->
                when (which) {
                    0 -> {
                        folder.apps.remove(appPackage)
                        if (folder.apps.isEmpty()) {
                            desktopFolders.remove(position)
                        }
                        saveDesktopFolders()
                        setupDesktopGrid()
                        parentDialog.dismiss()
                    }
                    1 -> {
                        folder.apps.remove(appPackage)
                        if (folder.apps.isEmpty()) {
                            desktopFolders.remove(position)
                        }
                        
                        // Find free position for the app
                        for (i in 0 until DESKTOP_MAX_APPS) {
                            if (!desktopPinnedApps.containsKey(i) && !desktopFolders.containsKey(i)) {
                                desktopPinnedApps[i] = appPackage
                                break
                            }
                        }
                        
                        saveDesktopFolders()
                        saveDesktopPinnedApps()
                        setupDesktopGrid()
                        parentDialog.dismiss()
                    }
                }
            }
            .show()
    }
    
    private fun showFolderOptionsDialog(folder: FolderInfo, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(folder.name)
            .setItems(arrayOf("Renomear", "Desfazer pasta")) { _, which ->
                when (which) {
                    0 -> {
                        val editText = EditText(this).apply {
                            setText(folder.name)
                            setPadding(32, 32, 32, 32)
                            setSelection(text.length)
                        }
                        
                        AlertDialog.Builder(this)
                            .setTitle("Renomear pasta")
                            .setView(editText)
                            .setPositiveButton("Salvar") { _, _ ->
                                val newName = editText.text.toString().trim()
                                if (newName.isNotEmpty()) {
                                    folder.name = newName
                                    saveDesktopFolders()
                                    setupDesktopGrid()
                                }
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                    1 -> {
                        // Unpack folder - move all apps to desktop
                        val apps = folder.apps.toList()
                        desktopFolders.remove(position)
                        
                        // Put first app in folder's position
                        if (apps.isNotEmpty()) {
                            desktopPinnedApps[position] = apps[0]
                        }
                        
                        // Find free positions for remaining apps
                        for (i in 1 until apps.size) {
                            for (pos in 0 until DESKTOP_MAX_APPS) {
                                if (!desktopPinnedApps.containsKey(pos) && !desktopFolders.containsKey(pos)) {
                                    desktopPinnedApps[pos] = apps[i]
                                    break
                                }
                            }
                        }
                        
                        saveDesktopFolders()
                        saveDesktopPinnedApps()
                        setupDesktopGrid()
                        Toast.makeText(this, "Pasta desfeita", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }
    
    private fun showDropZone() {
        dropZoneCircle.visibility = View.VISIBLE
        dropZoneCircle.scaleX = 0f
        dropZoneCircle.scaleY = 0f
        dropZoneCircle.alpha = 0f
        
        dropZoneCircle.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(0.9f)
            .setDuration(250)
            .setInterpolator(android.view.animation.OvershootInterpolator())
            .start()
    }
    
    private fun hideDropZone() {
        dropZoneCircle.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                dropZoneCircle.visibility = View.GONE
                isOverDropZone = false
            }
            .start()
    }
    
    private fun setupDropZones() {
        dropZoneCircle.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    isOverDropZone = true
                    v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).start()
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    isOverDropZone = false
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    true
                }
                DragEvent.ACTION_DROP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    currentDragPackage?.let { pkg ->
                        showDropOptionsDialog(pkg)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    if (!isOverDropZone) {
                        hideDropZone()
                    }
                    v.scaleX = 1f
                    v.scaleY = 1f
                    true
                }
                else -> true
            }
        }
    }
    
    private fun showDropOptionsDialog(packageName: String) {
        val pm = packageManager
        val appLabel = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(pm).toString()
        } catch (e: Exception) {
            "App"
        }
        
        val isSystemApp = isSystemApp(packageName)
        val options = if (isSystemApp) {
            arrayOf("Desfixar da tela inicial")
        } else {
            arrayOf("Desfixar da tela inicial", "Desinstalar")
        }
        
        AlertDialog.Builder(this)
            .setTitle(appLabel)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        unpinAppFromDesktop(packageName)
                    }
                    1 -> {
                        if (!isSystemApp) {
                            unpinAppFromDesktop(packageName)
                            val intent = Intent(Intent.ACTION_DELETE).apply {
                                data = android.net.Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        }
                    }
                }
                hideDropZone()
                currentDragPackage = null
            }
            .setOnCancelListener {
                hideDropZone()
                currentDragPackage = null
            }
            .show()
    }
    
    private fun handleDesktopDrag(cellView: View, event: DragEvent, targetPosition: Int): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                if (targetPosition != currentDragPosition) { // Prevent dropping onto itself
                    cellView.background = ContextCompat.getDrawable(this, R.drawable.drag_highlight_background)
                    cellView.invalidate()
                }
                return true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                cellView.background = null
                cellView.invalidate()
                return true
            }
            DragEvent.ACTION_DROP -> {
                cellView.background = null
                cellView.invalidate()
                
                val clipData = event.clipData
                if (clipData != null && clipData.itemCount > 0) {
                    val itemText = clipData.getItemAt(0).text.toString()
                    
                    val draggedPackage = when {
                        itemText.startsWith("dock_app:") -> itemText.replace("dock_app:", "")
                        itemText.startsWith("drawer_app:") -> itemText.replace("drawer_app:", "")
                        itemText.startsWith("desktop_app:") -> itemText.replace("desktop_app:", "")
                        isDraggingFromDock -> draggedDockPackageName
                        else -> null
                    }
                    
                    if (draggedPackage != null) {
                        val targetApp = desktopPinnedApps[targetPosition]
                        val targetFolder = desktopFolders[targetPosition]
                        
                        when {
                            // Dropping onto existing folder
                            targetFolder != null -> {
                                if (!targetFolder.apps.contains(draggedPackage)) {
                                    targetFolder.apps.add(draggedPackage)
                                    
                                    // Remove from source position
                                    removeAppFromSource(itemText, draggedPackage)
                                    
                                    saveDesktopFolders()
                                    saveDesktopPinnedApps()
                                    setupDesktopGrid()
                                }
                            }
                            
                            // Dropping onto existing app - create new folder
                            targetApp != null && targetApp != draggedPackage && targetPosition != currentDragPosition -> {
                                val newFolder = FolderInfo("Pasta", mutableListOf(targetApp, draggedPackage))
                                desktopFolders[targetPosition] = newFolder
                                desktopPinnedApps.remove(targetPosition)
                                
                                // Remove from source position
                                removeAppFromSource(itemText, draggedPackage)
                                
                                saveDesktopFolders()
                                saveDesktopPinnedApps()
                                setupDesktopGrid()
                                Toast.makeText(this, "Pasta criada", Toast.LENGTH_SHORT).show()
                            }
                            
                            // Dropping onto empty cell
                            targetApp == null && targetFolder == null -> {
                                desktopPinnedApps[targetPosition] = draggedPackage
                                
                                // Remove from source position
                                removeAppFromSource(itemText, draggedPackage)
                                
                                saveDesktopPinnedApps()
                                setupDesktopGrid()
                            }
                            
                            // Same app on same position, swap behavior
                            else -> {
                                if (currentDragPosition >= 0 && currentDragPosition != targetPosition) {
                                    moveDesktopApp(currentDragPosition, targetPosition, draggedPackage)
                                }
                            }
                        }
                        
                        // Clean up dock drag state
                        if (itemText.startsWith("dock_app:") || isDraggingFromDock) {
                            isDraggingFromDock = false
                            draggedDockPackageName = null
                            draggedDockIndex = -1
                        }
                        
                        return true
                    }
                }
                
                // Reset drag state
                currentDragPackage = null
                currentDragPosition = -1
                isDragging = false
                hideDropZone()
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                cellView.background = null
                cellView.invalidate()
                if (!event.result) {
                    currentDragPackage = null
                    currentDragPosition = -1
                    isDragging = false
                    hideDropZone()
                }
                return true
            }
            else -> return false
        }
    }

    private fun removeAppFromSource(itemText: String, packageName: String) {
        when {
            itemText.startsWith("dock_app:") || isDraggingFromDock -> {
                unpinApp(packageName)
            }
            itemText.startsWith("desktop_app:") -> {
                val sourcePos = desktopPinnedApps.entries.find { it.value == packageName }?.key
                if (sourcePos != null) {
                    desktopPinnedApps.remove(sourcePos)
                }
            }
            // drawer_app: doesn't need removal from source
        }
    }

    private fun moveDesktopApp(fromPosition: Int, toPosition: Int, packageName: String) {
        if (fromPosition == toPosition) return
        
        val targetFolder = desktopFolders[toPosition]
        if (targetFolder != null) {
            // Add to folder instead of swapping
            if (!targetFolder.apps.contains(packageName)) {
                targetFolder.apps.add(packageName)
                desktopPinnedApps.remove(fromPosition)
                saveDesktopFolders()
                saveDesktopPinnedApps()
                setupDesktopGrid()
            }
            return
        }
        
        // If the target position already has an app, swap them
        val existingApp = desktopPinnedApps[toPosition]
        
        desktopPinnedApps.remove(fromPosition)
        desktopPinnedApps[toPosition] = packageName
        
        if (existingApp != null) {
            desktopPinnedApps[fromPosition] = existingApp
        }
        
        saveDesktopPinnedApps()
        setupDesktopGrid()
    }

    private fun showAppOptionsDialog(appInfo: AppInfo) {
        if (appInfo.isInternalApp) {
            launchApp(appInfo.packageName)
            return
        }
        
        val options = mutableListOf<String>()
        options.add("Abrir")
        
        val isPinned = pinnedApps.contains(appInfo.packageName)
        if (isPinned) {
            options.add("Desfixar do dock")
        } else {
            options.add("Fixar no dock")
        }
        
        val isPinnedToDesktop = desktopPinnedApps.containsValue(appInfo.packageName)
        val isInsideFolder = desktopFolders.values.any { it.apps.contains(appInfo.packageName) }

        if (isPinnedToDesktop) {
            options.add("Desfixar da tela inicial")
        } else if (!isInsideFolder) { // Only offer to pin to desktop if not in a folder
            options.add("Fixar na tela inicial")
        }
        
        val isHidden = hiddenApps.contains(appInfo.packageName)
        if (showHiddenApps && isHidden) {
            options.add("Mostrar app")
        } else if (!isHidden) {
            options.add("Ocultar app")
        }
        
        options.add("Trocar ícone")
        if (customIconManager.hasCustomIcon(appInfo.packageName)) {
            options.add("Restaurar ícone original")
        }
        
        options.add("Informações do app")
        
        val isSystemApp = isSystemApp(appInfo.packageName)
        if (!isSystemApp) {
            options.add("Desinstalar")
        }
        
        AlertDialog.Builder(this)
            .setTitle(appInfo.label)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Abrir" -> launchApp(appInfo.packageName)
                    "Fixar no dock" -> pinApp(appInfo)
                    "Desfixar do dock" -> unpinApp(appInfo.packageName)
                    "Fixar na tela inicial" -> pinAppToDesktop(appInfo)
                    "Desfixar da tela inicial" -> unpinAppFromDesktop(appInfo.packageName)
                    "Ocultar app" -> hideApp(appInfo.packageName, appInfo.label)
                    "Mostrar app" -> showApp(appInfo.packageName, appInfo.label)
                    "Trocar ícone" -> pickIconForApp(appInfo.packageName)
                    "Restaurar ícone original" -> restoreOriginalIcon(appInfo.packageName)
                    "Informações do app" -> showAppInfo(appInfo.packageName)
                    "Desinstalar" -> uninstallApp(appInfo.packageName)
                }
            }
            .show()
    }

    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    private fun showAppInfo(packageName: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir informações do app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uninstallApp(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível desinstalar o app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pinApp(appInfo: AppInfo) {
        if (!pinnedApps.contains(appInfo.packageName)) {
            pinnedApps.add(appInfo.packageName)
            savePinnedApps()
            renderPinnedApps()
            filterApps(searchBar.text.toString())
            notifyPinnedAppsChanged() // Notify service about dock change
        }
    }
    
    private fun pinAppAtPosition(appInfo: AppInfo, position: Int) {
        if (!pinnedApps.contains(appInfo.packageName)) {
            // Clamp position to valid range
            val insertPosition = position.coerceIn(0, pinnedApps.size)
            pinnedApps.add(insertPosition, appInfo.packageName)
            savePinnedApps()
            renderPinnedApps()
            filterApps(searchBar.text.toString())
            notifyPinnedAppsChanged() // Notify service about dock change
        }
    }
    
    private fun unpinApp(packageName: String) {
        pinnedApps.remove(packageName)
        savePinnedApps()
        renderPinnedApps()
        filterApps(searchBar.text.toString())
        notifyPinnedAppsChanged() // Notify service about dock change
    }

    private fun renderPinnedApps() {
        dockAppsContainer.removeAllViews()
        val pm = packageManager

        // O layout_gravity="bottom" no XML faz os apps ficarem na parte inferior
        for ((index, packageName) in pinnedApps.withIndex()) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val defaultIcon = appInfo.loadIcon(pm)
                
                val icon = customIconManager.getIconForApp(packageName, defaultIcon)
                
                val iconSize = (64 * resources.displayMetrics.density).toInt()
                val iconContainer = FrameLayout(this).apply {
                    val layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                    val topMargin = (8 * resources.displayMetrics.density).toInt()
                    val bottomMargin = (8 * resources.displayMetrics.density).toInt()
                    layoutParams.setMargins(0, topMargin, 0, bottomMargin)
                    this.layoutParams = layoutParams
                    tag = index // Store index for drag & drop reordering
                }
                
                val iconView = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(iconSize, iconSize)
                    setImageDrawable(icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                iconContainer.addView(iconView)
                
                val notificationCount = NotificationListener.getNotificationCount(packageName)
                if (notificationCount > 0) {
                    val badgeSize = (20 * resources.displayMetrics.density).toInt()
                    val badge = TextView(this).apply {
                        text = if (notificationCount > 99) "99+" else notificationCount.toString()
                        setTextColor(Color.WHITE)
                        textSize = 10f
                        setTypeface(typeface, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#DD6200"))
                        }
                        layoutParams = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                            gravity = Gravity.TOP or Gravity.START
                        }
                    }
                    iconContainer.addView(badge)
                }

                iconContainer.setOnClickListener {
                    if (!isDragging) {
                        launchApp(packageName)
                    }
                }

                var dockTouchDownX = 0f
                var dockTouchDownY = 0f
                var dockDragging = false
                var dockDragReadyTime = 0L
                
                iconContainer.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            dockTouchDownX = event.x
                            dockTouchDownY = event.y
                            dockDragging = false
                            dockDragReadyTime = 0L
                            
                            longPressRunnable = Runnable {
                                if (!dockDragging) {
                                    val label = appInfo.loadLabel(pm).toString()
                                    val isSystemApp = isSystemApp(packageName)
                                    
                                    val options = mutableListOf("Abrir", "Fixar na tela inicial", "Trocar ícone")
                                    if (customIconManager.hasCustomIcon(packageName)) {
                                        options.add("Restaurar ícone original")
                                    }
                                    options.add("Desfixar do dock")
                                    if (!isSystemApp) {
                                        options.add("Desinstalar")
                                    }
                                    
                                    AlertDialog.Builder(this)
                                        .setTitle(label)
                                        .setItems(options.toTypedArray()) { _, which ->
                                            when (options[which]) {
                                                "Abrir" -> launchApp(packageName)
                                                "Fixar na tela inicial" -> {
                                                    val appInfoObj = AppInfo(
                                                        packageName = packageName,
                                                        label = label,
                                                        icon = icon
                                                    )
                                                    pinAppToDesktop(appInfoObj)
                                                    unpinApp(packageName)
                                                }
                                                "Trocar ícone" -> pickIconForApp(packageName)
                                                "Restaurar ícone original" -> restoreOriginalIcon(packageName)
                                                "Desfixar do dock" -> unpinApp(packageName)
                                                "Desinstalar" -> {
                                                    if (!isSystemApp) {
                                                        uninstallApp(packageName)
                                                    }
                                                }
                                            }
                                        }
                                        .show()
                                }
                            }
                            longPressHandler.postDelayed(longPressRunnable!!, 600)
                            false // Don't consume, let children handle
                        }
                        
                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = abs(event.x - dockTouchDownX)
                            val deltaY = abs(event.y - dockTouchDownY)
                            
                            if ((deltaX > dragThreshold || deltaY > dragThreshold) && !dockDragging) {
                                if (dockDragReadyTime == 0L) {
                                    dockDragReadyTime = System.currentTimeMillis() + dragDelayDuration
                                    longPressHandler.removeCallbacks(longPressRunnable!!)
                                } else if (System.currentTimeMillis() >= dockDragReadyTime) {
                                    dockDragging = true
                                    isDragging = true
                                    isDraggingFromDock = true
                                    draggedDockPackageName = packageName
                                    draggedDockIndex = index
                                    
                                    val clipData = ClipData.newPlainText("dock_app", "dock_app:$packageName")
                                    val shadowBuilder = View.DragShadowBuilder(view)
                                    
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        view.startDragAndDrop(clipData, shadowBuilder, packageName, 0)
                                    } else {
                                        view.startDrag(clipData, shadowBuilder, packageName, 0)
                                    }
                                    
                                    dockDragReadyTime = 0L
                                }
                            }
                            dockDragging
                        }
                        
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            longPressHandler.removeCallbacks(longPressRunnable!!)
                            dockDragReadyTime = 0L
                            dockDragging = false
                            false
                        }
                        
                        else -> false
                    }
                }
                
                iconContainer.setOnDragListener { targetView, event ->
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> {
                            true
                        }
                        DragEvent.ACTION_DRAG_ENTERED -> {
                            if (isDraggingFromDock) {
                                targetView.alpha = 0.5f
                            }
                            true
                        }
                        DragEvent.ACTION_DRAG_EXITED -> {
                            targetView.alpha = 1.0f
                            true
                        }
                        DragEvent.ACTION_DROP -> {
                            targetView.alpha = 1.0f
                            
                            if (isDraggingFromDock && draggedDockPackageName != null) {
                                val targetIndex = targetView.tag as? Int
                                if (targetIndex != null && targetIndex != draggedDockIndex) {
                                    val draggedPackage = draggedDockPackageName!!
                                    pinnedApps.removeAt(draggedDockIndex)
                                    pinnedApps.add(targetIndex, draggedPackage)
                                    
                                    savePinnedApps()
                                    renderPinnedApps()
                                    
                                    Toast.makeText(this, "Posição alterada", Toast.LENGTH_SHORT).show()
                                }
                            }
                            true
                        }
                        DragEvent.ACTION_DRAG_ENDED -> {
                            targetView.alpha = 1.0f
                            true
                        }
                        else -> false
                    }
                }

                dockAppsContainer.addView(iconContainer)
            } catch (e: Exception) {
                // If app icon cannot be loaded, remove it from pinned apps
                pinnedApps.remove(packageName)
                savePinnedApps()
            }
        }
        
        // </CHANGE> Rola o ScrollView para o final após adicionar os apps
        dockScrollView.post {
            dockScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun launchApp(packageName: String) {
        if (packageName == "com.example.launcher.settings") {
            val intent = Intent(this, LauncherSettingsActivity::class.java)
            startActivity(intent)
            return
        }
        
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        val installedApps = resolveInfoList
            .filter { it.activityInfo.packageName != packageName } // Exclude the launcher itself
            .map { resolveInfo ->
                val pkgName = resolveInfo.activityInfo.packageName
                val defaultIcon = resolveInfo.loadIcon(pm)
                
                val icon = customIconManager.getIconForApp(pkgName, defaultIcon)
                
                AppInfo(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = pkgName,
                    icon = icon
                )
            }
        
        val launcherSettingsApp = AppInfo(
            label = "Configurações do Launcher",
            packageName = "com.example.launcher.settings",
            icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_launcher_settings)!!,
            isInternalApp = true
        )
        
        appList = (installedApps + launcherSettingsApp).sortedBy { it.label.lowercase() }
    }

    private fun toggleAppDrawer() {
        if (appDrawerContainer.visibility == View.VISIBLE) {
            closeAppDrawer()
        } else {
            appDrawerContainer.visibility = View.VISIBLE
            val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_left)
            appDrawerContainer.startAnimation(slideIn)
            searchBar.setText("")
        }
    }

    private fun closeAppDrawer() {
        val slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
        slideOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                appDrawerContainer.visibility = View.GONE
            }
        })
        appDrawerContainer.startAnimation(slideOut)
        searchBar.setText("")
    }

    private fun showSortMenu(view: View) {
        val popupMenu = PopupMenu(this, view, Gravity.END)
        popupMenu.menu.add(0, 1, 0, "Nome (A-Z)")
        popupMenu.menu.add(0, 4, 1, "Nome (Z-A)")
        popupMenu.menu.add(0, 2, 2, "Mais recentes")
        if (showHiddenApps) {
            popupMenu.menu.add(0, 3, 3, "Ocultar apps escondidos")
        } else {
            popupMenu.menu.add(0, 3, 3, "Mostrar apps ocultos")
        }
        
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    // Sort by name alphabetically A-Z
                    sortAppsByName()
                    true
                }
                4 -> {
                    // Sort by name Z-A
                    sortAppsByNameDesc()
                    true
                }
                2 -> {
                    // Sort by most recent (installation time)
                    sortAppsByRecent()
                    true
                }
                3 -> {
                    showHiddenApps = !showHiddenApps
                    filterApps(searchBar.text.toString())
                    val message = if (showHiddenApps) "Mostrando apps ocultos" else "Apps ocultos escondidos"
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }

    private fun sortAppsByName() {
        appList = appList.sortedBy { it.label.lowercase() }
        filterApps(searchBar.text.toString())
    }

    private fun sortAppsByNameDesc() {
        appList = appList.sortedByDescending { it.label.lowercase() }
        filterApps(searchBar.text.toString())
    }

    private fun sortAppsByRecent() {
        val pm = packageManager
        val sortedApps = appList.sortedByDescending { appInfo ->
            try {
                pm.getPackageInfo(appInfo.packageName, 0).firstInstallTime
            } catch (e: Exception) {
                0L
            }
        }
        appList = sortedApps
        filterApps(searchBar.text.toString())
    }
    
    private fun setupDockSwipeListener() {
        var swipeStartX = 0f
        var swipeStartY = 0f
        var swipeStartTime = 0L
        var isSwipeDetected = false
        
        val dockSwipeDetector = View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX
                    swipeStartY = event.rawY
                    swipeStartTime = System.currentTimeMillis()
                    isSwipeDetected = false
                    false // Don't consume, let children handle
                }
                
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - swipeStartX
                    val deltaY = event.rawY - swipeStartY
                    val timeDelta = System.currentTimeMillis() - swipeStartTime
                    
                    // Detect horizontal swipe to the right: moved more than 150px horizontally, 
                    // less than 80px vertically, within 500ms
                    if (deltaX > 150 && deltaY < 80 && timeDelta < 500 && !isSwipeDetected) {
                        isSwipeDetected = true
                        openControlPanel()
                        true // Consume the event
                    } else {
                        false // Don't consume, let children handle
                    }
                }
                
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSwipeDetected = false
                    false
                }
                
                else -> false
            }
        }
        
        dockContainer.setOnTouchListener(dockSwipeDetector)
        
        var homeSwipeStartX = 0f
        var homeSwipeStartY = 0f
        var homeSwipeStartTime = 0L
        var isHomeSwipeDetected = false
        
        val homeSwipeDetector = View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    homeSwipeStartX = event.rawX
                    homeSwipeStartY = event.rawY
                    homeSwipeStartTime = System.currentTimeMillis()
                    isHomeSwipeDetected = false
                    false // Don't consume, let long press work
                }
                
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - homeSwipeStartX
                    val deltaY = abs(event.rawY - homeSwipeStartY)
                    val timeDelta = System.currentTimeMillis() - homeSwipeStartTime
                    
                    // Swipe left (negative deltaX) to hide dock
                    if (deltaX < -150 && deltaY < 80 && timeDelta < 500 && !isHomeSwipeDetected && !isDockHidden) {
                        isHomeSwipeDetected = true
                        hideDock()
                        true
                    }
                    // Swipe right (positive deltaX) to show dock
                    else if (deltaX > 150 && deltaY < 80 && timeDelta < 500 && !isHomeSwipeDetected && isDockHidden) {
                        isHomeSwipeDetected = true
                        showDock()
                        true
                    } else {
                        false
                    }
                }
                
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isHomeSwipeDetected = false
                    false
                }
                
                else -> false
            }
        }
        
        homeBackground.setOnTouchListener(homeSwipeDetector)
        
        controlPanelContainer.setOnClickListener {
            closeControlPanel()
        }
    }
    
    private fun hideDock() {
        isDockHidden = true
        val animation = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
        dockContainer.startAnimation(animation)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                dockContainer.visibility = View.GONE
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
    }

    private fun showDock() {
        isDockHidden = false
        dockContainer.visibility = View.VISIBLE
        val animation = AnimationUtils.loadAnimation(this, R.anim.slide_in_left)
        dockContainer.startAnimation(animation)
    }

    
    private fun openControlPanel() {
        if (appDrawerContainer.visibility == View.VISIBLE) {
            closeAppDrawer()
        }
        
        controlPanelContainer.visibility = View.VISIBLE
        val animation = AnimationUtils.loadAnimation(this, R.anim.slide_in_left)
        controlPanelContainer.startAnimation(animation)
        updateControlPanelStates()
    }
    
    private fun closeControlPanel() {
        val animation = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
        controlPanelContainer.startAnimation(animation)
        controlPanelContainer.postDelayed({
            controlPanelContainer.visibility = View.GONE
        }, 300)
    }
    
    private fun toggleControlPanel() {
        if (controlPanelContainer.visibility == View.VISIBLE) {
            closeControlPanel()
        } else {
            openControlPanel()
        }
    }
    
    private fun setupControlPanel() {
        wifiToggle.setOnClickListener {
            toggleWifi()
        }
        
        bluetoothToggle.setOnClickListener {
            toggleBluetooth()
        }
        
        
        flashlightToggle.setOnClickListener {
            toggleFlashlight()
        }
        
        airplaneModeToggle.setOnClickListener {
            toggleAirplaneMode()
        }
        
        calculatorToggle.setOnClickListener {
            openCalculator()
        }
        
        locationToggle.setOnClickListener {
            toggleLocation()
        }
        
        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setBrightness(progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        updateControlPanelStates()
    }
    
    private fun updateControlPanelStates() {
        updateWifiState()
        updateBluetoothState()
        updateFlashlightState()
        updateAirplaneModeState()
        updateLocationState()
        updateBrightnessState()
    }
    
    private fun requestControlPanelPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.CAMERA)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_SCAN)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_WRITE_SETTINGS)
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSIONS, REQUEST_CAMERA_PERMISSION -> {
                updateControlPanelStates()
            }
        }
    }

    private fun toggleWifi() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Open WiFi panel
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                startActivity(panelIntent)
            } else {
                // Android 9 and below - Open WiFi settings
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao abrir configurações de WiFi", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateWifiState() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val isEnabled = wifiManager.isWifiEnabled
            wifiToggle.isActivated = isEnabled
            wifiIcon.setColorFilter(if (isEnabled) Color.WHITE else Color.parseColor("#DD6200"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun toggleBluetooth() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao abrir configurações de Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateBluetoothState() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val isEnabled = bluetoothAdapter?.isEnabled == true
            bluetoothToggle.isActivated = isEnabled
            bluetoothIcon.setColorFilter(if (isEnabled) Color.WHITE else Color.parseColor("#DD6200"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    
    private fun toggleFlashlight() {
        try {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
                return
            }
            
            if (cameraId != null) {
                isFlashlightOn = !isFlashlightOn
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cameraManager?.setTorchMode(cameraId!!, isFlashlightOn)
                    updateFlashlightState()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao alternar lanterna", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateFlashlightState() {
        flashlightToggle.isActivated = isFlashlightOn
        flashlightIcon.setColorFilter(if (isFlashlightOn) Color.WHITE else Color.parseColor("#DD6200"))
    }
    
    private fun toggleAirplaneMode() {
        try {
            // Open airplane mode settings
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao abrir configurações de modo avião", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateAirplaneModeState() {
        try {
            val isEnabled = Settings.Global.getInt(
                contentResolver,
                Settings.Global.AIRPLANE_MODE_ON, 0
            ) == 1
            airplaneModeToggle.isActivated = isEnabled
            airplaneModeIcon.setColorFilter(if (isEnabled) Color.WHITE else Color.parseColor("#DD6200"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun openCalculator() {
        try {
            // Try to open the default calculator app
            val intent = Intent()
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_APP_CALCULATOR)
            startActivity(intent)
            closeControlPanel()
        } catch (e: Exception) {
            try {
                // Fallback: try common calculator package names
                val calcIntent = Intent()
                calcIntent.setClassName("com.android.calculator2", "com.android.calculator2.Calculator")
                startActivity(calcIntent)
                closeControlPanel()
            } catch (e2: Exception) {
                try {
                    // Another fallback
                    val calcIntent2 = Intent()
                    calcIntent2.setClassName("com.google.android.calculator", "com.android.calculator2.Calculator")
                    startActivity(calcIntent2)
                    closeControlPanel()
                } catch (e3: Exception) {
                    Toast.makeText(this, "Calculadora não encontrada", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun toggleLocation() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao abrir configurações de localização", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateLocationState() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val isEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                           locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            locationToggle.isActivated = isEnabled
            locationIcon.setColorFilter(if (isEnabled) Color.WHITE else Color.parseColor("#DD6200"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateBrightnessState() {
        try {
            val currentBrightness = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            brightnessSeekBar.progress = currentBrightness
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setBrightness(brightness: Int) {
        try {
            if (Settings.System.canWrite(this)) {
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness
                )
                
                // Apply brightness to current window
                val layoutParams = window.attributes
                layoutParams.screenBrightness = brightness / 255f
                window.attributes = layoutParams
            } else {
                // Request permission if not granted
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                Toast.makeText(this, "Permita modificar configurações do sistema", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao ajustar brilho", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            if (controlPanelContainer.visibility == View.VISIBLE) {
                updateControlPanelStates()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (widgets.any { it.style != 0 }) {
            startClockUpdates()
        }
        updateNotificationBadges()
        
        checkAndRestartGestureService()
    }

    private fun checkAndRestartGestureService() {
        try {
            val watcher = gestureServiceWatcher ?: return
            
            // If service should be enabled but is not running, the watcher will handle it
            if (watcher.isServiceEnabled() && !NavigationGestureService.isServiceRunning()) {
                // Service is enabled in settings but not running - wait for auto-restart
                // The watcher's periodic check will handle this
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Added function to show widget selector
    private fun showWidgetSelector() {
        val options = arrayOf(
            "Relógio Digital (Branco Clássico)",
            "Relógio Analógico",
            "Relógio Circular com Bateria e Clima"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Escolher Widget")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setClockWidget(WidgetInfo(style = 1))
                    1 -> setClockWidget(WidgetInfo(style = 2))
                    2 -> setClockWidget(WidgetInfo(style = 3)) // New style 3
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setClockWidget(widgetInfo: WidgetInfo) {
        val offsetY = widgets.size * 150f // 150dp offset for each new widget
        val adjustedWidgetInfo = widgetInfo.copy(
            y = 16f + offsetY
        )
        widgets.add(adjustedWidgetInfo)
        saveWidgetPreferences() // Use the new save function
        createSingleClockWidget(adjustedWidgetInfo) // Use the refactored creation function
        startClockUpdates()
        Toast.makeText(this, "Widget adicionado!", Toast.LENGTH_SHORT).show()
    }

    private fun createSingleClockWidget(widgetInfo: WidgetInfo) {
        val widgetView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(24, 16, 24, 16)
            tag = "widgetContent" // Tag with widget content
        }
        
        when (widgetInfo.style) {
            1 -> createStyle1Widget(widgetView)
            2 -> createStyle2Widget(widgetView)
            3 -> createStyle3Widget(widgetView) // Added style 3
        }
        
        val outerContainer = FrameLayout(this).apply {
            id = View.generateViewId() // Give it an ID for later retrieval
            tag = widgetInfo.id // Use widget ID as tag
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            x = widgetInfo.x
            y = widgetInfo.y
        }
        
        outerContainer.addView(widgetView)
        
        val editBorder = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            background = createEditBorderDrawable()
            visibility = View.GONE
            tag = "editBorder"
        }
        outerContainer.addView(editBorder)
        
        val handleSize = (32 * resources.displayMetrics.density).toInt()
        val resizeHandle = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(handleSize, handleSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
            background = createResizeHandleDrawable()
            visibility = View.GONE
            tag = "resizeHandle"
        }
        outerContainer.addView(resizeHandle)
        
        setupWidgetTouchListener(outerContainer, widgetView, resizeHandle, widgetInfo) // Pass widgetInfo
        
        widgetContainer.addView(outerContainer)
        
        outerContainer.post {
            // Set pivot to center for proper scaling
            widgetView.pivotX = widgetView.width / 2f
            widgetView.pivotY = widgetView.height / 2f
            widgetView.scaleX = widgetInfo.scale
            widgetView.scaleY = widgetInfo.scale
            updateEditBorderSize(widgetInfo.id)
        }
        
        updateClockWidget(widgetInfo.id)
    }

    // This function is now only for loading saved widgets, new widgets are added via setClockWidget
    private fun createClockWidget(style: Int) {
        // This function is now only used for loading saved widgets
        // For new widgets, use setClockWidget
    }
    
    private fun saveWidgetPreferences() {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Save widget count
        editor.putInt("widget_count", widgets.size)
        
        // Save each widget's properties
        widgets.forEachIndexed { index, widget ->
            editor.putString("widget_${index}_id", widget.id)
            editor.putInt("widget_${index}_style", widget.style)
            editor.putFloat("widget_${index}_x", widget.x)
            editor.putFloat("widget_${index}_y", widget.y)
            editor.putFloat("widget_${index}_scale", widget.scale)
        }
        
        editor.apply()
    }

    private fun saveWidgetPositionAndSize() {
        saveWidgetPreferences()
    }

    private fun loadWidgetPositionAndSize() {
        // Now handled by loadSavedWidgets
    }

    private fun saveWidgetPreference(style: Int) {
        // Kept for compatibility, but main saving is done by saveWidgetPreferences
    }

    private fun loadWidgets() {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        
        // Try new format first
        val widgetCount = prefs.getInt("widget_count", -1)
        
        if (widgetCount >= 0) {
            widgets.clear()
            widgetContainer.removeAllViews()
            
            for (i in 0 until widgetCount) {
                val id = prefs.getString("widget_${i}_id", java.util.UUID.randomUUID().toString()) ?: java.util.UUID.randomUUID().toString()
                val style = prefs.getInt("widget_${i}_style", 0)
                val x = prefs.getFloat("widget_${i}_x", 16f + (i * 50f)) // Default offset if not found
                val y = prefs.getFloat("widget_${i}_y", 16f + (i * 50f)) // Default offset if not found
                val scale = prefs.getFloat("widget_${i}_scale", 1.0f)
                
                if (style != 0) {
                    val widget = WidgetInfo(id, style, x, y, scale)
                    widgets.add(widget)
                    createSingleClockWidget(widget)
                }
            }
            
            if (widgets.isNotEmpty()) {
                startClockUpdates()
            }
        } else {
            // Fallback to old format for migration
            val oldStyle = prefs.getInt("widget_style", 0)
            if (oldStyle != 0) {
                val oldX = prefs.getFloat("widget_x", 16f)
                val oldY = prefs.getFloat("widget_y", 16f)
                val oldScale = prefs.getFloat("widget_scale", 1.0f)
                
                val widget = WidgetInfo(style = oldStyle, x = oldX, y = oldY, scale = oldScale)
                widgets.add(widget)
                createSingleClockWidget(widget)
                startClockUpdates()
                
                // Migrate to new format
                saveWidgetPreferences()
            }
        }
    }

    private fun showWidgetRemoveDialog(widgetId: String) {
        // Provide haptic feedback
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(50)
        
        AlertDialog.Builder(this)
            .setTitle("Remover Widget")
            .setMessage("Deseja remover este widget da tela inicial?")
            .setPositiveButton("Remover") { _, _ ->
                removeSpecificWidget(widgetId)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun removeSpecificWidget(widgetId: String) {
        val widgetToRemove = widgets.find { it.id == widgetId }
        
        if (widgetToRemove != null) {
            // Find and remove the view with animation
            for (i in 0 until widgetContainer.childCount) {
                val child = widgetContainer.getChildAt(i) as? FrameLayout ?: continue
                if (child.tag == widgetToRemove.id) {
                    child.animate()
                        .alpha(0f)
                        .scaleX(0.5f)
                        .scaleY(0.5f)
                        .setDuration(200)
                        .withEndAction {
                            widgetContainer.removeView(child)
                        }
                        .start()
                    // </CHANGE>
                    break
                }
            }
            
            widgets.remove(widgetToRemove)
            isWidgetEditMode = false
            editingWidgetId = null
            saveWidgetPreferences()
            
            if (widgets.isEmpty()) {
                stopClockUpdates()
            }
            
            Toast.makeText(this, "Widget removido!", Toast.LENGTH_SHORT).show()
        }
    }
    // </CHANGE>

    // </CHANGE> Updated to remove specific widget or last added
    private fun removeWidget() {
        if (widgets.isEmpty()) {
            Toast.makeText(this, "Nenhum widget para remover", Toast.LENGTH_SHORT).show()
            return
        }
        
        // If in edit mode, remove the editing widget; otherwise remove last added
        val widgetToRemove = if (editingWidgetId != null) {
            widgets.find { it.id == editingWidgetId }
        } else {
            widgets.lastOrNull()
        }
        
        if (widgetToRemove != null) {
            removeSpecificWidget(widgetToRemove.id) // Use new remove function
        }
    }
    // </CHANGE>

    private fun createStyle1Widget(container: LinearLayout) {
        // Time TextView
        val timeView = TextView(this).apply {
            id = R.id.widget_time // Changed to R.id.widget_time
            textSize = 64f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(4f, 2f, 2f, Color.parseColor("#44000000"))
            letterSpacing = 0.05f
        }
        
        // Date TextView
        val dateView = TextView(this).apply {
            id = R.id.widget_date // Changed to R.id.widget_date
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setShadowLayer(2f, 1f, 1f, Color.parseColor("#44000000"))
            alpha = 0.9f
        }
        
        container.addView(timeView)
        container.addView(dateView)
    }

    private fun createStyle2Widget(container: LinearLayout) {
        // Create analog clock view
        val analogClock = AnalogClockView(this).apply {
            id = R.id.analog_clock // Changed to R.id.analog_clock
            layoutParams = LinearLayout.LayoutParams(
                (180 * resources.displayMetrics.density).toInt(),
                (180 * resources.displayMetrics.density).toInt()
            )
        }
        
        container.addView(analogClock)
    }

    private fun createStyle3Widget(container: LinearLayout) {
        val circularClock = CircularBatteryClockView(this).apply {
            id = R.id.circular_battery_clock
            layoutParams = LinearLayout.LayoutParams(
                (250 * resources.displayMetrics.density).toInt(),
                (250 * resources.displayMetrics.density).toInt()
            )
        }
        
        container.addView(circularClock)
    }
    

    private fun updateClockWidget(widgetId: String) {
        val widgetInfo = widgets.find { it.id == widgetId } ?: return
        
        val widgetOuter = widgetContainer.findViewWithTag<FrameLayout>(widgetId) ?: return
        val widgetView = widgetOuter.findViewWithTag<LinearLayout>("widgetContent") ?: return
        
        when (widgetInfo.style) {
            1 -> {
                val timeView = widgetView.findViewById<TextView>(R.id.widget_time)
                val dateView = widgetView.findViewById<TextView>(R.id.widget_date)
                
                if (timeView != null && dateView != null) {
                    val calendar = java.util.Calendar.getInstance()
                    val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    val dateFormat = java.text.SimpleDateFormat("EEEE, d 'de' MMMM", java.util.Locale("pt", "BR"))
                    
                    timeView.text = timeFormat.format(calendar.time)
                    dateView.text = dateFormat.format(calendar.time)
                }
            }
            2 -> {
                // Analog clock updates itself via invalidate()
                val analogClock = widgetView.findViewById<View>(R.id.analog_clock)
                analogClock?.invalidate()
            }
            3 -> {
                val circularClock = widgetView.findViewById<CircularBatteryClockView>(R.id.circular_battery_clock)
                circularClock?.invalidate()
            }
        }
    }

    private fun startClockUpdates() {
        clockRunnable = object : Runnable {
            override fun run() {
                widgets.forEach { widgetInfo ->
                    updateClockWidget(widgetInfo.id)
                }
                clockHandler.postDelayed(this, 1000)
            }
        }
        clockHandler.post(clockRunnable)
    }

    private fun stopClockUpdates() {
        if (::clockRunnable.isInitialized) {
            clockHandler.removeCallbacks(clockRunnable)
        }
    }
    
    private fun registerPinnedAppsReceiver() {
        pinnedAppsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == EdgeSwipeService.ACTION_PINNED_APPS_CHANGED) {
                    loadPinnedApps()
                    renderPinnedApps()
                    loadDesktopPinnedApps() // Changed to loadDesktopApps()
                    setupDesktopGrid()
                    filterApps("")
                }
            }
        }
        val filter = IntentFilter(EdgeSwipeService.ACTION_PINNED_APPS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pinnedAppsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pinnedAppsReceiver, filter)
        }
    }

    private fun notifyPinnedAppsChanged() {
        val intent = Intent(EdgeSwipeService.ACTION_PINNED_APPS_CHANGED)
        sendBroadcast(intent)
    }

    // Function for loading desktop apps, used for migration
    private fun loadDesktopApps() {
        // This function might be used for migrating old data or if the structure changes significantly.
        // For now, loadDesktopPinnedApps() handles the current format.
    }
    
    // Function to load and apply wallpaper, consolidated logic
    private fun loadAndApplyWallpaper() {
        loadSavedWallpaper()
    }

    // Function to load and apply widget, consolidated logic
    private fun loadAndApplyWidget() {
        loadWidgets() // Use the new loadWidgets() function
    }
    
    private fun calculateDockDropPosition(dropY: Float): Int {
        val density = resources.displayMetrics.density
        val iconSize = (64 * density).toInt()
        val firstMargin = (32 * density).toInt() // Top margin for the first icon
        val normalMargin = (16 * density).toInt() // Margin between icons
        
        if (pinnedApps.isEmpty()) return 0
        
        var currentY = firstMargin // Start position of the first icon
        for (i in pinnedApps.indices) {
            val itemEnd = currentY + iconSize // Bottom position of the current icon
            if (dropY < itemEnd) {
                // If the drop Y is before the end of this icon, it belongs here or before
                // We want to insert *before* the item at index `i` if dropped in its upper half.
                // For simplicity, let's say if dropped within the bounds of icon `i`, it goes to position `i`.
                return i
            }
            currentY = itemEnd + normalMargin // Move to the start of the next icon
        }
        // If dropped below all icons, it goes to the end
        return pinnedApps.size
    }

    // Refactored setupWidgetTouchListener to include widgetInfo
    private fun setupWidgetTouchListener(container: FrameLayout, widgetView: View, resizeHandle: View, widgetInfo: WidgetInfo) {
        var startX = 0f
        var startY = 0f
        var startWidgetX = 0f
        var startWidgetY = 0f
        var isResizing = false
        var startScale = 1.0f
        var longPressRunnable: Runnable? = null
        val longPressHandler = Handler(Looper.getMainLooper())
        // </CHANGE>
        
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isResizing = true
                    startX = event.rawX
                    startY = event.rawY
                    startScale = widgetInfo.scale // Save initial scale
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isResizing) {
                        val deltaX = event.rawX - startX
                        val deltaY = event.rawY - startY
                        
                        // Calculate scale change based on diagonal movement
                        val currentDiagonal = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
                        val initialDiagonal = kotlin.math.sqrt((widgetView.width * widgetView.scaleX) * (widgetView.width * widgetView.scaleX) + (widgetView.height * widgetView.scaleY) * (widgetView.height * widgetView.scaleY))
                        
                        // This approach is complex and might not be intuitive. Let's use a simpler delta based scaling.
                        // A simple approach: Scale increases with movement, capped.
                        val scaleChange = (deltaX + deltaY) / 300f // Adjust divisor for sensitivity
                        widgetInfo.scale = (startScale + scaleChange).coerceIn(0.3f, 1.0f)
                        
                        widgetView.pivotX = widgetView.width / 2f
                        widgetView.pivotY = widgetView.height / 2f
                        widgetView.scaleX = widgetInfo.scale
                        widgetView.scaleY = widgetInfo.scale
                        
                        // Update the edit border and resize handle to match the new scaled size
                        updateEditBorderSize(widgetInfo.id)
                        
                        // container.post {
                        //     updateEditBorderSize(widgetInfo.id)
                        // }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isResizing) {
                        // Ensure widget stays within bounds after resize
                        validateWidgetBounds(widgetInfo, container, widgetView)
                        saveWidgetPreferences() // Save changes on resize complete
                    }
                    isResizing = false
                    true
                }
                else -> false
            }
        }
        // </CHANGE>
        
        container.setOnTouchListener { view, event ->
            if (isResizing) return@setOnTouchListener false // Ignore container touches while resizing
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startWidgetX = widgetInfo.x
                    startWidgetY = widgetInfo.y
                    longPressRunnable = Runnable {
                        showWidgetRemoveDialog(widgetInfo.id)
                    }
                    longPressHandler.postDelayed(longPressRunnable!!, 800) // 800ms for long press
                    // </CHANGE>
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = Math.abs(event.rawX - startX)
                    val deltaY = Math.abs(event.rawY - startY)
                    if (deltaX > 10 || deltaY > 10) {
                        longPressHandler.removeCallbacks(longPressRunnable!!)
                    }
                    // </CHANGE>
                    
                    if (isWidgetEditMode && editingWidgetId == widgetInfo.id) {
                        val moveX = event.rawX - startX
                        val moveY = event.rawY - startY
                        
                        widgetInfo.x = startWidgetX + moveX
                        widgetInfo.y = startWidgetY + moveY
                        
                        // Update the widget's position
                        view.x = widgetInfo.x
                        view.y = widgetInfo.y
                        
                        // Validate bounds to keep it within the container
                        validateWidgetBounds(widgetInfo, container, widgetView)
                        
                        // Also update the edit border position
                        updateEditBorderSize(widgetInfo.id)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable!!)
                    // </CHANGE>
                    
                    val deltaX = Math.abs(event.rawX - startX)
                    val deltaY = Math.abs(event.rawY - startY)
                    
                    // Tap detection: If movement is minimal
                    if (deltaX < 10 && deltaY < 10) {
                        if (isWidgetEditMode && editingWidgetId == widgetInfo.id) {
                            exitWidgetEditMode()
                        } else {
                            enterWidgetEditMode(widgetInfo.id)
                        }
                    } else if (isWidgetEditMode && editingWidgetId == widgetInfo.id) {
                        // If moved while in edit mode, save position
                        saveWidgetPreferences()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable!!)
                    // </CHANGE>
                    false
                }
                else -> false
            }
        }
        
        // Long press to enter edit mode
        container.setOnLongClickListener {
            if (!isWidgetEditMode) {
                enterWidgetEditMode(widgetInfo.id)
            }
            true
        }
    }

    private fun updateEditBorderSize(widgetId: String) {
        val widgetOuter = widgetContainer.findViewWithTag<FrameLayout>(widgetId) ?: return
        val editBorder = widgetOuter.findViewWithTag<View>("editBorder") ?: return
        val widgetContent = widgetOuter.findViewWithTag<View>("widgetContent") ?: return
        val resizeHandle = widgetOuter.findViewWithTag<View>("resizeHandle") ?: return
        
        val widgetInfo = widgets.find { it.id == widgetId } ?: return
        
        // The widgetView's actual size is its intrinsic size * scale
        val scaledWidth = widgetContent.width * widgetInfo.scale
        val scaledHeight = widgetContent.height * widgetInfo.scale
        
        // The edit border and resize handle should align with the scaled widget.
        // The widgetInfo.x/y define the top-left of the container.
        // The scaled widget is centered within the container (conceptually, due to pivotX/Y).
        // So, the border should be positioned relative to the container's top-left.
        
        val borderLayoutParams = editBorder.layoutParams as FrameLayout.LayoutParams
        borderLayoutParams.width = scaledWidth.toInt()
        borderLayoutParams.height = scaledHeight.toInt()
        
        // Calculate the offset from the container's top-left to the scaled widget's top-left.
        // This offset is due to the scaling happening from the center.
        val offsetX = (widgetContent.width - scaledWidth) / 2f
        val offsetY = (widgetContent.height - scaledHeight) / 2f
        
        borderLayoutParams.leftMargin = offsetX.toInt()
        borderLayoutParams.topMargin = offsetY.toInt()
        
        editBorder.layoutParams = borderLayoutParams
        editBorder.requestLayout()
        
        // Resize handle position
        val handleSize = (32 * resources.displayMetrics.density).toInt()
        val handleLayoutParams = resizeHandle.layoutParams as FrameLayout.LayoutParams
        handleLayoutParams.width = handleSize
        handleLayoutParams.height = handleSize
        // Position handle at the bottom-right of the scaled widget's area
        handleLayoutParams.leftMargin = offsetX.toInt() + scaledWidth.toInt() - handleSize
        handleLayoutParams.topMargin = offsetY.toInt() + scaledHeight.toInt() - handleSize
        
        resizeHandle.layoutParams = handleLayoutParams
        resizeHandle.requestLayout()
    }
    private fun createEditBorderDrawable(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setStroke(
                (2 * resources.displayMetrics.density).toInt(),
                android.graphics.Color.parseColor("#DD6200")
            )
            cornerRadius = 8 * resources.displayMetrics.density
            setColor(android.graphics.Color.TRANSPARENT)
            // Add dashed effect
            // Note: Dashed lines might not be directly supported by GradientDrawable in older APIs,
            // or might require specific drawing logic if not applied correctly.
            // For simplicity, we'll stick to the stroke. If dashed is crucial, consider Path or custom drawable.
        }
    }
    
    private fun createResizeHandleDrawable(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.parseColor("#DD6200"))
            cornerRadius = 4 * resources.displayMetrics.density
        }
    }
    
    private fun enterWidgetEditMode(widgetId: String) {
        isWidgetEditMode = true
        editingWidgetId = widgetId
        
        // Find the widget container by ID
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i) as? FrameLayout ?: continue
            if (child.tag == widgetId) {
                val editBorder = child.findViewWithTag<View>("editBorder")
                val resizeHandle = child.findViewWithTag<View>("resizeHandle")
                
                // Ensure UI elements are updated immediately
                updateEditBorderSize(widgetId)
                
                editBorder?.visibility = View.VISIBLE
                resizeHandle?.visibility = View.VISIBLE
                break // Found the widget, exit loop
            }
        }
    }
    
    private fun exitWidgetEditMode() {
        isWidgetEditMode = false
        
        // Hide edit UI for all widgets
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i) as? FrameLayout ?: continue
            val editBorder = child.findViewWithTag<View>("editBorder")
            val resizeHandle = child.findViewWithTag<View>("resizeHandle")
            
            editBorder?.visibility = View.GONE
            resizeHandle?.visibility = View.GONE
        }
        
        editingWidgetId = null
        saveWidgetPreferences()
    }

    private fun validateWidgetBounds(widgetInfo: WidgetInfo, container: View, widgetView: View) {
        val containerWidth = widgetContainer.width.toFloat()
        val containerHeight = widgetContainer.height.toFloat()
        
        // If container dimensions are not ready, skip validation
        if (containerWidth <= 0 || containerHeight <= 0) return
        
        val originalWidth = widgetView.width.toFloat()
        val originalHeight = widgetView.height.toFloat()
        
        // If widget dimensions are not ready, skip validation
        if (originalWidth <= 0 || originalHeight <= 0) return
        
        // Calculate scaled dimensions
        val scaledWidth = originalWidth * widgetInfo.scale
        val scaledHeight = originalHeight * widgetInfo.scale
        
        // Calculate the offset from the container's top-left to the scaled widget's top-left.
        // This offset is due to the scaling happening from the center.
        val offsetX = (originalWidth - scaledWidth) / 2f
        val offsetY = (originalHeight - scaledHeight) / 2f
        
        // Calculate min/max X position for the container's top-left corner
        val minX = -offsetX
        val maxX = containerWidth - offsetX - scaledWidth
        
        // Calculate min/max Y position for the container's top-left corner
        val minY = -offsetY
        val maxY = containerHeight - offsetY - scaledHeight
        
        // Clamp widgetInfo.x and widgetInfo.y
        widgetInfo.x = when {
            maxX < minX -> (minX + maxX) / 2f // Widget is larger than container, center it
            widgetInfo.x < minX -> minX
            widgetInfo.x > maxX -> maxX
            else -> widgetInfo.x
        }
        
        widgetInfo.y = when {
            maxY < minY -> (minY + maxY) / 2f // Widget is larger than container, center it
            widgetInfo.y < minY -> minY
            widgetInfo.y > maxY -> maxY
            else -> widgetInfo.y
        }
        
        // Update the actual position of the container view
        container.x = widgetInfo.x
        container.y = widgetInfo.y
    }
    // </CHANGE>

    private fun pickIconForApp(packageName: String) {
        pendingIconChangePackage = packageName
        // Request permission to read external storage if not already granted
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_ICON_REQUEST)
        } else {
            // If permission is not granted, request it
            requestPermissionLauncher.launch(permission)
        }
    }
    
    private fun restoreOriginalIcon(packageName: String) {
        AlertDialog.Builder(this)
            .setTitle("Restaurar ícone")
            .setMessage("Deseja restaurar o ícone original deste app?")
            .setPositiveButton("Sim") { _, _ ->
                if (customIconManager.removeCustomIcon(packageName)) {
                    Toast.makeText(this, "Ícone restaurado", Toast.LENGTH_SHORT).show()
                    // Atualiza a UI
                    loadInstalledApps()
                    filterApps(searchBar.text.toString())
                    renderPinnedApps()
                    setupDesktopGrid()
                    // Notifica o serviço para atualizar
                    sendBroadcast(Intent("com.example.launcher.REFRESH_DOCK"))
                } else {
                    Toast.makeText(this, "Erro ao restaurar ícone", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun checkNotificationListenerPermission() {
        // Permissions are now managed in PermissionsSetupActivity
        // No dialog needed here
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        // Check if the package name is present in the enabled listeners string
        return flat != null && flat.contains(packageName)
    }

    private fun registerNotificationReceiver() {
        notificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == NotificationListener.ACTION_NOTIFICATION_CHANGED) {
                    // Update UI with new notification counts
                    updateNotificationBadges()
                }
            }
        }
        
        val filter = IntentFilter(NotificationListener.ACTION_NOTIFICATION_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }
    }

    private fun updateNotificationBadges() {
        // Update app list notification counts
        appList.forEach { app ->
            app.notificationCount = NotificationListener.getNotificationCount(app.packageName)
        }
        filteredAppList.forEach { app ->
            app.notificationCount = NotificationListener.getNotificationCount(app.packageName)
        }
        
        // Notify adapter to refresh
        adapter.notifyDataSetChanged()
        
        // Re-render desktop and dock to update badges
        setupDesktopGrid()
        renderPinnedApps()
    }
    
    private fun registerWallpaperReceiver() {
        wallpaperReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.launcher.REFRESH_WALLPAPER") {
                    loadSavedWallpaper()
                }
            }
        }
        val filter = IntentFilter("com.example.launcher.REFRESH_WALLPAPER")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wallpaperReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wallpaperReceiver, filter)
        }
    }

    private fun isPermissionsSetupComplete(): Boolean {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        return prefs.getBoolean("permissions_setup_complete", false)
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
                                Toast.makeText(this@MainActivity, "App instalado: $packageName", Toast.LENGTH_SHORT).show()
                            }
                            PackageChangeReceiver.TYPE_UNINSTALLED -> {
                                // Já tratado pelo packageRemovedReceiver existente
                            }
                            PackageChangeReceiver.TYPE_UPDATED -> {
                                // App atualizado
                            }
                        }
                        
                        // Recarrega a lista de apps
                        loadInstalledApps()
                        filteredAppList.clear()
                        filteredAppList.addAll(appList)
                        adapter.notifyDataSetChanged()
                        
                        // Atualiza a dock e desktop
                        renderPinnedApps()
                        setupDesktopGrid()
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
}

