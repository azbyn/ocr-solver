package com.azbyn.ocr

import android.graphics.Bitmap
import android.widget.ImageView
import org.opencv.android.Utils.matToBitmap
import org.opencv.core.Mat

abstract class ImageViewFragment: BaseFragment() {
    private val previewViewModel: VM by viewModelDelegate()
    abstract fun getImageView(): ImageView

    fun setImageGrayscalePreview(src: Mat) =
            getImageView().setImageBitmap(previewViewModel.grayscaleMatToPreview(src))
    fun setImagePreview(src: Mat) =
            getImageView().setImageBitmap(previewViewModel.matToPreview(src))

    class VM : DumbViewModel() {
        private var bufferBitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        private var colored = Mat()
        fun init(bitmap: Bitmap) {
            bufferBitmap = bitmap
        }
        fun grayscaleMatToPreview(src: Mat): Bitmap {
            if (bufferBitmap.width != src.width() || bufferBitmap.height != src.height()) {
                bufferBitmap = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888)
            }
            colorMapAndNormalize(src, colored)

            matToBitmap(colored, bufferBitmap)
            return bufferBitmap
        }

        fun matToPreview(src: Mat): Bitmap {
            if (bufferBitmap.width != src.width() || bufferBitmap.height != src.height()) {
                bufferBitmap = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888)
            }
            matToBitmap(src, bufferBitmap)
            return bufferBitmap
        }
    }
}
