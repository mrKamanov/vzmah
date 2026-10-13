package com.vzmah.app.domain.model

enum class MouseButton(val hidBit: Int) {
    LEFT(0x01),
    RIGHT(0x02),
    MIDDLE(0x04),
}

enum class MouseSpeed(val speed: Float) {

    SLOW(0.35f),

    NORMAL(1.0f),

    FAST(2.2f),

    CUSTOM(1.0f),
}

sealed interface MouseAction {

    data class Move(val dx: Int, val dy: Int) : MouseAction

    data class Scroll(val steps: Int) : MouseAction

    data class Button(val button: MouseButton, val pressed: Boolean) : MouseAction
}

enum class TouchPhase { DOWN, MOVE, UP, CANCEL, SCROLL }

data class TrackpadPoint(val id: Int, val x: Float, val y: Float)

data class TrackpadSample(
    val phase: TouchPhase,
    val points: List<TrackpadPoint>,
    val nowMs: Long,
)

