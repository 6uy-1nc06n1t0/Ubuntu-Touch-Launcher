package com.example.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.DragEvent
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class EdgeSwipeService : Service() {

    private lateinit var windowManager: WindowManager
    private var edgeDetectorView: View? = null
    private var dockOverlayView: View? = null
    private val edgeWidth = 30
    private var isDockVisible = false
    private var isHiding = false
    private var isDrawerVisible = false
    private var isControlPanelVisible = false

    private lateinit var appList: MutableList<AppInfo>
    private lateinit var filteredAppList: MutableList<AppInfo>
    private lateinit var pinnedApps: MutableList<String>
    private lateinit var hiddenApps: MutableList<String>
    private var showHiddenApps = false
    
    private var appGridAdapter: AppGridAdapter? = null
    
    private var isDragging = false
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private val dragThreshold = 20f
    private val dragDelayDuration = 300L
    private var isDraggingFromDock = false
    private var draggedDockPackageName: String? = null
    private var draggedDockIndex = -1
    
    private var isFlashlightOn = false
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    
    // Control panel views
    private var wifiToggle: LinearLayout? = null
    private var bluetoothToggle: LinearLayout? = null
    private var airplaneModeToggle: LinearLayout? = null
    private var flashlightToggle: LinearLayout? = null
    private var calculatorToggle: LinearLayout? = null
    private var locationToggle: LinearLayout? = null
    private var brightnessSeekBar: SeekBar? = null
    private var wifiIcon: ImageView? = null
    private var bluetoothIcon: ImageView? = null
    private var airplaneModeIcon: ImageView? = null
    private var flashlightIcon: ImageView? = null
    private var calculatorIcon: ImageView? = null
    private var locationIcon: ImageView? = null

    // Overlay view elements
    private var overlayView: View? = null
    private var appDrawerContainer: FrameLayout? = null
    private var controlPanelContainer: FrameLayout? = null
    
    private var dockScrollView: ScrollView? = null
    
    private var pinnedAppsReceiver: BroadcastReceiver? = null

    private lateinit var customIconManager: CustomIconManager

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "edge_swipe_service_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PINNED_APPS_CHANGED = "com.example.launcher.PINNED_APPS_CHANGED"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        customIconManager = CustomIconManager(this)
        
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager?.cameraIdList?.get(0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        appList = mutableListOf()
        filteredAppList = mutableListOf()
        pinnedApps = mutableListOf()
        hiddenApps = mutableListOf()
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createEdgeDetector()
        registerPinnedAppsReceiver()
    }
    
    private fun registerPinnedAppsReceiver() {
        pinnedAppsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_PINNED_APPS_CHANGED) {
                    if (isDockVisible) {
                        loadPinnedApps()
                        val dockAppsContainer = overlayView?.findViewById<LinearLayout>(R.id.overlayDockAppsContainer)
                        if (dockAppsContainer != null) {
                            renderPinnedApps(dockAppsContainer)
                        }
                        filterApps("")
                    }
                }
            }
        }
        val filter = IntentFilter(ACTION_PINNED_APPS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pinnedAppsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pinnedAppsReceiver, filter)
        }
    }
    
    private fun notifyPinnedAppsChanged() {
        val intent = Intent(ACTION_PINNED_APPS_CHANGED)
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Edge Swipe Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Permite gestos de borda para abrir o dock"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Ubuntu Touch Launcher")
            .setContentText("Deslize da borda esquerda para abrir o dock")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createEdgeDetector() {
        edgeDetectorView = View(this)
        
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            edgeWidth,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.START or Gravity.TOP
        params.x = 0
        params.y = 0

        var swipeStartX = 0f
        var swipeStartY = 0f
        var isSwipeDetected = false

        edgeDetectorView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX
                    swipeStartY = event.rawY
                    isSwipeDetected = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - swipeStartX
                    val deltaY = abs(event.rawY - swipeStartY)

                    if (deltaX > 100 && deltaY < 100 && !isSwipeDetected) {
                        isSwipeDetected = true
                        if (!isDockVisible) {
                            showDockOverlay()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSwipeDetected = false
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(edgeDetectorView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showDockOverlay() {
        if (isDockVisible || isHiding) return
        
        try {
            val themedContext = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault)
            val inflater = LayoutInflater.from(themedContext)
            overlayView = inflater.inflate(R.layout.dock_overlay, null)
            
            if (overlayView == null) {
                Toast.makeText(this, "Erro ao criar overlay", Toast.LENGTH_SHORT).show()
                return
            }

            (overlayView as? BackInterceptLayout)?.onBackPressed = {
                handleBackPressed()
            }
            
            val dockContainer = overlayView?.findViewById<LinearLayout>(R.id.overlayDockContainer)
            val backgroundOverlay = overlayView?.findViewById<FrameLayout>(R.id.backgroundOverlay)
            val appDrawerBtn = overlayView?.findViewById<ImageView>(R.id.overlayBtnAppDrawer)
            val dockAppsContainer = overlayView?.findViewById<LinearLayout>(R.id.overlayDockAppsContainer)
            appDrawerContainer = overlayView?.findViewById<FrameLayout>(R.id.appDrawerContainer)
            controlPanelContainer = overlayView?.findViewById<FrameLayout>(R.id.overlayControlPanelContainer) // Added this line
            dockScrollView = overlayView?.findViewById(R.id.overlayDockScrollView) // Initialize dockScrollView
            
            initControlPanelViews()
            setupControlPanel()
            
            loadPinnedApps()
            loadHiddenApps()
            loadInstalledApps()

            if (dockAppsContainer != null) {
                renderPinnedApps(dockAppsContainer)
                setupDockDragListener(dockAppsContainer)
            }
            
            if (appDrawerContainer != null) {
                setupDrawerDragListener(appDrawerContainer!!)
            }
            
            backgroundOverlay?.setOnClickListener {
                if (isDrawerVisible) {
                    closeDrawerAndDock()
                } else if (isControlPanelVisible) {
                    closeControlPanel()
                } else {
                    hideOverlay()
                }
            }
            
            setupDockSwipeListener(dockContainer)
            
            appDrawerBtn?.setOnClickListener {
                if (isControlPanelVisible) {
                    closeControlPanel()
                }
                toggleAppDrawer()
            }
            
            appDrawerBtn?.setOnLongClickListener {
                if (isDrawerVisible) {
                    closeAppDrawer()
                }
                toggleControlPanel()
                true
            }

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.START or Gravity.TOP
            params.x = 0
            params.y = 0
            
            windowManager.addView(overlayView, params)
            isDockVisible = true
            
            overlayView?.requestFocus()
            
            overlayView?.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    handleBackPressed()
                    true
                } else {
                    false
                }
            }
            
            dockContainer?.let {
                val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_left)
                it.startAnimation(slideIn)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleBackPressed() {
        when {
            isControlPanelVisible -> {
                closeControlPanel()
            }
            appDrawerContainer?.visibility == View.VISIBLE -> {
                closeDrawerAndDock()
            }
            else -> {
                hideOverlay()
            }
        }
    }
    
    private fun initControlPanelViews() {
        wifiToggle = overlayView?.findViewById(R.id.overlayWifiToggle)
        bluetoothToggle = overlayView?.findViewById(R.id.overlayBluetoothToggle)
        airplaneModeToggle = overlayView?.findViewById(R.id.overlayAirplaneModeToggle)
        flashlightToggle = overlayView?.findViewById(R.id.overlayFlashlightToggle)
        calculatorToggle = overlayView?.findViewById(R.id.overlayCalculatorToggle)
        locationToggle = overlayView?.findViewById(R.id.overlayLocationToggle)
        brightnessSeekBar = overlayView?.findViewById(R.id.overlayBrightnessSeekBar)
        
        wifiIcon = overlayView?.findViewById(R.id.overlayWifiIcon)
        bluetoothIcon = overlayView?.findViewById(R.id.overlayBluetoothIcon)
        airplaneModeIcon = overlayView?.findViewById(R.id.overlayAirplaneModeIcon)
        flashlightIcon = overlayView?.findViewById(R.id.overlayFlashlightIcon)
        calculatorIcon = overlayView?.findViewById(R.id.overlayCalculatorIcon)
        locationIcon = overlayView?.findViewById(R.id.overlayLocationIcon)
    }
    
    private fun setupControlPanel() {
        wifiToggle?.setOnClickListener { toggleWifi() }
        bluetoothToggle?.setOnClickListener { toggleBluetooth() }
        airplaneModeToggle?.setOnClickListener { toggleAirplaneMode() }
        flashlightToggle?.setOnClickListener { toggleFlashlight() }
        calculatorToggle?.setOnClickListener { openCalculator() }
        locationToggle?.setOnClickListener { toggleLocation() }
        
        brightnessSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
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
    
    private fun setupDockSwipeListener(dockContainer: LinearLayout?) {
        var swipeStartX = 0f
        var swipeStartY = 0f
        var swipeStartTime = 0L
        var isSwipeDetected = false
        
        dockContainer?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX
                    swipeStartY = event.rawY
                    swipeStartTime = System.currentTimeMillis()
                    isSwipeDetected = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - swipeStartX
                    val deltaY = abs(event.rawY - swipeStartY)
                    val timeDelta = System.currentTimeMillis() - swipeStartTime
                    
                    if (deltaX > 150 && deltaY < 80 && timeDelta < 500 && !isSwipeDetected) {
                        isSwipeDetected = true
                        if (isDrawerVisible) {
                            closeAppDrawer()
                        }
                        openControlPanel()
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSwipeDetected = false
                    false
                }
                else -> false
            }
        }
    }
    
    private fun toggleControlPanel() {
        if (isControlPanelVisible) {
            closeControlPanel()
        } else {
            openControlPanel()
        }
    }
    
    private fun openControlPanel() {
        if (isDrawerVisible) {
            closeAppDrawer()
        }
        
        controlPanelContainer?.let { panel ->
            panel.visibility = View.VISIBLE
            isControlPanelVisible = true
            val animation = AnimationUtils.loadAnimation(this, R.anim.slide_in_left)
            panel.startAnimation(animation)
            updateControlPanelStates()
        }
    }
    
    private fun closeControlPanel() {
        controlPanelContainer?.let { panel ->
            val animation = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
            panel.startAnimation(animation)
            panel.postDelayed({
                panel.visibility = View.GONE
                isControlPanelVisible = false
            }, 300)
        }
    }
    
    private fun toggleWifi() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(panelIntent)
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun toggleBluetooth() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun toggleAirplaneMode() {
        try {
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun toggleFlashlight() {
        try {
            if (cameraId != null) {
                isFlashlightOn = !isFlashlightOn
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cameraManager?.setTorchMode(cameraId!!, isFlashlightOn)
                }
                updateFlashlightState()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateFlashlightState() {
        flashlightToggle?.isActivated = isFlashlightOn
        flashlightIcon?.setColorFilter(if (isFlashlightOn) Color.WHITE else Color.parseColor("#DD6200"))
    }
    
    private fun openCalculator() {
        try {
            val intent = Intent()
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_APP_CALCULATOR)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            closeControlPanel()
        } catch (e: Exception) {
            try {
                val calcIntent = Intent()
                calcIntent.setClassName("com.android.calculator2", "com.android.calculator2.Calculator")
                calcIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(calcIntent)
                closeControlPanel()
            } catch (e2: Exception) {
                Toast.makeText(this, "Calculadora não encontrada", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun toggleLocation() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateControlPanelStates() {
        updateWifiState()
        updateBluetoothState()
        updateFlashlightState()
        updateAirplaneModeState()
        updateLocationState()
        updateBrightnessState()
    }
    
    private fun updateWifiState() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val isEnabled = wifiManager.isWifiEnabled
            wifiToggle?.isActivated = isEnabled
            wifiIcon?.setColorFilter(if (isEnabled) Color.WHITE else Color.parseColor("#DD6200"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateBluetoothState() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val isEnabled = bluetoothAdapter?.isEnabled == true
            bluetoothToggle?.isActivated = isEnabled
            bluetoothIcon?.setColorFilter(if (isEnabled) Color.WHITE else Color.parseColor("#DD6200"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateAirplaneModeState() {
        try {
            val isEnabled = Settings.Global.getInt(
                contentResolver,
                Settings.Global.AIRPLANE_MODE_ON, 0
            ) == 1
            airplaneModeToggle?.isActivated = isEnabled
            airplaneModeIcon?.setColorFilter(if (isEnabled) Color.WHITE else Color.parseColor("#DD6200"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateLocationState() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val isEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                           locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            locationToggle?.isActivated = isEnabled
            locationIcon?.setColorFilter(if (isEnabled) Color.WHITE else Color.parseColor("#DD6200"))
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
            brightnessSeekBar?.progress = currentBrightness
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideOverlay() {
        if (!isDockVisible || isHiding) return
        isHiding = true
        
        val dockContainer = overlayView?.findViewById<LinearLayout>(R.id.overlayDockContainer)
        val slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
        
        slideOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                try {
                    if (overlayView != null) {
                        windowManager.removeView(overlayView)
                        overlayView = null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                isDockVisible = false
                isDrawerVisible = false
                isControlPanelVisible = false
                isHiding = false
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        
        dockContainer?.startAnimation(slideOut)
    }

    private fun toggleAppDrawer() {
        if (isDrawerVisible) {
            closeAppDrawer()
        } else {
            openAppDrawer()
        }
    }

    private fun openAppDrawer() {
        appDrawerContainer?.visibility = View.VISIBLE
        isDrawerVisible = true

        val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_left)
        appDrawerContainer?.startAnimation(slideIn)

        val appGrid = overlayView?.findViewById<GridView>(R.id.overlayAppGrid)
        val searchBar = overlayView?.findViewById<EditText>(R.id.overlaySearchBar)
        val sortMenuBtn = overlayView?.findViewById<ImageView>(R.id.overlayBtnSortMenu)

        if (appGrid != null) {
            appGridAdapter = AppGridAdapter(this, filteredAppList)
            appGrid.adapter = appGridAdapter
            
            appGrid.setOnItemClickListener { _, _, position, _ ->
                if (position < filteredAppList.size) {
                    val app = filteredAppList[position]
                    launchApp(app.packageName)
                }
            }
            
            appGrid.setOnItemLongClickListener { _, view, position, _ ->
                if (position < filteredAppList.size) {
                    val app = filteredAppList[position]
                    showAppOptionsDialog(app, view)
                }
                true
            }
        }

        searchBar?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        sortMenuBtn?.setOnClickListener {
            showSortMenu(it)
        }
    }

    private fun closeAppDrawer() {
        val searchBar = overlayView?.findViewById<EditText>(R.id.overlaySearchBar)
        
        searchBar?.let {
            it.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }

        val slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
        appDrawerContainer?.startAnimation(slideOut)
        
        appDrawerContainer?.postDelayed({
            appDrawerContainer?.visibility = View.GONE
            isDrawerVisible = false
            overlayView?.postDelayed({
                hideOverlay()
            }, 300)
        }, 300)
    }

    private fun closeDrawerAndDock() {
        appDrawerContainer?.let { drawer ->
            if (drawer.visibility == View.VISIBLE) {
                val searchBar = overlayView?.findViewById<EditText>(R.id.overlaySearchBar)
                searchBar?.let {
                    it.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                }
                
                val animation = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
                drawer.startAnimation(animation)
                drawer.postDelayed({
                    drawer.visibility = View.GONE
                    isDrawerVisible = false
                    overlayView?.postDelayed({
                        hideOverlay()
                    }, 300)
                }, 300)
            } else {
                hideOverlay()
            }
        } ?: hideOverlay()
    }
    
    private fun hideDrawer() {
        appDrawerContainer?.let { drawer ->
            if (drawer.visibility == View.VISIBLE) {
                val searchBar = overlayView?.findViewById<EditText>(R.id.overlaySearchBar)
                searchBar?.let {
                    it.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                }
                
                val animation = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
                drawer.startAnimation(animation)
                drawer.postDelayed({
                    drawer.visibility = View.GONE
                    isDrawerVisible = false
                    overlayView?.postDelayed({
                        hideOverlay()
                    }, 300)
                }, 300)
            }
        }
    }

    private fun loadPinnedApps() {
        try {
            val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            val pinnedString = prefs.getString("pinned_apps_ordered", null)
            pinnedApps = if (pinnedString != null && pinnedString.isNotEmpty()) {
                pinnedString.split(",").toMutableList()
            } else {
                mutableListOf()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            pinnedApps = mutableListOf()
        }
    }
    
    private fun savePinnedApps() {
        try {
            val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("pinned_apps_ordered", pinnedApps.joinToString(",")).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadHiddenApps() {
        try {
            val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
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
            val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("hidden_apps", hiddenApps.joinToString(",")).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun hideApp(packageName: String, label: String) {
        if (!hiddenApps.contains(packageName)) {
            hiddenApps.add(packageName)
            saveHiddenApps()
            filterApps("")
            Toast.makeText(this, "$label oculto", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showApp(packageName: String, label: String) {
        if (hiddenApps.contains(packageName)) {
            hiddenApps.remove(packageName)
            saveHiddenApps()
            filterApps("")
            Toast.makeText(this, "$label visível novamente", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        appList = resolveInfoList
            .filter { it.activityInfo.packageName != packageName }
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
            .sortedBy { it.label.lowercase() }
            .toMutableList()
        
        filterApps("")
    }

    private fun filterApps(query: String) {
        filteredAppList.clear()
        
        val visibleApps = appList.filter { app ->
            val isHidden = hiddenApps.contains(app.packageName)
            val isPinnedToDock = pinnedApps.contains(app.packageName)
            
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
        
        appGridAdapter?.notifyDataSetChanged()
    }

    private fun showSortMenu(view: View) {
        val popup = PopupMenu(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault), view, Gravity.END)
        popup.menu.add(0, 1, 0, "Nome (A-Z)")
        popup.menu.add(0, 4, 1, "Nome (Z-A)")
        popup.menu.add(0, 2, 2, "Mais recentes")
        if (showHiddenApps) {
            popup.menu.add(0, 3, 3, "Ocultar apps escondidos")
        } else {
            popup.menu.add(0, 3, 3, "Mostrar apps ocultos")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    sortAppsByName()
                    true
                }
                4 -> {
                    sortAppsByNameDesc()
                    true
                }
                2 -> {
                    sortAppsByRecent()
                    true
                }
                3 -> {
                    showHiddenApps = !showHiddenApps
                    filterApps("")
                    val message = if (showHiddenApps) "Mostrando apps ocultos" else "Apps ocultos escondidos"
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }
    
    private fun sortAppsByName() {
        appList = appList.sortedBy { it.label.lowercase() }.toMutableList()
        filterApps("")
    }
    
    private fun sortAppsByNameDesc() {
        appList = appList.sortedByDescending { it.label.lowercase() }.toMutableList()
        filterApps("")
    }
    
    private fun sortAppsByRecent() {
        val pm = packageManager
        appList = appList.sortedByDescending { appInfo ->
            try {
                pm.getPackageInfo(appInfo.packageName, 0).firstInstallTime
            } catch (e: Exception) {
                0L
            }
        }.toMutableList()
        filterApps("")
    }

    private fun showAppOptionsDialog(appInfo: AppInfo, view: View) {
        val options = mutableListOf<String>()
        options.add("Abrir")
        options.add("Informações do app")
        
        val isSystemApp = isSystemApp(appInfo.packageName)
        if (!isSystemApp) {
            options.add("Desinstalar")
        }
        
        val popup = PopupMenu(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault), view)
        options.forEachIndexed { index, option ->
            popup.menu.add(0, index, index, option)
        }
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (options[menuItem.itemId]) {
                "Abrir" -> launchApp(appInfo.packageName)
                "Informações do app" -> showAppInfo(appInfo.packageName)
                "Desinstalar" -> uninstallApp(appInfo.packageName)
            }
            true
        }
        
        popup.show()
    }
    
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isAppPinnedToDesktop(packageName: String): Boolean {
        val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val pinnedString = prefs.getString("desktop_pinned_apps_v2", null) ?: return false
        return pinnedString.contains(packageName)
    }
    
    private fun pinAppToDesktop(appInfo: AppInfo) {
        val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val pinnedString = prefs.getString("desktop_pinned_apps_v2", null)
        val desktopPinnedApps = mutableMapOf<Int, String>()
        
        if (pinnedString != null && pinnedString.isNotEmpty()) {
            pinnedString.split(",").forEach { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val position = parts[0].toIntOrNull()
                    val pkg = parts[1]
                    if (position != null) {
                        desktopPinnedApps[position] = pkg
                    }
                }
            }
        }
        
        if (desktopPinnedApps.containsValue(appInfo.packageName)) {
            Toast.makeText(this, "${appInfo.label} já está na tela inicial", Toast.LENGTH_SHORT).show()
            return
        }
        
        var freePosition = -1
        for (i in 0 until 44) {
            if (!desktopPinnedApps.containsKey(i)) {
                freePosition = i
                break
            }
        }
        
        if (freePosition == -1) {
            Toast.makeText(this, "Tela inicial cheia (máx. 44 apps)", Toast.LENGTH_SHORT).show()
            return
        }
        
        desktopPinnedApps[freePosition] = appInfo.packageName
        val newPinnedString = desktopPinnedApps.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit().putString("desktop_pinned_apps_v2", newPinnedString).apply()
        
        Toast.makeText(this, "${appInfo.label} fixado na tela inicial", Toast.LENGTH_SHORT).show()
        notifyPinnedAppsChanged()
    }
    
    private fun unpinAppFromDesktop(packageName: String) {
        val prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val pinnedString = prefs.getString("desktop_pinned_apps_v2", null) ?: return
        val desktopPinnedApps = mutableMapOf<Int, String>()
        
        pinnedString.split(",").forEach { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val position = parts[0].toIntOrNull()
                val pkg = parts[1]
                if (position != null) {
                    desktopPinnedApps[position] = pkg
                }
            }
        }
        
        val position = desktopPinnedApps.entries.find { it.value == packageName }?.key
        if (position != null) {
            desktopPinnedApps.remove(position)
            val newPinnedString = desktopPinnedApps.entries.joinToString(",") { "${it.key}:${it.value}" }
            prefs.edit().putString("desktop_pinned_apps_v2", newPinnedString).apply()
            Toast.makeText(this, "App removido da tela inicial", Toast.LENGTH_SHORT).show()
            notifyPinnedAppsChanged()
        }
    }
    
    private fun showAppInfo(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir informações do app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pinApp(appInfo: AppInfo) {
        if (!pinnedApps.contains(appInfo.packageName)) {
            pinnedApps.add(appInfo.packageName)
            savePinnedApps()
            val dockAppsContainer = overlayView?.findViewById<LinearLayout>(R.id.overlayDockAppsContainer)
            if (dockAppsContainer != null) {
                renderPinnedApps(dockAppsContainer)
            }
            filterApps("")
            notifyPinnedAppsChanged()
        }
    }
    
    private fun pinAppAtPosition(appInfo: AppInfo, position: Int) {
        if (!pinnedApps.contains(appInfo.packageName)) {
            val insertPosition = position.coerceIn(0, pinnedApps.size)
            pinnedApps.add(insertPosition, appInfo.packageName)
            savePinnedApps()
            val dockAppsContainer = overlayView?.findViewById<LinearLayout>(R.id.overlayDockAppsContainer)
            if (dockAppsContainer != null) {
                renderPinnedApps(dockAppsContainer)
            }
            filterApps("")
            notifyPinnedAppsChanged()
        }
    }
    
    private fun unpinApp(packageName: String) {
        pinnedApps.remove(packageName)
        savePinnedApps()
        val dockAppsContainer = overlayView?.findViewById<LinearLayout>(R.id.overlayDockAppsContainer)
        if (dockAppsContainer != null) {
            renderPinnedApps(dockAppsContainer)
        }
        filterApps("")
        notifyPinnedAppsChanged()
    }

    private fun uninstallApp(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível desinstalar o app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            hideOverlay()
        }
    }
    
    private fun setupDockDragListener(dockAppsContainer: LinearLayout) {
        dockAppsContainer.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    if (!isDraggingFromDock) {
                        v.alpha = 0.7f
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (!isDraggingFromDock) {
                        v.alpha = 1.0f
                    }
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    if (!isDraggingFromDock) {
                        v.alpha = 0.7f
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
                            // Reordering is handled per icon
                        } else if (itemText.startsWith("drawer_app:")) {
                            val packageName = itemText.replace("drawer_app:", "")
                            val appInfo = appList.find { app -> app.packageName == packageName }
                            if (appInfo != null && !pinnedApps.contains(packageName)) {
                                pinAppAtPosition(appInfo, targetPosition)
                            }
                        } else if (itemText.startsWith("desktop_app:")) {
                            val packageName = itemText.replace("desktop_app:", "")
                            val appInfo = appList.find { app -> app.packageName == packageName }
                            if (appInfo != null && !pinnedApps.contains(packageName)) {
                                unpinAppFromDesktop(packageName)
                                pinAppAtPosition(appInfo, targetPosition)
                            }
                        } else {
                            val appInfo = appList.find { app -> app.packageName == itemText }
                            if (appInfo != null) {
                                pinAppAtPosition(appInfo, targetPosition)
                            }
                        }
                    }
                    v.alpha = 1.0f
                    isDragging = false
                    isDraggingFromDock = false
                    draggedDockPackageName = null
                    draggedDockIndex = -1
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    v.alpha = 1.0f
                    isDragging = false
                    isDraggingFromDock = false
                    draggedDockPackageName = null
                    draggedDockIndex = -1
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupDrawerDragListener(appDrawerContainer: FrameLayout) {
        appDrawerContainer.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    val clipDesc = event.clipDescription
                    val hasText = clipDesc?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                    if (hasText && isDraggingFromDock) {
                        v.setBackgroundColor(Color.parseColor("#99000000"))
                    }
                    hasText
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (isDraggingFromDock) {
                        v.setBackgroundColor(Color.parseColor("#66FFFFFF"))
                    }
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    if (isDraggingFromDock) {
                        v.setBackgroundColor(Color.parseColor("#99000000"))
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
                        }
                    }
                    v.setBackgroundColor(Color.parseColor("#B3000000"))
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    v.setBackgroundColor(Color.parseColor("#B3000000"))
                    true
                }
                else -> true
            }
        }
    }
    
    private fun calculateDockDropPosition(dropY: Float): Int {
        val density = resources.displayMetrics.density
        val iconSize = (64 * density).toInt()
        val firstMargin = (32 * density).toInt()
        val normalMargin = (16 * density).toInt()
        
        if (pinnedApps.isEmpty()) return 0
        
        var currentY = firstMargin
        for (i in pinnedApps.indices) {
            val itemEnd = currentY + iconSize
            if (dropY < itemEnd) {
                return i
            }
            currentY = itemEnd + normalMargin
        }
        return pinnedApps.size
    }

    private fun renderPinnedApps(dockAppsContainer: LinearLayout) {
        dockAppsContainer.removeAllViews()
        val pm = packageManager

        // O layout_gravity="bottom" no XML faz os apps ficarem na parte inferior
        for ((index, packageName) in pinnedApps.withIndex()) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val defaultIcon = appInfo.loadIcon(pm)
                
                val icon = customIconManager.getIconForApp(packageName, defaultIcon)
                
                val iconView = ImageView(this)
                val iconSize = (64 * resources.displayMetrics.density).toInt()
                val layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                val topMargin = (8 * resources.displayMetrics.density).toInt()
                val bottomMargin = (8 * resources.displayMetrics.density).toInt()
                layoutParams.setMargins(0, topMargin, 0, bottomMargin)
                layoutParams.gravity = Gravity.CENTER_HORIZONTAL
                iconView.layoutParams = layoutParams
                iconView.setImageDrawable(icon)
                iconView.scaleType = ImageView.ScaleType.FIT_CENTER
                iconView.tag = index

                iconView.setOnClickListener {
                    if (!isDragging) {
                        launchApp(packageName)
                    }
                }

                var dockTouchDownX = 0f
                var dockTouchDownY = 0f
                var dockDragging = false
                var dockDragReadyTime = 0L
                
                iconView.setOnTouchListener { view, event ->
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
                                    
                                    val options = mutableListOf("Abrir", "Informações do app")
                                    if (!isSystemApp) {
                                        options.add("Desinstalar")
                                    }
                                    
                                    val popup = PopupMenu(ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault), view)
                                    options.forEachIndexed { idx, option ->
                                        popup.menu.add(0, idx, idx, option)
                                    }
                                    
                                    popup.setOnMenuItemClickListener { menuItem ->
                                        when (options[menuItem.itemId]) {
                                            "Abrir" -> launchApp(packageName)
                                            "Informações do app" -> showAppInfo(packageName)
                                            "Desinstalar" -> {
                                                if (!isSystemApp) {
                                                    uninstallApp(packageName)
                                                }
                                            }
                                        }
                                        true
                                    }
                                    popup.show()
                                }
                            }
                            longPressHandler.postDelayed(longPressRunnable!!, 600)
                            false
                        }
                        
                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = abs(event.x - dockTouchDownX)
                            val deltaY = abs(event.y - dockTouchDownY)
                            
                            if ((deltaX > dragThreshold || deltaY > dragThreshold) && !dockDragging) {
                                if (dockDragReadyTime == 0L) {
                                    dockDragReadyTime = System.currentTimeMillis() + dragDelayDuration
                                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
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
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                            dockDragReadyTime = 0L
                            dockDragging = false
                            false
                        }
                        
                        else -> false
                    }
                }
                
                iconView.setOnDragListener { targetView, event ->
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
                                    // Find the actual index in the original pinnedApps list
                                    val actualDraggedIndex = pinnedApps.indexOf(draggedPackage)
                                    if (actualDraggedIndex != -1) {
                                        pinnedApps.removeAt(actualDraggedIndex)
                                        // Calculate insertion position based on the targetIndex, considering the reversed iteration
                                        val reversedTargetIndex = pinnedApps.size - targetIndex
                                        pinnedApps.add(reversedTargetIndex, draggedPackage)
                                        
                                        savePinnedApps()
                                        renderPinnedApps(dockAppsContainer)
                                        notifyPinnedAppsChanged()
                                        
                                        Toast.makeText(this, "Posição alterada", Toast.LENGTH_SHORT).show()
                                    }
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

                dockAppsContainer.addView(iconView)
            } catch (e: Exception) {
                pinnedApps.remove(packageName)
                savePinnedApps()
            }
        }
        
        dockScrollView?.post {
            dockScrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            pinnedAppsReceiver?.let {
                unregisterReceiver(it)
            }
            if (edgeDetectorView != null) {
                windowManager.removeView(edgeDetectorView)
            }
            if (overlayView != null) { // Changed from dockOverlayView to overlayView
                windowManager.removeView(overlayView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        longPressHandler.removeCallbacksAndMessages(null)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    class AppGridAdapter(private val context: Context, private val apps: List<AppInfo>) : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
            
            val app = apps[position]
            val iconView = view.findViewById<ImageView>(R.id.appIcon)
            val nameView = view.findViewById<TextView>(R.id.appLabel)
            
            iconView.setImageDrawable(app.icon)
            nameView.text = app.label
            
            var touchStartX = 0f
            var touchStartY = 0f
            var touchStartTime = 0L
            var isDragging = false
            
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.rawX
                        touchStartY = event.rawY
                        touchStartTime = System.currentTimeMillis()
                        isDragging = false
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = abs(event.rawX - touchStartX)
                        val deltaY = abs(event.rawY - touchStartY)
                        val timeDelta = System.currentTimeMillis() - touchStartTime
                        
                        if (!isDragging && (deltaX > 30 || deltaY > 30) && timeDelta > 150) {
                            isDragging = true
                            val clipData = ClipData.newPlainText("drawer_app", "drawer_app:${app.packageName}")
                            val shadowBuilder = View.DragShadowBuilder(v)
                            v.startDragAndDrop(clipData, shadowBuilder, v, 0)
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            
            return view
        }
    }
}
