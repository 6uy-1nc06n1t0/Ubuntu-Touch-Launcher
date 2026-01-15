package com.example.launcher

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * Foreground Service that monitors the accessibility service and forces restart when it crashes.
 * Uses aggressive monitoring similar to UbikiTouch to ensure service stays alive.
 */
class AccessibilityServiceMonitor : Service() {

    companion object {
        private const val CHANNEL_ID = "accessibility_monitor_channel"
        private const val NOTIFICATION_ID = 9999
        private const val CHECK_INTERVAL_MS = 1000L // Check every 1 second
        private const val RESTART_DELAY_MS = 500L // Faster restart
        
        const val ACTION_CHECK_SERVICE = "com.example.launcher.CHECK_ACCESSIBILITY_SERVICE"
        const val ACTION_FORCE_RESTART = "com.example.launcher.FORCE_RESTART_ACCESSIBILITY"
        
        @Volatile
        private var isRunning = false
        
        fun isMonitorRunning(): Boolean = isRunning
        
        fun startMonitor(context: Context) {
            try {
                val intent = Intent(context, AccessibilityServiceMonitor::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        fun stopMonitor(context: Context) {
            try {
                context.stopService(Intent(context, AccessibilityServiceMonitor::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null
    private var lastServiceState = false
    private var consecutiveFailures = 0
    private var receiver: BroadcastReceiver? = null
    
    private var lastRecoveryAttempt = 0L
    private val minRecoveryInterval = 500L
    
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        registerReceivers()
        startPeriodicCheck()
        scheduleAlarmCheck()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CHECK_SERVICE -> checkAndRestartService()
            ACTION_FORCE_RESTART -> forceRestartAccessibilityService()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        isRunning = false
        stopPeriodicCheck()
        unregisterReceivers()
        cancelAlarmCheck()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Accessibility Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors gesture navigation service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LauncherSettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture Navigation Active")
            .setContentText("Monitoring navigation gestures")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun registerReceivers() {
        try {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_ON -> {
                            handler.postDelayed({ checkAndRestartService() }, 300L)
                        }
                        NavigationGestureService.ACTION_SERVICE_CRASHED -> {
                            forceRestartAccessibilityService()
                        }
                        NavigationGestureService.ACTION_SERVICE_STARTED -> {
                            consecutiveFailures = 0
                            lastServiceState = true
                        }
                        Intent.ACTION_PACKAGE_DATA_CLEARED -> {
                            val packageName = intent.data?.schemeSpecificPart
                            if (packageName == context?.packageName) {
                                handler.postDelayed({
                                    forceRestartAccessibilityService()
                                }, 1000L)
                            }
                        }
                    }
                }
            }
            
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(NavigationGestureService.ACTION_SERVICE_CRASHED)
                addAction(NavigationGestureService.ACTION_SERVICE_STARTED)
                addAction(Intent.ACTION_PACKAGE_DATA_CLEARED)
                addDataScheme("package")
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun unregisterReceivers() {
        try {
            receiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) { }
        receiver = null
    }
    
    private fun startPeriodicCheck() {
        stopPeriodicCheck()
        
        checkRunnable = object : Runnable {
            override fun run() {
                checkAndRestartService()
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
        
        handler.postDelayed(checkRunnable!!, CHECK_INTERVAL_MS)
    }
    
    private fun stopPeriodicCheck() {
        checkRunnable?.let { handler.removeCallbacks(it) }
        checkRunnable = null
    }
    
    private fun checkAndRestartService() {
        try {
            val isEnabled = isAccessibilityServiceEnabled()
            val isRunning = NavigationGestureService.isServiceRunning()
            val isAlive = NavigationGestureService.isServiceAlive()
            
            if (isEnabled) {
                when {
                    // Service is completely dead
                    !isAlive -> {
                        consecutiveFailures++
                        if (consecutiveFailures >= 1) {
                            forceRestartAccessibilityService()
                        }
                    }
                    // Service is alive but views are broken
                    !isRunning -> {
                        consecutiveFailures++
                        NavigationGestureService.forceReinitialize()
                        
                        // If still not working after short delay, try harder
                        handler.postDelayed({
                            if (!NavigationGestureService.isServiceRunning()) {
                                forceRestartAccessibilityService()
                            }
                        }, 500L)
                    }
                    // Service is running fine
                    else -> {
                        consecutiveFailures = 0
                    }
                }
            }
            
            lastServiceState = isRunning
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            
            val serviceName = "$packageName/${NavigationGestureService::class.java.canonicalName}"
            enabledServices.contains(serviceName) || 
                enabledServices.contains("$packageName/.NavigationGestureService")
        } catch (e: Exception) {
            false
        }
    }
    
    private fun forceRestartAccessibilityService() {
        val now = System.currentTimeMillis()
        if (now - lastRecoveryAttempt < minRecoveryInterval) {
            return
        }
        lastRecoveryAttempt = now
        
        try {
            // First try to reinitialize existing service instance
            NavigationGestureService.forceReinitialize()
            
            // Also send broadcast to trigger reinitialization
            sendBroadcast(Intent(NavigationGestureService.ACTION_REINITIALIZE).apply {
                setPackage(packageName)
            })
            
            // Schedule verification
            handler.postDelayed({
                if (!NavigationGestureService.isServiceRunning()) {
                    // Try again
                    NavigationGestureService.forceReinitialize()
                    
                    // Send another broadcast
                    sendBroadcast(Intent(NavigationGestureService.ACTION_REINITIALIZE).apply {
                        setPackage(packageName)
                    })
                }
            }, RESTART_DELAY_MS)
            
            // Another check after longer delay
            handler.postDelayed({
                if (!NavigationGestureService.isServiceRunning()) {
                    consecutiveFailures = 0 // Reset to try fresh
                    NavigationGestureService.forceReinitialize()
                }
            }, 2000L)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun scheduleAlarmCheck() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            
            val intent = Intent(this, AccessibilityServiceMonitor::class.java).apply {
                action = ACTION_CHECK_SERVICE
            }
            
            val pendingIntent = PendingIntent.getService(
                this,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 10000L,
                10000L,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun cancelAlarmCheck() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            
            val intent = Intent(this, AccessibilityServiceMonitor::class.java).apply {
                action = ACTION_CHECK_SERVICE
            }
            
            val pendingIntent = PendingIntent.getService(
                this,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Boot receiver to start the monitor service when device boots
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            val prefs = context.getSharedPreferences("gesture_service_prefs", Context.MODE_PRIVATE)
            val wasEnabled = prefs.getBoolean("service_enabled", false)
            
            if (wasEnabled) {
                AccessibilityServiceMonitor.startMonitor(context)
            }
        }
    }
}
