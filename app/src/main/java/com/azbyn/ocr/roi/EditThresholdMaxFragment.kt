package com.azbyn.ocr.roi

import com.azbyn.ocr.*
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc.THRESH_TOZERO_INV
import org.opencv.imgproc.Imgproc.threshold

class EditThresholdMaxFragment : BaseSlidersFragment(
        SliderData("Max", default=/*50*/100, max=255)) {

    override val viewModel: VM by viewModelDelegate()
    override val topBarName get() = mainActivity.getString(R.string.edit_threshold_max)


    class VM : SlidersViewModel() {
        private val inViewModel: BlurRoiFragment.VM by viewModelDelegate()
        private val baseMat get() = inViewModel.resultMat
        val value get() = lastValues[0]
        var resultMat = Mat()
            private set

        override fun update(p: IntArray) {
            super.update(p)
            updateImpl(baseMat, resultMat, value=p[0])
        }
        fun updateImpl(baseMat: Mat, resultMat: Mat, value: Int) {
            threshold(baseMat, resultMat, value.toDouble(), 255.0, THRESH_TOZERO_INV)
        }
        override fun update(frag: ImageViewFragment, p: IntArray) {
            frag.tryOrComplain {
                update(p)
                frag.setImageGrayscalePreview(resultMat)
            }
        }
    }
}
