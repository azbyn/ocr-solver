package com.azbyn.ocr

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatImageView
import com.azbyn.ocr.Misc.logd
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

//Based on https://github.com/MikeOrtiz/TouchImageView
@Suppress("MemberVisibilityCanBePrivate")
class ZoomableImageView: AppCompatImageView {
    companion object {
        // SuperMin and SuperMax multipliers. Determine how much the image can be
        // zoomed below or above the zoom boundaries, before animating back to the
        // min/max zoom boundary.
        private const val SUPER_MIN_MULTIPLIER = .75f
        private const val SUPER_MAX_MULTIPLIER = 2.0f


        //If setMinZoom(AUTOMATIC_MIN_ZOOM), then we'll set the min scale to include the whole image.
        //const val AUTOMATIC_MIN_ZOOM = -1.0f

        const val ZOOM_TIME = 500f
    }

    // Scale of image ranges from minScale to maxScale, where minScale == 1
    // when the image is stretched to fit view.
    private var normalizedScale = 1f

    private var margin = 10f

    // Matrix applied to image. MSCALE_X and MSCALE_Y should always be equal.
    // MTRANS_X and MTRANS_Y are the other values used. prevMatrix is the matrix
    // saved prior to the screen rotating.
    private val _matrix : Matrix = Matrix()

    private val prevMatrix : Matrix = Matrix()

    private var m = FloatArray(9)

    //val zoomEnabled = true
    enum class FixedPixel {CENTER, TOP_LEFT, BOTTOM_RIGHT}

    var orientationChangeFixedPixel = FixedPixel.CENTER
    var viewSizeChangeFixedPixel = FixedPixel.CENTER

    private var orientationJustChanged = false

    private enum class State {NONE, DRAG, ZOOM, FLING, ANIMATE_ZOOM}

    private var state = State.NONE


    //private var userSpecifiedMinScale = 0f
    //private var maxScaleIsSetByMultiplier = false
    //private var maxScaleMultiplier = 0f

    private var minScale = 1f
    private val maxScale = 7f
    /*
    val minZoom get() = minScale
    var maxZoom: Float
        get() = maxScale
        set(max) {
            maxScale = max
            superMaxScale = SUPER_MAX_MULTIPLIER * field
        }*/

    private var superMinScale: Float
    private var superMaxScale: Float

    //private lateinit var _context: Context
    private var fling: Fling? = null
    private var orientation = 0

    private var _scaleType: ScaleType = ScaleType.FIT_CENTER

    private var imageRenderedAtLeastOnce = false
    private var onDrawReady = false

    private var delayedZoomVariables: ZoomVariables? = null

    // Size of view and previous view size (ie before rotation)
    private var viewWidth = 0
    private var viewHeight = 0
    private var prevViewWidth = 0
    private var prevViewHeight = 0


    // Size of image when it is stretched to fit view. Before and After rotation.
    private var matchViewWidth = 0f
    private var matchViewHeight = 0f
    private var prevMatchViewWidth = 0f
    private var prevMatchViewHeight = 0f

    private var scaleDetector: ScaleGestureDetector
    private var gestureDetector: GestureDetector
    var doubleTapListener: GestureDetector.OnDoubleTapListener? = null
    private var userTouchListener: OnTouchListener? = null
    var touchImageViewListener: OnTouchImageViewListener? = null

    /**
     * Returns false if image is in initial, unzoomed state. False, otherwise.
     * @return true if image is zoomed
     */
    val isZoomed get() = normalizedScale != 1f

    constructor(ctx: Context) : this(ctx, null, 0)
    constructor(ctx: Context, attrs: AttributeSet?) : this(ctx, attrs, 0)

    constructor(ctx: Context, attrs: AttributeSet?, defStyle: Int) : super(ctx, attrs, defStyle) {
        //_scaleType = ScaleType.FIT_CENTER

        //maxScale = 7f
        superMinScale = SUPER_MIN_MULTIPLIER * minScale
        superMaxScale = SUPER_MAX_MULTIPLIER * maxScale

        orientation = resources.configuration.orientation
        scaleDetector = ScaleGestureDetector(ctx, ScaleListener())
        gestureDetector = GestureDetector(ctx, GestureListener())

        if (attrs != null) {
            val ta = ctx.obtainStyledAttributes(attrs, R.styleable.ZoomableImageView)
            margin = ta.getDimension(R.styleable.ZoomableImageView_margin, 0f)
            logd("margin $margin")
            ta.recycle()
        }
        //minScale = 1f
        configureImageView()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configureImageView() {
        super.setClickable(true)
        imageMatrix = _matrix
        scaleType = ScaleType.MATRIX
        super.setOnTouchListener(PrivateOnTouchListener())
    }
    fun mapRect(src: RectF, dst: RectF) {
        // the order weirdly is (dst, src)
        _matrix.mapRect(dst, src)
    }
    //using m might be bad, as this might be called from
    private val matrixBuffer = FloatArray(9)
    val matrixScale get() = matrixBuffer[Matrix.MSCALE_X]

    private fun updateMatrixBuffer() = _matrix.getValues(matrixBuffer)

    fun screenToDrawable(p: PointF) = screenToDrawable(p, p)

    fun screenToDrawable(src: PointF, dst: PointF) {
        // We know the matrix has the is of the form below, so we can simplify the matrix inversion
        // s 0 tx
        // 0 s ty
        // 0 0 1
        // where s=scale, tx=translateX, ty=translateY
        val b = matrixBuffer
        val s = b[Matrix.MSCALE_X]
        val tx = b[Matrix.MTRANS_X]
        val ty = b[Matrix.MTRANS_Y]
        //logd("b ${p.x.format(1)}, ${p.y.format(1)}")
        dst.x = (src.x-tx)/s
        dst.y = (src.y-ty)/s
        //logd("a ${p.x.format(1)}, ${p.y.format(1)}")
    }

    override fun setOnTouchListener(l: OnTouchListener?) {
        userTouchListener = l
    }

    override fun setImageResource(resId: Int) {
        imageRenderedAtLeastOnce = false
        super.setImageResource(resId)
        savePreviousImageValues()
        fitImageToView()
    }

    override fun setImageBitmap(bm: Bitmap) {
        imageRenderedAtLeastOnce = false
        super.setImageBitmap(bm)
        savePreviousImageValues()
        fitImageToView()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        imageRenderedAtLeastOnce = false
        drawable?.isFilterBitmap = false//No anti aliasing
        super.setImageDrawable(drawable)
        savePreviousImageValues()
        fitImageToView()
    }

    override fun setImageURI(uri: Uri?) {
        imageRenderedAtLeastOnce = false
        super.setImageURI(uri)
        savePreviousImageValues()
        fitImageToView()
    }

    override fun setScaleType(type: ScaleType) {
        if (type == ScaleType.MATRIX) {
            super.setScaleType(ScaleType.MATRIX)
        } else {
            _scaleType = type
            if (onDrawReady) {
                // If the image is already rendered, scaleType has been called programmatically
                // and the TouchImageView should be updated with the new scaleType.
                setZoom(this)
            }
        }
    }
    override fun getScaleType(): ScaleType = _scaleType//!!

    /*
    /**
     * Return a Rect representing the zoomed image.
     *
     * @return rect representing zoomed image
     */
    fun getZoomedRect(): RectF {
        if (_scaleType == ScaleType.FIT_XY) {
            throw UnsupportedOperationException("getZoomedRect() not supported with FIT_XY")
        }
        val topLeft = transformCoordTouchToBitmap(0f, 0f, true)
        val bottomRight = transformCoordTouchToBitmap(viewWidth.toFloat(), viewHeight.toFloat(),
                true)

        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        return RectF(topLeft.x / w, topLeft.y / h,
                bottomRight.x / w, bottomRight.y / h)
    }
     */

    /**
     * Save the current matrix and view dimensions
     * in the prevMatrix and prevView variables.
     */
    fun savePreviousImageValues() {
        if (viewHeight != 0 && viewWidth != 0) {
            _matrix.getValues(m)
            prevMatrix.setValues(m)
            prevMatchViewHeight = matchViewHeight
            prevMatchViewWidth = matchViewWidth
            prevViewHeight = viewHeight
            prevViewWidth = viewWidth
        }
    }
    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable("instanceState", super.onSaveInstanceState())
        bundle.putInt("orientation", orientation)
        bundle.putFloat("saveScale", normalizedScale)
        bundle.putFloat("matchViewHeight", matchViewHeight)
        bundle.putFloat("matchViewWidth", matchViewWidth)
        bundle.putInt("viewWidth", viewWidth)
        bundle.putInt("viewHeight", viewHeight)
        _matrix.getValues(m)
        bundle.putFloatArray("matrix", m)
        bundle.putBoolean("imageRendered", imageRenderedAtLeastOnce)
        bundle.putSerializable("viewSizeChangeFixedPixel", viewSizeChangeFixedPixel)
        bundle.putSerializable("orientationChangeFixedPixel", orientationChangeFixedPixel)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val bundle = state as? Bundle
        bundle?: return super.onRestoreInstanceState(state)
        normalizedScale = bundle.getFloat("saveScale")
        m = bundle.getFloatArray("matrix")!!
        prevMatrix.setValues(m)
        prevMatchViewHeight = bundle.getFloat("matchViewHeight")
        prevMatchViewWidth = bundle.getFloat("matchViewWidth")
        prevViewHeight = bundle.getInt("viewHeight")
        prevViewWidth = bundle.getInt("viewWidth")
        imageRenderedAtLeastOnce = bundle.getBoolean("imageRendered")
        viewSizeChangeFixedPixel = bundle.getSerializable("viewSizeChangeFixedPixel") as FixedPixel
        orientationChangeFixedPixel = bundle.getSerializable("orientationChangeFixedPixel") as FixedPixel
        val oldOrientation = bundle.getInt("orientation")
        if (orientation != oldOrientation) {
            orientationJustChanged = true
        }
        super.onRestoreInstanceState(bundle.getParcelable("instanceState"))
    }
    interface OnDrawListener {
        fun update()
    }
    private var onDrawListener: OnDrawListener? = null

    @Suppress("UNUSED_PARAMETER")
    fun removeOnDrawListener(l: OnDrawListener) {
        onDrawListener = null
    }

    fun addOnDrawListener(l: OnDrawListener) {
        onDrawListener = l
    }

    /*
    inline fun setOnDrawListener(crossinline f: () -> Unit) {
        onDrawListener = object : OnDrawListener {
            override fun update() { f() }
        }
    }*/

    override fun onDraw(canvas: Canvas?) {
        //logd("onDraw")
        onDrawReady = true
        imageRenderedAtLeastOnce = true
        if (delayedZoomVariables != null) {
            setZoom(delayedZoomVariables!!.scale, delayedZoomVariables!!.focusX,
                    delayedZoomVariables!!.focusY, delayedZoomVariables!!.scaleType)
            delayedZoomVariables = null
        }
        updateMatrixBuffer()
        onDrawListener?.update()
        super.onDraw(canvas)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        val newOrientation = resources.configuration.orientation
        if (newOrientation != orientation) {
            orientationJustChanged = true
            orientation = newOrientation
        }
        savePreviousImageValues()
    }

    /**
     * Get the current zoom. This is the zoom relative to the initial
     * scale, not the original resource.
     *
     * @return current zoom multiplier.
     */
    val currentZoom get() = normalizedScale
    /*
    /**
     * Set the min zoom multiplier. Default value: 1.
     *
     * @param min min zoom multiplier.
     */
    fun setMinZoom(min: Float) {
        userSpecifiedMinScale = min
        logd("set min zoom")
        if (min == AUTOMATIC_MIN_ZOOM) {
            logd("margin thing $margin")
            if (_scaleType == ScaleType.CENTER || _scaleType == ScaleType.CENTER_CROP) {
                val drawableWidth = drawable.intrinsicWidth
                val drawableHeight = drawable.intrinsicHeight
                if (drawable != null && drawableWidth > 0 && drawableHeight > 0) {
                    val widthRatio = viewWidth.toFloat() / drawableWidth
                    val heightRatio = viewHeight.toFloat() / drawableHeight
                    minScale = if (_scaleType == ScaleType.CENTER) {
                        min(widthRatio, heightRatio)
                    } else {  // CENTER_CROP
                        min(widthRatio, heightRatio) / max(widthRatio, heightRatio)
                    }
                }
            } else {
                minScale = 1.0f
            }
        } else {
            minScale = userSpecifiedMinScale
        }
        superMinScale = SUPER_MIN_MULTIPLIER * minScale
    }*/

    /**
     * Reset zoom and translation to initial state.
     */
    fun resetZoom() {
        //logd("resetZoom() $minScale, $margin")
        normalizedScale = minScale //1f
        fitImageToView()
    }

    /**
     * Set zoom to the specified scale. Image will be centered by default.
     *
     */
    fun setZoom(scale: Float) = setZoom(scale, 0.5f, 0.5f)

    /**
     * Set zoom to the specified scale. Image will be centered around the point
     * (focusX, focusY). These floats range from 0 to 1 and denote the focus point
     * as a fraction from the left and top of the view. For example, the top left
     * corner of the image would be (0, 0). And the bottom right corner would be (1, 1).
     *
     */
    fun setZoom(scale: Float, focusX: Float, focusY: Float) =
        setZoom(scale, focusX, focusY, _scaleType)


    /**
     * Set zoom to the specified scale. Image will be centered around the point
     * (focusX, focusY). These floats range from 0 to 1 and denote the focus point
     * as a fraction from the left and top of the view. For example, the top left
     * corner of the image would be (0, 0). And the bottom right corner would be (1, 1).
     *
     */
    fun setZoom(scale: Float, focusX: Float, focusY: Float, scaleType: ScaleType ) {
        //
        // setZoom can be called before the image is on the screen, but at this point,
        // image and view sizes have not yet been calculated in onMeasure. Thus, we should
        // delay calling setZoom until the view has been measured.
        //
        if (!onDrawReady) {
            delayedZoomVariables = ZoomVariables(scale, focusX, focusY, scaleType)
            return
        }
        /*
        if (userSpecifiedMinScale == AUTOMATIC_MIN_ZOOM) {
            setMinZoom(AUTOMATIC_MIN_ZOOM)
            if (normalizedScale < minScale) {
                normalizedScale = minScale
            }
        }*/

        if (scaleType != _scaleType) {
            setScaleType(scaleType)
        }
        resetZoom()
        scaleImage(scale, viewWidth / 2f, viewHeight / 2f, true)
        _matrix.getValues(m)
        m[Matrix.MTRANS_X] = -((focusX * imageWidth) - (viewWidth * 0.5f))
        m[Matrix.MTRANS_Y] = -((focusY * imageHeight) - (viewHeight * 0.5f))
        _matrix.setValues(m)
        fixTrans()
        imageMatrix = _matrix
    }

    /**
     * Set zoom parameters equal to another ZoomableImageView. Including scale, position,
     * and ScaleType.
     *
     */
    fun setZoom(img: ZoomableImageView) {
        val center = img.getScrollPosition()!!
        setZoom(img.currentZoom, center.x, center.y, img.scaleType)
    }


    /**
     * Return the point at the center of the zoomed image. The PointF coordinates range
     * in value between 0 and 1 and the focus point is denoted as a fraction from the left
     * and top of the view. For example, the top left corner of the image would be (0, 0).
     * And the bottom right corner would be (1, 1).
     *
     * @return PointF representing the scroll position of the zoomed image.
     */
    fun getScrollPosition(): PointF? {
        drawable?: return null
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight

        val point = transformCoordTouchToBitmap(viewWidth / 2f, viewHeight / 2f,
                true)
        point.x /= drawableWidth
        point.y /= drawableHeight
        return point
    }

    /**
     * Set the focus point of the zoomed image. The focus points are denoted as a fraction from the
     * left and top of the view. The focus points can range in value between 0 and 1.
     *
     */
    fun setScrollPosition(focusX: Float, focusY: Float) {
        setZoom(normalizedScale, focusX, focusY)
    }

    /**
     * Performs boundary checking and fixes the image matrix if it
     * is out of bounds.
     */
    fun fixTrans() {
        _matrix.getValues(m)
        val transX = m[Matrix.MTRANS_X]
        val transY = m[Matrix.MTRANS_Y]

        val fixTransX = getFixTrans(transX, viewWidth.toFloat(), imageWidth)
        val fixTransY = getFixTrans(transY, viewHeight.toFloat(), imageHeight)

        if (fixTransX != 0f || fixTransY != 0f) {
            _matrix.postTranslate(fixTransX, fixTransY)
        }
    }

    /**
     * When transitioning from zooming from focus to zoom from center (or vice versa)
     * the image can become unaligned within the view. This is apparent when zooming
     * quickly. When the content size is less than the view size, the content will often
     * be centered incorrectly within the view. fixScaleTrans first calls fixTrans() and
     * then makes sure the image is centered correctly within the view.
     */
    fun fixScaleTrans() {
        fixTrans()
        _matrix.getValues(m)
        if (imageWidth < viewWidth) {
            m[Matrix.MTRANS_X] = (viewWidth - imageWidth) / 2
        }

        if (imageHeight < viewHeight) {
            m[Matrix.MTRANS_Y] = (viewHeight - imageHeight) / 2
        }
        _matrix.setValues(m)
    }

    fun getFixTrans(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float

        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize

        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }

        if (trans < minTrans)
            return -trans + minTrans
        if (trans > maxTrans)
            return -trans + maxTrans
        return 0f
    }

    fun getFixDragTrans(delta: Float, viewSize: Float, contentSize: Float): Float {
        return if (contentSize <= viewSize) 0f else delta
    }

    private val imageWidth get() = matchViewWidth * normalizedScale

    private val imageHeight get() = matchViewHeight * normalizedScale

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val drawableWidth = drawable?.intrinsicHeight ?: 0
        val drawableHeight = drawable?.intrinsicHeight ?: 0
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val totalViewWidth = setViewSize(widthMode, widthSize, drawableWidth)
        val totalViewHeight = setViewSize(heightMode, heightSize, drawableHeight)

        if (!orientationJustChanged) {
            savePreviousImageValues()
        }

        // Image view width, height must consider padding
        val width = totalViewWidth - paddingLeft - paddingRight
        val height = totalViewHeight - paddingTop - paddingBottom

        // Set view dimensions
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        //
        // Fit content within view.
        //
        // onMeasure may be called multiple times for each layout change, including orientation
        // changes. For example, if the TouchImageView is inside a ConstraintLayout, onMeasure may
        // be called with:
        // widthMeasureSpec == "AT_MOST 2556" and then immediately with
        // widthMeasureSpec == "EXACTLY 1404", then back and forth multiple times in quick
        // succession, as the ConstraintLayout tries to solve its constraints.
        //
        // onSizeChanged is called once after the final onMeasure is called. So we make all changes
        // to class members, such as fitting the image into the new shape of the TouchImageView,
        // here, after the final size has been determined. This helps us avoid both
        // repeated computations, and making irreversible changes (e.g. making the View temporarily too
        // big or too small, thus making the current zoom fall outside of an automatically-changing
        // minZoom and maxZoom).
        //
        viewWidth = w
        viewHeight = h

        //if (margin >= 0f) {
        minScale = (w - 2*margin) / w
        superMinScale = SUPER_MIN_MULTIPLIER * minScale
        //logd("sz: $w, $h, $oldw, $oldh")
        //logd("updated with minscale? $minScale $currentZoom")
        if (currentZoom <= 1f && margin != 0f) {
            resetZoom()
        } else {
            fitImageToView()
        }
    }

    /**
     * This function can be called:
     * 1. When the TouchImageView is first loaded (onMeasure).
     * 2. When a new image is loaded (setImageResource|Bitmap|Drawable|URI).
     * 3. On rotation (onSaveInstanceState, then onRestoreInstanceState, then onMeasure).
     * 4. When the view is resized (onMeasure).
     * 5. When the zoom is reset (resetZoom).
     * <p>
     * In cases 2, 3 and 4, we try to maintain the zoom state and position as directed by
     * orientationChangeFixedPixel or viewSizeChangeFixedPixel (if there is an existing zoom state
     * and position, which there might not be in case 2).
     * <p>
     * If the normalizedScale is equal to 1, then the image is made to fit the View. Otherwise, we
     * maintain zoom level and attempt to roughly put the same part of the image in the View as was
     * there before, paying attention to orientationChangeFixedPixel or viewSizeChangeFixedPixel.
     */
    private fun fitImageToView() {
        val fixedPixel = if (orientationJustChanged)
            orientationChangeFixedPixel else viewSizeChangeFixedPixel
        orientationJustChanged = false

        if (drawable == null || drawable.intrinsicWidth == 0 || drawable.intrinsicHeight == 0) {
            return
        }
        // while this looks pointless, the super constructor calls setImageDrawable, which calls
        // this function before _matrix has a chance to be initialized
        @Suppress("SENSELESS_COMPARISON")
        if (_matrix == null || prevMatrix == null) {
            return
        }
        /*
        if (userSpecifiedMinScale == AUTOMATIC_MIN_ZOOM) {
            setMinZoom(AUTOMATIC_MIN_ZOOM)
            if (normalizedScale < minScale) {
                normalizedScale = minScale
            }
        }*/

        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight

        // Scale image for view
        var scaleX = viewWidth.toFloat() / drawableWidth
        var scaleY = viewHeight.toFloat() / drawableHeight
        when (_scaleType) {
            ScaleType.CENTER -> {
                scaleX = 1f
                scaleY = scaleX
            }

            ScaleType.CENTER_CROP -> {
                scaleY = max(scaleX, scaleY)
                scaleX = scaleY
            }

            ScaleType.CENTER_INSIDE -> {
                scaleY = min(1f, min(scaleX, scaleY))
                scaleX = scaleY

            }
            ScaleType.FIT_CENTER, ScaleType.FIT_START, ScaleType.FIT_END -> {
                scaleY = min(scaleX, scaleY)
                scaleX = scaleY
            }
            else -> Unit
        }

        // Put the image's center in the right place.
        val redundantXSpace = viewWidth - (scaleX * drawableWidth)
        val redundantYSpace = viewHeight - (scaleY * drawableHeight)
        matchViewWidth = viewWidth - redundantXSpace
        matchViewHeight = viewHeight - redundantYSpace
        if (!isZoomed && !imageRenderedAtLeastOnce) {

            // Stretch and center image to fit view
            _matrix.apply {
                setScale(scaleX, scaleY)
                when (_scaleType) {
                    ScaleType.FIT_START -> {
                        postTranslate(0f, 0f)
                    }
                    ScaleType.FIT_END -> postTranslate(redundantXSpace, redundantYSpace)
                    else ->
                        postTranslate(redundantXSpace / 2f, redundantYSpace / 2f)
                }
            }
            normalizedScale = 1f
        } else {
            // These values should never be 0 or we will set viewWidth and viewHeight
            // to NaN in newTranslationAfterChange. To avoid this, call savePreviousImageValues
            // to set them equal to the current values.
            if (prevMatchViewWidth == 0f || prevMatchViewHeight == 0f) {
                savePreviousImageValues()
            }

            // Use the previous matrix as our starting point for the new matrix.
            prevMatrix.getValues(m)

            // Rescale Matrix if appropriate
            m[Matrix.MSCALE_X] = matchViewWidth / drawableWidth * normalizedScale
            m[Matrix.MSCALE_Y] = matchViewHeight / drawableHeight * normalizedScale

            // TransX and TransY from previous matrix
            val transX = m[Matrix.MTRANS_X]
            val transY = m[Matrix.MTRANS_Y]

            // X position
            val prevActualWidth = prevMatchViewWidth * normalizedScale
            val actualWidth = imageWidth
            m[Matrix.MTRANS_X] = newTranslationAfterChange(transX, prevActualWidth, actualWidth, prevViewWidth, viewWidth, drawableWidth, fixedPixel)

            // Y position
            val prevActualHeight = prevMatchViewHeight * normalizedScale
            val actualHeight = imageHeight
            m[Matrix.MTRANS_Y] = newTranslationAfterChange(transY, prevActualHeight, actualHeight, prevViewHeight, viewHeight, drawableHeight, fixedPixel)

            //
            // Set the matrix to the adjusted scale and translation values.
            //
            _matrix.setValues(m)
        }
        fixTrans()
        imageMatrix = _matrix
    }

    /**
     * Set view dimensions based on layout params
     *
     */
    private fun setViewSize(mode: Int, size: Int, drawableWidth: Int): Int {
        return when(mode) {
            MeasureSpec.EXACTLY -> size
            MeasureSpec.AT_MOST -> min(drawableWidth, size)
            MeasureSpec.UNSPECIFIED -> drawableWidth
            else -> size
        }
    }

    /**
     * After any change described in the comments for fitImageToView, the matrix needs to be
     * translated. This function translates the image so that the fixed pixel in the image
     * stays in the same place in the View.
     *
     * @param trans                the value of trans in that axis before the rotation
     * @param prevImageSize        the width/height of the image before the rotation
     * @param imageSize            width/height of the image after rotation
     * @param prevViewSize         width/height of view before rotation
     * @param viewSize             width/height of view after rotation
     * @param drawableSize         width/height of drawable
     * @param sizeChangeFixedPixel how we should choose the fixed pixel
     */
    private fun newTranslationAfterChange(trans: Float, prevImageSize: Float, imageSize: Float,
                                          prevViewSize: Int, viewSize: Int, drawableSize: Int,
                                          sizeChangeFixedPixel: FixedPixel): Float {
        return when {
            imageSize < viewSize ->
                // The width/height of image is less than the view's width/height. Center it.
                (viewSize - (drawableSize * m[Matrix.MSCALE_X])) * 0.5f
            trans > 0 ->
                // The image is larger than the view, but was not before the view changed. Center it.
                -((imageSize - viewSize) * 0.5f)
            else -> {
                // Where is the pixel in the View that we are keeping stable, as a fraction of the
                // width/height of the View?
                val fixedPixelPositionInView = when (sizeChangeFixedPixel) {
                    FixedPixel.CENTER -> 0.5f
                    FixedPixel.BOTTOM_RIGHT -> 1.0f
                    FixedPixel.TOP_LEFT -> 0.0f
                }
                // Where is the pixel in the Image that we are keeping stable, as a fraction of the
                // width/height of the Image?
                val fixedPixelPositionInImage = (-trans +
                        (fixedPixelPositionInView * prevViewSize)) / prevImageSize
                // Here's what the new translation should be so that, after whatever change triggered
                // this function to be called, the pixel at fixedPixelPositionInView of the View is
                // still the pixel at fixedPixelPositionInImage of the image.
                return -((fixedPixelPositionInImage * imageSize) -
                        (viewSize * fixedPixelPositionInView))
            }
        }
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        _matrix.getValues(m)
        val x = m[Matrix.MTRANS_X]

        if (imageWidth < viewWidth) {
            return false
        } else if (x >= -1 && direction < 0) {
            return false
        } else if (abs(x) + viewWidth + 1 >= imageWidth && direction > 0) {
            return false
        }

        return true
    }

    override fun canScrollVertically(direction: Int): Boolean {
        _matrix.getValues(m)
        val y = m[Matrix.MTRANS_Y]

        if (imageHeight < viewHeight) {
            return false
        } else if (y >= -1 && direction < 0) {
            return false
        } else if (abs(y) + viewHeight + 1 >= imageHeight && direction > 0) {
            return false
        }

        return true
    }

    /**
     * Gesture Listener detects a single click or long click and passes that on
     * to the view's listener.
     *
     * @author Ortiz
     */
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
            return doubleTapListener?.onSingleTapConfirmed(e) ?: performClick()
        }

        override fun onLongPress(e: MotionEvent?) {
            performLongClick()
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent?,
                             velocityX: Float, velocityY: Float): Boolean {
            // If a previous fling is still active, it should be cancelled so that two flings
            // are not run simultaneously.
            fling?.cancelFling()
            fling = Fling(velocityX, velocityY)
            compatPostOnAnimation(fling!!)
            return super.onFling(e1, e2, velocityX, velocityY)
        }

        override fun onDoubleTap(e: MotionEvent?): Boolean {
            //logd("double tap $state")
            //return if (zoomEnabled) {
            if (doubleTapListener != null) {
                return doubleTapListener!!.onDoubleTap(e)
            }
            if (state == State.NONE || state == State.FLING) {
                val targetZoom = if (normalizedScale == minScale) maxScale else minScale
                val doubleTap = DoubleTapZoom(targetZoom, e!!.x, e.y, false)
                compatPostOnAnimation(doubleTap)
                return true
            }
            return false/*
            return when {
                    doubleTapListener != null -> doubleTapListener!!.onDoubleTap(e)
                    state == State.NONE -> {
                        val targetZoom = if (normalizedScale == minScale) maxScale else minScale
                        val doubleTap = DoubleTapZoom(targetZoom, e!!.x, e.y, false)
                        compatPostOnAnimation(doubleTap)
                        true
                    }
                    else -> false
                }*/
            /*} else {
                false
            }*/
        }

        override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
            return doubleTapListener?.onDoubleTapEvent(e) ?: false
        }
    }
    interface OnTouchImageViewListener {
        fun onMove()
    }

    /**
     * Responsible for all touch events. Handles the heavy lifting of drag and also sends
     * touch events to Scale Detector and Gesture Detector.
     *
     * @author Ortiz
     */
    private inner class PrivateOnTouchListener: OnTouchListener {
        // Remember last point position for dragging
        private val last = PointF()

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            if (drawable == null) {
                state = State.NONE
                return false
            }
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            // OnTouchImageViewListener is set: TouchImageView dragged by user.
            touchImageViewListener?.onMove()
            // User-defined OnTouchListener

            if (state != State.ZOOM && userTouchListener?.onTouch(v, event) == true) {
                //state = State.NONE
                //fling?.cancelFling()
                imageMatrix = _matrix
                //logd("state was $state, action ${event.action}")
                return true
            }


            if (state == State.NONE || state == State.DRAG || state == State.FLING) {
                val curr = PointF(event.x, event.y)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        last.set(curr)
                        fling?.cancelFling()
                        state = State.DRAG
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (state == State.DRAG) {
                            val deltaX = curr.x - last.x
                            val deltaY = curr.y - last.y
                            val fixTransX = getFixDragTrans(deltaX, viewWidth.toFloat(), imageWidth)
                            val fixTransY = getFixDragTrans(deltaY, viewHeight.toFloat(), imageHeight)
                            _matrix.postTranslate(fixTransX, fixTransY)
                            fixTrans()
                            last.set(curr.x, curr.y)
                        }
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_POINTER_UP -> state = State.NONE
                }
            }
            imageMatrix = _matrix

            // indicate event was handled
            return true
        }
    }

    /**
     * ScaleListener detects user two finger scaling and scales image.
     *
     * @author Ortiz
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
            state = State.ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleImage(detector.scaleFactor, detector.focusX, detector.focusY,
                    true)

            // OnTouchImageViewListener is set: TouchImageView pinch zoomed by user.
            touchImageViewListener?.onMove()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector?) {
            super.onScaleEnd(detector)
            state = State.NONE
            var animateToZoomBoundary = false
            var targetZoom = normalizedScale
            if (normalizedScale > maxScale) {
                targetZoom = maxScale
                animateToZoomBoundary = true
            } else if (normalizedScale < minScale) {
                targetZoom = minScale
                animateToZoomBoundary = true
            }

            if (animateToZoomBoundary) {
                val doubleTap = DoubleTapZoom(targetZoom, viewWidth / 2f,
                        viewHeight / 2f, true)
                compatPostOnAnimation(doubleTap)
            }
        }
    }

    private fun scaleImage(deltaScale: Float, focusX: Float, focusY: Float,
                           stretchImageToSuper: Boolean) {
        val lowerScale: Float
        val upperScale: Float
        if (stretchImageToSuper) {
            lowerScale = superMinScale
            upperScale = superMaxScale
        } else {
            lowerScale = minScale
            upperScale = maxScale
        }

        val origScale = normalizedScale
        var ds = deltaScale
        normalizedScale *= deltaScale
        if (normalizedScale > upperScale) {
            normalizedScale = upperScale
            ds = upperScale / origScale
        } else if (normalizedScale < lowerScale) {
            normalizedScale = lowerScale
            ds = lowerScale / origScale
        }

        _matrix.postScale(ds, ds, focusX, focusY)
        fixScaleTrans()
    }

    /**
     * DoubleTapZoom calls a series of runnables which apply
     * an animated zoom in/out graphic to the image.
     *
     * @author Ortiz
     */
    private inner class DoubleTapZoom(
            val targetZoom: Float,
            focusX: Float,
            focusY: Float,
            val stretchImageToSuper: Boolean
    ): Runnable {
        private val startTime: Long
        private val startZoom: Float
        private val bitmapX: Float
        private val bitmapY: Float
        private val interpolator = AccelerateDecelerateInterpolator()
        private val startTouch: PointF
        private val endTouch = PointF()

        init {
            state = State.ANIMATE_ZOOM
            startTime = System.currentTimeMillis()
            startZoom = normalizedScale
            val bitmapPoint = transformCoordTouchToBitmap(focusX, focusY, false)
            bitmapX = bitmapPoint.x
            bitmapY = bitmapPoint.y
            // Used for translating image during scaling
            startTouch = transformCoordBitmapToTouch(bitmapX, bitmapY)
            endTouch.x = viewWidth / 2f
            endTouch.y = viewHeight / 2f
        }

        override fun run() {
            if (drawable == null) {
                state = State.NONE
                return
            }
            val t = interpolate()
            val deltaScale = calculateDeltaScale(t)
            scaleImage(deltaScale, bitmapX, bitmapY, stretchImageToSuper)
            translateImageToCenterTouchPosition(t)
            fixScaleTrans()
            imageMatrix = _matrix

            // OnTouchImageViewListener is set: double tap runnable updates listener
            // with every frame.
            touchImageViewListener?.onMove()

            if (t < 1f) {
                // We haven't finished zooming
                compatPostOnAnimation(this)
            } else {
                // Finished zooming
                state = State.NONE
            }
        }

        /**
         * Interpolate between where the image should start and end in order to translate
         * the image so that the point that is touched is what ends up centered at the end
         * of the zoom.
         *
         */
        private fun translateImageToCenterTouchPosition(t: Float) {
            val targetX = startTouch.x + t * (endTouch.x - startTouch.x)
            val targetY = startTouch.y + t * (endTouch.y - startTouch.y)
            val curr = transformCoordBitmapToTouch(bitmapX, bitmapY)
            _matrix.postTranslate(targetX - curr.x, targetY - curr.y)
        }

        /**
         * Use interpolator to get t
         *
         */
        private fun interpolate(): Float {
            val currTime = System.currentTimeMillis()
            val elapsed = min(1f,  (currTime - startTime) / ZOOM_TIME)
            return interpolator.getInterpolation(elapsed)
        }

        /**
         * Interpolate the current targeted zoom and get the delta
         * from the current zoom.
         *
         */
        private fun calculateDeltaScale(t: Float): Float {
            val zoom = startZoom + t * (targetZoom - startZoom)
            return zoom / normalizedScale
        }
    }

    /**
     * This function will transform the coordinates in the touch event to the coordinate
     * system of the drawable that the imageview contain
     *
     * @param x            x-coordinate of touch event
     * @param y            y-coordinate of touch event
     * @param clipToBitmap Touch event may occur within view, but outside image content. True, to clip return value
     *                     to the bounds of the bitmap size.
     * @return Coordinates of the point touched, in the coordinate system of the original drawable.
     */
    private fun transformCoordTouchToBitmap(x: Float, y: Float, clipToBitmap: Boolean): PointF {
        _matrix.getValues(m)
        val origW = drawable.intrinsicWidth.toFloat()
        val origH = drawable.intrinsicHeight.toFloat()
        val transX = m[Matrix.MTRANS_X]
        val transY = m[Matrix.MTRANS_Y]
        var finalX = ((x - transX) * origW) / imageWidth
        var finalY = ((y - transY) * origH) / imageHeight

        if (clipToBitmap) {
            finalX = min(max(finalX, 0f), origW)
            finalY = min(max(finalY, 0f), origH)
        }

        return PointF(finalX, finalY)
    }

    /**
     * Inverse of transformCoordTouchToBitmap. This function will transform the coordinates in the
     * drawable's coordinate system to the view's coordinate system.
     *
     * @param bx x-coordinate in original bitmap coordinate system
     * @param by y-coordinate in original bitmap coordinate system
     * @return Coordinates of the point in the view's coordinate system.
     */
    private fun transformCoordBitmapToTouch(bx: Float, by: Float): PointF {
        _matrix.getValues(m)
        val origW = drawable.intrinsicWidth
        val origH = drawable.intrinsicHeight
        val px = bx / origW
        val py = by / origH
        val finalX = m[Matrix.MTRANS_X] + imageWidth * px
        val finalY = m[Matrix.MTRANS_Y] + imageHeight * py
        return PointF(finalX, finalY)
    }

    /**
     * Fling launches sequential runnables which apply
     * the fling graphic to the image. The values for the translation
     * are interpolated by the Scroller.
     *
     * @author Ortiz
     */
    private inner class Fling(velocityX: Float, velocityY: Float) : Runnable {
        var scroller: CompatScroller?
        var currX: Int
        var currY: Int
        init {
            state = State.FLING
            scroller = CompatScroller(/*_context*/ context)
            _matrix.getValues(m)

            val startX = m[Matrix.MTRANS_X].toInt()
            val startY = m[Matrix.MTRANS_Y].toInt()
            val minX: Int
            val maxX: Int
            val minY: Int
            val maxY: Int

            if (imageWidth > viewWidth) {
                minX = viewWidth - imageWidth.toInt()
                maxX = 0
            } else {
                maxX = startX
                minX = maxX
            }

            if (imageHeight > viewHeight) {
                minY = viewHeight - imageHeight.toInt()
                maxY = 0
            } else {
                maxY = startY
                minY = maxY
            }

            scroller!!.fling(startX, startY, velocityX.toInt(), velocityY.toInt(),
                    minX, maxX, minY, maxY)
            currX = startX
            currY = startY
        }

        fun cancelFling() {
            if (scroller != null) {
                state = State.NONE
                scroller!!.forceFinished(true)
            }
        }

        override fun run() {
            // OnTouchImageViewListener is set: TouchImageView listener has been flung by user.
            // Listener runnable updated with each frame of fling animation.
            touchImageViewListener?.onMove()

            if (scroller?.isFinished == true) {
                scroller = null
                return
            }

            if (scroller!!.computeScrollOffset()) {
                val newX = scroller!!.currX
                val newY = scroller!!.currY
                val transX = newX - currX
                val transY = newY - currY
                currX = newX
                currY = newY
                _matrix.postTranslate(transX.toFloat(), transY.toFloat())
                fixTrans()
                imageMatrix = _matrix
                compatPostOnAnimation(this)
            }
        }
    }

    @TargetApi(VERSION_CODES.GINGERBREAD)
    private class CompatScroller(context: Context ) {
        //val scroller: Scroller? = null
        val overScroller: OverScroller = OverScroller(context)
        fun fling(startX: Int, startY: Int, velocityX: Int, velocityY: Int,
                  minX: Int, maxX: Int, minY: Int, maxY: Int) {
            overScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY)
        }
        fun forceFinished(finished: Boolean) {
            overScroller.forceFinished(finished)
        }
        val isFinished get() = overScroller.isFinished

        fun computeScrollOffset(): Boolean {
            overScroller.computeScrollOffset()
            return overScroller.computeScrollOffset()
        }
        val currX get() = overScroller.currX
        val currY get() = overScroller.currY
    }

    @TargetApi(VERSION_CODES.JELLY_BEAN)
    private fun compatPostOnAnimation(runnable: Runnable ) {
        //if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
        postOnAnimation(runnable)
        /*
        } else {
            postDelayed(runnable, 1000 / 60)
        }*/
    }

    private class ZoomVariables(
        val scale: Float,
        val focusX: Float,
        val focusY: Float,
        val scaleType: ScaleType)
}
