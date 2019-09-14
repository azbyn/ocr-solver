package com.azbyn.ocr.rotate

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.azbyn.ocr.GRAYED_OUT_COLOR
import com.azbyn.ocr.Misc.logd
import kotlin.math.abs

class RotateViewer : View {
    private val density = resources.displayMetrics.density
    private val bigLinePaint: Paint = Paint().apply {
        strokeWidth = 3 * density
        textAlign = Paint.Align.CENTER
        textSize = 20 * density
        isAntiAlias = true
        color = Color.WHITE
    }
    private val smallLinePaint: Paint = Paint().apply {
        strokeWidth = 2* density
        isAntiAlias = true
        color = Color.WHITE
    }
    private val centerLinePaint: Paint = Paint().apply {
        strokeWidth = 2 * density
        isAntiAlias = true
        color = Color.RED
    }
    private val angleWidth = 30f

    private var centralAngle = 0f

    var angle = 0f
        set(a) {
            val a360 = a % 360
            val delta = abs((centralAngle - a360) %360)
            //logd("delta $delta")
            if (delta > 45) return
            field = a360
            invalidate()
            overlay?.setAngle(angle)
        }

    fun reset() {
        angle = centralAngle
        //logd("reset: $angle = $centralAngle")
    }
    fun rotate90() {
        centralAngle = (centralAngle-90) % 360
        overlay!!.rotate90()
        angle -= 90
        logd("CA: $centralAngle")
    }

    private var _pressed = false
    private var prevX = 0f
    private var pxPerAngle = 1f
    var overlay: RotateImageOverlay? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        //return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> _pressed = true
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                _pressed = false
                return true
            }
            MotionEvent.ACTION_MOVE -> if (_pressed) {
                val deltaX =  prevX - event.x
                val deltaAngle = deltaX / pxPerAngle
                angle += deltaAngle
                //logd("dx: $deltaAngle, da $deltaAngle")
            }
            else -> return false
        }
        prevX = event.x

        return true
    }
    constructor(ctx: Context) : super(ctx)
    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    private val grayPaint: Paint = Paint().apply {
        strokeWidth = 0f
        color = GRAYED_OUT_COLOR
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawPaint(grayPaint)
        val w = width.toFloat()
        val h = height.toFloat()
        val angleWidth10 = (angleWidth/10).toInt()
        canvas.translate(w/2f, h/2f)
        //how much physical space is between 1 degree lines
        pxPerAngle = width / angleWidth
        val angle10 = ((angle - centralAngle) / 10).toInt() * 10

        val startX = -(angle%10) * pxPerAngle
        for (a in -(angleWidth10 / 2)-1..(angleWidth10 / 2)+1) {
            val x = startX + a *10 * pxPerAngle
            val angle = a *10+ angle10
            //logd("angle $angle ($a)")
            canvas.drawLine(x, 0f, x, +50f, bigLinePaint)

            canvas.drawText(abs(angle).toString(), x, -10f, bigLinePaint)

            for (i in 2..8 step 2) {
                val x2 = x + pxPerAngle * i
                canvas.drawLine(x2, 10f, x2, 40f, smallLinePaint)
            }
        }
        canvas.drawLine(0f, 10f,0f, h/2f, centerLinePaint)
    }
}