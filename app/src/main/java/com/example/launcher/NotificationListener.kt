package com.example.launcher

import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {

    companion object {
        const val ACTION_NOTIFICATION_CHANGED = "com.example.launcher.NOTIFICATION_CHANGED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_NOTIFICATION_COUNT = "notification_count"
        
        // Singleton para acesso às contagens de notificações
        private val notificationCounts = mutableMapOf<String, Int>()
        
        fun getNotificationCount(packageName: String): Int {
            return notificationCounts[packageName] ?: 0
        }
        
        fun getAllNotificationCounts(): Map<String, Int> {
            return notificationCounts.toMap()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            updateNotificationCounts()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            updateNotificationCounts()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateNotificationCounts()
    }

    private fun updateNotificationCounts() {
        try {
            val activeNotifications = activeNotifications ?: return
            
            // Limpa contagens antigas
            notificationCounts.clear()
            
            // Conta notificações por pacote
            for (sbn in activeNotifications) {
                val packageName = sbn.packageName
                
                // Ignora notificações do próprio launcher e notificações persistentes/em andamento
                if (packageName == this.packageName) continue
                if (sbn.isOngoing) continue
                
                val currentCount = notificationCounts[packageName] ?: 0
                notificationCounts[packageName] = currentCount + 1
            }
            
            // Envia broadcast para atualizar a UI
            val intent = Intent(ACTION_NOTIFICATION_CHANGED).apply {
                setPackage(packageName)
            }
            sendBroadcast(intent)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
