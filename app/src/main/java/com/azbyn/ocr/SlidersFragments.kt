package com.azbyn.ocr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.CallSuper
import org.json.JSONObject

class SliderData(val name: String, var default: Int,
                 val min: Int = 0, var max: Int = 100,
                 val stepSize: Int = 1,
                 //show float displays the value as a decimal
                 private val showFloat: Boolean = false)  {
    // move from here
    fun setMax(seekBar: SeekBar) {
        seekBar.max = (max - min) / stepSize
    }

    fun setProgress(seekBar: SeekBar, valueText: TextView, p: Int) {
        seekBar.progress = (p - min) / stepSize
        updateValueText(valueText, p)
    }
    fun setProgress(seekBar: SeekBar) {
        seekBar.progress = (default - min) / stepSize
    }
    fun updateValueText(valueText: TextView, progress: Int) {
        valueText.text =
                if (showFloat) "${progress/10}.${progress%10}"
                else progress.toString()
    }

    fun getProgress(seekBar: SeekBar): Int {
        return seekBar.progress * stepSize + min
    }
}

abstract class SlidersViewModel: BaseViewModel() {
    //this could be less than 3, but it's simpler this way
    @Suppress("SetterBackingFieldAssignment")
    val lastValues = IntArray(3)
    fun fastForward(frag: BaseFragment, p: IntArray) {
        val t = measureTimeSec {
            init(frag)
            update(p)
            cleanup()
        }
        logd("$className: $t")
    }
    abstract fun update(frag: ImageViewFragment, p: IntArray)

    @CallSuper
    protected open fun update(p: IntArray) {
        for ((i, prog) in p.withIndex()) {
            lastValues[i] = prog
        }
    }
    open fun cleanup() = Unit

    fun saveData(sliderDatas: Array<SliderData>) = JSONObject().apply {
        for ((i, sd) in sliderDatas.withIndex()) {
            put(sd.name, lastValues[i])
        }
    }
}

//this assumes the layout elements are named like this: <whaterer>1, <whatever>2 ...
abstract class DumbSlidersFragment(
        protected val sliderDatas: Array<SliderData>,
        private val layout: Int
) : ImageViewFragment(), SeekBar.OnSeekBarChangeListener {
    constructor(sd1: SliderData, sd2: SliderData, sd3: SliderData) :
            this(arrayOf(sd1, sd2, sd3), R.layout.sliders3_edit)

    constructor(sd1: SliderData, sd2: SliderData) :
            this(arrayOf(sd1, sd2), R.layout.sliders2_edit)

    constructor(sd1: SliderData) :
            this(arrayOf(sd1), R.layout.sliders1_edit)

    private companion object {
        val seekBarIndices = intArrayOf(R.id.seekBar1, R.id.seekBar2, R.id.seekBar3)
        val valueTextIndices = intArrayOf(R.id.valueText1, R.id.valueText2, R.id.valueText3)
        val infoTextIndices = intArrayOf(R.id.infoText1, R.id.infoText2, R.id.infoText3)
        val minusIndices = intArrayOf(R.id.minus1, R.id.minus2, R.id.minus3)
        val plusIndices = intArrayOf(R.id.plus1, R.id.plus2, R.id.plus3)
    }
    private var imageView: ZoomableImageView? = null
    final override fun getImageView() = imageView!!

    private val progressBuffer = IntArray(sliderDatas.size)
    private val seekBars = Array<SeekBar?>(sliderDatas.size) { null }
    private val valueTexts = Array<TextView?>(sliderDatas.size) { null }

    private var ignoreSeekBar = false
    private var isPaused = true

    protected abstract val topBarName: String

    final override fun onStopTrackingTouch(sb: SeekBar?) = Unit
    final override fun onStartTrackingTouch(sb: SeekBar?) = Unit
    final override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
        //logd("from ${sb.progress} ($isPaused, $ignoreSeekBar)")
        if (!ignoreSeekBar && !isPaused) {
            // logd("updated")
            update()
        }
    }

    final override fun onCreateView(i: LayoutInflater, container: ViewGroup?, b: Bundle?): View?
            = i.inflate(layout, container, false)

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //logd("OIDA, i'm $this")
        view.findViewById<TextView>(R.id.topText).text = topBarName
        view.findViewById<View>(R.id.back).setOnClickListener { onBack() }
        view.findViewById<View>(R.id.ok).setOnClickListener { onOK() }
        view.findViewById<View>(R.id.reset).setOnClickListener { onReset() }
        imageView = view.findViewById(R.id.imageView)
        fun <T: View> findIds(indices: IntArray, dst: Array<T?>) {
            for (i in sliderDatas.indices) {
                dst[i] = view.findViewById(indices[i])
            }
        }
        findIds(seekBarIndices, seekBars)
        findIds(valueTextIndices, valueTexts)
        for ((i, s) in sliderDatas.withIndex()) {
            val sb = seekBars[i]!!
            sb.setOnSeekBarChangeListener(this)
            view.findViewById<TextView>(infoTextIndices[i]).text = s.name
            view.findViewById<View>(minusIndices[i]).setOnClickListener { --sb.progress }
            view.findViewById<View>(plusIndices[i]).setOnClickListener { ++sb.progress }
        }
    }
    abstract val lastValues: IntArray
    abstract fun viewModelInit()

    final override fun initImpl(isOnBack: Boolean) {
        imageView?.resetZoom()
        //logd("$this($isOnBack)")
        viewModelInit()

        initSliderData(sliderDatas)
        runIgnoreSeekBar {
            for ((i, s) in sliderDatas.withIndex()) {
                s.setMax(seekBars[i]!!)
            }
        }
        if (isOnBack) {
            runIgnoreSeekBar {
                for ((i, sd) in sliderDatas.withIndex()) {
                    sd.setProgress(seekBars[i]!!, valueTexts[i]!!, lastValues[i])
                }
            }
            updateImpl(progressBuffer)
        } else {
            onReset()
        }
        initImpl()
    }
    open fun updateImpl(p: IntArray) = Unit
    open fun initImpl() = Unit

    @CallSuper
    override fun onResume() {
        super.onResume()
        isPaused = false
    }
    @CallSuper
    override fun onPause() {
        super.onPause()
        //logd("paused $this")
        isPaused = true
    }
    @CallSuper
    open fun onReset() {
        imageView?.resetZoom()
        runIgnoreSeekBar {
            for ((i, s) in sliderDatas.withIndex()) {
                s.setProgress(seekBars[i]!!)
            }
        }
        update()
    }
    fun update() {
        //logd("$this()")
        for ((i, s) in sliderDatas.withIndex()) {
            progressBuffer[i] = s.getProgress(seekBars[i]!!)
            s.updateValueText(valueTexts[i]!!, progressBuffer[i])
        }
        //viewModel.lastValues = progressBuffer
        updateImpl(progressBuffer)
    }

    final override fun fastForward() {
        //logd("$this()")
        initSliderData(sliderDatas)
        for ((i, s) in sliderDatas.withIndex()) {
            progressBuffer[i] = s.default
        }
        fastForwardImpl(progressBuffer)
    }
    open fun fastForwardImpl(p: IntArray) = Unit

    private inline fun runIgnoreSeekBar(f: () -> Unit) {
        ignoreSeekBar = true
        f()
        ignoreSeekBar = false
    }
    open fun initSliderData(sliderDatas: Array<SliderData>) = Unit
}
abstract class BaseSlidersFragment : DumbSlidersFragment {
    constructor(sd1: SliderData, sd2: SliderData, sd3: SliderData) : super(sd1, sd2, sd3)
    constructor(sd1: SliderData, sd2: SliderData) : super(sd1, sd2)
    constructor(sd1: SliderData) : super(sd1)

    abstract val viewModel: SlidersViewModel

    final override val lastValues: IntArray get() = viewModel.lastValues
    final override fun viewModelInit() {
        viewModel.init(this)
    }

    @CallSuper
    override fun updateImpl(p: IntArray) = viewModel.update(this, p)

    final override fun fastForwardImpl(p: IntArray) = viewModel.fastForward(this, p)
    final override fun saveData(path: String) = viewModel.saveData(sliderDatas)

    @CallSuper
    override fun lightCleanup() = viewModel.cleanup()
}
