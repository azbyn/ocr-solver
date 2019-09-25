package com.azbyn.ocr.capture


import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.hardware.camera2.CameraCharacteristics as CC
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.annotation.CallSuper
import androidx.core.app.ActivityCompat
import com.azbyn.ocr.*
import com.azbyn.ocr.Misc.logd
import com.azbyn.ocr.Misc.logw
import com.azbyn.ocr.capture.CameraUtils.checkAspectsEqual
import com.azbyn.ocr.capture.CameraUtils.chooseOptimalSize
import com.azbyn.ocr.capture.CameraUtils.getOptimalReducedSize
import com.azbyn.ocr.capture.CameraUtils.sensorToDeviceRotation
import kotlinx.android.synthetic.main.capture.*
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.max

abstract class CaptureFragmentBase:
        BaseFragment(),
        ImageReader.OnImageAvailableListener,
        ActivityCompat.OnRequestPermissionsResultCallback {
    @Suppress("UNUSED_PARAMETER")
    private fun logd(s: String) = Unit//logd(s, offset=1)

    internal companion object {
        const val REQUEST_PERMISSIONS = 1
        val PERMISSIONS = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)

        //private const val DIALOG = "dialog"

        const val IMAGE_FORMAT = ImageFormat.YUV_420_888

        // Max preview size that is guaranteed by Camera2 API
        const val MAX_PREVIEW_WIDTH = 1920
        const val MAX_PREVIEW_HEIGHT = 1080

        //Timeout for the pre-capture sequence.
        const val PRECAPTURE_TIMEOUT_MS: Long = 1000

        //Tolerance when comparing aspect ratios.
        const val ASPECT_RATIO_TOLERANCE = 0.005
    }
    enum class State {
        CLOSED, OPENED, PREVIEW, WAITING_FOR_3A_CONVERGENCE
    }

    /**
     * An [OrientationEventListener] used to determine when device rotation has occurred.
     * This is mainly necessary for when the device is rotated by 180 degrees, in which case
     * onCreate or onConfigurationChanged is not called as the view dimensions remain the same,
     * but the orientation of the has changed, and thus the preview rotation must be updated.
     */
    private var orientationListener: OrientationEventListener? = null

    /**
     * A [CameraCaptureSession] for camera preview.
     */
    private var captureSession: CameraCaptureSession? = null

    /**
     * A reference to the opened [CameraDevice].
     */
    private var cameraDevice: CameraDevice? = null

    /**
     * The [android.util.Size] of camera preview.
     */
    private var previewSize: Size? = null

    private lateinit var textureView: CaptureTextureView
    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) =
                configureTransform(width, height)

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) =
                configureTransform(width, height)

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            synchronized (cameraStateLock) {
                previewSize = null
            }
            return true
        }
        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    }


    /**
     * An [ImageReader] that handles still image capture.
     * */
    private var imageReader: ImageReader? = null


    // true if a capture is pending (natürlich) (a bool because we only care about one capture)
    private var pendingUserCapture = false

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)

    /**
     * A lock protecting camera state.
     */
    private val cameraStateLock = Any()

    //callbacks und stuff
    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */
    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            logd("()")
            // This method is called when the camera is opened.  We start camera preview here if
            // the TextureView displaying this has been set up.
            synchronized (cameraStateLock) {
                state = State.OPENED
                cameraOpenCloseLock.release()
                this@CaptureFragmentBase.cameraDevice = cameraDevice

                // Start the preview session if the TextureView has been set up already.
                if (previewSize != null && textureView.isAvailable) {
                    createCameraPreviewSessionLocked()
                }
            }
            //cameraOpenCloseLock.release()
            //this@CaptureFragment.cameraDevice = cameraDevice
            //createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            logd("()")
            synchronized (cameraStateLock) {
                state = State.CLOSED
                cameraOpenCloseLock.release()
                cameraDevice.close()
                this@CaptureFragmentBase.cameraDevice = null
            }

            /*
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@CaptureFragment.cameraDevice = null

             */
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            loge("Received camera device error: $error")
            synchronized (cameraStateLock) {
                state = State.CLOSED
                cameraOpenCloseLock.release()
                cameraDevice.close()
                this@CaptureFragmentBase.cameraDevice = null
            }
            mainActivity.finish()

            //onDisconnected(cameraDevice)
            //this@CaptureFragment.mainActivity.finish()
        }
    }

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events for the preview and
     * pre-capture sequence.
     */
    private val preCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        fun process(result: CaptureResult) {
            synchronized(cameraStateLock) {
                if (state != State.WAITING_FOR_3A_CONVERGENCE) {
                    return
                }
                //logd("precapture $flashEnabled")
                var readyToCapture = if (!camera3AHandler.noAFRun) {
                    val afState = result[CaptureResult.CONTROL_AF_STATE] ?: return
                    // If auto-focus has reached locked state, we are ready to capture
                    (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED)
                } else {
                    true
                }

                // If we are running on an non-legacy device, we should also wait until
                // auto-exposure and auto-white-balance have converged as well before
                // taking a picture.
                if (!camera3AHandler.isLegacyLocked) {
                    val aeState = result[CaptureResult.CONTROL_AE_STATE] ?: return
                    val awbState = result[CaptureResult.CONTROL_AWB_STATE] ?: return

                    readyToCapture = readyToCapture &&
                            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED &&
                            awbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED
                }

                // If we haven't finished the pre-capture sequence but have hit our maximum
                // wait timeout, too bad! Begin capture anyway.
                if (!readyToCapture && captureTimer.hitTimeoutLocked()) {
                    logw("Timed out waiting for pre-capture sequence to complete.")
                    readyToCapture = true
                }

                if (readyToCapture && pendingUserCapture) {
                    captureStillPictureLocked()
                    //logd("captured?")
                    // TODO CLOSE?
                    // After this, the camera will go back to the normal state of preview.
                    state = State.PREVIEW
                }
            }
        }

        override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest,
                                         partialResult: CaptureResult) = process(partialResult)

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest,
                                        result: TotalCaptureResult) = process(result)
    }

    /**
    // State protected by [cameraStateLock].
    //
    // The following state is used across both the UI and background threads. Methods with "Locked"
    // in the name expect cameraStateLock to be held while calling.
     */
    /**
     * The current state of camera state for taking pictures.
     *
     * @see [captureCallback]
     */
    private var state = State.CLOSED

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles the still capture request.
     */
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest,
                                      timestamp: Long, frameNumber: Long) = Unit

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest,
                                        result: TotalCaptureResult) = Unit

        override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest,
                                     failure: CaptureFailure) {
            synchronized (cameraStateLock) {
                finishedCaptureLocked()
            }
            loge("Capture failed!")
        }
    }


    // BaseFragment functions:

    @CallSuper
    override fun initImpl(isOnBack: Boolean) = init()

    @CallSuper
    open fun init() {
        synchronized(cameraStateLock) {
            backgroundHandler = BackgroundHandler()
        }
        //startBackgroundThread()
        openCamera()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we should
        // configure the preview bounds here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable) {
            configureTransform(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
        if (orientationListener?.canDetectOrientation()==true) {
            orientationListener?.enable()
        }
        setupFlash()
    }

    final override fun lightCleanup() {
        orientationListener?.disable()
        closeCamera()
        tryOrComplain {
            backgroundHandler?.stop()
            synchronized(cameraStateLock) {
                backgroundHandler = null
            }
        }
    }
    final override fun onResume() {
        super.onResume()
        logd("()")
        init()
    }

    final override fun onPause() {
        lightCleanup()
        super.onPause()
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //logd("CREATED CaptureFragment")
        textureView = view.findViewById(R.id.texture)

        orientationListener = object : OrientationEventListener(mainActivity,
                SensorManager.SENSOR_DELAY_NORMAL) {
            val THRESH = 10
            val views = arrayOf(picture, calibrate, useSaved, flash)

            var rotation = 0

            override fun onOrientationChanged(orientation: Int) {
                rotation = when {
                    rotation != 0 && (orientation < THRESH || orientation > 360 - THRESH) -> 0
                    rotation != 180 && (orientation in 180-THRESH..180+THRESH) -> 180
                    rotation != 90 && (orientation in 270-THRESH..270+THRESH) -> 90
                    rotation != 270 && (orientation in 90-THRESH..90+THRESH) -> 270
                    else -> return
                }
                //rotation = r
                val r = rotation.toFloat()
                for (v in views) {
                    v.rotation = r
                }
                this@CaptureFragmentBase.deviceOrientation = rotation
                //orientation = rotation + 90
            //var last = -1
                //if (p0 / 90 == last) return
                //last = p0 / 90
                /*if (textureView.isAvailable) {
                    // Setup a new OrientationEventListener.  This is used to handle rotation
                    // events like a 180 degree rotation that do not normally trigger a call
                    // to onCreate to do view re-layout or otherwise cause the preview TextureView's
                    // size to change.
                    configureTransform(textureView.width, textureView.height)
                }*/
            }
        }
    }


    /**
     * Timer to use with pre-capture sequence to ensure a timely capture if 3A convergence is
     * taking too long.
     */
    private var captureTimer = CaptureTimer()
    private lateinit var camera3AHandler: Camera3AHandler
    private val characteristics get() = camera3AHandler.characteristics

    //protected functions:
    var flashEnabled get() = camera3AHandler.flashEnabled
        set(v) { camera3AHandler.flashEnabled = v }

    protected fun toggleFlash(): Boolean {
        val res = camera3AHandler.toggleFlash()
        camera3AHandler.setFlashAndAE(previewRequestBuilder)
        logd("change flash")
        captureSession?.setRepeatingRequest(previewRequestBuilder.build(), preCaptureCallback,
                backgroundHandler?.handler)
        return res
    }
    private fun setupFlash() {
        if (camera3AHandler.flashEnabled) return
        logd("setup")
        camera3AHandler.setFlashAndAE(previewRequestBuilder)
        captureSession?.setRepeatingRequest(previewRequestBuilder.build(), preCaptureCallback,
                backgroundHandler?.handler)
    }

    //private var _orientation = 0
    private var deviceOrientation = 0

    protected var orientation = 0
        private set
    /*: Int get() {
        //val r = if (_sensorOrientation == 270) 180
        //    else if (_sensorOrientation == 180) 270
        //    else _orientation - _sensorOrientation
        //logd("o: $_orientation, r: $_sensorOrientation -> $r")
        return (_orientation - _sensorOrientation + 360) % 360
    } /*_orientation - 90 - _sensorOrientation*/*/

    /**
     * Initiate a still image capture.
     * This function sends a capture request that initiates a pre-capture sequence in our state
     * machine that waits for auto-focus to finish, ending in a "locked" state where the lens is no
     * longer moving, waits for auto-exposure to choose a good exposure value, and waits for
     * auto-white-balance to converge.
     */
    @CallSuper
    protected open fun takePicture() {
        logd("takePicture")
        synchronized (cameraStateLock) {
            pendingUserCapture = true

            // If we already triggered a pre-capture sequence, or are in a state where we cannot
            // do this, return immediately.
            if (state != State.PREVIEW) {
                return
            }

            try {
                // Trigger an auto-focus run if camera is capable. If the camera is already focused,
                // this should do nothing.
                if (!camera3AHandler.noAFRun) {
                    previewRequestBuilder[CaptureRequest.CONTROL_AF_TRIGGER] =
                            CameraMetadata.CONTROL_AF_TRIGGER_START
                }
                /*
                if (!flashEnabled) {
                    previewRequestBuilder[]
                }
                 */

                // If this is not a legacy device, we can also trigger an auto-exposure metering
                // run.
                if (!camera3AHandler.isLegacyLocked) {
                    // Tell the camera to lock focus.
                    previewRequestBuilder[CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER] =
                            CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START
                }

                // Update state machine to wait for auto-focus, auto-exposure, and
                // auto-white-balance (aka. "3A") to converge.
                state = State.WAITING_FOR_3A_CONVERGENCE

                // Start a timer for the pre-capture sequence.
                captureTimer = CaptureTimer.now()
                camera3AHandler.setFlashAndAE(previewRequestBuilder)
                // Replace the existing repeating request with one with updated 3A triggers.
                captureSession?.capture(previewRequestBuilder.build(), preCaptureCallback,
                        backgroundHandler?.handler)
            } catch (e: CameraAccessException) {
                loge(e)
            }
            return@synchronized
        }
    }

    // permissions:
    final override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS) {
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    loge("didn't get the required permissions")
                    return
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun hasAllPermissionsGranted(): Boolean {
        for (p in PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(mainActivity, p) !=
                    PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requestCameraPermissions() {
        fun shouldShowRationale(): Boolean {
            for (p in PERMISSIONS) {
                if (shouldShowRequestPermissionRationale(p)) return true
            }
            return false
        }

        if (shouldShowRationale()) {
            AlertDialog.Builder(mainActivity)
                    .setMessage(R.string.request_camera_permission)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        //ctx?.finish()
                    }
                    .create().show()
            //PermissionConfirmationDialog.newInstance().show(childFragmentManager, FRAGMENT_DIALOG)
        } else {
            requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS)
        }
    }

    // Camera functions:

    /**
     * Sets up state related to camera that is needed before opening a [CameraDevice].
     */
    private fun setUpCameraOutputs(): Boolean {
        logd("()")
        val manager = mainActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
        if (manager == null) {
            //ErrorDialog.buildErrorDialog("This device doesn't support Camera2 API.").
            //        show(fragmentManager, DIALOG)
            loge("This device doesn't support Camera2 API.")
            return false
        }

        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                // We don't use a front facing camera.
                val cameraDirection = characteristics[CameraCharacteristics.LENS_FACING]
                if (cameraDirection != null &&
                        cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }
                val map = characteristics[CC.SCALER_STREAM_CONFIGURATION_MAP] ?: continue
                val outputSizes = map.getOutputSizes(IMAGE_FORMAT)
                camera3AHandler = Camera3AHandler(characteristics)

                logd("set all the stuff")
                // For still image captures, we use the closest to our optimal size
                val sz = getOptimalReducedSize(outputSizes)
                //logd("sizes: ${listOf(*outputSizes)}")
                //logd("sz ${sz.width}, ${sz.height}")

                synchronized (cameraStateLock) {
                    if (imageReader == null) {
                        imageReader = ImageReader.newInstance(sz.width, sz.height,
                                IMAGE_FORMAT, 1/*5*/)
                    }
                    imageReader?.setOnImageAvailableListener(this, backgroundHandler?.handler)
                    this.cameraId = cameraId
                }
                return true
            }
        } catch (e: CameraAccessException) {
            loge(e)
        }

        loge("This device doesn't seem to have any back cameras")
        return false
    }

    @SuppressWarnings("MissingPermission")
    private fun openCamera() {
        logd("()")
        if (!setUpCameraOutputs()) {
            return
        }
        if (!hasAllPermissionsGranted()) {
            requestCameraPermissions()
            return
        }
        val manager = mainActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // Wait for any previously running session to finish.
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                loge(RuntimeException("Time out waiting to lock camera opening."))
                return
            }

            lateinit var cameraId: String
            var backgroundHandler: Handler?
            synchronized (cameraStateLock) {
                cameraId = this.cameraId
                backgroundHandler = this.backgroundHandler?.handler
            }

            // Attempt to open the camera. mStateCallback will be called on the background handler's
            // thread when this succeeds or fails.
            manager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            loge(e)
        } catch (e: InterruptedException) {
            loge(RuntimeException("Interrupted while trying to lock camera opening.", e))
        }
    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        logd("()")
        try {
            cameraOpenCloseLock.acquire()
            synchronized (cameraStateLock) {
                // Reset state and clean up resources used by the camera.
                // Note: After calling this, the ImageReaders will be closed after any background
                // tasks saving Images from these readers have been completed.
                pendingUserCapture = false
                state = State.CLOSED
                captureSession?.close()
                captureSession = null
                cameraDevice?.close()
                cameraDevice = null
                imageReader?.close()
                imageReader = null
            }
        } catch (e: InterruptedException) {
            loge(RuntimeException("Interrupted while trying to lock camera closing.", e))
        } finally {
            cameraOpenCloseLock.release()
        }
    }


    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSessionLocked() {
        logd("()")
        try {
            val texture = textureView.surfaceTexture
            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice?.createCaptureSession(listOf(surface, imageReader?.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            // The camera is already closed
                            if (cameraDevice == null) return

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession
                            try {
                                camera3AHandler.setup3AControlsLocked(previewRequestBuilder)
                                // Finally, we start displaying the camera preview.
                                cameraCaptureSession.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        preCaptureCallback, backgroundHandler?.handler)
                                state = State.PREVIEW
                            } catch (e: Throwable) {
                                loge(e)
                                return
                            }
                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            showToast("Failed to configure camera.")
                        }
                    }, backgroundHandler?.handler)
        } catch (e: CameraAccessException) {
            loge(e)
        }

    }


    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        synchronized (cameraStateLock) {
            //textureView ?: return
            activity ?: return

            val map = characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]!!

            // Find the rotation of the device relative to the native device orientation.
            val deviceRotation = mainActivity.windowManager.defaultDisplay.rotation
            val displaySize = Point()
            mainActivity.windowManager.defaultDisplay.getSize(displaySize)

            // Find the rotation of the device relative to the camera sensor's orientation.
            val totalRotation = sensorToDeviceRotation(characteristics, deviceRotation)
            // Swap the view dimensions for calculation as needed if they are rotated relative to
            // the sensor.
            val swappedDimensions = totalRotation == 90 || totalRotation == 270

            val rotatedViewWidth = if (swappedDimensions) viewHeight else viewWidth
            val rotatedViewHeight = if (swappedDimensions) viewWidth else viewHeight
            var maxPreviewWidth = if (swappedDimensions) displaySize.y else displaySize.x
            var maxPreviewHeight = if (swappedDimensions) displaySize.x else displaySize.y

            // Preview should not be larger than display size and 1080p.
            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT

            // Find the best preview size for these view dimensions and configured JPEG size.
            val largest = Collections.max(map.getOutputSizes(IMAGE_FORMAT).toList(),
                    CameraUtils.CompareSizesByArea())
            val previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                    rotatedViewWidth, rotatedViewHeight,
                    maxPreviewWidth, maxPreviewHeight,
                    largest)

            if (swappedDimensions) {
                textureView.setAspectRatio(previewSize.height, previewSize.width)
            } else {
                textureView.setAspectRatio(previewSize.width, previewSize.height)
            }

            //val rotation = (ORIENTATIONS[deviceRotation]) //% 360

            val matrix = Matrix()
            val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
            val bufferRect = RectF(0f, 0f,
                    previewSize.height.toFloat(), previewSize.width.toFloat())
            val centerX = viewRect.centerX()
            val centerY = viewRect.centerY()
            // Initially, output stream images from the Camera2 API will be rotated to the native
            // device orientation from the sensor's orientation, and the TextureView will default to
            // scaling these buffers to fill it's view bounds.  If the aspect ratios and relative
            // orientations are correct, this is fine.
            //
            // However, if the device orientation has been rotated relative to its native
            // orientation so that the TextureView's dimensions are swapped relative to the
            // native device orientation, we must do the following to ensure the output stream
            // images are not incorrectly scaled by the TextureView:
            //   - Undo the scale-to-fill from the output buffer's dimensions (i.e. its dimensions
            //     in the native device orientation) to the TextureView's dimension.
            //   - Apply a scale-to-fill from the output buffer's rotated dimensions
            //     (i.e. its dimensions in the current device orientation) to the TextureView's
            //     dimensions.
            //   - Apply the rotation from the native device orientation to the current device
            //     rotation.
            when (deviceRotation) {
                Surface.ROTATION_90, Surface.ROTATION_270 -> {
                    bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
                    val scale = max(viewHeight.toFloat() / previewSize.height,
                            viewWidth.toFloat() / previewSize.width)
                    with(matrix) {
                        setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                        postScale(scale, scale, centerX, centerY)
                        // ROTATION_90 = 1 -> -90 degrees
                        // ROTATION_270 = 3 -> 90 degrees
                        postRotate((90f * (deviceRotation - 2)), centerX, centerY)
                        //postRotate(rotation.toFloat(), centerX, centerY)
                    }
                }
                Surface.ROTATION_180 -> {
                    matrix.postRotate(180f, centerX, centerY)
                }
            }
            textureView.setTransform(matrix)

            // Start or restart the active capture session if the preview was initialized or
            // if its aspect ratio changed significantly.
            if (this.previewSize == null || !checkAspectsEqual(previewSize, this.previewSize!!)) {
                this.previewSize = previewSize
                if (state != State.CLOSED) {
                    createCameraPreviewSessionLocked()
                }
            }
        }
    }


    /**
     * Send a capture request to the camera device that initiates a capture
     * Call this only with [cameraStateLock] held.
     */
    private fun captureStillPictureLocked() {
        logd("()")
        try {
            /*
            captureSession?.apply {
                stopRepeating()

                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                    abortCaptures()
                }
                capture(captureBuilder?.build()!!, captureCallback, null)
            }
            * */
            cameraDevice?: return

            // This is the CaptureRequest.Builder that we use to take a picture.
            val captureBuilder = cameraDevice!!.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader?.surface!!)
            // Use the same AE and AF modes as the preview.
            camera3AHandler.setup3AControlsLocked(captureBuilder)
            //updateFlash(captureBuilder)
            logd("captureStill")

            // Set orientation.

            val rotation = mainActivity.windowManager.defaultDisplay.rotation
            logd("rot $rotation")
            orientation = sensorToDeviceRotation(characteristics, deviceOrientation/*rotation*/)

            // This is pointless as we don't use JPEG
            /*
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    sensorToDeviceRotation(characteristics, rotation))
            */
            // Set request tag to easily track results in callbacks.
            //captureBuilder.setTag(requestCounter.getAndIncrement())
            captureSession?.capture(captureBuilder.build(), captureCallback, backgroundHandler?.handler)
        } catch (e: CameraAccessException) {
            loge(e)
        }
    }

    /**
     * Called after a capture has completed; resets the AF trigger state for the
     * pre-capture sequence.
     * Call this only with [cameraStateLock] held.
     */
    private fun finishedCaptureLocked() {
        logd("()")
        try {
            // Reset the auto-focus trigger in case AF didn't run quickly enough.
            if (!camera3AHandler.noAFRun) {
                previewRequestBuilder[CaptureRequest.CONTROL_AF_TRIGGER] =
                        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL

                captureSession?.capture(previewRequestBuilder.build(), preCaptureCallback,
                        backgroundHandler?.handler)

                previewRequestBuilder[CaptureRequest.CONTROL_AF_TRIGGER] =
                        CameraMetadata.CONTROL_AF_TRIGGER_IDLE
            }
        } catch (e: CameraAccessException) {
            loge(e)
        }
    }

    /**
     * ID of the current [CameraDevice].
     */
    private lateinit var cameraId: String

    private var backgroundHandler: BackgroundHandler? = null

    internal class BackgroundHandler {
        /**
         * An additional thread for running tasks that shouldn't block the UI. This is used for all
         * callbacks from the [CameraDevice] and [CameraCaptureSession]s.
         */
        val thread = HandlerThread("CameraBackground").also { it.start() }
        /**
         * A [Handler] for running tasks in the background.
         */
        val handler: Handler = Handler(thread.looper!!)

        /**
         * Stops the background thread and its [Handler].
         */
        fun stop() {
            logd("()")
            thread.quitSafely()
            thread.join()
        }
    }
}