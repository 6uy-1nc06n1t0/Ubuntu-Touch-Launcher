package com.example.launcher

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.Calendar

class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val hourMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val hourHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(4f, 2f, 2f, Color.parseColor("#66000000"))
    }

    private val minuteHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(3f, 1f, 1f, Color.parseColor("#66000000"))
    }

    private val secondHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        style = Paint.Style.FILL
    }

    private val centerDotOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B71C1C")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = minOf(w, h) / 2f - 16f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val calendar = Calendar.getInstance()
        val hours = calendar.get(Calendar.HOUR)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        val millis = calendar.get(Calendar.MILLISECOND)

        // Draw hour markers
        drawHourMarkers(canvas)

        // Draw hands
        drawHourHand(canvas, hours, minutes)
        drawMinuteHand(canvas, minutes, seconds)
        drawSecondHand(canvas, seconds, millis)

        // Draw center dot
        canvas.drawCircle(centerX, centerY, 10f, centerDotPaint)
        canvas.drawCircle(centerX, centerY, 10f, centerDotOutlinePaint)

        // Redraw every frame for smooth second hand
        postInvalidateDelayed(50)
    }

    private fun drawHourMarkers(canvas: Canvas) {
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            
            val innerRadius = radius * 0.82f
            val outerRadius = radius * 0.95f
            
            val startX = centerX + innerRadius * Math.cos(angle).toFloat()
            val startY = centerY + innerRadius * Math.sin(angle).toFloat()
            val endX = centerX + outerRadius * Math.cos(angle).toFloat()
            val endY = centerY + outerRadius * Math.sin(angle).toFloat()
            
            // Thicker markers at 12, 3, 6, 9
            if (i % 3 == 0) {
                hourMarkerPaint.strokeWidth = 6f
                hourMarkerPaint.color = Color.WHITE
            } else {
                hourMarkerPaint.strokeWidth = 4f
                hourMarkerPaint.color = Color.parseColor("#BDBDBD")
            }
            
            canvas.drawLine(startX, startY, endX, endY, hourMarkerPaint)
        }
    }

    private fun drawHourHand(canvas: Canvas, hours: Int, minutes: Int) {
        val hourAngle = Math.toRadians(((hours + minutes / 60f) * 30 - 90).toDouble())
        val handLength = radius * 0.5f
        
        val endX = centerX + handLength * Math.cos(hourAngle).toFloat()
        val endY = centerY + handLength * Math.sin(hourAngle).toFloat()
        
        // Draw shadow/outline for depth
        canvas.drawLine(centerX, centerY, endX, endY, hourHandPaint)
    }

    private fun drawMinuteHand(canvas: Canvas, minutes: Int, seconds: Int) {
        val minuteAngle = Math.toRadians(((minutes + seconds / 60f) * 6 - 90).toDouble())
        val handLength = radius * 0.72f
        
        val endX = centerX + handLength * Math.cos(minuteAngle).toFloat()
        val endY = centerY + handLength * Math.sin(minuteAngle).toFloat()
        
        canvas.drawLine(centerX, centerY, endX, endY, minuteHandPaint)
    }

    private fun drawSecondHand(canvas: Canvas, seconds: Int, millis: Int) {
        // Smooth second hand movement
        val smoothSeconds = seconds + millis / 1000f
        val secondAngle = Math.toRadians((smoothSeconds * 6 - 90).toDouble())
        val handLength = radius * 0.85f
        val tailLength = radius * 0.15f
        
        // Main hand
        val endX = centerX + handLength * Math.cos(secondAngle).toFloat()
        val endY = centerY + handLength * Math.sin(secondAngle).toFloat()
        
        // Tail (opposite direction)
        val tailX = centerX - tailLength * Math.cos(secondAngle).toFloat()
        val tailY = centerY - tailLength * Math.sin(secondAngle).toFloat()
        
        canvas.drawLine(tailX, tailY, endX, endY, secondHandPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = (180 * resources.displayMetrics.density).toInt()
        
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
