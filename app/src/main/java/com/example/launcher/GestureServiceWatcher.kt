package com.example.launcher

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Watches the NavigationGestureService and handles auto-restart when crashes occur
 */
class GestureServiceWatcher(
    private val context: Context,
    private val onServiceNeedsAttention: (() -> Unit)? = null
) {
    
    companion object {
        private const val PREFS_NAME = "gesture_service_prefs"
        private const val CHECK_INTERVAL_MS = 3000L  // Reduced from 10s to 3s for faster recovery
        private const val RESTART_DELAY_MS = 1000L  // Reduced from 2s to 1s
        private const val MAX_RESTART_ATTEMPTS = 5
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var isWatching = false
    private var checkRunnable: Runnable? = null
    private var restartAttempts = 0
    private var lastRestartTime = 0L
    
    private val crashReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NavigationGestureService.ACTION_SERVICE_CRASHED -> {
                    // Service crashed, attempt to restart after a delay
                    onServiceNeedsAttention?.invoke()
                    handler.postDelayed({
                        attemptServiceRestart()
                    }, RESTART_DELAY_MS)
                }
                NavigationGestureService.ACTION_SERVICE_STARTED -> {
                    // Service started successfully, reset restart attempts
                    restartAttempts = 0
                }
            }
        }
    }
    
    /**
     * Start watching the gesture service
     */
    fun startWatching() {
        if (isWatching) return
        isWatching = true
        restartAttempts = 0
        
        // Register crash receiver
        try {
            val filter = IntentFilter().apply {
                addAction(NavigationGestureService.ACTION_SERVICE_CRASHED)
                addAction(NavigationGestureService.ACTION_SERVICE_STARTED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(crashReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(crashReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Start periodic checks
        startPeriodicCheck()
        
        // Check for previous crash on startup
        checkForPreviousCrash()
    }
    
    /**
     * Stop watching the gesture service
     */
    fun stopWatching() {
        if (!isWatching) return
        isWatching = false
        
        try {
            context.unregisterReceiver(crashReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        
        stopPeriodicCheck()
    }
    
    private fun startPeriodicCheck() {
        stopPeriodicCheck()
        
        checkRunnable = object : Runnable {
            override fun run() {
                if (!isWatching) return
                
                // Check if service should be running but isn't
                if (isServiceEnabled()) {
                    if (!isServiceActuallyRunning()) {
                        // Service is enabled but not running properly
                        attemptServiceRestart()
                    } else {
                        // Service is running, reset attempts
                        restartAttempts = 0
                    }
                }
                
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
        
        handler.postDelayed(checkRunnable!!, CHECK_INTERVAL_MS)
    }
    
    private fun stopPeriodicCheck() {
        checkRunnable?.let { handler.removeCallbacks(it) }
        checkRunnable = null
    }
    
    /**
     * Check if user has enabled the accessibility service in settings
     */
    fun isServiceEnabled(): Boolean {
        return try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledServices = accessibilityManager?.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            
            enabledServices?.any { 
                it.resolveInfo.serviceInfo.packageName == context.packageName &&
                it.resolveInfo.serviceInfo.name == NavigationGestureService::class.java.name
            } == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Check if service instance is actually running and responsive
     */
    private fun isServiceActuallyRunning(): Boolean {
        return NavigationGestureService.isServiceRunning()
    }
    
    /**
     * Check for crashes that occurred before app restart
     */
    private fun checkForPreviousCrash() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val crashed = prefs.getBoolean("service_crashed", false)
            
            if (crashed && isServiceEnabled()) {
                // Clear the flag
                prefs.edit().putBoolean("service_crashed", false).apply()
                
                // Attempt restart
                handler.postDelayed({
                    attemptServiceRestart()
                }, RESTART_DELAY_MS)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Attempt to restart the accessibility service
     */
    private fun attemptServiceRestart() {
        if (!isServiceEnabled()) {
            // Service is disabled in settings, don't try to restart
            restartAttempts = 0
            return
        }
        
        // Prevent too frequent restart attempts
        val now = System.currentTimeMillis()
        if (now - lastRestartTime < 500L) {
            return
        }
        lastRestartTime = now
        
        // Check if we've exceeded max attempts
        if (restartAttempts >= MAX_RESTART_ATTEMPTS) {
            // Reset after some time to allow future attempts
            handler.postDelayed({
                restartAttempts = 0
            }, 30000L) // Reset after 30 seconds
            return
        }
        
        restartAttempts++
        
        try {
            // Try to force reinitialize the service
            NavigationGestureService.forceReinitialize()
            
            // Also send broadcast as backup
            val intent = Intent("com.example.launcher.REINITIALIZE_SERVICE")
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Open accessibility settings for user to manually toggle service if needed
     */
    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
