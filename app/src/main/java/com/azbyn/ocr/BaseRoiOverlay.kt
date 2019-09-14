package com.azbyn.ocr

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent

abstract class BaseRoiOverlay : BaseOverlay {
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
    // the 'radius' of a square (aka a square of length radius*2)
    // lineRadius   = how close to the line a touch has to be to be registered as a line press
    private var lineRadius = 0f
    private val scale get() = imageView?.matrixScale ?: 1f

    //private val roi get() = viewModel.roi
    protected var matWidth = 0f
        private set
    protected var matHeight = 0f
        private set

    abstract val roi : CvRect//()

    private var pressType = PT_NONE
    private val prev = PointF()
    private val p = PointF()

    protected fun updateRoi() {
        roi.x = rect.left.toInt()
        roi.y = rect.right.toInt()
        roi.width = rect.width().toInt()
        roi.height = rect.height().toInt()
    }
    constructor(ctx: Context) : super(ctx)
    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    override fun init(drawableWidth: Int, drawableHeight: Int, imageView: ZoomableImageView) {
        super.init(drawableWidth, drawableHeight, imageView)
        matWidth = drawableWidth.toFloat()
        matHeight = drawableHeight.toFloat()
        lineRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 5f,
                resources.displayMetrics) * width / matWidth
        onReset()
    }

    abstract fun onReset()

    final override fun onTouchImpl(event: MotionEvent): Boolean {
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
                update()
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
        val deltaX = (x - prev.x)
        val deltaY = (y - prev.y)

        val isCenter = pressType == (PT_H_CENTER or PT_V_CENTER)

        if (isCenter) {
            rect.left += deltaX
            rect.right += deltaX
            if (rect.left < 0) {
                rect.right -= rect.left
                rect.left = 0f
            } else if (rect.right > matWidth) {
                rect.left += matWidth - rect.right
                rect.right = matWidth
            }

            rect.top += deltaY
            rect.bottom += deltaY
            if (rect.top < 0) {
                rect.bottom -= rect.top
                rect.top = 0f
            } else if (rect.bottom > matHeight) {
                rect.top += matHeight - rect.bottom
                rect.bottom = matHeight
            }
        } else {
            if ((pressType and PT_LEFT) != 0) {
                rect.left += deltaX
                if (rect.width() < MIN_SIZE) {
                    rect.left = rect.right - MIN_SIZE
                } else if (rect.left < 0) {
                    rect.left = 0f
                }
            }
            else if ((pressType and PT_RIGHT) != 0) {
                rect.right += deltaX
                if (rect.width() < MIN_SIZE) {
                    rect.right = rect.left + MIN_SIZE
                } else if (rect.right > matWidth) {
                    rect.right = matWidth
                }
            }

            if ((pressType and PT_TOP) != 0) {
                rect.top += deltaY
                if (rect.height() < MIN_SIZE) {
                    rect.top = rect.bottom - MIN_SIZE
                } else if (rect.top < 0) {
                    rect.top = 0f
                }
            }
            else if ((pressType and PT_BOTTOM) != 0) {
                rect.bottom += deltaY
                if (rect.height() < MIN_SIZE) {
                    rect.bottom = rect.top + MIN_SIZE
                } else if (rect.bottom > matHeight) {
                    rect.bottom = matHeight
                }
            }
        }
    }

}