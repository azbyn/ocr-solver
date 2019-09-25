package com.azbyn.ocr

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.azbyn.ocr.Misc.logd
import org.json.JSONObject
import kotlin.reflect.KProperty

abstract class DumbViewModel: ViewModel() {
    open fun quickInit(frag: BaseFragment) = Unit
    protected open fun logd(s: String) { logd(s, offset=1) }

    val className: String get() {
        val full = this::class.java.name
        return full.substring(full.lastIndexOf('.')+1)
    }
}
abstract class BaseViewModel: DumbViewModel() {
    lateinit var viewModelProvider: ViewModelProvider

    class ViewModelDelegate<T: ViewModel>(val c: Class<T>) {
        operator fun getValue(thisRef: BaseViewModel, property: KProperty<*>): T =
                thisRef.viewModelProvider[c]
    }

    inline fun <reified T : ViewModel> viewModelDelegate(): ViewModelDelegate<T> {
        return ViewModelDelegate(T::class.java)
    }
    inline fun <reified T : ViewModel>getViewModel(): T = viewModelProvider[T::class.java]


    //@CallSuper
    open fun init(frag: BaseFragment) = Unit// quickInit(frag)
    final override fun quickInit(frag: BaseFragment) {
        this.viewModelProvider = frag.viewModelProvider
    }
}

abstract class BaseFragment : Fragment() {
    val className: String get() {
        val full = this::class.java.name
        return full.substring(full.lastIndexOf('.')+1)
    }
    val fragmentIndex: FragmentIndex by lazy {
        //logd("I am :${this::class.java.name}")
        FragmentIndex.get(this::class.java)
    }
    open val nextFragment: FragmentIndex = fragmentIndex.next()
    open val prevFragment: FragmentIndex = fragmentIndex.prev()

    val viewModelProvider: ViewModelProvider by lazy {
        ViewModelProvider(mainActivity, ViewModelProvider.NewInstanceFactory())
                //ViewModelProvider.AndroidViewModelFactory(mainActivity.application))
    }

    class ViewModelDelegate<T: DumbViewModel>(val c: Class<T>) {
        //private var value: T? = null
        operator fun getValue(thisRef: BaseFragment, property: KProperty<*>): T {
            val res = thisRef.viewModelProvider[c]
            res.quickInit(thisRef)
            return res
        }
    }
    inline fun <reified T : DumbViewModel>viewModelDelegate(): ViewModelDelegate<T>
            = ViewModelDelegate(T::class.java)

    inline fun <reified T : DumbViewModel>getViewModel(): T = viewModelProvider[T::class.java]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //logd("$this.onCreate() - $fragmentIndex")
        FragmentManagerAdapter.replaceFragment(fragmentIndex, this)
    }
    /*
    init {
        logd("new $this(): $activity")
    }*/
    private var _mainActivity : MainActivity? = null
    var mainActivity
        get() = _mainActivity!!
        set(v) { _mainActivity = v }

    //protected val sdPath: String
    //        get() = Environment.getExternalStorageDirectory().path
    val fragmentManager get() = mainActivity.fragmentManager
    open fun fastForward() {
        logd("fastForward not implemented? @$this")
    }

    fun setCurrent(index: FragmentIndex, isOnBack: Boolean=false) =
            fragmentManager.setCurrent(index, isOnBack)

    fun showToast(text: String) = mainActivity.runOnUiThread {
        Toast.makeText(mainActivity, text, Toast.LENGTH_SHORT).show()
    }

    private var shouldInit = false
    fun init(isOnBack: Boolean) {
        if (!isVisible && !shouldInit) {
            logd("shouldInit")
            shouldInit = true
            return
        }
        //logd("init: $this")
        initImpl(isOnBack)
    }

    final override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (shouldInit) init(isOnBack=false)
    }

    @CallSuper
    open fun onBack() = setCurrent(prevFragment, isOnBack=true)
    @CallSuper
    open fun onOK() = setCurrent(nextFragment, isOnBack=false)

    protected abstract fun initImpl(isOnBack: Boolean)

    abstract fun saveData(path: String): JSONObject?
    //open fun saveData(path: String): JSONObject? = null

    open fun lightCleanup() = Unit

    final override fun onAttach(context: Context) {
        super.onAttach(context)
        _mainActivity = context as MainActivity
    }

    final override fun onDetach() {
        super.onDetach()
        _mainActivity = null
    }
}