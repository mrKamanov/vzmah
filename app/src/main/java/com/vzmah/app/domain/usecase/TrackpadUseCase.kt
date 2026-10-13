package com.vzmah.app.domain.usecase

import com.vzmah.app.domain.model.MouseAction
import com.vzmah.app.domain.model.MouseButton
import com.vzmah.app.domain.model.TouchPhase
import com.vzmah.app.domain.model.TrackpadSample
import kotlin.math.abs
import kotlin.math.roundToInt

data class TrackpadConfig(
    val speed: Float = 1.0f,
    val tapSlopPx: Float = 14f,
    val tapTimeoutMs: Long = 320L,
    val longPressMs: Long = 450L,
    val scrollScale: Float = 0.5f,
    val twoFingerSlopPx: Float = 24f,
    val accelStartPxMs: Float = 0.4f,
    val accelGain: Float = 2.5f,
    val maxAccel: Float = 5.0f,
)

class TrackpadUseCase(
    config: TrackpadConfig = TrackpadConfig(),
) {

    private var cfg: TrackpadConfig = config

    private var singleActive = false
    private var dragging = false
    private var downAtMs = 0L
    private var movedPx = 0f
    private var accumX = 0f
    private var accumY = 0f

    private var lastMoveAtMs = 0L
    private var velocityPxMs = 0f

    private var twoFingerActive = false
    private var twoFingerMovedPx = 0f

    private var scrollRemainder = 0f

    fun setSpeed(speed: Float) {
        cfg = cfg.copy(speed = speed.coerceIn(MIN_SPEED, MAX_SPEED))
    }

    fun setScrollScale(scale: Float) {
        cfg = cfg.copy(scrollScale = scale.coerceIn(MIN_SCROLL_SCALE, MAX_SCROLL_SCALE))
    }

    fun onSample(sample: TrackpadSample): List<MouseAction> = when (sample.phase) {
        TouchPhase.DOWN -> onDown(sample)
        TouchPhase.MOVE -> onDelta(sample.points.firstOrNull()?.x ?: 0f, sample.points.firstOrNull()?.y ?: 0f, sample.nowMs)

        TouchPhase.SCROLL -> onScroll(
            steps = sample.points.firstOrNull()?.x?.toInt() ?: 0,
            distance = sample.points.firstOrNull()?.y ?: 0f,
        )
        TouchPhase.UP -> onUp(sample)
        TouchPhase.CANCEL -> onCancel()
    }

    fun onDelta(dx: Float, dy: Float, nowMs: Long): List<MouseAction> {
        if (!singleActive) return emptyList()
        val actions = mutableListOf<MouseAction>()

        movedPx += abs(dx) + abs(dy)
        updateVelocity(dx, dy, nowMs)

        if (!dragging &&
            movedPx <= cfg.tapSlopPx &&
            nowMs - downAtMs >= cfg.longPressMs
        ) {
            dragging = true
            actions += MouseAction.Button(MouseButton.LEFT, pressed = true)
        }

        val accel = accelMultiplier()
        accumX += dx * cfg.speed * accel
        accumY += dy * cfg.speed * accel
        val dxInt = accumX.toInt()
        val dyInt = accumY.toInt()
        accumX -= dxInt
        accumY -= dyInt
        if (dxInt != 0 || dyInt != 0) actions += MouseAction.Move(dxInt, dyInt)
        return actions
    }

    private fun updateVelocity(dx: Float, dy: Float, nowMs: Long) {
        val dtMs = (nowMs - lastMoveAtMs).coerceIn(1L, MAX_FRAME_MS).toFloat()
        lastMoveAtMs = nowMs
        val instant = (abs(dx) + abs(dy)) / dtMs
        velocityPxMs = velocityPxMs * VEL_SMOOTHING + instant * (1f - VEL_SMOOTHING)
    }

    private fun accelMultiplier(): Float {
        val over = velocityPxMs - cfg.accelStartPxMs
        if (over <= 0f) return 1f
        return (1f + over * cfg.accelGain).coerceAtMost(cfg.maxAccel)
    }

    private fun onDown(sample: TrackpadSample): List<MouseAction> {
        when (sample.points.size) {
            1 -> {
                val p = sample.points[0]
                singleActive = true
                dragging = false
                downAtMs = sample.nowMs
                movedPx = 0f
                accumX = 0f
                accumY = 0f
                lastMoveAtMs = sample.nowMs
                velocityPxMs = 0f
                twoFingerActive = false
                return emptyList()
            }

            2 -> {

                singleActive = false
                twoFingerActive = true
                twoFingerMovedPx = 0f
                scrollRemainder = 0f
                return if (dragging) {
                    dragging = false
                    listOf(MouseAction.Button(MouseButton.LEFT, pressed = false))
                } else {
                    emptyList()
                }
            }

            else -> {

                return emptyList()
            }
        }
    }

    private fun onUp(sample: TrackpadSample): List<MouseAction> {
        if (twoFingerActive) {
            twoFingerActive = false

            return if (twoFingerMovedPx <= cfg.twoFingerSlopPx) {
                listOf(
                    MouseAction.Button(MouseButton.RIGHT, pressed = true),
                    MouseAction.Button(MouseButton.RIGHT, pressed = false),
                )
            } else {
                emptyList()
            }
        }

        if (!singleActive) return emptyList()
        singleActive = false
        val wasDrag = dragging
        dragging = false
        val isTap = movedPx <= cfg.tapSlopPx &&
            (sample.nowMs - downAtMs) <= cfg.tapTimeoutMs

        return when {
            wasDrag -> listOf(MouseAction.Button(MouseButton.LEFT, pressed = false))
            isTap -> listOf(
                MouseAction.Button(MouseButton.LEFT, pressed = true),
                MouseAction.Button(MouseButton.LEFT, pressed = false),
            )

            else -> emptyList()
        }
    }

    private fun onScroll(steps: Int, distance: Float): List<MouseAction> {
        twoFingerMovedPx += abs(distance)
        if (steps == 0) return emptyList()

        scrollRemainder += steps * cfg.scrollScale
        val wheel = scrollRemainder.toInt()
        if (wheel == 0) return emptyList()
        scrollRemainder -= wheel
        return listOf(MouseAction.Scroll(wheel.coerceIn(-127, 127)))
    }

    private fun onCancel(): List<MouseAction> {
        val actions = mutableListOf<MouseAction>()
        if (dragging) actions += MouseAction.Button(MouseButton.LEFT, pressed = false)
        dragging = false
        singleActive = false
        twoFingerActive = false
        return actions
    }

    fun cancel(): List<MouseAction> = onCancel()

    val isDragging: Boolean get() = dragging

    private companion object {
        const val MIN_SPEED = 0.15f
        const val MAX_SPEED = 4.0f

        const val MIN_SCROLL_SCALE = 0.2f
        const val MAX_SCROLL_SCALE = 3.0f

        const val VEL_SMOOTHING = 0.55f

        const val MAX_FRAME_MS = 64L
    }
}

