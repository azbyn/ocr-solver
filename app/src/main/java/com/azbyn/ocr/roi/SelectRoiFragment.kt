package com.azbyn.ocr.roi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.azbyn.ocr.*
import kotlinx.android.synthetic.main.select_roi.roiText
import kotlinx.android.synthetic.main.select_roi.imageView
import kotlinx.android.synthetic.main.select_roi.overlay
import org.opencv.core.Mat
import org.opencv.core.Rect
import kotlin.math.max
import kotlin.math.min

class SelectRoiFragment: ImageViewFragment() {
    override fun getImageView(): ImageView = imageView
    override val prevFragment = FragmentIndex.ACCEPT
    //override val fragmentIndex = FragmentManagerAdapter.SELECT_ROI

    private val inViewModel: AcceptFragment.VM by viewModelDelegate()
    private val viewModel: VM by viewModelDelegate()

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.select_roi, container, false)

    override fun initImpl(isOnBack: Boolean) {
        viewModel.init(inViewModel.resultMat, isOnBack)
        imageView.resetZoom()
        overlay.runWhenInitialized {
            //viewModel.init(inViewModel.resultMat)

            val matWidth = viewModel.fullMapWidth
            val matHeight = viewModel.fullMapHeight
            overlay.init(matWidth, matHeight, viewModel.roi, imageView, roiText)
        }
        setImagePreview(inViewModel.resultMat)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.back).setOnClickListener { onBack() }
        view.findViewById<View>(R.id.reset).setOnClickListener {
            imageView.resetZoom()
            viewModel.reset()
            overlay.onReset()
        }
        view.findViewById<View>(R.id.ok).setOnClickListener { onOK() }
    }
    override fun onOK() {
        viewModel.finish()
        super.onOK()
    }
    override fun fastForward() = viewModel.fastForward(inViewModel.resultMat)

    class VM : DumbViewModel() {
        companion object {
            private const val INITIAL_SIZE = 500
        }
        private lateinit var prevMat: Mat
        val fullMapWidth get() = prevMat.width()
        val fullMapHeight get() = prevMat.height()
        val roi = Rect()
        var resultMat = Mat()
            private set

        fun fastForward(prev: Mat) {
            init(prev, isOnBack=false)
            finish()
        }
        fun init(prev: Mat, isOnBack: Boolean) {
            prevMat = prev
            if (!isOnBack)
                reset()
        }
        fun reset() {
            roi.x = max(0, (prevMat.width() - INITIAL_SIZE) / 2)
            roi.y = max(0, (prevMat.height() - INITIAL_SIZE) / 2)
            roi.width = min(INITIAL_SIZE, prevMat.width())
            roi.height = min(INITIAL_SIZE, prevMat.height())
        }
        fun finish() {
            resultMat = prevMat.submat(roi)
        }
    }
}