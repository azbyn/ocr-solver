package com.azbyn.ocr.roi

import com.azbyn.ocr.*
import org.json.JSONObject
import org.opencv.core.Core.absdiff
import org.opencv.core.CvType.CV_8U
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc.*


class BlurRoiFragment : BaseSlidersFragment(
        SliderData("Blur", default=41, min=1, max=251, stepSize=2),
        //SliderData("Dilate", default=1, min=1, max=21, stepSize=2)) {
        SliderData("Dilate", default=0, min=0, max=10, stepSize=1)) {

    override val viewModel: VM by viewModelDelegate()
    override val topBarName: String get() = mainActivity.getString(R.string.blur)

    class VM : SlidersViewModel() {
        private val inViewModel: SelectRoiFragment.VM by viewModelDelegate()
        private val baseMat get() = inViewModel.resultMat
        private var bg = Mat()
        var resultMat = Mat()
            private set

        val blurVal get() = lastValues[0]
        val dilateVal get() = lastValues[1]

        override fun cleanup() {
            bg = Mat()
        }
        fun updateImpl(baseMat: Mat, resultMat: Mat, blurVal: Int, dilateVal: Int) {
            //we remove all shadows from the bitmap
            val inMat = if (dilateVal > 0) {
                val k = Mat.ones(3, 3, CV_8U)
                dilate(baseMat, resultMat, k, Point(-1.0, -1.0), dilateVal)
                resultMat
            } else {
                baseMat
            }

            medianBlur(inMat, bg, blurVal)
            absdiff(inMat, bg, resultMat)
        }
        override fun update(p: IntArray) {
            super.update(p)
            updateImpl(baseMat, resultMat, blurVal, dilateVal)
        }

        // this is fairly light computation (< 50ms) so we don't bother creating threads and
        // stopping them when we get a new update
        override fun update(frag: ImageViewFragment, p: IntArray) {
            frag.tryOrComplain {
                update(p)
                frag.setImageGrayscalePreview(resultMat)
            }
        }
    }
}
