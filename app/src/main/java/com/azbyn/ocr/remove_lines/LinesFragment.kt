package com.azbyn.ocr.remove_lines

import com.azbyn.ocr.*
import com.azbyn.ocr.roi.EditLinesFragment
import org.opencv.core.*
import org.opencv.core.CvType.CV_8UC3
import org.opencv.imgproc.Imgproc.*
import kotlin.math.*

class LinesFragment : BaseSlidersFragment(
        SliderData("Thresh", default=-1, max=300, stepSize=5),
        SliderData("Length", default=-1, max=-1, stepSize=10),
        // the image is already rotated, so lower tolerance than the first time we did this
        SliderData("Angle", default=10, max=50, stepSize=1, showFloat=true)) {
    override val viewModel: VM by viewModelDelegate()
    override val topBarName get() = mainActivity.getString(R.string.edit_lines)

    override fun initSliderData(sliderDatas: Array<SliderData>) {
        sliderDatas[0].default = getViewModel<EditLinesFragment.VM>().thresh
        // even through the viewModel isn't initialised, this should work
        sliderDatas[1].max = viewModel.maxLen
        sliderDatas[1].default = viewModel.maxLen / 5//2
    }
    class VM : SlidersViewModel() {
        private val baseMat get() = getViewModel<ThresholdFragment.VM>().resultMat
        private var colored = Mat()
        var lines = MatOfInt4()
            private set
        val size: Size get() = baseMat.size()
        val maxLen get() = min(baseMat.width(), baseMat.height())
        val rejectAngle get() = lastValues[2] * 0.1

        override fun init(frag: BaseFragment) {
            super.init(frag)
            colored = Mat(baseMat.rows(), baseMat.cols(), CV_8UC3)
        }

        override fun cleanup() {
            colored = Mat()
        }

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            JniImpl.linesExtract(
                    matAddr=baseMat.nativeObj,
                    linesAddr=lines.nativeObj,
                    outputAddr=if (isFastForward) 0 else colored.nativeObj,
                    thresh=p[0], length=p[1].toDouble(), rejectAngle=p[2] * 0.1)
        }

        override fun update(frag: ImageViewFragment, p: IntArray) {
            frag.tryOrComplain {
                val t = measureTimeSec {
                    update(p)
                }
                logd("time = $t")
                frag.setImagePreview(colored)
            }
        }
    }
}
