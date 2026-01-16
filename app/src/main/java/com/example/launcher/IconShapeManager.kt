package com.example.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

enum class IconShape {
    ORIGINAL,       // Formato original do ícone
    SQUIRCLE,       // Quadrado arredondado (como na imagem)
    CIRCLE,         // Círculo
    ROUNDED_SQUARE, // Quadrado com cantos arredondados
    TEARDROP        // Formato gota
}

class IconShapeManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREF_ICON_SHAPE = "icon_shape"
    }
    
    fun getIconShape(): IconShape {
        val shapeName = prefs.getString(PREF_ICON_SHAPE, IconShape.ORIGINAL.name)
        return try {
            IconShape.valueOf(shapeName ?: IconShape.ORIGINAL.name)
        } catch (e: Exception) {
            IconShape.ORIGINAL
        }
    }
    
    fun setIconShape(shape: IconShape) {
        prefs.edit().putString(PREF_ICON_SHAPE, shape.name).apply()
    }
    
    fun applyShape(drawable: Drawable, size: Int = 128): Drawable {
        val shape = getIconShape()
        
        if (shape == IconShape.ORIGINAL) {
            return drawable
        }
        
        val bitmap = drawableToBitmap(drawable, size)
        val shapedBitmap = applyShapeToBitmap(bitmap, shape)
        
        return BitmapDrawable(context.resources, shapedBitmap)
    }
    
    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val scaledBitmap = Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
            return scaledBitmap
        }
        
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }
    
    private fun applyShapeToBitmap(source: Bitmap, shape: IconShape): Bitmap {
        val size = source.width
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Desenhar o fundo com cor de preenchimento
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.color = 0xFFFFFFFF.toInt() // Fundo branco
        
        val path = getShapePath(shape, size.toFloat())
        canvas.drawPath(path, bgPaint)
        
        // Aplicar a máscara do ícone
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, 0f, 0f, paint)
        
        return output
    }
    
    private fun getShapePath(shape: IconShape, size: Float): Path {
        val path = Path()
        val padding = size * 0.05f // 5% padding
        val rect = RectF(padding, padding, size - padding, size - padding)
        
        when (shape) {
            IconShape.SQUIRCLE -> {
                // Squircle - quadrado super arredondado (como na imagem)
                val radius = size * 0.35f
                path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            }
            IconShape.CIRCLE -> {
                val centerX = size / 2
                val centerY = size / 2
                val radius = (size - padding * 2) / 2
                path.addCircle(centerX, centerY, radius, Path.Direction.CW)
            }
            IconShape.ROUNDED_SQUARE -> {
                val radius = size * 0.15f
                path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            }
            IconShape.TEARDROP -> {
                // Formato gota - cantos arredondados exceto um
                val radius = size * 0.35f
                val radii = floatArrayOf(
                    radius, radius,  // Top-left
                    radius, radius,  // Top-right
                    radius, radius,  // Bottom-right
                    0f, 0f           // Bottom-left (canto reto)
                )
                path.addRoundRect(rect, radii, Path.Direction.CW)
            }
            else -> {
                path.addRect(rect, Path.Direction.CW)
            }
        }
        
        return path
    }
    
    fun getShapeDisplayName(shape: IconShape): String {
        return when (shape) {
            IconShape.ORIGINAL -> "Original"
            IconShape.SQUIRCLE -> "Squircle"
            IconShape.CIRCLE -> "Círculo"
            IconShape.ROUNDED_SQUARE -> "Quadrado Arredondado"
            IconShape.TEARDROP -> "Gota"
        }
    }
}

