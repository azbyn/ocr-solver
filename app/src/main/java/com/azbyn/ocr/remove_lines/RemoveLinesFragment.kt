package com.azbyn.ocr.remove_lines

import com.azbyn.ocr.*
import org.opencv.core.Core.absdiff
import org.opencv.core.CvType.CV_8U
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc.*

class RemoveLinesFragment : BaseSlidersFragment(
        SliderData("LnBlur", default=5, min=1, max=31, stepSize=2),
        SliderData("Open", default=0, max=1),
        SliderData("AllBlur", default=3, min=1, max=31, stepSize=2)) {

    override val viewModel: VM by viewModelDelegate()
    override val topBarName get() = mainActivity.getString(R.string.remove_lines)

    class VM : SlidersViewModel() {
        private val inViewModel: LinesMaskFragment.VM by viewModelDelegate()
        private val fullMat get() = getViewModel<BlurFragment.VM>().resultMat
        private val linesMat get() = inViewModel.resultMat
        var resultMat = Mat()
            private set

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            val blur = p[0]
            val open = p[1] != 0
            val postBlur = p[2].toDouble()
            linesMat.copyTo(resultMat)
            val kernel = Mat.ones(3, 3, CV_8U)
            if (open) {
                morphologyEx(resultMat, resultMat, MORPH_OPEN, kernel)

                //medianBlur(resultMat, resultMat, blur)
            }//else {
            blur(resultMat, resultMat, Size(blur.toDouble(), blur.toDouble()))
            //}
            absdiff(resultMat, fullMat, resultMat)
            blur(resultMat, resultMat, Size(postBlur, postBlur))
            //medianBlur(resultMat, resultMat, postBlur)
            //threshold(resultMat, resultMat, max.toDouble(), 255.0, THRESH_BINARY)
            //if (iter == 3)
            //threshold(resultMat, resultMat, 0.0, 255.0, THRESH_TOZERO + THRESH_OTSU)

            //adaptiveThreshold(resultMat, resultMat, 255.0, ADAPTIVE_THRESH_GAUSSIAN_C,
            //        THRESH_BINARY, 11, 2.0)// blockSize, c.toDouble())

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
