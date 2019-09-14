package com.azbyn.ocr.remove_lines

import com.azbyn.ocr.*
import com.azbyn.ocr.roi.EditThresholdMaxFragment
import com.azbyn.ocr.roi.EditThresholdMinFragment
import com.azbyn.ocr.roi.GridMorphologicalFragment
import org.opencv.core.Mat

class ThresholdFragment : BaseSlidersFragment(
        SliderData("Max", default = -1, min = 0, max = 255, stepSize = 1),
        SliderData("Min", default = -1, min = 0, max = 255, stepSize = 1),
        // because the lines are smaller, doing a morphological oppening (ie values >= 2)
        // would destroy some lines
        SliderData("Morph", default = 1, min = 0, max = 3, stepSize = 1)) {

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

        override fun update(p: IntArray) {
            super.update(p)
            val max = p[0]
            val min = p[1]
            val value = p[2]
            //adaptiveThreshold(baseMat, resultMat, 255.0, ADAPTIVE_THRESH_GAUSSIAN_C,
            //       THRESH_BINARY_INV, blockSize, c.toDouble())
            //threshold(baseMat, resultMat, 0.0, 255.0, THRESH_BINARY + THRESH_OTSU)

            //logd("UPDATE($max, $min $value)")
            getViewModel<EditThresholdMaxFragment.VM>().updateImpl(baseMat, resultMat, max)
            val minVM = getViewModel<EditThresholdMinFragment.VM>()
            minVM.updateImpl(resultMat, resultMat, value=min, blur=minVM.blur)
            getViewModel<GridMorphologicalFragment.VM>().updateImpl(resultMat, value)
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
