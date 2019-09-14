package com.azbyn.ocr.roi

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.TextView
import com.azbyn.ocr.BaseRoiOverlay
import com.azbyn.ocr.R
import com.azbyn.ocr.ZoomableImageView

class SelectRoiOverlay : BaseRoiOverlay {
    private val density = resources.displayMetrics.density
    private lateinit var text: TextView

    private val paint: Paint = Paint().apply {
        strokeWidth = 2f * density
        //color = 0xA0_FF_FF_FF.toInt()
        color = 0xA0_00_FF_00.toInt()
        style = Paint.Style.STROKE
    }

    fun init(drawableWidth: Int, drawableHeight: Int,
             imageView: ZoomableImageView, text: TextView) {
        super.init(drawableWidth, drawableHeight, imageView)
        this.text = text
    }

    override fun update() {
        updateRoi()
        text.text = context.getString(R.string.select_roi_text,
                roi.width, roi.height, roi.x, roi.y)
        super.update()
    }
    constructor(ctx: Context) : super(ctx)
    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    override fun onDraw(canvas: Canvas, screenRect: RectF) {
        paint.strokeWidth = 2f * density * (imageView?.currentZoom?:1f)
        canvas.drawRect(screenRect, paint)
    }

    /*
    private companion object {
        private const val MIN_SIZE = 300

        //PT = press type
        const val PT_NONE     = 0x00

        const val PT_LEFT     = 0x01
        const val PT_RIGHT    = 0x02
        const val PT_H_CENTER = 0x04

        const val PT_TOP      = 0x10
        const val PT_BOTTOM   = 0x20
        const val PT_V_CENTER = 0x40
    }
    private val density = resources.displayMetrics.density
    // the 'radius' of a square (aka a square of length radius*2)
    // lineRadius   = how close to the line a touch has to be to be registered as a line press
    private var lineRadius = 0f
    private val scale get() = imageView?.matrixScale ?: 1f

    private lateinit var viewModel: SelectRoiFragment.VM
    private val roi get() = viewModel.roi
    private val matWidth get() = viewModel.fullMapWidth
    private val matHeight get() = viewModel.fullMapHeight

    //private val scaledRoi = RectF()
    private lateinit var text: TextView

    private val paint: Paint = Paint().apply {
        strokeWidth = 2f * density
        //color = 0xA0_FF_FF_FF.toInt()
        color = 0xA0_00_FF_00.toInt()
        style = Paint.Style.STROKE
    }

    constructor(ctx: Context) : super(ctx)
    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    private fun updateRoi() {
        rect.left = roi.x.toFloat()
        rect.top = roi.y.toFloat()
        rect.right = (roi.x + roi.width).toFloat()
        rect.bottom = (roi.y + roi.height).toFloat()
        text.text = context.getString(R.string.select_roi_text,
                roi.width, roi.height, roi.x, roi.y)
        invalidate()
    }

    fun init(viewModel: SelectRoiFragment.VM, imageView: ZoomableImageView, text: TextView) {
        this.text = text
        this.viewModel = viewModel
        super.init(0,0, imageView)
        lineRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 5f,
                resources.displayMetrics) * width / matWidth
        reset()
    }
    fun reset() {
        updateRoi()
    }

    //private var prevX = 0f
    //private var prevY = 0f
    private var pressType = PT_NONE
    private val prev = PointF()
    private val p = PointF()

    //override fun onTouchEvent(event: MotionEvent): Boolean {
    override fun onTouchImpl(event: MotionEvent): Boolean {
        if (pressType == PT_NONE && event.action != MotionEvent.ACTION_DOWN)
            return false

        if (event.pointerCount > 1) {
            pressType = PT_NONE
            return false
        }

        p.x = event.x
        p.y = event.y
        this.imageView?.screenToDrawable(p)

        return when (event.action) {
            MotionEvent.ACTION_DOWN -> onDown()
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                pressType = PT_NONE
                //logd("cancel/up")
                true
            }
            MotionEvent.ACTION_MOVE -> {
                onMove(p.x, p.y)
                prev.x = p.x
                prev.y = p.y
                true
            }
            else -> false
        }
    }
    private fun onDown(): Boolean {
        val l = rect.left
        val r = rect.right
        val t = rect.top
        val b = rect.bottom
        val lr = lineRadius / scale
        // if the press type is not PT_NONE
        // presses might be blocked to the ImageView
        assert(pressType == PT_NONE)
        var res = when {
            p.x < l - lr -> return false
            p.x < l + lr -> PT_LEFT
            p.x < r - lr -> PT_H_CENTER
            p.x < r + lr -> PT_RIGHT
            else -> return false
        }
        res = res or when {
            p.y < t - lr -> return false
            p.y < t + lr -> PT_TOP
            p.y < b - lr -> PT_V_CENTER
            p.y < b + lr -> PT_BOTTOM
            else -> return false
        }
        prev.x = p.x
        prev.y = p.y
        pressType = res
        return true
    }
    private fun onMove(x: Float, y: Float) {
        //logd("move")
        val deltaX = (x - prev.x).toInt()
        val deltaY = (y - prev.y).toInt()

        val isCenter = pressType == (PT_H_CENTER or PT_V_CENTER)

        if (isCenter) {
            roi.x += deltaX
            if (roi.x < 0) {
                roi.x = 0
            } else if (roi.x + roi.width > matWidth) {
                roi.x = matWidth - roi.width
            }

            roi.y += deltaY
            if (roi.y < 0) {
                roi.y = 0
            } else if ((roi.y + roi.height) > matHeight) {
                roi.y = matHeight - roi.height
            }
        } else {
            if ((pressType and PT_LEFT) != 0) {
                roi.x += deltaX
                roi.width -= deltaX
                if (roi.width < MIN_SIZE) {
                    val right = roi.x + roi.width
                    roi.x = right - MIN_SIZE
                    roi.width = MIN_SIZE
                } else if (roi.x < 0) {
                    roi.width += roi.x
                    roi.x = 0
                }
            }
            else if ((pressType and PT_RIGHT) != 0) {
                roi.width += deltaX
                if (roi.width < MIN_SIZE) {
                    roi.width = MIN_SIZE
                } else if (roi.x + roi.width > matWidth) {
                    roi.width = matWidth - roi.x
                }
            }

            if ((pressType and PT_TOP) != 0) {
                roi.y += deltaY
                roi.height -= deltaY
                if (roi.height < MIN_SIZE) {
                    val bot = roi.y + roi.height
                    roi.y = bot - MIN_SIZE
                    roi.height = MIN_SIZE
                } else if (roi.y < 0) {
                    roi.height += roi.y
                    roi.y = 0
                }
            }
            else if ((pressType and PT_BOTTOM) != 0) {
                roi.height += deltaY
                if (roi.height < MIN_SIZE) {
                    roi.height = MIN_SIZE
                } else if (roi.y + roi.height > matHeight) {
                    roi.height = matHeight - roi.y
                }
            }
        }
        updateRoi()
    }

    override fun onDraw(canvas: Canvas, screenRect: RectF) {
        paint.strokeWidth = 2f * density * (imageView?.currentZoom?:1f)
        canvas.drawRect(screenRect, paint)
    }*/
}