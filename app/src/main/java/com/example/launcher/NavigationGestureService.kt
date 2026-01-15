package com.example.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

class NavigationGestureService : AccessibilityService() {

    companion object {
        const val ACTION_SERVICE_CRASHED = "com.example.launcher.SERVICE_CRASHED"
        const val ACTION_SERVICE_STARTED = "com.example.launcher.SERVICE_STARTED"
        const val ACTION_REINITIALIZE = "com.example.launcher.ACTION_REINITIALIZE"
        const val PREFS_NAME = "gesture_service_prefs"
        const val KEY_SERVICE_ENABLED = "service_enabled"
        
        @Volatile
        private var instance: NavigationGestureService? = null
        
        fun isServiceRunning(): Boolean {
            return try {
                val inst = instance
                inst != null && 
                inst.isServiceConnected && 
                inst.rightEdgeView?.isAttachedToWindow == true &&
                inst.bottomEdgeView?.isAttachedToWindow == true
            } catch (e: Exception) {
                false
            }
        }
        
        fun isServiceAlive(): Boolean {
            return try {
                instance != null && instance?.isServiceConnected == true
            } catch (e: Exception) {
                false
            }
        }
        
        fun forceReinitialize() {
            try {
                instance?.safeReinitialize()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        fun getInstance(): NavigationGestureService? = instance
    }

    private var windowManager: WindowManager? = null
    private var rightEdgeView: View? = null
    private var bottomEdgeView: View? = null
    
    @Volatile
    private var isServiceConnected = false
    
    @Volatile
    private var isReinitializing = false
    
    private val edgeWidth = 30
    private val bottomEdgeHeight = 60
    
    private val swipeThreshold = 80
    private val maxHorizontalDeviation = 150
    
    private var isBottomSwipeActive = false
    private var bottomSwipeStartTime = 0L
    private var swipeCompletedTime = 0L
    private val holdDuration = 400L
    private val handler = Handler(Looper.getMainLooper())
    private var recentAppsRunnable: Runnable? = null
    private var hasTriggeredRecents = false
    
    private var watchdogRunnable: Runnable? = null
    private val watchdogIntervalMs = 1000L // More aggressive watchdog - 1 second
    
    private var recoveryAttempts = 0
    private val maxRecoveryAttempts = 10 // More recovery attempts

    private var reinitializeReceiver: BroadcastReceiver? = null
    
    private var packageChangedReceiver: BroadcastReceiver? = null
    
    private var lastSuccessfulOperation = 0L

    override fun onCreate() {
        super.onCreate()
        setupExceptionHandler()
        registerPackageChangedReceiver()
    }
    
    private fun registerPackageChangedReceiver() {
        try {
            packageChangedReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_PACKAGE_DATA_CLEARED,
                        Intent.ACTION_PACKAGE_RESTARTED -> {
                            val packageName = intent.data?.schemeSpecificPart
                            if (packageName == context?.packageName) {
                                handler.postDelayed({
                                    safeReinitialize()
                                }, 1000L)
                            }
                        }
                    }
                }
            }
            
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_DATA_CLEARED)
                addAction(Intent.ACTION_PACKAGE_RESTARTED)
                addDataScheme("package")
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(packageChangedReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setupExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isServiceRelated = throwable.stackTrace.any { 
                it.className.contains("NavigationGestureService") ||
                it.className.contains("GestureService")
            }
            
            if (isServiceRelated && isServiceConnected) {
                try {
                    safeBroadcast(ACTION_SERVICE_CRASHED)
                    
                    handler.post {
                        safeReinitialize()
                    }
                    return@setDefaultUncaughtExceptionHandler
                } catch (e: Exception) {
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceConnected = true
        isReinitializing = false
        recoveryAttempts = 0
        lastSuccessfulOperation = System.currentTimeMillis()
        
        saveServiceEnabled(true)
        
        clearCrashedFlag()
        safeBroadcast(ACTION_SERVICE_STARTED)
        
        configureServiceInfo()
        
        safeInitialize()
        startWatchdog()
        registerReinitializeReceiver()
        
        AccessibilityServiceMonitor.startMonitor(this)
    }
    
    private fun configureServiceInfo() {
        try {
            serviceInfo = serviceInfo?.apply {
                // Request all event types to keep service active
                eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                // Keep service running even when no events
                flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                notificationTimeout = 100
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun registerReinitializeReceiver() {
        try {
            reinitializeReceiver?.let {
                try { unregisterReceiver(it) } catch (e: Exception) { }
            }
            
            reinitializeReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_REINITIALIZE) {
                        safeReinitialize()
                    }
                }
            }
            
            val filter = IntentFilter(ACTION_REINITIALIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(reinitializeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(reinitializeReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun saveServiceEnabled(enabled: Boolean) {
        try {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SERVICE_ENABLED, enabled)
                .apply()
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun safeInitialize() {
        if (!isServiceConnected) return
        
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (windowManager == null) {
                handler.postDelayed({ safeInitialize() }, 500L)
                return
            }
            createEdgeDetectorsSafe()
        } catch (e: Exception) {
            e.printStackTrace()
            handler.postDelayed({ safeInitialize() }, 1000L)
        }
    }
    
    private fun createEdgeDetectorsSafe() {
        if (!isServiceConnected || windowManager == null) return
        
        try {
            safeCleanupViews()
            
            handler.post {
                try {
                    createRightEdgeDetector()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            handler.postDelayed({
                try {
                    createBottomEdgeDetector()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                handler.postDelayed({
                    verifyAndRepairViews()
                }, 300L)
            }, 100L)
            
        } catch (e: Exception) {
            e.printStackTrace()
            handler.postDelayed({ createEdgeDetectorsSafe() }, 1000L)
        }
    }
    
    private fun verifyAndRepairViews() {
        if (!isServiceConnected) return
        
        try {
            val rightOk = rightEdgeView?.isAttachedToWindow == true
            val bottomOk = bottomEdgeView?.isAttachedToWindow == true
            
            if (!rightOk) {
                try {
                    createRightEdgeDetector()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (!bottomOk) {
                try {
                    createBottomEdgeDetector()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (rightOk && bottomOk) {
                recoveryAttempts = 0
                lastSuccessfulOperation = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun safeReinitialize() {
        if (!isServiceConnected) {
            instance = this
            isServiceConnected = true
        }
        
        if (isReinitializing) return
        
        if (recoveryAttempts >= maxRecoveryAttempts) {
            handler.postDelayed({
                recoveryAttempts = 0
                safeReinitialize()
            }, 3000L) // Reduced wait time
            return
        }
        
        isReinitializing = true
        recoveryAttempts++
        
        val backoffDelay = (200L * recoveryAttempts).coerceAtMost(1500L) // Faster recovery
        
        handler.post {
            try {
                safeCleanupViews()
                
                handler.postDelayed({
                    try {
                        // Re-acquire window manager
                        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                        
                        if (windowManager != null) {
                            createEdgeDetectorsSafe()
                        } else {
                            // WindowManager null, try again
                            handler.postDelayed({
                                isReinitializing = false
                                safeReinitialize()
                            }, 500L)
                            return@postDelayed
                        }
                    } finally {
                        isReinitializing = false
                    }
                }, backoffDelay)
            } catch (e: Exception) {
                e.printStackTrace()
                isReinitializing = false
            }
        }
    }
    
    private fun safeCleanupViews() {
        try {
            rightEdgeView?.let { view ->
                try {
                    if (view.isAttachedToWindow) {
                        windowManager?.removeView(view)
                    } else Unit
                } catch (e: Exception) {
                    try {
                        windowManager?.removeViewImmediate(view)
                    } catch (e2: Exception) { }
                }
            }
        } catch (e: Exception) { }
        rightEdgeView = null
        
        try {
            bottomEdgeView?.let { view ->
                try {
                    if (view.isAttachedToWindow) {
                        windowManager?.removeView(view)
                    } else Unit
                } catch (e: Exception) {
                    try {
                        windowManager?.removeViewImmediate(view)
                    } catch (e2: Exception) { }
                }
            }
        } catch (e: Exception) { }
        bottomEdgeView = null
    }
    
    private fun startWatchdog() {
        stopWatchdog()
        
        watchdogRunnable = object : Runnable {
            override fun run() {
                if (!isServiceConnected) {
                    isServiceConnected = true
                    safeReinitialize()
                    handler.postDelayed(this, watchdogIntervalMs)
                    return
                }
                
                try {
                    val rightOk = try { rightEdgeView?.isAttachedToWindow == true } catch (e: Exception) { false }
                    val bottomOk = try { bottomEdgeView?.isAttachedToWindow == true } catch (e: Exception) { false }
                    
                    if (!rightOk || !bottomOk) {
                        if (!isReinitializing) {
                            if (!rightOk && bottomOk) {
                                try { createRightEdgeDetector() } catch (e: Exception) { safeReinitialize() }
                            } else if (rightOk && !bottomOk) {
                                try { createBottomEdgeDetector() } catch (e: Exception) { safeReinitialize() }
                            } else {
                                safeReinitialize()
                            }
                        }
                    } else {
                        lastSuccessfulOperation = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (!isReinitializing) {
                        safeReinitialize()
                    }
                }
                
                handler.postDelayed(this, watchdogIntervalMs)
            }
        }
        
        handler.postDelayed(watchdogRunnable!!, watchdogIntervalMs)
    }
    
    private fun stopWatchdog() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        watchdogRunnable = null
    }
    
    private fun clearCrashedFlag() {
        try {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("service_crashed", false)
                .apply()
        } catch (e: Exception) { }
    }
    
    private fun safeBroadcast(action: String) {
        try {
            sendBroadcast(Intent(action).apply {
                setPackage(packageName)
            })
        } catch (e: Exception) { }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (isServiceConnected) {
                lastSuccessfulOperation = System.currentTimeMillis()
                
                // Periodically verify views on events
                if (System.currentTimeMillis() - lastSuccessfulOperation > 5000L) {
                    verifyAndRepairViews()
                }
            }
        } catch (e: Exception) { }
    }

    override fun onInterrupt() {
        handler.post {
            if (!isReinitializing) {
                safeReinitialize()
            }
        }
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        safeBroadcast(ACTION_SERVICE_CRASHED)
        
        // Return true so onRebind will be called if service reconnects
        return true
    }
    
    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        
        instance = this
        isServiceConnected = true
        isReinitializing = false
        recoveryAttempts = 0
        
        // Reinitialize everything
        handler.post {
            safeInitialize()
            startWatchdog()
        }
        
        safeBroadcast(ACTION_SERVICE_STARTED)
    }
    
    override fun onDestroy() {
        // Don't set isServiceConnected = false, let the service try to recover
        // Only cleanup if truly being destroyed
        
        try {
            reinitializeReceiver?.let {
                try { unregisterReceiver(it) } catch (e: Exception) { }
            }
            packageChangedReceiver?.let {
                try { unregisterReceiver(it) } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        
        // Broadcast crash so monitor can try to restart
        safeBroadcast(ACTION_SERVICE_CRASHED)
        
        super.onDestroy()
    }

    private fun createRightEdgeDetector() {
        if (!isServiceConnected || windowManager == null) return
        
        try {
            rightEdgeView?.let { view ->
                try {
                    if (view.isAttachedToWindow) {
                        windowManager?.removeView(view)
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        
        rightEdgeView = View(this).apply {
            tag = "right_edge_detector"
        }
        
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
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

        params.gravity = Gravity.END or Gravity.TOP
        params.x = 0
        params.y = 0

        var swipeStartX = 0f
        var swipeStartY = 0f
        var isSwipeDetected = false

        rightEdgeView?.setOnTouchListener { _, event ->
            if (!isServiceConnected) {
                isServiceConnected = true
                return@setOnTouchListener false
            }
            
            try {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        swipeStartX = event.rawX
                        swipeStartY = event.rawY
                        isSwipeDetected = false
                        lastSuccessfulOperation = System.currentTimeMillis()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = swipeStartX - event.rawX
                        val deltaY = abs(event.rawY - swipeStartY)

                        if (deltaX > swipeThreshold && deltaY < maxHorizontalDeviation && !isSwipeDetected) {
                            isSwipeDetected = true
                            safePerformAction { performGlobalAction(GLOBAL_ACTION_BACK) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isSwipeDetected = false
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post { verifyAndRepairViews() }
                false
            }
        }

        try {
            windowManager?.addView(rightEdgeView, params)
            lastSuccessfulOperation = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
            rightEdgeView = null
            handler.postDelayed({
                if (isServiceConnected && rightEdgeView == null) {
                    createRightEdgeDetector()
                }
            }, 500L)
        }
    }

    private fun createBottomEdgeDetector() {
        if (!isServiceConnected || windowManager == null) return
        
        try {
            bottomEdgeView?.let { view ->
                try {
                    if (view.isAttachedToWindow) {
                        windowManager?.removeView(view)
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        
        bottomEdgeView = View(this).apply {
            tag = "bottom_edge_detector"
        }
        
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val params = WindowManager.LayoutParams(
            screenWidth,
            bottomEdgeHeight,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = 0

        var swipeStartX = 0f
        var swipeStartY = 0f

        bottomEdgeView?.setOnTouchListener { _, event ->
            if (!isServiceConnected) {
                isServiceConnected = true
                return@setOnTouchListener false
            }
            
            try {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        swipeStartX = event.rawX
                        swipeStartY = event.rawY
                        isBottomSwipeActive = false
                        hasTriggeredRecents = false
                        bottomSwipeStartTime = System.currentTimeMillis()
                        swipeCompletedTime = 0L
                        recentAppsRunnable?.let { handler.removeCallbacks(it) }
                        lastSuccessfulOperation = System.currentTimeMillis()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = abs(event.rawX - swipeStartX)
                        val deltaY = swipeStartY - event.rawY

                        if (deltaY > swipeThreshold && deltaX < maxHorizontalDeviation && !isBottomSwipeActive) {
                            isBottomSwipeActive = true
                            swipeCompletedTime = System.currentTimeMillis()
                            
                            recentAppsRunnable = Runnable {
                                if (isBottomSwipeActive && !hasTriggeredRecents && isServiceConnected) {
                                    hasTriggeredRecents = true
                                    safeVibrate()
                                    safePerformAction { performGlobalAction(GLOBAL_ACTION_RECENTS) }
                                }
                            }
                            handler.postDelayed(recentAppsRunnable!!, holdDuration)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        recentAppsRunnable?.let { handler.removeCallbacks(it) }
                        
                        if (isBottomSwipeActive && !hasTriggeredRecents) {
                            safePerformAction { performGlobalAction(GLOBAL_ACTION_HOME) }
                        }
                        
                        isBottomSwipeActive = false
                        hasTriggeredRecents = false
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post { verifyAndRepairViews() }
                false
            }
        }

        try {
            windowManager?.addView(bottomEdgeView, params)
            lastSuccessfulOperation = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
            bottomEdgeView = null
            handler.postDelayed({
                if (isServiceConnected && bottomEdgeView == null) {
                    createBottomEdgeDetector()
                }
            }, 500L)
        }
    }
    
    private fun safePerformAction(action: () -> Boolean): Boolean {
        return try {
            if (isServiceConnected) {
                val result = action()
                lastSuccessfulOperation = System.currentTimeMillis()
                result
            } else {
                isServiceConnected = true
                safeReinitialize()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            handler.post { verifyAndRepairViews() }
            false
        }
    }
    
    private fun safeVibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(50)
                }
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }
}
