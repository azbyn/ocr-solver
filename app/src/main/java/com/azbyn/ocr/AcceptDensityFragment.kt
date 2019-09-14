package com.azbyn.ocr

import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.azbyn.ocr.Misc.logd
import com.azbyn.ocr.roi.EditLinesFragment
import com.azbyn.ocr.roi.SelectDensityFragment
import kotlinx.android.synthetic.main.accept_density.*
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgcodecs.Imgcodecs.imwrite
import org.opencv.imgproc.Imgproc.getRotationMatrix2D
import org.opencv.imgproc.Imgproc.warpAffine

class AcceptDensityFragment : ImageViewFragment() {
    companion object {
        private const val ROTATE_DEFAULT = true
    }

    override fun getImageView(): ImageView = imageView

    private val viewModel: VM by viewModelDelegate()
    private val path get() = mainActivity.path

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.accept_density, container, false)

    override fun initImpl(isOnBack: Boolean) {
        imageView.resetZoom()
        viewModel.init(this)
        if (isOnBack) {
            autoRotate.isChecked = viewModel.lastRotated
        } else {
            autoRotate.isChecked = ROTATE_DEFAULT
        }
        viewModel.update(this, autoRotate.isChecked)

        overlay.runWhenInitialized {
            val matWidth = viewModel.resultMat.width()
            val matHeight = viewModel.resultMat.height()
            overlay.init(matWidth, matHeight, DESIRED_DENSITY, imageView)
        }
        densityText.text = mainActivity.getString(R.string.accept_density_text,
                viewModel.resultMat.width(), viewModel.resultMat.height(), viewModel.angle)
    }
    override fun lightCleanup() = overlay.cleanup()

    override fun fastForward() {
        viewModel.fastForward(this, ROTATE_DEFAULT)
        viewModel.writeMatAsync(path)
    }
    override fun onOK() {
        viewModel.writeMatAsync(path)
        super.onOK()
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        back.setOnClickListener { onBack() }
        autoRotate.setOnClickListener {
            autoRotate.isChecked = !autoRotate.isChecked
            viewModel.update(this, autoRotate.isChecked)
        }
        ok.setOnClickListener { onOK() }
        fastForward.setOnClickListener {
            fragmentManager.fastForwardTo(FragmentIndex.BLOBBING) {
                viewModel.writeMatAsync(path)
            }
        }
    }

    class VM : BaseViewModel() {
        private val inViewModel: SelectDensityFragment.VM by viewModelDelegate()
        private val linesViewModel: EditLinesFragment.VM by viewModelDelegate()

        private val prevMat get() = inViewModel.resultMat
        val resultMat = Mat()
        val angle get() = linesViewModel.resultImageAngle
        private var m = Mat()
        var lastRotated = ROTATE_DEFAULT
            private set

        fun fastForward(frag: BaseFragment, rotated: Boolean) {
            init(frag)
            update(rotated)
        }
        override fun init(frag: BaseFragment) {
            super.init(frag)
            m = getRotationMatrix2D(Point(prevMat.width() / 2.0, prevMat.height() / 2.0),
                    angle, 1.0)
        }
        fun update(rotated: Boolean) {
            prevMat.copyTo(resultMat)
            if (rotated) {
                warpAffine(resultMat, resultMat, m, prevMat.size())
            }
        }
        fun update(frag: ImageViewFragment, rotated: Boolean) {
            lastRotated = rotated
            update(rotated)
            frag.setImagePreview(resultMat)
        }
        fun writeMatAsync(path: String) {
            /*
            AsyncTask.execute {
                val t = measureTimeSec {
                    imwrite("$path/scaled.jpg", resultMat)
                }
                logd("wrote img done in $t")
            }*/
        }
    }
}