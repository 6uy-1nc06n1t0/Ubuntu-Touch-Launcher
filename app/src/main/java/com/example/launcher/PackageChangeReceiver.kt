package com.example.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver que detecta instalação, desinstalação e atualização de apps.
 * Envia um broadcast interno para que as Activities atualizem suas listas de apps.
 */
class PackageChangeReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_APPS_CHANGED = "com.example.launcher.APPS_CHANGED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_CHANGE_TYPE = "change_type"
        
        const val TYPE_INSTALLED = "installed"
        const val TYPE_UNINSTALLED = "uninstalled"
        const val TYPE_UPDATED = "updated"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        val packageName = intent.data?.schemeSpecificPart ?: return
        
        // Ignorar nosso próprio pacote
        if (packageName == context.packageName) return
        
        val changeType = when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                // Verifica se é uma atualização ou nova instalação
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    TYPE_UPDATED
                } else {
                    TYPE_INSTALLED
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                // Ignora se está sendo atualizado (vai receber PACKAGE_ADDED depois)
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    return
                }
                TYPE_UNINSTALLED
            }
            Intent.ACTION_PACKAGE_REPLACED -> TYPE_UPDATED
            else -> return
        }
        
        // Envia broadcast interno para atualizar as listas de apps
        val updateIntent = Intent(ACTION_APPS_CHANGED).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_CHANGE_TYPE, changeType)
            setPackage(context.packageName)
        }
        context.sendBroadcast(updateIntent)
    }
}

