package com.azbyn.ocr.capture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.AttributeSet
import android.util.Log
import android.view.View
import kotlin.math.*

// TODO add this to CaptureTextureView (fun onDraw(): super.onDraw(); ...)


data class Quaternion(val x: Float, val y: Float, val z: Float, val w: Float) {
    constructor(arr: FloatArray) : this(arr[0], arr[1], arr[2], arr[3])
    fun toEuler(): EulerAngles {
        // roll (x-axis rotation)
        val sinr_cosp = +2.0 * (w * x + y * z)
        val cosr_cosp = +1.0 - 2.0 * (x * x + y * y)
        val roll = atan2(sinr_cosp, cosr_cosp)

        // pitch (y-axis rotation)
        val sinp = +2.0 * (w * y - z * x)
        val pitch = if (abs(sinp) >= 1) (PI / 2).withSign(sinp) // use 90 degrees if out of range
        else asin(sinp)

        // yaw (z-axis rotation)
        val siny_cosp = +2.0 * (w * z + x * y)
        val cosy_cosp = +1.0 - 2.0 * (y * y + z * z)
        val yaw = atan2(siny_cosp, cosy_cosp)
        return EulerAngles(yaw, pitch, roll)
    }
}
fun radiansMod(a: Double): Double = ((a + PI) % (2*PI)) - PI

data class EulerAngles(val yaw: Double, val pitch:Double, val roll: Double) {
    constructor() : this(0.0,0.0,0.0)

    val yawAngle get()   = yaw * 180 / PI
    val pitchAngle get() = pitch * 180 / PI
    val rollAngle get()  = roll * 180 / PI

    operator fun plus(rhs: EulerAngles): EulerAngles {
        return EulerAngles(radiansMod(yaw + rhs.yaw),
                radiansMod(pitch + rhs.pitch),
                radiansMod(roll + rhs.roll))
    }
    operator fun minus(rhs: EulerAngles): EulerAngles {
        return EulerAngles(radiansMod(yaw - rhs.yaw),
                radiansMod(pitch - rhs.pitch),
                radiansMod(roll - rhs.roll))
    }
    override fun toString(): String {
        return "(%+5.1f, %+5.1f, %+5.1f)".format(yawAngle, pitchAngle, rollAngle)
        //"(yaw %.2f pitch %.2f roll %.2f)".format(yawAngle, pitchAngle, rollAngle)
    }
}

class AngleIndicator : View, SensorEventListener {
    companion object {
        private const val TAG = "azbyn-ocr"
        private const val maxAngle = 10f//5f
        private const val lineWidth = 5f
    }

    private fun makeAngle(value: Float) : Float{
        invalidate()
        return when {
            value < -maxAngle -> -1f
            value > maxAngle -> 1f
            else -> value / maxAngle
        }
    }

    var hAngle : Float = 0f
        private set(a) { field = makeAngle(a) }
    var vAngle = 0.0f
        private set(a) { field = makeAngle(a) }

    private val paint: Paint = Paint().apply { strokeWidth = lineWidth }

    constructor(ctx: Context) : super(ctx)
    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)


    private var calibrateAngles = EulerAngles()
    private var rawAngles = EulerAngles()
    fun calibrate() {
        calibrateAngles = rawAngles
        Log.d(TAG, "Calibrate $calibrateAngles")
    }
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // ¯\_(ツ)_/¯
    }
    override fun onSensorChanged(event: SensorEvent) {
        /*
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val angleA = (atan2(x, z) * 180 / PI).toFloat()
        val angleB = (atan2(y, z) * 180 / PI).toFloat()
        val angleC = (atan2(y, x) * 180 / PI).toFloat()

        //assuming landscape mode
        val poiningDown = abs(angleA) < 45// || abs(angleB) < 45
        if (poiningDown) {
            this.paperAngle = angleA
            this.leftToRightAngle = angleB
        } else {
            this.paperAngle = angleA - 90
            this.leftToRightAngle = angleC
        }
        frag.angleIndicator.hAngle = paperAngle
        frag.angleIndicator.vAngle = leftToRightAngle
        */
        rawAngles = Quaternion(event.values).toEuler()
        val a = rawAngles - calibrateAngles
        vAngle = a.rollAngle.toFloat()
        hAngle = a.pitchAngle.toFloat()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val margin = 10f

        val w = width.toFloat()
        val h = height.toFloat()
        canvas.apply {
            val c = 0x32// 0xA5
            paint.setARGB(255, c, c, c)
            //paint.color

            drawLine(w-margin, lineWidth, w-margin, h, paint)
            drawLine(lineWidth, margin, w, margin, paint)
            drawLine(w/2, 0f, w/2, margin * 2 + lineWidth, paint)

            drawLine(w, h/2,w-(margin * 2 + lineWidth), h/2, paint)

            paint.setARGB(255, 0xB5, 0, 0)

            val w2 = w/2 - lineWidth
            val h2 = h/2 - lineWidth

            val x : Float = hAngle * w2+ w2+ lineWidth
            drawLine(x, 0f, x, margin * 2, paint)
            val y : Float = vAngle * h2 + h2 + lineWidth
            drawLine(w, y, w-(margin * 2), y, paint)
        }
    }
}