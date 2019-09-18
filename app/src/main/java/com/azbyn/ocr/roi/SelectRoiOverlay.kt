package com.azbyn.ocr.roi

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.widget.TextView
import com.azbyn.ocr.BaseRoiOverlay
import com.azbyn.ocr.CvRect
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
    override lateinit var roi: CvRect

    constructor(ctx: Context) : super(ctx)
    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    fun init(drawableWidth: Int, drawableHeight: Int, roi: CvRect,
             imageView: ZoomableImageView, text: TextView) {
        this.text = text
        this.roi = roi
        super.init(drawableWidth, drawableHeight, imageView)
    }

    override fun update() {
        text.text = context.getString(R.string.select_roi_text,
                roi.width, roi.height, roi.x, roi.y)
        super.update()
    }

    private val screenRect = RectF()
    override fun onDrawImpl(canvas: Canvas) {
        imageView?.mapRect(rect, screenRect)
        paint.strokeWidth = 2f * density * (imageView?.currentZoom?:1f)
        canvas.drawRect(screenRect, paint)
    }
}