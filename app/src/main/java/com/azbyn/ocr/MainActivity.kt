package com.azbyn.ocr

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.azbyn.ocr.Misc.logd
import com.azbyn.ocr.Misc.logeSimple
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {
    lateinit var fragmentManager: FragmentManagerAdapter
    lateinit var lastFilePath: String
    lateinit var path: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tryOrComplain {
            setContentView(R.layout.main_activity)
            fragmentManager = FragmentManagerAdapter(supportFragmentManager, findViewById(R.id.container))
            path = getExternalFilesDir(null)!!.path
            lastFilePath = "$path/img.jpg"
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
        //logd("onSaveInstanceState")
        outState.putInt("curr", fragmentManager.current)
        super.onSaveInstanceState(outState)
    }
/*
    fun writeCfg() {
        try {
            val file = File(getExternalFilesDir(null), CONFIG_FILE)
            val obj = JSONObject()
            obj.put("lastPhoto", "w_imgog.jpg")
            val output = BufferedWriter(FileWriter(file))
            output.write(obj.toString())
            output.close()
        } catch (e: IOException) {
            loge("writeCfg:", e)
        }
    }
    fun readCfg(): JSONObject? {
        return try {
            val stream = File(getExternalFilesDir(null), CONFIG_FILE).inputStream()
            //val stream = assets.open(CONFIG_FILE)
            val size = stream.available()
            val buffer = ByteArray(size)
            stream.read(buffer)
            stream.close()

            JSONObject(String(buffer, StandardCharsets.UTF_8))
        } catch (e : IOException) {
            loge("readCfg:", e)
            null
        }
    }
    */

    override fun onBackPressed() = fragmentManager.onBack()

    companion object {
        //const val CONFIG_FILE = "config.txt"
        init {
            if (!OpenCVLoader.initDebug()) {
                logeSimple("opencv initDebug failed")
            }
            System.loadLibrary("jni")
        }
    }
}
