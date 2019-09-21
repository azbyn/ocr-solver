package com.azbyn.ocr.capture

import android.os.SystemClock

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
internal inline class CaptureTimer(private val captureTimer: Long = 0) {
    companion object {
        fun now() = CaptureTimer(SystemClock.elapsedRealtime())
    }


    //private var captureTimer: Long = 0
    /*
    /**
     * Start the timer for the pre-capture sequence.
     * Call this only with [cameraStateLock] held.
     */
    private fun startTimerLocked() {
        captureTimer = SystemClock.elapsedRealtime()
    }*/
    /**
     * Check if the timer for the pre-capture sequence has been hit.
     *
     * @return true if the timeout occurred.
     */
    fun hitTimeoutLocked() =
            (SystemClock.elapsedRealtime() - captureTimer) > CaptureFragmentBase.PRECAPTURE_TIMEOUT_MS
}
