package com.azbyn.ocr

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.CallSuper

abstract class BaseOverlay : View, ZoomableImageView.OnDrawListener, View.OnTouchListener {
    protected var imageView: ZoomableImageView? = null

    constructor(ctx: Context) : super(ctx)
    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    @SuppressLint("ClickableViewAccessibility")
    @CallSuper
    open fun init(drawableWidth: Int, drawableHeight: Int, imageView: ZoomableImageView) {
        this.imageView = imageView
        imageView.addOnDrawListener(this)
        imageView.setOnTouchListener(this)
    }
    @CallSuper
    @SuppressLint("ClickableViewAccessibility")
    open fun cleanup() {
        imageView?.removeOnDrawListener(this)
        imageView?.setOnTouchListener(null)
    }

    final override fun onTouchEvent(event: MotionEvent): Boolean = false
    final override fun onTouch(v: View, e: MotionEvent): Boolean = onTouchImpl(e)

    abstract fun onTouchImpl(event: MotionEvent): Boolean

    @CallSuper
    /*final */override fun update() = invalidate()

    protected val rect = RectF(0f, 0f, 50f, 50f)
    protected fun setRect(l: Float, t: Float, r: Float, b: Float) {
        rect.left = l
        rect.top = t
        rect.right = r
        rect.bottom = b
    }
    private val screenRect = RectF()

    @SuppressLint("WrongCall")
    final override fun onDraw(canvas: Canvas) {
        imageView ?: return
        imageView?.mapRect(rect, screenRect)
        onDraw(canvas, screenRect)
    }
    abstract fun onDraw(canvas: Canvas, screenRect: RectF)
}