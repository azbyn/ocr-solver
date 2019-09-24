package com.azbyn.ocr.remove_lines

import com.azbyn.ocr.*
import org.opencv.core.CvType.CV_8U
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc.*

class BlobMask2Fragment : BaseSlidersFragment(
        //SliderData("Thrsh", default = 32/*24*/, min = 0, max = 255, stepSize = 1),
        SliderData("Dilate",  default = 3, min = 1, max = 31, stepSize = 2),
        SliderData("Erode",  default = 3, min = 1, max = 31, stepSize = 2),
        SliderData("Blur",  default = 7, min = 1, max = 31, stepSize = 2)
) {
    override val viewModel: VM by viewModelDelegate()
    override val topBarName: String get() = "Blob Mask 2"

    class VM : SlidersViewModel() {
        //private val fullMat get() = getViewModel<RemoveLinesFragment.VM>().resultMat
        private val baseMat get() = getViewModel<BlobMask1Fragment.VM>().resultMat
        var resultMat = Mat()
            private set

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            //val smallThresh = p[0].toDouble()//32.0
            val dilate = p[0]
            val erode = p[1]
            val blur = p[2].toDouble()

            //threshold(baseMat, resultMat, smallThresh, 255.0, THRESH_BINARY)

            dilate(baseMat, resultMat, Mat.ones(dilate, dilate, CV_8U))
            erode(resultMat, resultMat, Mat.ones(erode, erode, CV_8U))
            blur(resultMat, resultMat, Size(blur, blur))
            threshold(resultMat, resultMat, 32.0, 255.0, THRESH_BINARY)
            /*
            val blur = 3.0
            //val thickness = 7//p[2]
            val t2 = 32.0// 32.0//p[2].toDouble()//32
            //val t3 = 60// p[2]//14
            val ker2 = p[2]//14
             */

            /*
            val preBlur = p[0].toDouble()
            val postBlur = p[1].toDouble()

            blur(baseMat, resultMat, Size(preBlur, preBlur))
            threshold(resultMat, resultMat, 0.0, 255.0, THRESH_TOZERO + THRESH_OTSU)
            blur(resultMat, resultMat, Size(postBlur, postBlur))
            //threshold(baseMat, resultMat, smallThresh, 255.0, THRESH_BINARY)
            threshold(resultMat, resultMat, smallThresh, 255.0, THRESH_TOZERO)
*/

            //getViewModel<LinesMaskFragment.VM>().getLinesMask(resultMat, 7)
            //threshold(baseMat, resultMat, 0.0, 255.0, THRESH_BINARY + THRESH_OTSU)

            /*
            //bitwise_not(resultMat, resultMat)
             */
            //bitwise_and(resultMat, fullMat, resultMat)
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
