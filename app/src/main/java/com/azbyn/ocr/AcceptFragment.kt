package com.azbyn.ocr

import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.azbyn.ocr.Misc.logd
import com.azbyn.ocr.capture.CaptureFragment
import kotlinx.android.synthetic.main.accept.*
import org.opencv.core.CvType.CV_64FC1
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs.imwrite
import org.opencv.imgproc.Imgproc.getRotationMatrix2D
import org.opencv.imgproc.Imgproc.warpAffine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class AcceptFragment : ImageViewFragment() {
    override val nextFragment = FragmentIndex.SELECT_ROI
    override val prevFragment = FragmentIndex.CAPTURE
    override fun getImageView(): ImageView = imageView

    private val viewModel: VM by viewModelDelegate()

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.accept, container, false)

    override fun initImpl(isOnBack: Boolean) {
        viewModel.init(this)
        imageView.resetZoom()
        setImagePreview(viewModel.resultMat)
    }
    override fun onOK() {
        viewModel.writeMatAsync(mainActivity.lastFilePath)
        super.onOK()
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //logd("Accept.onViewCreated")
        back.setOnClickListener { onBack() }
        rotate.setOnClickListener { setCurrent(FragmentIndex.ROTATE) }
        crop.setOnClickListener { setCurrent(FragmentIndex.CROP) }
        ok.setOnClickListener { onOK() }
        fastForward.setOnClickListener {
            fragmentManager.fastForwardTo(FragmentIndex.ACCEPT_DENSITY) {
                viewModel.writeMatAsync(mainActivity.lastFilePath)
            }
        }
        feelingLucky.setOnClickListener {
            fragmentManager.fastForwardTo(FragmentIndex.FINAL, msg="Felt lucky for") {
                viewModel.writeMatAsync(mainActivity.lastFilePath)
            }
        }
    }

    class VM : BaseViewModel() {
        val resultMat get() = getViewModel<CaptureFragment.VM>().mat

        fun writeMatAsync(path: String) {
            /*
            AsyncTask.execute {
                val t = measureTimeSec {
                    imwrite(path, resultMat)
                }
                logd("wrote img done in $t")
            }*/
        }
        fun crop(ctx: Context, roi: CvRect) {
            ctx.tryOrComplain {
                resultMat.submat(roi).copyTo(resultMat)
            }
        }
        fun rotate(ctx: Context, angle: Double, isHorizontal: Boolean) {
            val w = resultMat.width().toDouble()
            val h = resultMat.height().toDouble()
            ctx.tryOrComplain {
                if (isHorizontal) {
                    // More info about the math involved:
                    // https://docs.opencv.org/4.1.0/d4/d61/tutorial_warp_affine.html
                    val m = Mat(2, 3, CV_64FC1)

                    val a = -angle * PI / 180
                    val c = cos(a)
                    val s = sin(a)
                    // this makes it that m*(w/2, h/2, 1) = (h/2, w/2)^t
                    // and that the image gets rotated by the angle
                    m.put(0, 0,
                            +c, s, (1-s)*(h/2) - c * (w/2),
                            -s, c, (1+s)*(w/2) - c * (h/2))

                    warpAffine(resultMat, resultMat, m, Size(h, w))
                } else {
                    //logd("angle $angle")
                    val m = getRotationMatrix2D(Point(w / 2, h / 2), -angle, 1.0)
                    warpAffine(resultMat, resultMat, m, resultMat.size()) // Size(w, h))
                }
            }
        }
    }
}