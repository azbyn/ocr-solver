package com.azbyn.ocr

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.azbyn.ocr.Misc.logd
import com.azbyn.ocr.Misc.logwtf
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {
    lateinit var fragmentManager: FragmentManagerAdapter
    val path: String by lazy { getExternalFilesDir(null)!!.path }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tryOrComplain {
            setContentView(R.layout.main_activity)
            fragmentManager = FragmentManagerAdapter(supportFragmentManager,
                    findViewById(R.id.container), savedInstanceState)
            if (savedInstanceState == null) {
                logd("THE BEGINNING <onCreate>")
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        fragmentManager.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() = fragmentManager.onBack()

    companion object {
        init {
            if (!OpenCVLoader.initDebug()) {
                logwtf("OpenCV initDebug failed")
            }
            System.loadLibrary("jni")
        }
    }
}
