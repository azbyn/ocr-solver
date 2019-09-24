package com.azbyn.ocr.remove_lines

import com.azbyn.ocr.*
import com.azbyn.ocr.roi.BlurRoiFragment
import org.opencv.core.Mat

class BlurFragment : BaseSlidersFragment(
        SliderData("Blur", default = -1, min = 1, max = 251, stepSize = 2),
        //SliderData("Dilate", default=1, min=1, max=21, stepSize=2)) {
        SliderData("Dilate", default = -1, min = 0, max = 10, stepSize = 1)) {

    //override val fragmentIndex = FragmentManagerAdapter.BLUR
    override val viewModel: VM by viewModelDelegate()
    private val blurVM: BlurRoiFragment.VM by viewModelDelegate()

    override val topBarName: String get() = mainActivity.getString(R.string.blur)

    override fun initImpl() {
        viewModel.init(this)
        super.initImpl()
    }

    override fun initSliderData(sliderDatas: Array<SliderData>) {
        sliderDatas[0].default = blurVM.blurVal
        sliderDatas[1].default = blurVM.dilateVal
    }
    class VM : SlidersViewModel() {
        private val blurViewModel: BlurRoiFragment.VM by viewModelDelegate()
        private val baseMat get() = getViewModel<AcceptDensityFragment.VM>().resultMat
        var resultMat = Mat()
            private set

        override fun cleanup() = blurViewModel.cleanup()

        override fun update(p: IntArray, isFastForward: Boolean) {
            super.update(p, isFastForward)
            blurViewModel.updateImpl(baseMat, resultMat, blurVal=p[0], dilateVal=p[1])
        }

        override fun update(frag: ImageViewFragment, p: IntArray) {
            frag.tryOrComplain {
                update(p)
                frag.setImageGrayscalePreview(resultMat)
            }
        }
    }
}
