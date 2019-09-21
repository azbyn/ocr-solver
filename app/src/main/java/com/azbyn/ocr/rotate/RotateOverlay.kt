package com.azbyn.ocr.rotate

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import com.azbyn.ocr.GRAYED_OUT_COLOR
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min

class RotateOverlay : View {
    var rotateViewer: RotateViewer? = null
    private lateinit var imageView: ImageView
    private val density = resources.displayMetrics.density
    private var center = PointF(0f, 0f)

    private val grayPaint: Paint = Paint().apply {
        strokeWidth = 0f
        color = GRAYED_OUT_COLOR
    }
    private val linePaint: Paint = Paint().apply {
        strokeWidth = density
        //color = 0xA0_FF_FF_FF.toInt()
        color = 0xA0_39_71_ED.toInt()
    }

    private data class MatrixData(var scale: Float = 0f,
                                  var x: Float = 0f, var y: Float= 0f,
                                  var translateX: Float = 0f, var translateY: Float= 0f,
                                  var sizeX: Int = 0, var sizeY: Int = 0,
                                  var squareSize: Float = 0f) {
        fun init(w: Float, h: Float, bW: Float, bH: Float, orgBW: Float, orgBH: Float,
                 padding: Float, sizeX: Int, sizeY: Int) {
            scale = min((w - padding) / bW,
                    (h - padding) / bH)
            x = (w - scale * bW) / 2
            y = (h - scale * bH) / 2
            translateX = (w - scale * orgBW) / 2
            translateY = (h - scale * orgBH) / 2

            this.sizeX = sizeX
            this.sizeY = sizeY
            squareSize = (w-2*x) /sizeX
        }
        fun updateMatrix(m: Matrix) {
            m.reset()
            //Log.d(TAG, "Translate ($x, $y)")
            m.postScale(scale, scale)
            m.postTranslate(translateX, translateY)
        }
    }
    private val _matrix = Matrix()
    private val verticalData   = MatrixData()
    private val horizontalData = MatrixData()
    private var currentData = verticalData

    private var isHor = false
    fun rotate90() {
        isHor = !isHor
        //Log.d(TAG, "HORI: $isHor")
        currentData = if (isHor) horizontalData else verticalData
        prevA = 0f
        currentData.updateMatrix(_matrix)
        //_matrix.postRotate(prevA, currentData.x, currentData.y)
        invalidate()
    }

    constructor(ctx: Context) : super(ctx)
    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    private var bW = 0f
    private var bH = 0f
    fun initMatrix(_bW: Int, _bH: Int, iv: ImageView) {
        imageView = iv
        bW = _bW.toFloat()
        bH = _bH.toFloat()
        val w = imageView.width.toFloat()
        val h = imageView.height.toFloat()

        val sizeX = 12
        val squareSize = (bW / sizeX)
        val sizeY = (bH/squareSize).toInt()

        verticalData.  init(w, h, bW, bH, bW, bH, 50*density, sizeX, sizeY)
        horizontalData.init(w, h, bH, bW, bW, bH, 20*density, sizeY, sizeX)

        center.x = w/2f
        center.y = h/2f

        currentData.updateMatrix(_matrix)

        imageView.scaleType = ImageView.ScaleType.MATRIX

        imageView.imageMatrix = _matrix
        invalidate()
    }
    private var prevA = 0f
    //only to be used from RotateViewer
    fun setAngle(a: Float) {
        //Log.d(TAG,"Prev $prevA, now $a")
        _matrix.postRotate(-prevA, center.x, center.y)
        _matrix.postRotate(a, center.x, center.y)
        prevA = a
        imageView.imageMatrix = _matrix
    }
    val angle get() = prevA.toDouble()
    val isHorizontal get() = isHor


    private var _pressed = false
    private var prevTouchAngle = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        rotateViewer ?: return false
        val x = event.x - (width/2)
        val y = event.y - (height/2)

        //atan2(0,0) is defined, so no need to worry
        val angle = (atan2(y, x) / PI.toFloat() * 180)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> _pressed = true
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                _pressed = false
                return true
            }
            MotionEvent.ACTION_MOVE -> if (_pressed) {
                val deltaAngle = angle - prevTouchAngle
                rotateViewer!!.angle += deltaAngle
            }
            else -> return false
        }
        prevTouchAngle = angle

        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val padX = currentData.x
        val padY = currentData.y
        val squareSize = currentData.squareSize

        canvas.drawRect(0f,0f, padX, h, grayPaint)
        canvas.drawRect(w-padX,0f, w, h, grayPaint)
        canvas.drawRect(padX,0f, w-padX, padY, grayPaint)
        canvas.drawRect(padX,h-padY, w-padX, h, grayPaint)

        for (i in 0..currentData.sizeX) {
            val x = padX + squareSize*i
            canvas.drawLine(x, padY, x,h-padY, linePaint)
        }
        for (i in 0..currentData.sizeY) {
            val y = padY + squareSize*i
            canvas.drawLine(padX, y, w-padX, y, linePaint)
        }
    }
}