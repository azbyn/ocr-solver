package com.azbyn.ocr.remove_lines

import com.azbyn.ocr.*
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs.imwrite
import org.opencv.imgproc.Imgproc.rectangle

class AcceptBlobsFragment : BaseSlidersFragment(
        SliderData("Img", default = -1, min=-1, max = -2)
        //SliderData("Dilate", default=3, min=1, max=31, stepSize=2),
        //SliderData("Erode", default=3, min=1, max=31, stepSize=2)
) {

    override val viewModel: VM by viewModelDelegate()
    override val topBarName: String get() = "Accept Blobs"
    //mainActivity.getString(R.string.threshold)

    override fun initSliderData(sliderDatas: Array<SliderData>) {
        super.initSliderData(sliderDatas)
        sliderDatas[0].max = viewModel.blobs.size-1
    }
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
                            result=blobs)
                }
                bounds ?: throw Exception("bounds == null?")
                logd("blobbinTime: $t")
            }
            logd("blobSize = ${blobs.size}")
            logd("boundsSize = ${bounds?.size}")
        }

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            if (isFastForward) return

            val i = p[0]
            val d = p[1]
            val e = p[2]
            //val showMask = p[1] != 0
            if (i == -1) {
                maskMat.copyTo(previewMat)
                JniImpl.bitwiseAndSingleBlob(x=0, y=0, w=fullMat.width(), h=fullMat.height(),
                        blobAddr=previewMat.nativeObj, imgAddr=fullMat.nativeObj,
                        dilateVal=d, erodeVal=e)
                colorMapAndNormalize(previewMat, previewMat)
                val col = Scalar(255.0, 0.0, 0.0)
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

            } else {
                /*
                    if (!showMask) {
                        val m = fullMat.submat(boundsAt(i-1))
                        bitwise_and(blobs[i-1], m, previewMat)
                    } else {
                    }*/
                //previewMat = blobs[i - 1]
                val a = bounds!!
                blobs[i].copyTo(previewMat)
                JniImpl.bitwiseAndSingleBlob(x=a[i*4], y=a[i*4+1], w=a[i*4+2], h=a[i*4+3],
                        blobAddr=previewMat.nativeObj, imgAddr=fullMat.nativeObj,
                        dilateVal=d, erodeVal=e)
                colorMapAndNormalize(previewMat, previewMat)
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
