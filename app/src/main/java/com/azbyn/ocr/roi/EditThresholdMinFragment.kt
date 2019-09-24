package com.azbyn.ocr.roi

import com.azbyn.ocr.*
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc.*

class EditThresholdMinFragment : BaseSlidersFragment(
        SliderData("Min", default=10, max=-1),
        SliderData("Blur", default=3, max=51, min=1, stepSize=2)) {
    override val viewModel: VM by viewModelDelegate()
    override val topBarName: String get() = mainActivity.getString(R.string.edit_threshold_min)

    override fun initSliderData(sliderDatas: Array<SliderData>) {
        sliderDatas[0].max = viewModel.maxVal
    }

    class VM : SlidersViewModel() {
        private val inViewModel: EditThresholdMaxFragment.VM by viewModelDelegate()
        private val baseMat get() = inViewModel.resultMat
        var resultMat = Mat()
            private set
        val maxVal get() = inViewModel.value
        val value get() = lastValues[0]
        val blur get() = lastValues[1]

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            updateImpl(baseMat, resultMat, value, blur)
        }
        fun updateImpl(baseMat: Mat, resultMat: Mat, value: Int, blur: Int) {
            medianBlur(baseMat, resultMat, blur)
            // we need 1s and 0s for morphological operations
            threshold(resultMat, resultMat, value.toDouble(), 1.0, THRESH_BINARY)
            //threshold(resultMat, resultMat, value.toDouble(), 255.0, THRESH_TOZERO)
        }

        override fun update(frag: ImageViewFragment, p: IntArray) {
            frag.tryOrComplain {
                update(p)
                frag.setImageGrayscalePreview(resultMat)
            }
        }
    }
}
