package com.example.launcher

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * Entry point activity that appears in the app drawer.
 * Redirects to PermissionsSetupActivity to configure the launcher.
 */
class SetupActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Small delay to ensure smooth transition
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, PermissionsSetupActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 100)
    }
}
