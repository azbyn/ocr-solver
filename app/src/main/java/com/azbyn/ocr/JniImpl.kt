package com.azbyn.ocr

import org.opencv.core.Mat

fun colorMapAndNormalize(src: Mat, dst: Mat) =
        JniImpl.colorMapAndNormalize(src.nativeObj, dst.nativeObj)
fun morphologicalSkeleton(src: Mat, dst: Mat) =
        JniImpl.morphologicalSkeleton(src.nativeObj, dst.nativeObj)

object JniImpl {
    external fun blobbing(maskAddr: Long, result: ArrayList<Mat>,
                          desiredDensity: Int): IntArray?

    external fun bitwiseAndBlobs(boundsArr: IntArray, blobs: ArrayList<Mat>, imgAddr: Long,
                                 dilateVal: Int, erodeVal: Int)

    external fun bitwiseAndSingleBlob(x: Int, y: Int, w: Int, h: Int,
                                      blobAddr: Long, imgAddr: Long,
                                      dilateVal: Int, erodeVal: Int)

    external fun linesExtract(matAddr: Long, linesAddr: Long, outputAddr: Long,
                              thresh: Int, length: Double, rejectAngle: Double)


    external fun colorMapAndNormalize(srcAddr: Long, dstAddr: Long)
    external fun morphologicalSkeleton(srcAddr: Long, dstAddr: Long)


// superLinesGetDensity(lines.nativeObj, colored.nativeObj, minLength=p[0],
// slineSize=p[1], rejectAngle=rejectAngle, desiredDensity=DESIRED_DENSITY)
    external fun superLinesGetDensity(linesAddr: Long, outputAddr: Long,
                                      minLength: Int, slineSize: Int, rejectAngle: Double,
                                      desiredDensity: Int): Int


    external fun superLinesRemoval(linesAddr: Long, outputAddr: Long,
                                   mids: Long,
                                   width: Int, height: Int,
                                   minLength: Int, slineSize: Int,
                                   rejectAngle: Double, scale: Double)

    external fun newSlineMids(): Long
    external fun delSlineMids(addr: Long)


    external fun getLinesMask(resultAddr: Long, thickness: Int,
                              midsAddr: Long, width: Int, height: Int)

        //external fun matrixScreenToDrawable(mat: FloatArray, vec: FloatArray)
    //external fun matrixDrawableToScreen(mat: FloatArray, vec: PointF)
}
