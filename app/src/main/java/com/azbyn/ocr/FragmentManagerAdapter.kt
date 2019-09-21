package com.azbyn.ocr

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import com.azbyn.ocr.Misc.logd
import com.azbyn.ocr.capture.CaptureFragment
import com.azbyn.ocr.crop.CropFragment
import com.azbyn.ocr.remove_lines.*
import com.azbyn.ocr.roi.*
import com.azbyn.ocr.rotate.RotateFragment

class NoSwipeViewPager: ViewPager {
    constructor(ctx: Context) : super(ctx)
    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean = false
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = false
}

@Suppress("unused")
enum class FragmentIndex(private val clazz: Class<*>) {
    // The order matters, as pressing back defaults to the fragment above in this list
    // and pressing ok defaults to the fragment below
    CAPTURE(CaptureFragment::class.java),
    ACCEPT(AcceptFragment::class.java),
    ROTATE(RotateFragment::class.java),
    CROP(CropFragment::class.java),

    SELECT_ROI(SelectRoiFragment::class.java),
    BLUR_ROI(BlurRoiFragment::class.java),
    EDIT_THRESHOLD_MAX(EditThresholdMaxFragment::class.java),
    EDIT_THRESHOLD_MIN(EditThresholdMinFragment::class.java),
    GRID_MORPHOLOGICAL(GridMorphologicalFragment::class.java),
    EDIT_LINES(EditLinesFragment::class.java),
    SUPER_LINES(SuperLinesFragment::class.java),
    SELECT_DENSITY(SelectDensityFragment::class.java),

    ACCEPT_DENSITY(AcceptDensityFragment::class.java),
    BLUR(BlurFragment::class.java),
    THRESHOLD(ThresholdFragment::class.java),
    LINES(LinesFragment::class.java),
    SUPER_LINES_MK2(SuperLinesMk2Fragment::class.java),
    LINES_MASK(LinesMaskFragment::class.java),
    REMOVE_LINES(RemoveLinesFragment::class.java),
    BLOB_MASK1(BlobMask1Fragment::class.java),
    BLOB_MASK2(BlobMask2Fragment::class.java),
    BLOBBING(BlobbingFragment::class.java),

    RESULT(ResultFragment::class.java)
    ;
    fun newInstance(): BaseFragment = clazz.newInstance() as BaseFragment
    fun prev() : FragmentIndex {
        return if (ordinal == 0) this
        else values[ordinal - 1]
    }
    fun next() : FragmentIndex {
        return if (ordinal == LEN - 1) this
        else values[ordinal + 1]
    }
    companion object {
        val values = values()
        val LEN = values.size
        val FINAL = values[LEN-1]

        private val indexMap = mutableMapOf<Class<*>, FragmentIndex>()
        fun get(clazz: Class<*>): FragmentIndex = indexMap[clazz]!!
        //fun fromInt(int: Int) = values[int]
        init {
            for (i in values) {
                indexMap[i.clazz] = i
            }
        }
    }

}
class FragmentManagerAdapter(fm: FragmentManager, private val viewPager: NoSwipeViewPager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    init {
        viewPager.adapter = this
    }
    companion object {
        private val fragments = Array<BaseFragment?>(FragmentIndex.LEN) { null }
        init {
            for (i in FragmentIndex.values()) {
                fragments[i.ordinal] = i.newInstance()
            }
        }

        fun replaceFragment(index: FragmentIndex, frag: BaseFragment) {
            fragments[index.ordinal] = frag
        }
    }

    override fun getItem(position: Int): BaseFragment = fragments[position]!!
    fun getItem(index: FragmentIndex) = getItem(index.ordinal)

    override fun getCount(): Int = FragmentIndex.LEN
    val currentFragment
        get() = fragments[viewPager.currentItem]

    val current: Int get() = viewPager.currentItem
    val currentIndex: FragmentIndex get() = FragmentIndex.values[current]

    fun setCurrent(index: FragmentIndex, isOnBack: Boolean) {
        currentFragment?.lightCleanup()
        viewPager.currentItem = index.ordinal
        currentFragment!!.init(isOnBack)
    }
    fun onBack() {
        currentFragment!!.onBack()
    }

    private var pendingFastForward = false
/*
    fun fastForwardTo(to: FragmentIndex, msg: String = "Done in") = fastForwardTo(to, msg) {}
    fun fastForwardTo(to: FragmentIndex, after: () -> Unit) = fastForwardTo(to, "Done in", after)*/

    // TODO add file 'last.txt' with last folder, and use that for use saved
    // and gray it out if it doesn't exist

    // TODO add AcceptBlobsFragment \w seeing individual blobs and removal?

    // TODO left-swipe list of fragments selector
    // TODO crash data with images saved etc.

    fun fastForwardTo(to: FragmentIndex, msg: String = "Done in" /*, after: () -> Unit*/) {
        //logd("pending? $pendingFastForward")
        if (pendingFastForward) return
        if (currentIndex == to) return
        pendingFastForward = true
        //logd("skip")
        val frag = currentFragment!!
        frag.tryOrComplain {
            val t = measureTimeSec {
                var fi: FragmentIndex = currentIndex
                val mainActivity = currentFragment!!.mainActivity
                while (fi != to) {
                    val f = getItem(fi)
                    f.mainActivity = mainActivity
                    f.fastForward()
                    fi = f.nextFragment
                }
            }
            //after()
            logd("$msg ${t}s.")
            frag.showToast("$msg ${t}s.")

            setCurrent(to, isOnBack=false)
        }
        pendingFastForward = false
        //logd("finish")
    }
}