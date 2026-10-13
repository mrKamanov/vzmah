package com.vzmah.app.data.sensor

import com.vzmah.app.domain.model.MotionVector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

class StepFilter(
    private val sampleRateHz: Double = DEFAULT_SAMPLE_RATE_HZ,
    private val gravityTimeConstantSec: Double = 1.5,
    private val cadenceFrequencyHz: Double = 2.0,
    private val cadenceQuality: Double = 0.8,
    private val baselineTimeConstantSec: Double = 0.3,
) {

    private val notch = NotchFilter.forFrequency(
        sampleRateHz = sampleRateHz,
        centerHz = cadenceFrequencyHz,
        quality = cadenceQuality,
    )

    private var gravityEstimate = MotionVector(0.0, MotionVector.GRAVITY, 0.0)
    private var baseline = MotionVector.ZERO
    private var lastTimestampSec: Double? = null
    private var initialized = false

    fun reset() {
        gravityEstimate = MotionVector(0.0, MotionVector.GRAVITY, 0.0)
        baseline = MotionVector.ZERO
        lastTimestampSec = null
        initialized = false
        notch.reset()
    }

    fun process(timestampSec: Double, rawAccel: MotionVector): FilteredMotion {
        if (!initialized) {
            initialized = true
            gravityEstimate = rawAccel
            lastTimestampSec = timestampSec
            return FilteredMotion(linearAccel = MotionVector.ZERO, filteredAccel = MotionVector.ZERO)
        }

        val last = lastTimestampSec ?: timestampSec
        val dt = (timestampSec - last).coerceIn(MIN_DT_SEC, MAX_DT_SEC)
        lastTimestampSec = timestampSec

        gravityEstimate = lowPass(gravityEstimate, rawAccel, dt, gravityTimeConstantSec)
        val linear = rawAccel - gravityEstimate

        baseline = lowPass(baseline, linear, dt, baselineTimeConstantSec)
        val highPassed = linear - baseline

        val filtered = notch.process(highPassed)
        return FilteredMotion(linearAccel = linear, filteredAccel = filtered)
    }

    private fun lowPass(
        state: MotionVector,
        input: MotionVector,
        dt: Double,
        timeConstantSec: Double,
    ): MotionVector {
        val alpha = 1.0 - exp(-dt / timeConstantSec)
        return MotionVector(
            x = state.x + (input.x - state.x) * alpha,
            y = state.y + (input.y - state.y) * alpha,
            z = state.z + (input.z - state.z) * alpha,
        )
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 50.0

        const val CADENCE_LOW_HZ = 1.5
        const val CADENCE_HIGH_HZ = 2.5

        private const val MIN_DT_SEC = 1e-4
        private const val MAX_DT_SEC = 0.5
    }
}

data class FilteredMotion(

    val linearAccel: MotionVector,

    val filteredAccel: MotionVector,
)

private class NotchFilter(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private val x1 = DoubleArray(3)
    private val x2 = DoubleArray(3)
    private val y1 = DoubleArray(3)
    private val y2 = DoubleArray(3)

    fun reset() {
        x1.fill(0.0); x2.fill(0.0); y1.fill(0.0); y2.fill(0.0)
    }

    fun process(input: MotionVector): MotionVector {
        val inArray = doubleArrayOf(input.x, input.y, input.z)
        val outArray = DoubleArray(3)
        for (axis in 0..2) {
            outArray[axis] = b0 * inArray[axis] + b1 * x1[axis] + b2 * x2[axis] -
                a1 * y1[axis] - a2 * y2[axis]
            x2[axis] = x1[axis]
            x1[axis] = inArray[axis]
            y2[axis] = y1[axis]
            y1[axis] = outArray[axis]
        }
        return MotionVector(outArray[0], outArray[1], outArray[2])
    }

    companion object {
        fun forFrequency(sampleRateHz: Double, centerHz: Double, quality: Double): NotchFilter {
            val nyquist = sampleRateHz / 2.0
            require(sampleRateHz > 0.0) { "sampleRateHz must be positive" }
            require(centerHz in 1e-3..nyquist) { "centerHz must be inside (0, Nyquist]" }
            require(quality > 0.0) { "quality must be positive" }

            val w0 = 2.0 * PI * centerHz / sampleRateHz
            val alpha = sin(w0) / (2.0 * quality)
            val a0 = 1.0 + alpha
            val cosW0 = cos(w0)
            return NotchFilter(
                b0 = 1.0 / a0,
                b1 = -2.0 * cosW0 / a0,
                b2 = 1.0 / a0,
                a1 = -2.0 * cosW0 / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }
    }
}
