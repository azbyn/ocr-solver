package com.azbyn.ocr.remove_lines

import com.azbyn.ocr.*
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs.imwrite
import org.opencv.imgproc.Imgproc.rectangle

class BlobbingFragment : BaseSlidersFragment(
        //SliderData("Mask", default = 0, max = 1)
        SliderData("Dilate", default=3, min=1, max=31, stepSize=2),
        SliderData("Erode", default=3, min=1, max=31, stepSize=2)
        //SliderData("Morph", default = 1, min = 0, max = 3, stepSize = 1)
) {

    override val viewModel: VM by viewModelDelegate()
    override val topBarName: String get() = "Blobbing"//mainActivity.getString(R.string.threshold)

    override fun onOK() {
        //logd("wrote lines")
        //imwrite("${mainActivity.path}/final.jpg", getViewModel<FinalThresholdFragment.VM>().resultMat)
        imwrite("${mainActivity.path}/blobs2.jpg", getViewModel<BlobMask2Fragment.VM>().resultMat)
        showToast("SAVED final.jpg")
        super.onOK()
    }

    class VM : SlidersViewModel() {
        private val maskMat get() = getViewModel<BlobMask2Fragment.VM>().resultMat
        private val fullMat get() = getViewModel<RemoveLinesFragment.VM>().resultMat
        //private val fullMat get() = getViewModel<FinalThresholdFragment.VM>().resultMat
        private var previewMat = Mat()

        private var bounds: IntArray? = null
        var blobs = arrayListOf<Mat>()
            private set

        fun finish() {
            JniImpl.bitwiseAndBlobs(boundsArr=bounds!!, blobs=blobs,
                    imgAddr=fullMat.nativeObj, dilateVal=lastValues[1],
                    erodeVal=lastValues[2])
        }

        override fun init(frag: BaseFragment) {
            super.init(frag)
            frag.tryOrComplain {
                val t = measureTimeSec {
                    bounds = JniImpl.blobbing(
                            maskAddr=maskMat.nativeObj,
                            result=blobs,
                            desiredDensity=DESIRED_DENSITY)
                }
                bounds ?: throw Exception("bounds == null?")
                logd("blobbinTime: $t")
            }
            logd("blobSize = ${blobs.size}")
            logd("boundsSize = ${bounds?.size}")
        }

        override fun update(p: IntArray) {
            super.update(p)
            //val i = p[0]
            val d = p[0]
            val e = p[1]
            maskMat.copyTo(previewMat)
            JniImpl.bitwiseAndSingleBlob(x=0, y=0, w=fullMat.width(), h=fullMat.height(),
                    blobAddr=previewMat.nativeObj, imgAddr=fullMat.nativeObj,
                    dilateVal=d, erodeVal=e)
            colorMapAndNormalize(previewMat, previewMat)
            val col = Scalar(0.0, 255.0, 0.0, 0.0)
            val a = bounds!!
            val rect = Rect()
            for (j in 0 until (a.size/4)) {
                //the thickness might hide some parts
                rect.x=a[j*4]-1
                rect.y=a[j*4+1]-1
                rect.width=a[j*4+2]+2
                rect.height=a[j*4+3]+2
                rectangle(previewMat, rect, col, 2)
            }
        }

        override fun update(frag: ImageViewFragment, p: IntArray) {
            frag.tryOrComplain {
                update(p)
                frag.setImagePreview(previewMat)
            }
        }
    }
}
