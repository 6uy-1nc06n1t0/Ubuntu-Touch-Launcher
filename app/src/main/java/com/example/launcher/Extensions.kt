package com.example.launcher

import android.content.res.Resources

fun Int.dp(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}
