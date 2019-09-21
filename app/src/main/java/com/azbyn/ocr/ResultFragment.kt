package com.azbyn.ocr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.azbyn.ocr.Misc.logd
import com.azbyn.ocr.capture.CaptureFragment
import kotlinx.android.synthetic.main.result.*
import org.json.JSONObject
import java.io.File

class ResultFragment : BaseFragment() {
    override val nextFragment = FragmentIndex.CAPTURE
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.result, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //logd("Accept.onViewCreated")
        back.setOnClickListener { onBack() }
        newPhoto.setOnClickListener { onOK() }
        wasGood.setOnClickListener {
            wasGood.isChecked = !wasGood.isChecked
            //viewModel.update(this, autoRotate.isChecked)
        }
        save.setOnClickListener { onSave() }
    }

    override fun initImpl(isOnBack: Boolean) {
        wasGood.isChecked = true
    }

    override fun saveData(path: String) = JSONObject().apply {
        put("good", wasGood.isChecked)
    }

    private fun onSave() {
        val t = measureTimeSec {
            tryOrComplain {
                val cf = getViewModel<CaptureFragment.VM>()
                val timestamp = cf.timestamp
                val dir = File(mainActivity.path, timestamp)
                if (!dir.exists()) {
                    if (!dir.mkdir()) {
                        loge("mkdir failed for ${dir.path}")
                        return
                    }
                }
                val json = JSONObject().apply {
                    put("version", BuildConfig.VERSION_NAME)
                    put("versionCode", BuildConfig.VERSION_CODE)
                    put("buildType", BuildConfig.BUILD_TYPE)
                    put("timestamp", timestamp)
                    put("now", cf.getTimeStampNow())
                }
                val path = dir.path
                //logd("ts: $timestamp")
                for (i in FragmentIndex.values) {
                    val f = fragmentManager.getItem(i)
                    f.mainActivity = mainActivity
                    val obj = f.saveData(path)
                    if (obj != null) {
                        json.put(f.className, obj)
                    }
                }
                File(dir, "data.json").writeText(json.toString(4))
                logd("$timestamp ${json.toString(4)}")
                cf.saveLast(mainActivity)
            }
        }
        logd("saved in ${t}s")
        showToast("saved in ${t}s")
    }
}