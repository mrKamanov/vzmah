package com.vzmah.app.domain.usecase

import com.vzmah.app.domain.model.MouseAction
import com.vzmah.app.domain.model.MouseButton
import com.vzmah.app.domain.model.TouchPhase
import com.vzmah.app.domain.model.TrackpadPoint
import com.vzmah.app.domain.model.TrackpadSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadUseCaseTest {

    private val config = TrackpadConfig(
        speed = 1.0f,
        tapSlopPx = 14f,
        tapTimeoutMs = 320L,
        longPressMs = 450L,
        scrollScale = 1.0f,
        twoFingerSlopPx = 24f,
    )

    private fun pad(config: TrackpadConfig = this.config) = TrackpadUseCase(config)

    private fun down(x: Float, y: Float, nowMs: Long, id: Int = 0) =
        TrackpadSample(TouchPhase.DOWN, listOf(TrackpadPoint(id, x, y)), nowMs)

    private fun move(dx: Float, dy: Float, nowMs: Long, id: Int = 0) =
        TrackpadSample(TouchPhase.MOVE, listOf(TrackpadPoint(id, dx, dy)), nowMs)

    private fun up(x: Float, y: Float, nowMs: Long, id: Int = 0) =
        TrackpadSample(TouchPhase.UP, listOf(TrackpadPoint(id, x, y)), nowMs)

    private fun twoFinger(phase: TouchPhase, x: Float, y: Float, nowMs: Long) =
        TrackpadSample(phase, listOf(TrackpadPoint(0, x, y), TrackpadPoint(1, x, y + 10f)), nowMs)

    @Test
    fun `dragging a finger moves the pointer`() {
        val pad = pad()

        pad.onSample(down(100f, 100f, 0L))

        val actions = pad.onSample(move(4f, 0f, 16L))

        assertEquals(listOf(MouseAction.Move(4, 0)), actions)
    }

    @Test
    fun `a fast flick accelerates the pointer`() {
        val pad = pad()

        pad.onSample(down(0f, 0f, 0L))

        val moves = pad.onSample(move(40f, 0f, 16L)).filterIsInstance<MouseAction.Move>()

        assertEquals(1, moves.size)
        assertTrue("fast movement must be amplified, got dx=${moves[0].dx}", moves[0].dx > 40)
    }

    @Test
    fun `slow movement keeps one to one precision`() {
        val pad = pad()

        pad.onSample(down(0f, 0f, 0L))
        val moves = pad.onSample(move(3f, 0f, 16L)).filterIsInstance<MouseAction.Move>()

        assertEquals("slow movement must not be amplified", listOf(MouseAction.Move(3, 0)), moves)
    }

    @Test
    fun `slow movement is not lost to rounding`() {
        val pad = pad()

        pad.onSample(down(0f, 0f, 0L))

        assertEquals(emptyList<MouseAction>(), pad.onSample(move(0.4f, 0f, 16L)))
        assertEquals(emptyList<MouseAction>(), pad.onSample(move(0.4f, 0f, 32L)))
        assertEquals(listOf(MouseAction.Move(1, 0)), pad.onSample(move(0.4f, 0f, 48L)))
    }

    @Test
    fun `a quick tap is a left click`() {
        val pad = pad()

        pad.onSample(down(50f, 50f, 0L))
        val actions = pad.onSample(up(50f, 50f, 120L))

        assertEquals(
            listOf(
                MouseAction.Button(MouseButton.LEFT, pressed = true),
                MouseAction.Button(MouseButton.LEFT, pressed = false),
            ),
            actions,
        )
    }

    @Test
    fun `a swipe across the pad is not a click`() {
        val pad = pad()

        pad.onSample(down(0f, 0f, 0L))
        pad.onSample(move(60f, 0f, 16L))
        val actions = pad.onSample(up(60f, 0f, 40L))

        assertTrue("long swipe must not click", actions.none { it is MouseAction.Button })
    }

    @Test
    fun `holding before dragging keeps the button pressed until release`() {
        val pad = pad()

        pad.onSample(down(100f, 100f, 0L))
        val held = pad.onSample(move(1f, 0f, 500L))

        assertTrue(
            "long hold must press the button",
            held.contains(MouseAction.Button(MouseButton.LEFT, pressed = true)),
        )
        assertTrue("dragging state must be reported", pad.isDragging)

        pad.onSample(move(59f, 0f, 520L))
        val released = pad.onSample(up(160f, 100f, 600L))

        assertEquals(listOf(MouseAction.Button(MouseButton.LEFT, pressed = false)), released)
    }

    @Test
    fun `a long press without movement still ends with a single release`() {
        val pad = pad()

        pad.onSample(down(10f, 10f, 0L))
        val held = pad.onSample(move(0f, 0f, 600L))
        assertTrue(held.contains(MouseAction.Button(MouseButton.LEFT, pressed = true)))

        val released = pad.onSample(up(10f, 10f, 700L))
        assertEquals(1, released.size)
        assertEquals(MouseAction.Button(MouseButton.LEFT, pressed = false), released[0])
    }

    @Test
    fun `cancel releases a held button`() {
        val pad = pad()

        pad.onSample(down(0f, 0f, 0L))
        pad.onSample(move(0f, 0f, 600L))
        assertTrue(pad.isDragging)

        val actions = pad.onSample(TrackpadSample(TouchPhase.CANCEL, emptyList(), 700L))

        assertEquals(listOf(MouseAction.Button(MouseButton.LEFT, pressed = false)), actions)
        assertTrue("dragging must be cleared", !pad.isDragging)
    }

    @Test
    fun `two finger tap is a right click`() {
        val pad = pad()

        pad.onSample(twoFinger(TouchPhase.DOWN, 50f, 50f, 0L))
        val actions = pad.onSample(twoFinger(TouchPhase.UP, 50f, 50f, 150L))

        assertEquals(
            listOf(
                MouseAction.Button(MouseButton.RIGHT, pressed = true),
                MouseAction.Button(MouseButton.RIGHT, pressed = false),
            ),
            actions,
        )
    }

    @Test
    fun `two finger scroll is not a right click`() {
        val pad = pad()

        pad.onSample(twoFinger(TouchPhase.DOWN, 50f, 50f, 0L))

        val scrolled = pad.onSample(
            TrackpadSample(TouchPhase.SCROLL, listOf(TrackpadPoint(0, -1f, 60f)), 40L),
        )
        assertEquals(listOf(MouseAction.Scroll(-1)), scrolled)

        val actions = pad.onSample(twoFinger(TouchPhase.UP, 50f, 90f, 400L))

        assertTrue(
            "scroll must not right click, got $actions",
            actions.none { it is MouseAction.Button },
        )
    }

    @Test
    fun `second finger cancels pointer movement of the first`() {
        val pad = pad()

        pad.onSample(down(0f, 0f, 0L))
        val moved = pad.onSample(move(30f, 0f, 16L))
        assertTrue(moved.any { it is MouseAction.Move })

        pad.onSample(twoFinger(TouchPhase.DOWN, 30f, 0f, 20L))
        val afterSecond = pad.onSample(twoFinger(TouchPhase.MOVE, 0f, 0f, 40L))

        assertTrue(
            "pointer must not move while two fingers are down, got $afterSecond",
            afterSecond.none { it is MouseAction.Move },
        )
    }

    @Test
    fun `speed scales the pointer movement`() {
        val pad = pad(config.copy(speed = 2.0f))

        pad.onSample(down(0f, 0f, 0L))
        assertEquals(listOf(MouseAction.Move(20, 0)), pad.onSample(move(10f, 0f, 16L)))
    }

    @Test
    fun `setSpeed is applied to later movement`() {

        val pad = pad(config.copy(accelStartPxMs = 1000f))

        pad.onSample(down(0f, 0f, 0L))
        pad.setSpeed(0.25f)

        var x = 0f
        var totalDx = 0
        for (step in 1..4) {
            x = step * 10f
            totalDx += pad.onSample(move(x - (step - 1) * 10f, 0f, step * 16L))
                .filterIsInstance<MouseAction.Move>()
                .sumOf { it.dx }
        }

        assertEquals(10, totalDx)
    }

    @Test
    fun `speed is clamped to a sane range`() {
        val pad = pad()

        pad.setSpeed(1000f)
        pad.onSample(down(0f, 0f, 0L))
        val fast = pad.onSample(move(1f, 0f, 16L))
        val maxMove = fast.filterIsInstance<MouseAction.Move>().maxOfOrNull { it.dx } ?: 0
        assertTrue("speed must be clamped, got dx=$maxMove", maxMove <= 4)
    }
}
