package com.azbyn.ocr.capture

import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import com.azbyn.ocr.Misc.logw
import com.azbyn.ocr.capture.CaptureFragmentBase.Companion.ASPECT_RATIO_TOLERANCE
import java.util.*
import java.lang.Long.signum
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.sqrt

internal object CameraUtils {
    // We want to keep the image as small as possible for better performance
    // while keeping enough details. around 4MP seems like a good option.
    fun getOptimalReducedSize(choices: Array<Size>): Size {
        val largest = Collections.max(
                listOf(*choices),
                CompareSizesByArea())
        val desiredResolutionArea = 4e6 // 4 MP
        val f = sqrt(desiredResolutionArea / (largest.width * largest.height))
        val w = (f * largest.width).toInt()
        val h = (f * largest.height).toInt()
        //logd("reduced-size $w, $h")
        return chooseOptimalSize(choices, w, h, w, h, largest)
    }

    /**
     * Given `choices` of `Size`s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as
     * the respective max size, and whose aspect ratio matches with the specified value. If such
     * size doesn't exist, choose the largest one that is at most as large as the respective max
     * size, and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended
     *                          output class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal `Size`, or an arbitrary one if none were big enough
     */
    fun chooseOptimalSize(
            choices: Array<Size>,
            textureViewWidth: Int,
            textureViewHeight: Int,
            maxWidth: Int,
            maxHeight: Int,
            aspectRatio: Size): Size {

        // Collect the supported resolutions that are at least as big as the preview Surface
        val bigEnough = ArrayList<Size>()
        // Collect the supported resolutions that are smaller than the preview Surface
        val notBigEnough = ArrayList<Size>()
        val w = aspectRatio.width
        val h = aspectRatio.height
        for (option in choices) {
            if (option.width <= maxWidth && option.height <= maxHeight &&
                    option.height == option.width * h / w) {
                if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        return when {
            bigEnough.size > 0 -> Collections.min(bigEnough, CompareSizesByArea())
            notBigEnough.size > 0 -> Collections.max(notBigEnough, CompareSizesByArea())
            else -> {
                logw("Couldn't find any suitable preview size")
                return choices[0]
            }
        }
    }

    /**
     * Return true if the two given {@link Size}s have the same aspect ratio.
     *
     * @param a first {@link Size} to compare.
     * @param b second {@link Size} to compare.
     * @return true if the sizes have the same aspect ratio, otherwise false.
     */
    fun checkAspectsEqual(a: Size, b: Size): Boolean {
        val aAspect = a.width.toDouble() / a.height
        val bAspect = b.width.toDouble() / b.height
        return abs(aAspect - bAspect) <= ASPECT_RATIO_TOLERANCE
    }

    /**
     * Rotation need to transform from the camera sensor orientation to the device's current
     * orientation.
     *
     * @param c                 the [CameraCharacteristics] to query for the camera sensor
     *                          orientation.
     * @param deviceOrientation the current device orientation relative to the native device
     *                          orientation.
     * @return the total rotation from the sensor orientation to the current device orientation.
     */
    fun sensorToDeviceRotation(c: CameraCharacteristics, deviceOrientation: Int): Int {
        val sensorOrientation = c[CameraCharacteristics.SENSOR_ORIENTATION]!!
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        return (sensorOrientation - 90 * deviceOrientation + 360) % 360
    }

    internal class CompareSizesByArea : Comparator<Size> {

        // We cast here to ensure the multiplications won't overflow
        override fun compare(lhs: Size, rhs: Size) =
                signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)

    }
}