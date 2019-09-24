package com.azbyn.ocr.remove_lines

import com.azbyn.ocr.*
import com.azbyn.ocr.Misc.logw
import com.azbyn.ocr.roi.SuperLinesFragment.VM.Companion.addLine
import com.azbyn.ocr.roi.SuperLinesFragment.VM.SuperLine
import org.opencv.core.CvType.CV_8U
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc.*
import org.opencv.core.Scalar
import java.lang.IndexOutOfBoundsException
import kotlin.math.*

class SuperLinesMk2Fragment : BaseSlidersFragment(
        //SliderData("Threshold", default=30, max=300, stepSize=5),
        SliderData("Length", default=-1, max=-1, stepSize=10),
        SliderData("Sline", default=15, max=150)) {

    override val viewModel: VM by viewModelDelegate()
    override val topBarName get() = mainActivity.getString(R.string.super_lines)


    override fun initSliderData(sliderDatas: Array<SliderData>) {
        sliderDatas[0].max = viewModel.maxLen
        sliderDatas[0].default = viewModel.maxLen / 5
    }

    class VM : SlidersViewModel() {
        override fun logd(s: String) = Unit
        private val inViewModel: LinesFragment.VM by viewModelDelegate()
        private var colored = Mat()
        val maxLen get() = inViewModel.maxLen
        val vertMids = mutableListOf<Int>()
        val horiMids = mutableListOf<Int>()

        override fun cleanup() {
            colored = Mat()
        }

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            val minLength = p[0]
            val slineSize = p[1]

            val vertSlines = mutableListOf<SuperLine>()
            val horiSlines = mutableListOf<SuperLine>()
            val rejectAngle = inViewModel.rejectAngle
            val buf = IntArray(4)

            colored = Mat(inViewModel.size, CV_8U)
            for (i in 0 until inViewModel.lines.rows()) {
                inViewModel.lines.get(i, 0, buf)
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
            /*
            //horiSlines.removeAt(horiSlines.size-3)
            //horiSlines.removeAt(horiSlines.size-2)
            horiSlines.removeAt(horiSlines.size-1)
            horiSlines.removeAt(horiSlines.size-1)
            horiSlines.removeAt(horiSlines.size-1)
            horiSlines.removeAt(1)
            horiSlines.removeAt(1)
             */
            //horiSlines.add(SuperLine(horiSlines[horiSlines.size-1].mid +1,
            //        0, colored.width))

            //horiSlines.add(0, SuperLine(0, 0, colored.width()))
            //horiSlines.add(SuperLine(colored.height(), 0, colored.width()))

            drawLines(horiSlines, horiMids, minLength, bottom=colored.height(),
                    lineMaxLength=colored.width().toDouble()) {
                pt: Point, mid, other ->
                pt.y = mid
                pt.x = other
            }
            drawLines(vertSlines, vertMids, minLength, bottom=colored.width(),
                    lineMaxLength=colored.height().toDouble()) {
                pt: Point, mid, other ->
                pt.x = mid
                pt.y = other
            }
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

        private inline fun drawLines(
                slines: MutableList<SuperLine>,
                mids: MutableList<Int>,
                minLength: Int,
                bottom: Int,
                lineMaxLength: Double,
                editPoint: (p: Point, mid: Double, other: Double) -> Unit) {
            evalSlines(slines, mids, minLength, bottom)

            val col = Scalar(255.0)

            val p1 = Point()
            val p2 = Point()
            for (m in mids) {
                val md = m.toDouble()
                editPoint(p1, md, 0.0)
                editPoint(p2, md, lineMaxLength)
                line(colored, p1, p2, col, 5)
            }
            for (sl in slines) {
                if (sl.len < minLength) continue
                val md = sl.mid.toDouble()
                editPoint(p1, md, sl.top.toDouble())
                editPoint(p2, md, sl.bot.toDouble())
                line(colored, p1, p2, Scalar(128.0), 3)
            }
        }

        // int arithmetic is recommended
        private fun isSmall(n: Int) = n <= DESIRED_DENSITY * 3 / 4 //.75
        //private fun isSuperSmall(n: Int) = n <= DESIRED_DENSITY / 3 // .33
        private fun isBig(n: Int) = n >= DESIRED_DENSITY *3 / 2 // 1.5

        private fun addIfBig(diff: Int, start: Int, mids: MutableList<Int>): Boolean {
            val res = isBig(diff)
            if (res) {
                logd("isBig")
                // equivalent to round for float
                val newLines = (diff + DESIRED_DENSITY/2) / DESIRED_DENSITY
                //val distance = diff / newLines
                for (j in 0..newLines) {
                    val value = start + j * DESIRED_DENSITY //* distance * multiplier
                    logd("insert $j: $value")
                    mids.add(value)
                }
            }
            return res
        }
        private fun evalSlines(slines: MutableList<SuperLine>,
                               mids: MutableList<Int>,
                               minLength: Int,
                               bottom: Int) {
            mids.clear()
            // If there are no slines, there's nothing to do. Great!
            if (slines.size == 0) {
                logw("Empty slines?")
                return
            }
            var i = 0
            val len = slines.size - 1
            fun getCurr(): Int {
                // We find the first sline bigger than [minLength],
                // If we don't find any, we throw IndexOutOfBoundsException
                // indicating that it finished
                while (slines[i].len <= minLength) {
                    // since len = size - 1, we check with '>', not '>='
                    if (++i > len) throw IndexOutOfBoundsException()
                }
                return slines[i].mid
            }
            var next = -1
            try {
                // the exception simplifies a lot of things
                mids.add(getCurr())
                ++i

                //this should be handled with slineSize

                /*val c = getCurr()
                // if the difference between lines is very small (ie lines are close together)
                // we evaluate that as a single line in the middle of the initial lines
                if (isSuperSmall(c - mids[0])) {
                    logd("first super small")
                    mids[0] = (c + mids[0]) / 2
                    ++i
                }*/

                /*
                // If there's a big space without any lines, we add as many lines
                // as would fit in that space.
                // We can do this because we know that we should have a line every
                // [DESIRED_DENSITY] pixels.
                val c = getCurr()
                //if (addIfBig(diff = c - mids[0], start = mids[0], mids = mids)) ++i
                if (addIfBig(diff = c - mids[0], start = c, mids = mids, multiplier=-1)) ++i
                // we iterate over slines, adding to mid where necessary
                 */
                while (i < len) {
                    val curr = getCurr()
                    // having references would have been useful
                    var j = i + 1
                    while (slines[j].len <= minLength) {
                        if (++j > len) throw IndexOutOfBoundsException()
                    }
                    next = slines[j].mid

                    val diffL = curr - mids[mids.size - 1]
                    val diffR = next - curr
                    //logd("diffs: $diffL, $diffR")
                    when {
                        // If there's a big space without any lines, we add as many lines
                        // as would fit in that space.
                        // We can do this because we know that we should have a line every
                        // [DESIRED_DENSITY] pixels.
                        addIfBig(diff = diffR, start = curr, mids = mids) -> Unit
                        // having 2 close lines isn't that bad
                        /*
                        // if the difference between lines is very small (ie lines are close together)
                        // we evaluate that as a single line in the middle of the initial lines
                        isSuperSmall(diffR) -> {
                            logd("isSuperSmall")
                            mids.add((curr + next) / 2)
                            ++i
                        }
                         */
                        // this is likely to occur if we have a fake line in the middle of
                        // two normal lines
                        isSmall(diffL) && isSmall(diffR) -> {
                            logd("both small, skiping")
                        }
                        /*
                    // we have the regular grid, then a fake line to the left/right of it
                    isSmall(diffL) && !isSmall(diffR) -> {
                        mids[mids.size-1] = curr
                    }
                    !isSmall(diffL) && isSmall(diffR) -> {
                        mids.add(curr)
                        ++i
                    }*/
                        // the line is good, so we add it to [mids]
                        else -> mids.add(curr)
                    }
                    ++i
                }
            } catch (e: IndexOutOfBoundsException) {
                logd("caught")
            }
            // We skip the last line in the loop, so we add it now
            if (next != -1)
                mids.add(next)


            fun addIfBigReversed(diff: Int, end: Int, index: Int) {
                if (isBig(diff)) {
                    val newLines = (diff + DESIRED_DENSITY/2) / DESIRED_DENSITY
                    logd("isBigReversed $index; new:$newLines")
                    //val distance = diff / newLines
                    for (j in 1..newLines) {
                        val value = end - j * DESIRED_DENSITY //* distance
                        logd("insert $j: $value")
                        mids.add(index, value)
                    }
                }
            }
            //if there's enough space add lines at the beginning
            addIfBigReversed(mids[0], mids[0], 0)
            //if there's enough space add lines at the end
            val curr = mids[mids.size-1]
            if (addIfBig(diff = bottom - curr, start = curr, mids = mids))

            if (mids.size < 2) return
            // the first difference doesn't get checked for big spaces
            addIfBigReversed(mids[1] - mids[0], mids[1], 1)
            //mids.add(0,0)
            //mids.add(mids.size, bottom)
            // The top and bottom tend to get fake lines from paper edges and such
            /*
            if (isSmall(mids[1] - mids[0])) {
                logd("removed first")
                mids.removeAt(0)
            }
            val last = mids.size -1
            if (isSmall(mids[last] - mids[last-1])) {
                logd("removed last")
                mids.removeAt(last)
            }
*/
            logd("lines: $mids")
            var s = "diffs: "
            for (j in 1 until mids.size) {
                s += "${mids[j] - mids[j-1]}, "
            }
            logd(s)
        }
    }
}
