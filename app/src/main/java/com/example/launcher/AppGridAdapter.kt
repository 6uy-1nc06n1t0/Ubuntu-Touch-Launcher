package com.example.launcher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class AppGridAdapter(
    private val context: Context,
    private val apps: List<AppInfo>
) : BaseAdapter() {

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

        iconView.setImageDrawable(appInfo.icon)
        labelView.text = appInfo.label

        if (appInfo.notificationCount > 0) {
            notificationBadge.visibility = View.VISIBLE
            // Show "99+" if count exceeds 99
            notificationBadge.text = if (appInfo.notificationCount > 99) "99+" else appInfo.notificationCount.toString()
        } else {
            notificationBadge.visibility = View.GONE
        }

        return view
    }
}
