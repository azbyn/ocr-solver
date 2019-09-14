package com.azbyn.ocr.roi

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.azbyn.ocr.*
import com.azbyn.ocr.Misc.logd
import com.azbyn.ocr.Misc.whyIsThisCalled
import kotlinx.android.synthetic.main.select_density.*
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc.INTER_AREA
import org.opencv.imgproc.Imgproc.resize

class SelectDensityFragment: DumbSlidersFragment(
        arrayOf(SliderData("Size", default=-1, max=-1, min=10)),
                R.layout.select_density) {

    override val topBarName get() = mainActivity.getString(R.string.select_density)
    private val viewModel: VM by viewModelDelegate()
    override val lastValues = IntArray(1)
        get() {
            field[0] = viewModel.resultDensity
            return field
        }

    override fun viewModelInit() = viewModel.init(this)

    override fun initImpl() {
        imageView.resetZoom()
        val previewMat = getViewModel<SelectRoiFragment.VM>().resultMat
        setImagePreview(previewMat)
        overlay.runWhenInitialized {
            val matWidth = previewMat.width()
            val matHeight = previewMat.height()
            val density = viewModel.resultDensity

            overlay.init(matWidth, matHeight, density, imageView)
        }
    }

    override fun initSliderData(sliderDatas: Array<SliderData>) {
        sliderDatas[0].default = viewModel.defaultDensity//resultDensity
        sliderDatas[0].max = viewModel.maxSize
    }

    override fun onReset() {
        super.onReset()
        viewModel.reset()
        imageView.resetZoom()
        overlay.reset(viewModel.resultDensity)
    }
    override fun fastForwardImpl(p: IntArray) = viewModel.fastForward(this)

    override fun updateImpl(p: IntArray) {
        overlay.update(p[0])
        viewModel.update(p[0])
        super.updateImpl(p)
    }
    override fun lightCleanup() = overlay.cleanup()

    override fun onOK() {
        viewModel.finish()
        super.onOK()
    }

    class VM : BaseViewModel() {
        private val inViewModel: SuperLinesFragment.VM by viewModelDelegate()
        private val fullMat get() = getViewModel<AcceptFragment.VM>().resultMat
        var resultMat = Mat()
            private set

        val maxSize get() = inViewModel.maxLen
        val defaultDensity get() = inViewModel.resultDensity
        var resultDensity = 1
            private set

        fun getString(context: Context) =
                context.getString(R.string.select_density_text,
                        fullMat.width(), fullMat.height(),
                        fullMat.width() * DESIRED_DENSITY / resultDensity,
                        fullMat.height() * DESIRED_DENSITY / resultDensity)
        // + "($DESIRED_DENSITY, $resultDensity)"

        fun fastForward(frag: BaseFragment) {
            init(frag)
            finish()
        }

        override fun init(frag: BaseFragment) {
            super.init(frag)
            reset()
        }
        fun reset() {
            resultDensity = defaultDensity
        }
        fun update(density: Int) {
            resultDensity = density
        }
        fun finish() {
            val f = DESIRED_DENSITY.toDouble() / resultDensity
            resize(fullMat, resultMat, Size(), f, f, INTER_AREA)
        }
    }
}