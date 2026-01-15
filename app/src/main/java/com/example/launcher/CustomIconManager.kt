package com.example.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Gerenciador de ícones personalizados para apps
 * Salva e carrega ícones customizados do armazenamento interno
 */
class CustomIconManager(private val context: Context) {
    
    private val iconsDir: File by lazy {
        File(context.filesDir, "custom_icons").apply {
            if (!exists()) mkdirs()
        }
    }
    
    private val prefs by lazy {
        context.getSharedPreferences("custom_icons_prefs", Context.MODE_PRIVATE)
    }
    
    /**
     * Salva um ícone personalizado para um pacote
     */
    fun saveCustomIcon(packageName: String, uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap != null) {
                // Redimensiona para um tamanho padrão de ícone
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 192, 192, true)
                
                val iconFile = getIconFile(packageName)
                FileOutputStream(iconFile).use { out ->
                    scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                
                // Marca que este pacote tem ícone personalizado
                prefs.edit().putBoolean(packageName, true).apply()
                
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
                bitmap.recycle()
                
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Carrega o ícone personalizado se existir, ou retorna null
     */
    fun getCustomIcon(packageName: String): Drawable? {
        if (!hasCustomIcon(packageName)) return null
        
        return try {
            val iconFile = getIconFile(packageName)
            if (iconFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bitmap != null) {
                    BitmapDrawable(context.resources, bitmap)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Verifica se um pacote tem ícone personalizado
     */
    fun hasCustomIcon(packageName: String): Boolean {
        return prefs.getBoolean(packageName, false) && getIconFile(packageName).exists()
    }
    
    /**
     * Remove o ícone personalizado de um pacote
     */
    fun removeCustomIcon(packageName: String): Boolean {
        return try {
            val iconFile = getIconFile(packageName)
            if (iconFile.exists()) {
                iconFile.delete()
            }
            prefs.edit().remove(packageName).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Obtém o arquivo do ícone para um pacote
     */
    private fun getIconFile(packageName: String): File {
        return File(iconsDir, "${packageName.replace(".", "_")}.png")
    }
    
    /**
     * Retorna o ícone para exibição (personalizado ou padrão)
     */
    fun getIconForApp(packageName: String, defaultIcon: Drawable): Drawable {
        return getCustomIcon(packageName) ?: defaultIcon
    }
}
