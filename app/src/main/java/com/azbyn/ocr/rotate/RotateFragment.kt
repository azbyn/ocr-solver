package com.azbyn.ocr.rotate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.azbyn.ocr.*
import kotlinx.android.synthetic.main.rotate.*

class RotateFragment : ImageViewFragment() {
    override val nextFragment = FragmentIndex.ACCEPT
    override val prevFragment = FragmentIndex.ACCEPT

    override fun getImageView(): ImageView = imageView
    //override val fragmentIndex = FragmentManagerAdapter.ROTATE
    private val viewModel: AcceptFragment.VM by viewModelDelegate()

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.rotate, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        back.setOnClickListener { onBack() }
        reset.setOnClickListener { rotateViewer?.reset() }
        add90.setOnClickListener { rotateViewer?.rotate90() }
        ok.setOnClickListener { onOK() }

        rotateViewer.overlay = overlay
        overlay.rotateViewer = rotateViewer
    }
    override fun initImpl(isOnBack: Boolean) {
        setImagePreview(viewModel.resultMat)
        imageView.runWhenInitialized {
            overlay.initMatrix(viewModel.resultMat.width(), viewModel.resultMat.height(), imageView)
            rotateViewer!!.reset()
        }
    }
    override fun onOK() {
        //tryOrComplain {}
        viewModel.rotate(mainActivity, overlay.angle, overlay.isHorizontal)
        super.onOK()
    }
}