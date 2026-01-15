package com.example.launcher

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionsSetupActivity : AppCompatActivity() {

    private lateinit var launcherStatus: ImageView
    private lateinit var launcherButton: Button
    private lateinit var launcherContainer: LinearLayout

    private lateinit var overlayStatus: ImageView
    private lateinit var overlayButton: Button
    private lateinit var overlayContainer: LinearLayout
    
    private lateinit var accessibilityStatus: ImageView
    private lateinit var accessibilityButton: Button
    private lateinit var accessibilityContainer: LinearLayout
    
    private lateinit var notificationStatus: ImageView
    private lateinit var notificationButton: Button
    private lateinit var notificationContainer: LinearLayout

    private lateinit var restrictedButton: Button
    private lateinit var restrictedContainer: LinearLayout
    
    private lateinit var continueButton: Button
    private lateinit var allPermissionsGranted: TextView

    private val roleManagerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions_setup)
        
        initViews()
        setupClickListeners()
        updatePermissionStates()
    }
    
    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }
    
    private fun initViews() {
        launcherStatus = findViewById(R.id.launcherStatus)
        launcherButton = findViewById(R.id.launcherButton)
        launcherContainer = findViewById(R.id.launcherContainer)

        overlayStatus = findViewById(R.id.overlayStatus)
        overlayButton = findViewById(R.id.overlayButton)
        overlayContainer = findViewById(R.id.overlayContainer)
        
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        accessibilityButton = findViewById(R.id.accessibilityButton)
        accessibilityContainer = findViewById(R.id.accessibilityContainer)
        
        notificationStatus = findViewById(R.id.notificationStatus)
        notificationButton = findViewById(R.id.notificationButton)
        notificationContainer = findViewById(R.id.notificationContainer)

        restrictedButton = findViewById(R.id.restrictedButton)
        restrictedContainer = findViewById(R.id.restrictedContainer)
        
        continueButton = findViewById(R.id.continueButton)
        allPermissionsGranted = findViewById(R.id.allPermissionsGranted)
    }
    
    private fun setupClickListeners() {
        launcherButton.setOnClickListener {
            requestDefaultLauncher()
        }

        overlayButton.setOnClickListener {
            requestOverlayPermission()
        }
        
        accessibilityButton.setOnClickListener {
            requestAccessibilityPermission()
        }
        
        notificationButton.setOnClickListener {
            requestNotificationListenerPermission()
        }

        restrictedButton.setOnClickListener {
            openAppInfo()
        }
        
        continueButton.setOnClickListener {
            markSetupComplete()
            finish()
        }
    }
    
    private fun updatePermissionStates() {
        val launcherDefault = isDefaultLauncher()
        val overlayGranted = isOverlayPermissionGranted()
        val accessibilityGranted = isAccessibilityServiceEnabled()
        val notificationGranted = isNotificationListenerEnabled()
        
        updatePermissionUI(launcherContainer, launcherStatus, launcherButton, launcherDefault)
        updatePermissionUI(overlayContainer, overlayStatus, overlayButton, overlayGranted)
        updatePermissionUI(accessibilityContainer, accessibilityStatus, accessibilityButton, accessibilityGranted)
        updatePermissionUI(notificationContainer, notificationStatus, notificationButton, notificationGranted)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            restrictedContainer.visibility = View.VISIBLE
        } else {
            restrictedContainer.visibility = View.GONE
        }
        
        val allGranted = launcherDefault && overlayGranted && accessibilityGranted && notificationGranted
        
        if (allGranted) {
            allPermissionsGranted.visibility = View.VISIBLE
            continueButton.text = "Continuar"
            continueButton.alpha = 1f
        } else {
            allPermissionsGranted.visibility = View.GONE
            continueButton.text = "Pular por agora"
            continueButton.alpha = 0.7f
        }
    }
    
    private fun updatePermissionUI(container: LinearLayout, statusIcon: ImageView, button: Button, isGranted: Boolean) {
        if (isGranted) {
            statusIcon.setImageResource(R.drawable.ic_check_circle)
            statusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            button.visibility = View.GONE
            container.alpha = 0.7f
        } else {
            statusIcon.setImageResource(R.drawable.ic_warning)
            statusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            button.visibility = View.VISIBLE
            container.alpha = 1f
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val currentLauncher = resolveInfo?.activityInfo?.packageName
        return currentLauncher == packageName
    }

    private fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    roleManagerLauncher.launch(intent)
                }
            }
        } else {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            val componentName = ComponentName(this, MainActivity::class.java)
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            
            startActivity(Intent.createChooser(intent, "Selecione o launcher padrão"))
        }
    }
    
    private fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) {
            0
        }
        
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val colonSplitter = settingValue.split(":")
                for (componentName in colonSplitter) {
                    if (componentName.contains(packageName) && 
                        componentName.contains("NavigationGestureService")) {
                        return true
                    }
                }
            }
        }
        return false
    }
    
    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }
    
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
    
    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
    
    private fun requestNotificationListenerPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }
    
    private fun markSetupComplete() {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("permissions_setup_complete", true).apply()
    }
}
