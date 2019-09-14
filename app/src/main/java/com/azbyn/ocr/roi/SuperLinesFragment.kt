package com.azbyn.ocr.roi

import com.azbyn.ocr.*
import com.azbyn.ocr.Misc.logw
import org.opencv.core.CvType.CV_8U
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc.*
import org.opencv.core.Scalar
import kotlin.math.*

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

        override fun update(p: IntArray) {
            super.update(p)
            val minLength = p[0]
            val slineSize = p[1]
            val vertSlines = mutableListOf<SuperLine>()
            val horiSlines = mutableListOf<SuperLine>()

            val buf = IntArray(4)
            colored = Mat(inViewModel.size, CV_8U)
            for (i in 0 until lines.rows()) {
                lines.get(i, 0, buf)
                val x1 = buf[0]
                val y2 = buf[1]// because buf[1] is usually greater than buff[3]
                val x2 = buf[2]
                val y1 = buf[3]
                // the angle is guaranteed to be in the first quadrant
                val theta = atan2(abs(y2 - y1).toFloat(), abs(x2 - x1).toFloat()) / PI * 180
                when {
                    theta < rejectAngle -> addLine(slineSize, horiSlines, y1, y2, x1, x2)
                    theta > (90 - rejectAngle) -> addLine(slineSize, vertSlines, x1, x2, y1, y2)
                    else -> Unit
                }
            }
            //merge close super lines?

            //val w = colored.width().toDouble()
            //val h = colored.height().toDouble()
            val thickness = 5

            var totalDiff = 0
            var prev = -1
            var count = 0

            fun iterateSline(/*len: Int, */mid: Int, p1: Point, p2: Point) {
                if (prev != -1) {
                    //logd("diff = ${mid - prev}")
                    totalDiff += mid - prev
                    count += 1
                }
                prev = mid
                line(colored, p1, p2, /*Scalar((len * 200.0) / maxLen + 55)*/Scalar(255.0),
                        thickness)
            }
            for (sl in horiSlines) {
                if (sl.len < minLength) continue
                val m = sl.mid.toDouble()
                iterateSline(sl.mid, Point(sl.top.toDouble(), m), Point(sl.bot.toDouble(), m))
            }
            prev = -1
            for (sl in vertSlines) {
                if (sl.len < minLength) continue
                val m = sl.mid.toDouble()
                iterateSline(sl.mid, Point(m, sl.top.toDouble()), Point(m, sl.bot.toDouble()))
            }
            /*Do this?
                def reject_outliers(data, m=2):
                    d = np.abs(data - np.median(data))
                    mdev = np.median(d)
                    s = d/(mdev if mdev else 1.)
                    return np.array(data)[s<m]#  data[abs(data - np.mean(data)) < m * np.std(data)]
             */
            //logd("vert: $vertSlines")
            //logd("hori: $horiSlines")
            resultDensity = if (count == 0) {
                logw("super lines diff count == 0")
                DESIRED_DENSITY
            } else {
                totalDiff / count
            }
            logd("size: $totalDiff / $count = $resultDensity")
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
        class SuperLine(var mid: Int, var top: Int, var bot: Int) {
            val len get() = bot - top

            override fun toString(): String = "(SL $mid - $len)"
            fun add(lineMid: Int, lineTop: Int, lineBot: Int, slineSize: Int): Boolean {
                val res = abs(mid - lineMid) <= slineSize
                if (res) {
                    val lineLen = lineBot - lineTop
                    if (lineLen != 0) {
                        //mid = ((mid * len) + (lineMid * lineLen)) / (lineLen + len)
                        mid = ((mid * len*len) + (lineMid * lineLen*lineLen)) /
                                (lineLen*lineLen + len*len)
                        top = min(top, lineTop)
                        bot = max(bot, lineBot)
                    }
                }
                return res
            }
        }
        companion object {
            fun addLine(slineSize: Int, slines: MutableList<SuperLine>,
                        left: Int, right: Int,
                        top: Int, bot: Int) {
                if (top > bot) {
                    //logw("Top > Bot ($top > $bot)")
                    addLine(slineSize, slines, left, right, bot, top)
                    return
                }
                //this also sorts the slines after middle size
                var index = slines.size
                var bestDiff = Int.MAX_VALUE
                val mid = (left + right) / 2
                //logd("adding $lineMid in ${slines.size}")
                for ((i, sl) in slines.withIndex()) {
                    if (sl.add(mid, top, bot, slineSize)) return
                    val diff = sl.mid - mid
                    if (diff in 1 until bestDiff) {
                        bestDiff = diff
                        index = i
                    }
                }
                //logd("matches not found")
                slines.add(index, SuperLine(mid, top, bot))
            }
        }
    }
}
