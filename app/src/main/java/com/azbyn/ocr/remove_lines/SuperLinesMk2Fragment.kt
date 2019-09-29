package com.azbyn.ocr.remove_lines

import android.os.Bundle
import com.azbyn.ocr.*
import org.opencv.core.Mat

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

    /*
    //THIS crashes the app with (SIGTRAP)
    override fun onDestroy() {
        super.onDestroy()
        viewModel.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.onCreate()
    }*/

    class VM : SlidersViewModel() {
        override fun logd(s: String) = Unit

        private val inViewModel: LinesFragment.VM by viewModelDelegate()
        private var colored = Mat()
        val maxLen get() = inViewModel.maxLen
        var mids = JniImpl.newSlineMids()
            private set
        fun onCreate() {
            if (mids == 0L) mids = JniImpl.newSlineMids()
        }
        fun onDestroy() {
            logd("destroy")
            JniImpl.delSlineMids(mids)
            mids = 0
        }

        //private val scale get() = getViewModel<ThresholdFragment.VM>().inverseScale

        override fun cleanup() {
            colored = Mat()
        }

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            JniImpl.superLinesRemoval(
                    linesAddr=inViewModel.lines.nativeObj,
                    outputAddr=if (isFastForward) 0 else colored.nativeObj, //colored.nativeObj,
                    mids=mids,
                    width=inViewModel.sizeWidth,
                    height=inViewModel.sizeHeight,
                    minLength=p[0],
                    slineSize=p[1],
                    rejectAngle=inViewModel.rejectAngle,
                    scale=getViewModel<ThresholdFragment.VM>().inverseScale
            )
        }

        override fun update(frag: ImageViewFragment, p: IntArray) {
            frag.tryOrComplain {
                logTimeSec { update(p) }
                frag.setImageGrayscalePreview(colored)
            }
        }
    }
}
