package com.azbyn.ocr.remove_lines

import com.azbyn.ocr.*
import com.azbyn.ocr.roi.EditThresholdMaxFragment
import org.opencv.core.Core.bitwise_and
import org.opencv.core.CvType.CV_8U
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc.*
import org.opencv.core.Scalar

class LinesMaskFragment : BaseSlidersFragment(
        SliderData("Width", default=5, max=30),
        SliderData("Max", default=-1, max=255)) {
        //override val fragmentIndex = FragmentManagerAdapter.SUPER_LINES
    override val viewModel: VM by viewModelDelegate()

    override val topBarName get() = mainActivity.getString(R.string.line_mask)

    override fun initSliderData(sliderDatas: Array<SliderData>) {
        sliderDatas[1].default = getViewModel<EditThresholdMaxFragment.VM>().value /2 // *2/3
    }

    class VM : SlidersViewModel() {
        private val inViewModel: SuperLinesMk2Fragment.VM by viewModelDelegate()
        private val fullMat get() = getViewModel<BlurFragment.VM>().resultMat
        var resultMat = Mat()
            private set

        @Suppress("MemberVisibilityCanBePrivate")
        fun getLinesMask(resultMat: Mat, thickness: Int) {
            /*resultMat = */Mat(fullMat.size(), CV_8U).copyTo(resultMat)
            val col = Scalar(255.0)
            val p1 = Point(0.0, -1.0)
            val p2 = Point(resultMat.width().toDouble(), -1.0)

            for (m in inViewModel.horiMids) {
                val md = m.toDouble()
                p1.y = md
                p2.y = md
                line(resultMat, p1, p2, col, thickness)
            }
            p1.y = 0.0
            p2.y = resultMat.height().toDouble()
            for (m in inViewModel.vertMids) {
                val md = m.toDouble()
                p1.x = md
                p2.x = md
                line(resultMat, p1, p2, col, thickness)
            }
        }

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            val thickness = p[0]
            val max = p[1]
            getLinesMask(resultMat, thickness)

            bitwise_and(resultMat, fullMat, resultMat)
            getViewModel<EditThresholdMaxFragment.VM>().updateImpl(resultMat, resultMat, max)
            //medianBlur(colored, colored, 3)
        }

        override fun update(frag: ImageViewFragment, p: IntArray) {
            frag.tryOrComplain {
                val t = measureTimeSec {
                    update(p)
                }
                logd("time = $t")
                frag.setImageGrayscalePreview(resultMat)
            }
        }
    }
}
