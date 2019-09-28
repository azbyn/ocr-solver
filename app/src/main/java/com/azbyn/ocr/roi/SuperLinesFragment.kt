package com.azbyn.ocr.roi

import com.azbyn.ocr.*
import org.opencv.core.CvType.CV_8U
import org.opencv.core.Mat

class SuperLinesFragment : BaseSlidersFragment(
        //SliderData("Threshold", default=30, max=300, stepSize=5),
        SliderData("Length", default=100, max=-1, stepSize=10),
        SliderData("Sline", default=15, max=150)) {
    //override val fragmentIndex = FragmentManagerAdapter.SUPER_LINES
    override val viewModel: VM by viewModelDelegate()
    override val topBarName get() = mainActivity.getString(R.string.super_lines)

    override fun initSliderData(sliderDatas: Array<SliderData>) {
        sliderDatas[0].max = viewModel.maxLen
        sliderDatas[0].default = viewModel.maxLen / 2
    }

    class VM : SlidersViewModel() {
        override fun logd(s: String) = Unit// Misc.logd(s)
        private val inViewModel: EditLinesFragment.VM by viewModelDelegate()
        private val lines get() = inViewModel.lines
        private val rejectAngle get() = inViewModel.rejectAngle
        private var colored = Mat()
        val maxLen get() = inViewModel.maxLen
        var resultDensity = DESIRED_DENSITY

        override fun cleanup() {
            colored = Mat()
        }

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            if (!isFastForward) {
                colored = Mat(inViewModel.size, CV_8U)
            }
            resultDensity = JniImpl.superLinesGetDensity(lines.nativeObj,
                    outputAddr=if (isFastForward) 0 else colored.nativeObj,
                    minLength=p[0], slineSize=p[1], rejectAngle=rejectAngle.toDouble())
        }

        override fun update(frag: ImageViewFragment, p: IntArray) {
            frag.tryOrComplain {
                val t = measureTimeSec {
                    update(p)
                }
                logd("time = $t")
                frag.setImageGrayscalePreview(colored)
            }
        }
    }
}
