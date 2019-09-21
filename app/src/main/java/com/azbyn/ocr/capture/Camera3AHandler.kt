package com.azbyn.ocr.capture

import android.hardware.camera2.CameraCharacteristics as CC
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureRequest as CR

internal class Camera3AHandler(val characteristics: CameraCharacteristics) {
    val canUseAE = characteristics[CC.CONTROL_AE_AVAILABLE_MODES]?.contains(
            CC.CONTROL_AE_MODE_ON_AUTO_FLASH) == true
    val canUseAF = characteristics[CC.CONTROL_AF_AVAILABLE_MODES]?.contains(
            CC.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true
    val canUseAWB = characteristics[CC.CONTROL_AWB_AVAILABLE_MODES]?.contains(
            CC.CONTROL_AWB_MODE_AUTO) == true
    val canUseFlash = characteristics[CC.FLASH_INFO_AVAILABLE] == true

    var flashEnabled = true
    /**
     * Whether or not the currently configured camera device is fixed-focus.
     */
    var noAFRun = false
        private set

    /**
     * Check if we are using a device that only supports the LEGACY hardware level.
     */
    val isLegacyLocked =
            characteristics[CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL] ==
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY

    fun toggleFlash(previewRequestBuilder: CaptureRequest.Builder): Boolean {
        flashEnabled = !flashEnabled
        setFlashAndAE(previewRequestBuilder)
        return flashEnabled
    }

    fun setFlashAndAE(builder: CaptureRequest.Builder) {
        //logd("update flash: $flashEnabled")
        // if there is an auto-magical flash control mode available, use it, otherwise default to
        // the "on" mode, which is guaranteed to always be available.

        builder[CR.CONTROL_AE_MODE] =
                if (canUseAE && flashEnabled) CR.CONTROL_AE_MODE_ON_AUTO_FLASH
                else CR.CONTROL_AE_MODE_ON
        //logd("canuseFlash $canUseFlash")
        if (canUseFlash) {
            builder[CR.FLASH_MODE] = if (flashEnabled) CameraMetadata.FLASH_MODE_SINGLE
            else CameraMetadata.FLASH_MODE_OFF
        }
    }

    /**
     * Configure the given [CaptureRequest.Builder] to use auto-focus, auto-exposure, and
     * auto-white-balance controls if available.
     * Call this only with [cameraStateLock] held.
     *
     * @param builder the builder to configure.
     */
    fun setup3AControlsLocked(builder: CaptureRequest.Builder) {
        //logd("setup3A $flashEnabled")
        // Enable auto-magical 3A run by camera device
        builder[CR.CONTROL_MODE] = CC.CONTROL_MODE_AUTO

        val minFocusDist = characteristics[CC.LENS_INFO_MINIMUM_FOCUS_DISTANCE] ?: 0.0

        // If MINIMUM_FOCUS_DISTANCE is 0, lens is fixed-focus and we need to skip the AF run.
        noAFRun = minFocusDist == 0.0

        if (!noAFRun) {
            // If there is a "continuous picture" mode available, use it, otherwise default to AUTO.
            builder[CR.CONTROL_AF_MODE] =
                    if (canUseAF)
                        CR.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    else CR.CONTROL_AF_MODE_AUTO
        }

        // if there is an auto-magical flash control mode available, use it, otherwise default to
        // the "on" mode, which is guaranteed to always be available.
        setFlashAndAE(builder)

        // If there is an auto-magical white balance control mode available, use it.
        if (canUseAWB) {
            // Allow AWB to run auto-magically if this device supports this
            builder[CR.CONTROL_AWB_MODE] = CR.CONTROL_AWB_MODE_AUTO
        }
    }
}
