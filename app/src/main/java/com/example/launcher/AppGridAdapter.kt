package com.example.launcher

import android.content.Context
import android.graphics.Outline
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class AppGridAdapter(
    private val context: Context,
    private val apps: List<AppInfo>
) : BaseAdapter() {

    private val iconShapeManager = IconShapeManager(context)

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): AppInfo = apps[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_app, parent, false)

        val appInfo = apps[position]
        
        val iconView = view.findViewById<ImageView>(R.id.appIcon)
        val labelView = view.findViewById<TextView>(R.id.appLabel)
        val notificationBadge = view.findViewById<TextView>(R.id.notificationBadge)

        val shapedIcon = iconShapeManager.applyShape(appInfo.icon, 128)
        iconView.setImageDrawable(shapedIcon)
        
        // Aplicar clip para o formato
        applyIconClip(iconView)
        
        labelView.text = appInfo.label

        if (appInfo.notificationCount > 0) {
            notificationBadge.visibility = View.VISIBLE
            notificationBadge.text = if (appInfo.notificationCount > 99) "99+" else appInfo.notificationCount.toString()
        } else {
            notificationBadge.visibility = View.GONE
        }

        return view
    }

    private fun applyIconClip(iconView: ImageView) {
        val shape = iconShapeManager.getIconShape()
        
        if (shape == IconShape.ORIGINAL) {
            iconView.clipToOutline = false
            iconView.outlineProvider = null
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            iconView.clipToOutline = true
            iconView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val size = minOf(view.width, view.height)
                    when (shape) {
                        IconShape.SQUIRCLE -> {
                            val radius = size * 0.35f
                            outline.setRoundRect(0, 0, size, size, radius)
                        }
                        IconShape.CIRCLE -> {
                            outline.setOval(0, 0, size, size)
                        }
                        IconShape.ROUNDED_SQUARE -> {
                            val radius = size * 0.15f
                            outline.setRoundRect(0, 0, size, size, radius)
                        }
                        IconShape.TEARDROP -> {
                            val radius = size * 0.35f
                            outline.setRoundRect(0, 0, size, size, radius)
                        }
                        else -> {
                            outline.setRect(0, 0, size, size)
                        }
                    }
                }
            }
        }
    }
}

