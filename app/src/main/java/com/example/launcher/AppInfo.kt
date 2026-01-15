package com.example.launcher

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    var notificationCount: Int = 0,
    val isInternalApp: Boolean = false
)
