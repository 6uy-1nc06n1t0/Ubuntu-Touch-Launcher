package com.example.launcher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.*

class CircularBatteryClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Colors matching the design
    private val batteryColor = Color.parseColor("#DD6200") // Orange for battery
    private val emptyBatteryColor = Color.WHITE // Changed empty battery color to white
    private val backgroundColor = Color.parseColor("#1A1A1A") // Dark background
    private val textColor = Color.WHITE
    private val dateColor = Color.parseColor("#AAAAAA")

    // Battery arc paint
    private val batteryArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = batteryColor
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    private val emptyArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = emptyBatteryColor
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    // Time paint
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    // Date paint
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dateColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var arcRect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = minOf(w, h) / 2f - 20f

        // Update stroke width based on size
        val strokeWidth = radius * 0.06f
        batteryArcPaint.strokeWidth = strokeWidth
        emptyArcPaint.strokeWidth = strokeWidth

        // Set arc rectangle
        val arcPadding = strokeWidth / 2f + 8f
        arcRect.set(
            centerX - radius + arcPadding,
            centerY - radius + arcPadding,
            centerX + radius - arcPadding,
            centerY + radius - arcPadding
        )

        // Update text sizes based on widget size
        timePaint.textSize = radius * 0.45f
        datePaint.textSize = radius * 0.11f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Get battery level
        val batteryLevel = getBatteryLevel()

        // Draw empty arc (full circle background) - white color
        canvas.drawArc(arcRect, -90f, 360f, false, emptyArcPaint)

        // Draw battery arc (proportional to battery level)
        // Start from top (-90 degrees) and sweep clockwise
        val sweepAngle = (batteryLevel / 100f) * 360f
        canvas.drawArc(arcRect, -90f, sweepAngle, false, batteryArcPaint)

        // Draw time
        drawTime(canvas)

        // Draw date
        drawDate(canvas)

        // Redraw every second
        postInvalidateDelayed(1000)
    }

    private fun getBatteryLevel(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            50 // Default to 50% if cannot read
        }
    }

    private fun drawTime(canvas: Canvas) {
        val calendar = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeText = timeFormat.format(calendar.time)

        canvas.drawText(timeText, centerX, centerY + timePaint.textSize * 0.15f, timePaint)
    }

    private fun drawDate(canvas: Canvas) {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEE, d 'DE' MMMM 'DE' yyyy", Locale("pt", "BR"))
        val dateText = dateFormat.format(calendar.time).uppercase()

        val dateY = centerY + timePaint.textSize * 0.15f + datePaint.textSize * 1.8f
        canvas.drawText(dateText, centerX, dateY, datePaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = (100 * resources.displayMetrics.density).toInt()

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(desiredSize, widthSize)
            else -> desiredSize
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredSize, heightSize)
            else -> desiredSize
        }

        val size = minOf(width, height)
        setMeasuredDimension(size, size)
    }
}
