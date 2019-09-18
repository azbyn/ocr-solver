package com.azbyn.ocr.capture

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.hardware.camera2.CameraCharacteristics as CC
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureRequest as CR
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.azbyn.ocr.*
import com.azbyn.ocr.Misc.logw
import kotlinx.android.synthetic.main.capture.*
import org.json.JSONObject
import org.opencv.core.Core.*
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs.*
import java.io.File

import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class CaptureFragment : BaseFragment(),
        ActivityCompat.OnRequestPermissionsResultCallback {
    class VM: DumbViewModel() {
        var mat = Mat()
            private set
        var timestamp = ""
            private set
        private val formater = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.GERMANY)

        fun getTimeStampNow() = formater.format(Calendar.getInstance().time)
        fun setTimeStampNow() {
            timestamp = getTimeStampNow()
        }

        fun initFromImage(img: Image, rotation: Int) {
            setTimeStampNow()

            // this is very fast but it's not true grayscale (1/3 R, 1/3 G, 1/3 B)
            // but is (0.299 R, 0.578 G, 0.114 B)
            // for our purposes (taking pictures of paper that's already almost grayscale)
            // this is useful since it makes blue (most common pen color) darker
            val buffer = img.planes[0].buffer
            val data = ByteArray(buffer.capacity())
            buffer.get(data)
            mat = Mat(img.height, img.width, IMREAD_GRAYSCALE) //data)

            mat.put(0,0, data)
            /*
            mat = Yuv.rgb(img)
            cvtColor(mat, mat, COLOR_BGR2GRAY)
            */

            img.close()
            when (rotation) {
                90 -> rotate(mat, mat, ROTATE_90_CLOCKWISE)
                180 -> rotate(mat, mat, ROTATE_180)
                270 -> rotate(mat, mat, ROTATE_90_COUNTERCLOCKWISE)
            }


            //((ByteBuffer) frame.duplicate().clear()).get(data);
            /*val size = bitmap.rowBytes * bitmap.height
            val byteBuffer = ByteBuffer.allocate(size)
            logd("size = $size")
            bitmap.copyPixelsToBuffer(byteBuffer)
            val byteArray = byteBuffer.array()
            val mat = Mat(w, h, CvType.CV_8UC4)
            mat.put(0, 0, byteArray)
            */
        }
        fun initFromPath(frag: BaseFragment, path: String) {
            val f = File(path)
            if (!f.exists()) {
                frag.loge("File '$path' not found")
                return
            }
            timestamp = formater.format(Date(f.lastModified()))
            mat = imread(path, IMREAD_GRAYSCALE)
            //bitmap = BitmapFactory.decodeFile(mainActivity.lastFilePath)
        }
        fun reset() {
            mat = Mat()//?
        }
    }

    override fun onOK() {
        getViewModel<AcceptFragment.VM>().clearHistory()
        super.onOK()
    }

    override val nextFragment = FragmentIndex.ACCEPT
    override fun saveData(path: String): JSONObject? = null

    private val viewModel: VM by viewModelDelegate()

    @Suppress("UNUSED_PARAMETER")
    private fun logd(s: String) = Unit// Misc.logd(s)
    private companion object {
        const val REQUEST_PERMISSIONS = 1
        val PERMISSIONS = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)

        //private const val DIALOG = "dialog"

        private const val IMAGE_FORMAT = ImageFormat.YUV_420_888

        // Max preview size that is guaranteed by Camera2 API
        private const val MAX_PREVIEW_WIDTH = 1920
        private const val MAX_PREVIEW_HEIGHT = 1080

        //Timeout for the pre-capture sequence.
        private const val PRECAPTURE_TIMEOUT_MS: Long = 1000

        //Tolerance when comparing aspect ratios.
        private const val ASPECT_RATIO_TOLERANCE = 0.005
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
     * An [AutoFitTextureView] for camera preview.
     */
    private lateinit var textureView: AutoFitTextureView
    /**
     * An additional thread for running tasks that shouldn't block the UI. This is used for all
     * callbacks from the [CameraDevice] and [CameraCaptureSession]s.
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)
    /**
     * A lock protecting camera state.
     */
    private val cameraStateLock = Any()

    /**
    // State protected by [cameraStateLock].
    //
    // The following state is used across both the UI and background threads. Methods with "Locked"
    // in the name expect cameraStateLock to be held while calling.
     */

    /**
     * ID of the current [CameraDevice].
     */
    private lateinit var cameraId: String

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
    // private lateinit var previewSize: Size

    /**
     * The [CameraCharacteristics] for the currently configured camera device.
     */
    private lateinit var characteristics: CameraCharacteristics

    /**
     * A [Handler] for running tasks in the background.
     */
    private var backgroundHandler: Handler? = null

    /**
     * An [ImageReader] that handles still image capture.
     * */
    private var imageReader: ImageReader? = null


    /**
     * Whether or not the currently configured camera device is fixed-focus.
     */
    private var noAFRun = false

    // true if a capture is pending (natürlich) (a bool because we only care about one capture)
    private var pendingUserCapture = false

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    /**
     * The current state of camera state for taking pictures.
     *
     * @see .captureCallback
     */
    private var state = State.CLOSED

    /**
     * Timer to use with pre-capture sequence to ensure a timely capture if 3A convergence is
     * taking too long.
     */
    private var captureTimer: Long = 0

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
                this@CaptureFragment.cameraDevice = cameraDevice

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
                this@CaptureFragment.cameraDevice = null
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
                this@CaptureFragment.cameraDevice = null
            }
            mainActivity.finish()

            //onDisconnected(cameraDevice)
            //this@CaptureFragment.mainActivity.finish()
        }
    }

    private var toast: Toast? = null
    private var time: Long = 0
    /**
     * This a callback object for the [ImageReader]. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener {
        logd("onImageAvailableListener $mainActivity")
        tryOrComplain {
            val timeBegin = currentTimeMillis()
            viewModel.initFromImage(it.acquireNextImage(), orientation)
            logd("orientation: $orientation")
            toast?.cancel()
            val now = currentTimeMillis()
            val dt = (now - time) / 1e3f
            val beginDt = (now - timeBegin) / 1e3f

            logd("time $dt (in listener $beginDt)")
            showToast("Done $dt")
            mainActivity.runOnUiThread { onOK() }
        }
        /*
        backgroundHandler?.post(ImageSaver(image/*it.acquireNextImage()*/, file))
        logd("SAVED FILE ${Environment.getExternalStorageDirectory()}/img.jpg")
        */
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
                var readyToCapture = if (!noAFRun) {
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
                if (!isLegacyLocked) {
                    val aeState = result[CaptureResult.CONTROL_AE_STATE] ?: return
                    val awbState = result[CaptureResult.CONTROL_AWB_STATE] ?: return

                    readyToCapture = readyToCapture &&
                            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED &&
                            awbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED
                }

                // If we haven't finished the pre-capture sequence but have hit our maximum
                // wait timeout, too bad! Begin capture anyway.
                if (!readyToCapture && hitTimeoutLocked()) {
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

    private var flashEnabled = true
    private var canUseAE = false
    private var canUseAF = false
    private var canUseAWB = false
    private var canUseFlash = false
    private fun setFlashAndAE(builder: CaptureRequest.Builder) {
        //logd("update flash: $flashEnabled")
        // if there is an auto-magical flash control mode available, use it, otherwise default to
        // the "on" mode, which is guaranteed to always be available.

        builder[CR.CONTROL_AE_MODE] = if (canUseAE && flashEnabled) CR.CONTROL_AE_MODE_ON_AUTO_FLASH
        else CR.CONTROL_AE_MODE_ON
        //logd("canuseFlash $canUseFlash")
        if (canUseFlash) {
            builder[CR.FLASH_MODE] = if (flashEnabled) CameraMetadata.FLASH_MODE_SINGLE
            else CameraMetadata.FLASH_MODE_OFF
        }
        //logd("FLASH_MODE ${builder[CR.FLASH_MODE] == CameraMetadata.FLASH_MODE_SINGLE}")
        //logd("AE_MODE ${builder[CR.CONTROL_AE_MODE] == CameraMetadata.CONTROL_AE_MODE_ON}")
        /*
        else CameraMetadata.FLASH_MODE_OFF
        if (flashSupported) {
            if (flashEnabled) {
                requestBuilder[CaptureRequest.CONTROL_AE_MODE] =
                //CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH
                //requestBuilder[CaptureRequest.CONTROL_AE_MODE] =
                //CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH

                ///requestBuilder[CaptureRequest.CONTROL_AE_MODE] =
                //CameraMetadata.CONTROL_AE_MODE_ON
                //requestBuilder[CaptureRequest.FLASH_MODE] = CameraMetadata.FLASH_MODE_SINGLE
            } else {
                requestBuilder[CaptureRequest.CONTROL_AE_MODE] = CameraMetadata.CONTROL_AE_MODE_ON
                requestBuilder[CaptureRequest.FLASH_MODE] = CameraMetadata.FLASH_MODE_OFF
            }
        }
         */
    }

    private lateinit var sensorManager : SensorManager
    private var rotSensor: Sensor? = null

    //override fun onBack() = Unit
    override fun initImpl(isOnBack: Boolean) {
        startBackgroundThread()
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
    }

    override fun lightCleanup() {
        orientationListener?.disable()
        closeCamera()
        stopBackgroundThread()
    }

    override fun onResume() {
        super.onResume()
        initImpl(isOnBack=false)
    }

    override fun onPause() {
        lightCleanup()
        super.onPause()
    }

    override fun onCreateView(i: LayoutInflater, container: ViewGroup?, b: Bundle?): View?
            = i.inflate(R.layout.capture, container, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = mainActivity.getSystemService(SENSOR_SERVICE) as SensorManager
        rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //logd("CREATED CaptureFragment")
        picture.setOnClickListener { takePicture() }
        calibrate.setOnClickListener { angleIndicator.calibrate() }
        useSaved.setOnClickListener {
            viewModel.initFromPath(this, mainActivity.lastFilePath)
            onOK()
        }
        flash.setOnClickListener {
            flashEnabled = !flashEnabled
            flash.setImageResource(
                    if (flashEnabled) R.drawable.ic_flash_auto
                    else R.drawable.ic_flash_off)
            setFlashAndAE(previewRequestBuilder)
            logd("change flash")
            captureSession?.setRepeatingRequest(previewRequestBuilder.build(), preCaptureCallback,
                    backgroundHandler)
            //captureSession!!.capture(previewRequestBuilder.build(), preCaptureCallback,
                    //backgroundHandler)
            //prev
            //updateFlash()
        }

        textureView = view.findViewById(R.id.texture)

        rotSensor.also { sensor ->
            sensorManager.registerListener(angleIndicator,
                    sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        orientationListener = object : OrientationEventListener(mainActivity,
                SensorManager.SENSOR_DELAY_NORMAL) {
            //var last = -1
            override fun onOrientationChanged(p0: Int) {
                //if (p0 / 90 == last) return
                //last = p0 / 90
                if (textureView.isAvailable) {
                    //logd("oida, new orientation $p0")
                    // Setup a new OrientationEventListener.  This is used to handle rotation
                    // events like a 180 degree rotation that do not normally trigger a call
                    // to onCreate to do view re-layout or otherwise cause the preview TextureView's
                    // size to change.
                    configureTransform(textureView.width, textureView.height)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //logd("destroyed CaptureFragment")
        sensorManager.unregisterListener(angleIndicator)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS) {
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    loge("didn't get required permissions")
                    return
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    // We want to keep the image as small as possible for better performance
    // while keeping enough details. around 4MP seems like a good option.
    private fun getOptimalReducedSize(choices: Array<Size>): Size {
        val largest = Collections.max(
                listOf(*choices),
                CompareSizesByArea())
        val desiredResolutionArea = 4e6 // 4 MP
        val f = sqrt(desiredResolutionArea / (largest.width * largest.height))
        val w = (f * largest.width).toInt()
        val h = (f * largest.height).toInt()
        //logd("reduced-size $w, $h")
        return chooseOptimalSize(choices, w, h, w, h, largest)
    }
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
                canUseAE = characteristics[CC.CONTROL_AE_AVAILABLE_MODES]?.contains(
                        CC.CONTROL_AE_MODE_ON_AUTO_FLASH) == true
                canUseAF = characteristics[CC.CONTROL_AF_AVAILABLE_MODES]?.contains(
                        CC.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true
                canUseAWB = characteristics[CC.CONTROL_AWB_AVAILABLE_MODES]?.contains(
                        CC.CONTROL_AWB_MODE_AUTO) == true
                canUseFlash = characteristics[CC.FLASH_INFO_AVAILABLE] == true

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
                    imageReader?.setOnImageAvailableListener(
                            onImageAvailableListener, backgroundHandler)

                    this.characteristics = characteristics
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
                backgroundHandler = this.backgroundHandler
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
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        logd("()")
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        synchronized(cameraStateLock) {
            backgroundHandler = Handler(backgroundThread?.looper!!)
        }
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        logd("()")
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            synchronized(cameraStateLock) {
                backgroundHandler = null
            }
        } catch (e: InterruptedException) {
            loge(e)
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
                                setup3AControlsLocked(previewRequestBuilder)
                                // Finally, we start displaying the camera preview.
                                cameraCaptureSession.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        preCaptureCallback, backgroundHandler)
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
                    }, backgroundHandler)
        } catch (e: CameraAccessException) {
            loge(e)
        }

    }
    /**
     * Configure the given [CaptureRequest.Builder] to use auto-focus, auto-exposure, and
     * auto-white-balance controls if available.
     * Call this only with [cameraStateLock] held.
     *
     * @param builder the builder to configure.
     */
    private fun setup3AControlsLocked(builder: CaptureRequest.Builder) {
        //logd("setup3A $flashEnabled")
        // Enable auto-magical 3A run by camera device
        builder[CR.CONTROL_MODE] = CC.CONTROL_MODE_AUTO

        val minFocusDist = characteristics[CC.LENS_INFO_MINIMUM_FOCUS_DISTANCE] ?: 0.0

        // If MINIMUM_FOCUS_DISTANCE is 0, lens is fixed-focus and we need to skip the AF run.
        noAFRun = minFocusDist == 0.0

        if (!noAFRun) {
            // If there is a "continuous picture" mode available, use it, otherwise default to AUTO.
            builder[CR.CONTROL_AF_MODE] =
                    if (canUseAF)
                        CR.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    else CR.CONTROL_AF_MODE_AUTO
        }

        // if there is an auto-magical flash control mode available, use it, otherwise default to
        // the "on" mode, which is guaranteed to always be available.
        setFlashAndAE(builder)

        // If there is an auto-magical white balance control mode available, use it.
        if (canUseAWB) {
            // Allow AWB to run auto-magically if this device supports this
            builder[CR.CONTROL_AWB_MODE] = CR.CONTROL_AWB_MODE_AUTO
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
                    CompareSizesByArea())
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
     * Initiate a still image capture.
     * This function sends a capture request that initiates a pre-capture sequence in our state
     * machine that waits for auto-focus to finish, ending in a "locked" state where the lens is no
     * longer moving, waits for auto-exposure to choose a good exposure value, and waits for
     * auto-white-balance to converge.
     */
    private fun takePicture() {
        logd("takePicture")
        time = currentTimeMillis()
        viewModel.reset()
        toast = Toast.makeText(mainActivity, "Please wait...", Toast.LENGTH_SHORT).also {
            it.show()
        }
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
                if (!noAFRun) {
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
                if (!isLegacyLocked) {
                    // Tell the camera to lock focus.
                    previewRequestBuilder[CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER] =
                            CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START
                }

                // Update state machine to wait for auto-focus, auto-exposure, and
                // auto-white-balance (aka. "3A") to converge.
                state = State.WAITING_FOR_3A_CONVERGENCE

                // Start a timer for the pre-capture sequence.
                startTimerLocked()
                setFlashAndAE(previewRequestBuilder)
                // Replace the existing repeating request with one with updated 3A triggers.
                captureSession?.capture(previewRequestBuilder.build(), preCaptureCallback,
                        backgroundHandler)
            } catch (e: CameraAccessException) {
                loge(e)
            }
            return@synchronized
        }
    }

    private var orientation = 0
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
            setup3AControlsLocked(captureBuilder)
            //updateFlash(captureBuilder)
            logd("captureStill")

            // Set orientation.

            val rotation = mainActivity.windowManager.defaultDisplay.rotation
            orientation = sensorToDeviceRotation(characteristics, rotation)
            // This is pointless as we don't use JPEG
            /*
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    sensorToDeviceRotation(characteristics, rotation))
            */
            // Set request tag to easily track results in callbacks.
            //captureBuilder.setTag(requestCounter.getAndIncrement())
            captureSession?.capture(captureBuilder.build(), captureCallback, backgroundHandler)
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
            if (!noAFRun) {
                previewRequestBuilder[CaptureRequest.CONTROL_AF_TRIGGER] =
                        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL

                captureSession?.capture(previewRequestBuilder.build(), preCaptureCallback,
                        backgroundHandler)

                previewRequestBuilder[CaptureRequest.CONTROL_AF_TRIGGER] =
                        CameraMetadata.CONTROL_AF_TRIGGER_IDLE
            }
        } catch (e: CameraAccessException) {
            loge(e)
        }
    }

    /**
     * Given `choices` of `Size`s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as
     * the respective max size, and whose aspect ratio matches with the specified value. If such
     * size doesn't exist, choose the largest one that is at most as large as the respective max
     * size, and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended
     *                          output class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal `Size`, or an arbitrary one if none were big enough
     */
    private fun chooseOptimalSize(
            choices: Array<Size>,
            textureViewWidth: Int,
            textureViewHeight: Int,
            maxWidth: Int,
            maxHeight: Int,
            aspectRatio: Size): Size {

        // Collect the supported resolutions that are at least as big as the preview Surface
        val bigEnough = ArrayList<Size>()
        // Collect the supported resolutions that are smaller than the preview Surface
        val notBigEnough = ArrayList<Size>()
        val w = aspectRatio.width
        val h = aspectRatio.height
        for (option in choices) {
            if (option.width <= maxWidth && option.height <= maxHeight &&
                    option.height == option.width * h / w) {
                if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        return when {
            bigEnough.size > 0 -> Collections.min(bigEnough, CompareSizesByArea())
            notBigEnough.size > 0 -> Collections.max(notBigEnough, CompareSizesByArea())
            else -> {
                loge("Couldn't find any suitable preview size")
                return choices[0]
            }
        }

    }

    /**
     * Return true if the two given {@link Size}s have the same aspect ratio.
     *
     * @param a first {@link Size} to compare.
     * @param b second {@link Size} to compare.
     * @return true if the sizes have the same aspect ratio, otherwise false.
     */
    private fun checkAspectsEqual(a: Size, b: Size): Boolean {
        val aAspect = a.width.toDouble() / a.height
        val bAspect = b.width.toDouble() / b.height
        return abs(aAspect - bAspect) <= ASPECT_RATIO_TOLERANCE
    }

    /**
     * Rotation need to transform from the camera sensor orientation to the device's current
     * orientation.
     *
     * @param c                 the [CameraCharacteristics] to query for the camera sensor
     *                          orientation.
     * @param deviceOrientation the current device orientation relative to the native device
     *                          orientation.
     * @return the total rotation from the sensor orientation to the current device orientation.
     */
    private fun sensorToDeviceRotation(c: CameraCharacteristics, deviceOrientation: Int): Int {
        val sensorOrientation = c[CameraCharacteristics.SENSOR_ORIENTATION]!!
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        return (sensorOrientation - 90 * deviceOrientation + 360) % 360
    }

    /**
     * Check if we are using a device that only supports the LEGACY hardware level.
     *
     * Call this only with [cameraStateLock] held.
     *
     * @return true if this is a legacy device.
     */
    val isLegacyLocked get() =
        characteristics[CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL] ==
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY

    /**
     * Start the timer for the pre-capture sequence.
     * Call this only with [cameraStateLock] held.
     */
    private fun startTimerLocked() {
        captureTimer = SystemClock.elapsedRealtime()
    }

    /**
     * Check if the timer for the pre-capture sequence has been hit.
     * Call this only with [cameraStateLock] held.
     *
     * @return true if the timeout occurred.
     */
    private fun hitTimeoutLocked() =
            (SystemClock.elapsedRealtime() - captureTimer) > PRECAPTURE_TIMEOUT_MS
}

