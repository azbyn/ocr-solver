
package com.azbyn.ocr

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.CallSuper
import androidx.lifecycle.AndroidViewModel
import com.azbyn.ocr.Misc.fmtMsg
import com.azbyn.ocr.Misc.logeImpl
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger
import kotlin.system.measureTimeMillis

const val DESIRED_DENSITY = 30
const val GRAYED_OUT_COLOR: Int = 0xA0_00_00_00.toInt()
fun Float.format(digits: Int=2) = "%.${digits}f".format(this)
fun Double.format(digits: Int=2) = "%.${digits}f".format(this)

object Misc {
    private const val TAG = "azbyn-ocr"

    private val formater = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY)

    fun fmtMsg(priority: String, msg: String, className: String, funName: String): String =
            "${formater.format(Calendar.getInstance().time)} $priority @$className.$funName: $msg"
    fun fmtMsg(priority: String, msg: String, offset: Int): String {
        val st = Thread.currentThread().stackTrace[5+offset]
        val className = st.className.substringAfterLast('.')
        return fmtMsg(priority, msg, className, st.methodName)
    }

    //TODO write to a log file
    fun logd(msg: String = "", e: Throwable? = null, offset: Int=0) =
            Log.d(TAG, fmtMsg("D", msg, offset), e)

    fun logw(msg: String = "", e: Throwable? = null, offset: Int=0) =
            Log.w(TAG, fmtMsg("D", msg, offset), e)
    fun whyIsThisCalled() {
        val st = Thread.currentThread().stackTrace
        for (i in 3 until st.size) {
            val el = st[i]
            Log.i(TAG, "${el.className}.${el.methodName}@${el.lineNumber}")
        }
    }

    fun logeImpl(ctx: Context, msg: String = "", e: Throwable? = null, offset: Int) {
        val st = e?.stackTrace?.get(0) ?: Thread.currentThread().stackTrace[5+offset]
        val className = st.className.substringAfterLast('.')
        val funName = st.methodName

        val stackTraceMsg = if (e != null) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            sw.toString()
        } else {
            ""
        }
        Log.e(TAG, fmtMsg("E", "", className, funName), e)
        AlertDialog.Builder(ctx)
                .setTitle("Error @$className.$funName")
                .setMessage("$msg: $stackTraceMsg")
                .setPositiveButton(android.R.string.ok) { _, _ -> Unit }//ctx.finish()
                .show()
    }
    fun logeSimple(msg: String = "", e: Throwable? = null) = Log.e(TAG, fmtMsg("E", msg, offset=0), e)
}
inline fun BaseFragment.tryOrComplain(f: () -> Unit) {
    try {
       f()
    } catch (e: Throwable) {
        loge(e)
    }
//fun BaseFragment.loge(e: Throwable?) = logeImpl(mainActivity, "", e)
}

fun BaseFragment.loge(e: Throwable?) = logeImpl(mainActivity, "", e, 0)
fun BaseFragment.loge(msg: String, offset: Int=0) =
        logeImpl(mainActivity, msg, null, offset)

//fun loge(ctx: Context, e: Throwable?) = logeImpl(ctx, "", e)
//fun loge(ctx: Context, msg: String) = logeImpl(ctx, msg, null)

inline fun Context.tryOrComplain(f: () -> Unit) {
    try {
        f()
    } catch (e: Throwable) {
        logeImpl(this, "", e, offset=0)
    }
}
inline fun measureTimeSec(f: () -> Unit): Float = measureTimeMillis(f) / 1000f

fun View.runWhenInitialized(f: (() -> Unit)) {
    if (width != 0) {
        f()
        return
    }
    this.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (width != 0) {
                        f()
                        viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            })
}

