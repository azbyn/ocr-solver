package com.azbyn.ocr.roi

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import com.azbyn.ocr.BaseOverlay
import com.azbyn.ocr.ZoomableImageView
import kotlin.math.abs

class SelectDensityOverlay : BaseOverlay {
    private val pos = PointF()

    private var matWidth = 0
    private var matHeight = 0

    // because the size can be very small we add a lower bound
    private var minTouchSize = 0f

    private var firstX = 0f
    private var firstY = 0f
    /*private val r = RectF()
    private val paint2: Paint = Paint().apply {
        style = Paint.Style.FILL
        color = 0x7F_00_00_FF//.toInt()
    }*/


    private val paint: Paint = Paint().apply {
        color = 0xA0_00_FF_00.toInt()
        style = Paint.Style.FILL
    }

    private var halfSize = 0f

    constructor(ctx: Context) : super(ctx)
    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    fun init(matWidth: Int, matHeight: Int, density: Int, imageView: ZoomableImageView) {
        //scale = min(width.toFloat() / matWidth, height.toFloat() / matHeight)
        super.init(matWidth, matHeight, imageView)
        this.matWidth = matWidth
        this.matHeight = matHeight
        minTouchSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 5f,
                resources.displayMetrics) * width / matHeight
        reset(density)
    }


    fun update(density: Int) {
        halfSize = density /* scale*/ / 2f
        updateRect()
    }

    private fun updateRect() {
        rect.left = (pos.x - halfSize)
        rect.top = (pos.y - halfSize)
        rect.right = (pos.x + halfSize)
        rect.bottom = (pos.y + halfSize)
        invalidate()
        //super.update()
    }

    val p = PointF()
    /*
    override fun update() {
        this.imageView?.screenToDrawable(p)
        super.update()
    }*/

    fun reset(density: Int) {
        pos.x = this.matWidth / 2f
        pos.y = this.matHeight / 2f
        update(density)
    }
    private var handlingTouch = false

    override fun onTouchImpl(event: MotionEvent): Boolean {
        if (!handlingTouch && event.action != MotionEvent.ACTION_DOWN)
            return false
        if (event.pointerCount > 1) {
            handlingTouch = false
            return false
        }
        p.x = event.x
        p.y = event.y
        this.imageView?.screenToDrawable(p)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val scale = imageView?.matrixScale ?: 1f
                val touchSize = halfSize + (minTouchSize / scale) //max(halfSize, minTouchSize)
                //logd("touchSize: $touchSize")
                firstX = pos.x - p.x
                firstY = pos.y - p.y
                handlingTouch = (abs(firstX) < touchSize && abs(firstY) < touchSize)
                return handlingTouch
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> return true
            MotionEvent.ACTION_MOVE -> {
                pos.x = firstX + p.x
                pos.y = firstY + p.y
                if (pos.x < halfSize) {
                    pos.x = halfSize
                } else if (pos.x + halfSize > matWidth) {
                    pos.x = matWidth - halfSize
                }
                if (pos.y < halfSize) {
                    pos.y = halfSize
                } else if (pos.y + halfSize > matHeight) {
                    pos.y = matHeight - halfSize
                }
                updateRect()
                return true
            }
            else -> return false
        }
        // prevX = p.x
        // prevY = p.y
        //return true
    }

    override fun onDraw(canvas: Canvas, screenRect: RectF) {
        canvas.drawRect(screenRect, paint)
        /*
        val scale = imageView?.matrixScale ?: 1f
        logd("scale $scale")
        val touchSize = halfSize + (minTouchSize / scale) //max(halfSize, minTouchSize)
        r.left = pos.x -touchSize
        r.top = pos.y - touchSize
        r.right = pos.x + touchSize
        r.bottom = pos.y + touchSize
        imageView?.mapRect(r, r)
        canvas.drawRect(r, paint2)*/
    }
}