package com.azbyn.ocr

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.azbyn.ocr.Misc.logd
import com.azbyn.ocr.Misc.logeSimple
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {
    lateinit var fragmentManager: FragmentManagerAdapter
    val path: String by lazy { getExternalFilesDir(null)!!.path }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tryOrComplain {
            setContentView(R.layout.main_activity)
            fragmentManager = FragmentManagerAdapter(supportFragmentManager,
                    findViewById(R.id.container))

            //path = getExternalFilesDir(null)!!.path
            if (savedInstanceState != null) {
                logd("MainActivity.onCreate()")
                val v = savedInstanceState.getInt("curr")
                if (v >= 0 && v <= FragmentIndex.LEN) {
                    fragmentManager.setCurrent(FragmentIndex.values[v], isOnBack=false)
                }
            } else {
                logd("THE BEGINING <onCreate>")
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("curr", fragmentManager.current)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() = fragmentManager.onBack()

    companion object {
        init {
            if (!OpenCVLoader.initDebug()) {
                logeSimple("opencv initDebug failed")
            }
            System.loadLibrary("jni")
        }
    }
}
