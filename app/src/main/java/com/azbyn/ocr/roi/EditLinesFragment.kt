package com.azbyn.ocr.roi

import com.azbyn.ocr.*
import org.opencv.core.CvType.CV_8UC3
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc.*
import org.opencv.core.Scalar
import org.opencv.core.Size
import kotlin.math.*

class EditLinesFragment : BaseSlidersFragment(
        SliderData("Thresh", default=30, max=300, stepSize=5),
        SliderData("Length", default=-1, max=-1, stepSize=10),
        SliderData("Angle", default=5, max=15, stepSize=1)) {

    override val viewModel: VM by viewModelDelegate()
    override val topBarName get() = mainActivity.getString(R.string.edit_lines)

    override fun initSliderData(sliderDatas: Array<SliderData>) {
        sliderDatas[1].max = viewModel.maxLen
        sliderDatas[1].default = viewModel.maxLen / 5//2
    }

    class VM : SlidersViewModel() {
        private val inViewModel: GridMorphologicalFragment.VM by viewModelDelegate()
        private val baseMat get() = inViewModel.resultMat
        private var colored = Mat()
        var lines = Mat()
            private set
        val size: Size get() = baseMat.size()
        val maxLen get() = min(baseMat.height(), baseMat.width())
        var resultImageAngle = 0.0
            private set

        var rejectAngle = 0
            private set
        var thresh = 0
            private set

        override fun cleanup() {
            colored = Mat()
        }

        override fun update(p: IntArray) {
            super.update(p)
            val thresh = p[0]
            val length = p[1]
            val rejectAngle = p[2]
            //lines = Mat()
            this.rejectAngle = rejectAngle
            this.thresh = thresh
            HoughLinesP(baseMat, lines, 1.0, PI / 180, thresh,
                    length.toDouble(), 20.0)// maxLineGap.toDouble())
            //logd("lines: ${lines.size()}")
            val buf = IntArray(4)
            colored = Mat(baseMat.rows(), baseMat.cols(), CV_8UC3)
            var totalVAngle = 0.0
            var vangleCount = 0.0

            var totalHAngle = 0.0
            var hangleCount = 0.0
            loop@ for (i in 0 until lines.rows()) {
                lines.get(i, 0, buf)
                // the angle is guaranteed to be in the first quadrant
                val p1 = Point(buf[0].toDouble(), buf[1].toDouble())
                val p2 = Point(buf[2].toDouble(), buf[3].toDouble())
                val x = p2.x - p1.x
                val y = p2.y - p1.y
                val realAngle = atan2(y, x) / PI * 180
                val len = sqrt(x*x+y*y)
                val theta = atan2(abs(p2.y - p1.y), abs(p2.x - p1.x)) / PI * 180
                val col = when {
                    theta < rejectAngle -> {
                        totalVAngle += realAngle * len
                        vangleCount += len
                        Scalar(0.0, 0.0, 255.0)
                    }
                    theta > (90-rejectAngle) -> {
                        totalHAngle += realAngle * len
                        hangleCount += len
                        Scalar(0.0, 255.0, 0.0)
                    }
                    else -> continue@loop
                }
                line(colored, p1, p2, col, 2)
            }
            val vAngle = if (vangleCount == 0.0) 0.0 else totalVAngle / vangleCount
            val hAngle = if (hangleCount == 0.0) 0.0 else totalHAngle / hangleCount - 90
            resultImageAngle = if (abs(vAngle - hAngle) > 1) {
                logd("big difference between hAngle and vAngle")
                if (abs(vAngle) > abs(hAngle)) {
                    hAngle
                } else {
                    vAngle
                }
            } else {
                (vAngle + hAngle) / 2.0
            }
            //logd("v: $vAngle, h: $hAngle (angle $resultImageAngle)")
        }

        override fun update(frag: ImageViewFragment, p: IntArray) {
            frag.tryOrComplain {
                //val t = measureTimeSec {
                update(p)
                //}
                //logd("time = $t")
                frag.setImagePreview(colored)
            }
        }
    }
}
