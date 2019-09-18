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

    @CallSuper
    @SuppressLint("ClickableViewAccessibility")
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
    override fun update() = invalidate()

    final override fun onDraw(canvas: Canvas) {
        imageView ?: return
        onDrawImpl(canvas)
    }
    abstract fun onDrawImpl(canvas: Canvas)
}