package com.azbyn.ocr.roi

import com.azbyn.ocr.*
import org.opencv.core.CvType.CV_8U
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc.*

class GridMorphologicalFragment : BaseSlidersFragment(
        SliderData("Iter", default=2, max=3)) {
    //override val fragmentIndex = FragmentManagerAdapter.GRID_MORPHOLOGICAL
    override val viewModel: VM by viewModelDelegate()
    override val topBarName get() = mainActivity.getString(R.string.morphological)

    //override fun lightCleanup() = viewModel.cleanup()

    class VM : SlidersViewModel() {
        //private val inViewModel: EditThresholdMinFragment.VM by viewModelDelegate()
        private val baseMat get() = getViewModel<EditThresholdMinFragment.VM>().resultMat

        var resultMat = Mat()
            private set
        //val value get() = lastValues[0]

        fun updateImpl(resultMat: Mat, value: Int) {
            // this is already 0 and 1 from EditThresholdMinFragment
            val kernel = Mat.ones(3, 3, CV_8U)// value, value, CV_8U)
            if (value >= 2) morphologyEx(resultMat, resultMat, MORPH_OPEN, kernel)
            if (value >= 1) morphologicalSkeleton(resultMat, resultMat)
            if (value >= 3) morphologyEx(resultMat, resultMat, MORPH_CLOSE, kernel)
            //threshold(baseMat, baseMat, 0.0, 255.0, THRESH_BINARY)
        }

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            val value = p[0]
            baseMat.copyTo(resultMat)
            updateImpl(resultMat, value)
        }

        override fun update(frag: ImageViewFragment, p: IntArray) {
            frag.tryOrComplain {
                logTimeSec { update(p) }
                frag.setImageGrayscalePreview(resultMat)
            }
        }
    }
}
