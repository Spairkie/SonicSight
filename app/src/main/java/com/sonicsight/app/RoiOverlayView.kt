package com.sonicsight.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class RoiOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFFFFFFFF.toInt()
    }
    private val shade = Paint().apply { color = 0x44000000 }
    private var roi = RectF(0.2f, 0.28f, 0.8f, 0.62f)
    private var downX = 0f
    private var downY = 0f
    private var start = RectF()

    fun normalizedRoi(): RectF = RectF(roi)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = RectF(roi.left * width, roi.top * height, roi.right * width, roi.bottom * height)
        canvas.drawRect(0f, 0f, width.toFloat(), r.top, shade)
        canvas.drawRect(0f, r.bottom, width.toFloat(), height.toFloat(), shade)
        canvas.drawRect(0f, r.top, r.left, r.bottom, shade)
        canvas.drawRect(r.right, r.top, width.toFloat(), r.bottom, shade)
        canvas.drawRect(r, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                start.set(roi)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - downX) / width
                val dy = (event.y - downY) / height
                val w = start.width()
                val h = start.height()
                val left = min(max(start.left + dx, 0f), 1f - w)
                val top = min(max(start.top + dy, 0f), 1f - h)
                roi.set(left, top, left + w, top + h)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
