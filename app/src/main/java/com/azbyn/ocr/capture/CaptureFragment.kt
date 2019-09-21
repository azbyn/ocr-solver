package com.azbyn.ocr.capture

import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.azbyn.ocr.*
import kotlinx.android.synthetic.main.capture.*
import org.json.JSONObject
import org.opencv.core.Core.*
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs.IMREAD_GRAYSCALE
import org.opencv.imgcodecs.Imgcodecs.imread
import java.io.File
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.*

class CaptureFragment : CaptureFragmentBase() {

    @Suppress("UNUSED_PARAMETER")
    private fun logd(s: String) = Misc.logd(s, offset = 1)

    class VM : DumbViewModel() {
        var mat = Mat()
            private set
        var timestamp = ""
            private set
        private val formater = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", TIME_LOCALE)
        private var lastFile: File? = null

        fun init(mainActivity: MainActivity): Boolean {
            lastFile = null
            val f = File(mainActivity.path, "last.txt")
            //logd("exists ${f.exists()}")
            if (!f.exists()) return false
            val dirPath = f.readText().trim()
            val dirFile = File(mainActivity.path, dirPath)
            //logd("path '$dirFile'")
            //logd("exists2 ${dirFile.exists()}")
            if (!dirFile.exists()) return false
            lastFile = dirFile
            return true
        }

        fun saveLast(mainActivity: MainActivity) {
            lastFile = File("${mainActivity.path}/$timestamp", IMAGE_FILE_NAME)
            logd("Oida, saved $lastFile")
            val f = File(mainActivity.path, "last.txt")
            f.writeText("$timestamp/$IMAGE_FILE_NAME")
        }

        fun getTimeStampNow(): String = formater.format(Calendar.getInstance().time)
        fun setTimeStampNow() {
            timestamp = getTimeStampNow()
        }

        fun fromImage(img: Image, rotation: Int) {
            logd("Oida, fromImage")
            setTimeStampNow()

            // this is very fast but it's not true grayscale (1/3 R, 1/3 G, 1/3 B)
            // but is (0.299 R, 0.578 G, 0.114 B)
            // for our purposes (taking pictures of paper that's already almost grayscale)
            // this is useful since it makes blue (most common pen color) darker
            val buffer = img.planes[0].buffer
            val data = ByteArray(buffer.capacity())
            buffer.get(data)
            mat = Mat(img.height, img.width, IMREAD_GRAYSCALE) //data)

            mat.put(0, 0, data)
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
        }

        fun fromPath(frag: BaseFragment): Boolean {
            logd("Oida, fromPath $lastFile")
            lastFile ?: return false
            if (!lastFile!!.exists()) {
                frag.loge("File '${lastFile!!.path}' not found")
                return false
            }
            timestamp = formater.format(Date(lastFile!!.lastModified()))
            mat = imread(lastFile!!.path, IMREAD_GRAYSCALE)
            return true
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

    private val viewModel: VM by viewModelDelegate()

    private lateinit var sensorManager : SensorManager
    private var rotSensor: Sensor? = null

    private var toast: Toast? = null
    private var time: Long = 0

    override fun saveData(path: String): JSONObject? = null


    override fun onCreateView(i: LayoutInflater, container: ViewGroup?, b: Bundle?): View?
            = i.inflate(R.layout.capture, container, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = mainActivity.getSystemService(SENSOR_SERVICE) as SensorManager
        rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    override fun onImageAvailable(it: ImageReader) {
        logd("onImageAvailable $mainActivity")
        tryOrComplain {
            val timeBegin = currentTimeMillis()
            viewModel.fromImage(it.acquireNextImage(), orientation)
            logd("orientation: $orientation")
            toast?.cancel()
            val now = currentTimeMillis()
            val dt = (now - time) / 1e3f
            val beginDt = (now - timeBegin) / 1e3f

            logd("time $dt (in listener $beginDt)")
            showToast("Done $dt")
            mainActivity.runOnUiThread { onOK() }
        }
    }

    private fun setUseSavedColor(value: Boolean) {
        fun impl(id: Int) {
            val color = mainActivity.getColor(id)
            val colorTint = ContextCompat.getColorStateList(mainActivity, id)
            useSaved.backgroundTintList = colorTint
            useSaved.setTextColor(color)
        }
        impl(if (value) R.color.default_ else R.color.grayedOut)
    }

    //override fun onBack() = Unit
    override fun initImpl(isOnBack: Boolean) {
        super.initImpl(isOnBack)
        val res = viewModel.init(mainActivity)
        setUseSavedColor(res)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //logd("CREATED CaptureFragment")
        picture.setOnClickListener { takePicture() }
        calibrate.setOnClickListener { angleIndicator.calibrate() }
        useSaved.setOnClickListener {
            if (!viewModel.fromPath(this)) {
                setUseSavedColor(false)
            } else {
                onOK()
            }
        }
        flash.setOnClickListener {
            val enabled = toggleFlash()
            flash.setImageResource(
                    if (enabled) R.drawable.ic_flash_auto
                    else R.drawable.ic_flash_off)
        }

        rotSensor.also { sensor ->
            sensorManager.registerListener(angleIndicator,
                    sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //logd("destroyed CaptureFragment")
        sensorManager.unregisterListener(angleIndicator)
    }

    override fun takePicture() {
        time = currentTimeMillis()
        viewModel.reset()
        toast = Toast.makeText(mainActivity, "Please wait...", Toast.LENGTH_SHORT).also {
            it.show()
        }
        super.takePicture()
    }
}
