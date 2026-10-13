package com.vzmah.app.domain.usecase

import com.vzmah.app.domain.model.MotionSample
import com.vzmah.app.domain.model.MotionVector
import com.vzmah.app.domain.model.PresentationCommand
import kotlin.math.abs

data class GestureConfig(
    val nodGyroRadPerSec: Double = 0.9,
    val calmGyroRadPerSec: Double = 0.45,
    val calmAccelG: Double = 0.12,
    val calmMs: Long = 900L,
    val rearmMs: Long = 500L,
)

enum class GestureSensitivity(val nodGyroRadPerSec: Double) {

    SOFT(1.6),

    NORMAL(0.9),

    SHARP(0.55),
}

class ProcessGestureUseCase(
    private val config: GestureConfig = GestureConfig(),
) {

    private var nodThresholdRadPerSec: Double = config.nodGyroRadPerSec

    private var quietSinceMs: Long? = null

    private var lastFiredAtMs: Long? = null

    val nodThreshold: Double get() = nodThresholdRadPerSec

    fun setSensitivity(sensitivity: GestureSensitivity) {
        nodThresholdRadPerSec = sensitivity.nodGyroRadPerSec
    }

    private fun isCalm(sample: MotionSample): Boolean =
        sample.magnitude < config.calmAccelG * MotionVector.GRAVITY &&
            abs(sample.angularVelocity.x) < config.calmGyroRadPerSec

    private fun isArmedAt(nowMs: Long): Boolean {
        val quietSince = quietSinceMs ?: return false
        val lastFired = lastFiredAtMs ?: return nowMs - quietSince >= config.calmMs
        return nowMs - lastFired >= config.rearmMs && nowMs - quietSince >= config.calmMs
    }

    fun calmProgress(nowMs: Long): Float {
        if (config.calmMs <= 0L) return 1f
        val quietSince = quietSinceMs ?: return 0f
        val elapsed = nowMs - quietSince
        if (elapsed < 0L) return 0f
        return (elapsed.toDouble() / config.calmMs).coerceIn(0.0, 1.0).toFloat()
    }

    fun evaluate(sample: MotionSample, nowMs: Long): PresentationCommand? {
        val armed = isArmedAt(nowMs)

        if (!isCalm(sample) || quietSinceMs == null) {
            quietSinceMs = nowMs
        }

        if (!armed) return null
        if (abs(sample.angularVelocity.x) < nodThresholdRadPerSec) return null

        lastFiredAtMs = nowMs
        quietSinceMs = nowMs
        return if (sample.angularVelocity.x < 0.0) {
            PresentationCommand.NextSlide
        } else {
            PresentationCommand.PreviousSlide
        }
    }

    fun reset() {
        quietSinceMs = null
        lastFiredAtMs = null
    }
}

