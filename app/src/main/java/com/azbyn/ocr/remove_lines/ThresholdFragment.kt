package com.azbyn.ocr.remove_lines

import com.azbyn.ocr.*
import com.azbyn.ocr.roi.EditThresholdMaxFragment
import com.azbyn.ocr.roi.EditThresholdMinFragment
import com.azbyn.ocr.roi.GridMorphologicalFragment
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc.*

class ThresholdFragment : BaseSlidersFragment(
        SliderData("Max", default = -1, min = 0, max = 255, stepSize = 1),
        SliderData("Min", default = -1, min = 0, max = 255, stepSize = 1),
        // because the lines are smaller, doing a morphological opening (ie values >= 2)
        // would destroy some lines
        //SliderData("Morph", default = 1, min = 0, max = 3, stepSize = 1)) {
        SliderData("Scale", default = 20, min = 10, max = 50, stepSize = 1, showFloat=true)) {

    override val viewModel: VM by viewModelDelegate()
    override val topBarName: String get() = mainActivity.getString(R.string.threshold)

    override fun initSliderData(sliderDatas: Array<SliderData>) {
        sliderDatas[0].default = getViewModel<EditThresholdMaxFragment.VM>().value / 2
        sliderDatas[1].default = getViewModel<EditThresholdMinFragment.VM>().value
        //sliderData3.default = getViewModel<GridMorphologicalFragment.VM>().value
    }


    class VM : SlidersViewModel() {
        private val baseMat get() = getViewModel<BlurFragment.VM>().resultMat
        var resultMat = Mat()
            private set
        val inverseScale get() = lastValues[2] * 0.1

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            val max = p[0]
            val min = p[1]
            val value = 1
            val downscale = 10.0 / p[2] //1.0/ (p[2] * 0.1)
            //this would get called anyway because of onResume
            //adaptiveThreshold(baseMat, resultMat, 255.0, ADAPTIVE_THRESH_GAUSSIAN_C,
            //       THRESH_BINARY_INV, blockSize, c.toDouble())
            //threshold(baseMat, resultMat, 0.0, 255.0, THRESH_BINARY + THRESH_OTSU)

            //logd("UPDATE($max, $min $value)")
            getViewModel<EditThresholdMaxFragment.VM>().updateImpl(baseMat, resultMat, max)
            val minVM = getViewModel<EditThresholdMinFragment.VM>()
            minVM.updateImpl(resultMat, resultMat, value=min, blur=minVM.blur)
            getViewModel<GridMorphologicalFragment.VM>().updateImpl(resultMat, value)
            resize(resultMat, resultMat, Size(), downscale, downscale, INTER_AREA)
            threshold(resultMat, resultMat, 0.0, 255.0, THRESH_BINARY)
        }

        override fun update(frag: ImageViewFragment, p: IntArray) {
            frag.tryOrComplain {
                val t = measureTimeSec {
                    update(p)
                }
                logd("time: $t")
                frag.setImageGrayscalePreview(resultMat)
            }
        }
    }
}
