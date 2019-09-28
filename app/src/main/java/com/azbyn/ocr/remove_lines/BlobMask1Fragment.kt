package com.azbyn.ocr.remove_lines

import com.azbyn.ocr.*
import com.azbyn.ocr.Misc.logd
import com.azbyn.ocr.roi.EditThresholdMaxFragment
import com.azbyn.ocr.roi.EditThresholdMinFragment
import com.azbyn.ocr.roi.GridMorphologicalFragment
import org.opencv.core.Core.*
import org.opencv.core.CvType.CV_8U
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc.*

class BlobMask1Fragment : BaseSlidersFragment(
        SliderData("thrsh1", default = 75/*100*/, min = 0, max = 255*2, stepSize = 1),
        SliderData("thrsh2", default = 150/*200*/, min = 0, max = 255*2, stepSize = 1)
        //SliderData("Blur",  default = 9, min = 1, max = 31, stepSize = 2)
) {

    override val viewModel: VM by viewModelDelegate()
    override val topBarName: String get() = "Blob Mask 1"
    private val removeLines get() = getViewModel<AcceptDensityFragment.VM>().removeLines
    override val prevFragment: FragmentIndex get() =
        if (removeLines) super.prevFragment
        else FragmentIndex.ACCEPT_DENSITY

    class VM : SlidersViewModel() {
        private val removeLines get() = getViewModel<AcceptDensityFragment.VM>().removeLines
        val baseMat get() =
            if (removeLines) getViewModel<RemoveLinesFragment.VM>().resultMat
            else getViewModel<BlurFragment.VM>().resultMat

        var resultMat = Mat()
            private set

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            val t1 = p[0].toDouble()
            val t2 = p[1].toDouble()
            //val blur = p[2].toDouble()

            Canny(baseMat, resultMat, t1, t2, 3)
            //blur(resultMat, resultMat, Size(blur, blur))
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
