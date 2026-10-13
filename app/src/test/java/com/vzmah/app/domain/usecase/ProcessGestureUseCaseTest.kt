package com.vzmah.app.domain.usecase

import com.vzmah.app.domain.model.MotionSample
import com.vzmah.app.domain.model.MotionVector
import com.vzmah.app.domain.model.PresentationCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessGestureUseCaseTest {

    private val config = GestureConfig(
        nodGyroRadPerSec = 0.9,
        calmGyroRadPerSec = 0.45,
        calmAccelG = 0.12,
        calmMs = 900L,
        rearmMs = 500L,
    )

    private fun settle(useCase: ProcessGestureUseCase, fromMs: Long = 0L) {
        var now = fromMs
        repeat(20) {
            assertNull(useCase.evaluate(calm(), now))
            now += 50L
        }
    }

    @Test
    fun `nod does not fire before the phone has been still`() {
        val useCase = ProcessGestureUseCase(config)

        assertNull(useCase.evaluate(nod(rate = -2.0), nowMs = 100L))
    }

    @Test
    fun `nod fires after the calm window is filled`() {
        val useCase = ProcessGestureUseCase(config)
        settle(useCase)

        assertEquals(PresentationCommand.NextSlide, useCase.evaluate(nod(rate = -2.0), nowMs = 5_000L))
    }

    @Test
    fun `calm progress grows from zero and saturates at one`() {
        val useCase = ProcessGestureUseCase(config)

        assertNull(useCase.evaluate(calm(), 0L))
        assertEquals(0.0f, useCase.calmProgress(0L), 1e-6f)
        assertTrue(useCase.calmProgress(450L) > 0.4f)
        assertTrue(useCase.calmProgress(450L) < 0.6f)
        assertEquals(1.0f, useCase.calmProgress(2_000L), 1e-6f)
    }

    @Test
    fun `calm progress is not complete on the very first frame`() {
        val useCase = ProcessGestureUseCase(config)

        useCase.evaluate(calm(), 10_000L)

        assertEquals(0.0f, useCase.calmProgress(10_000L), 1e-6f)
    }

    @Test
    fun `walking never arms the detector`() {
        val useCase = ProcessGestureUseCase(config)

        var now = 0L
        var maxProgress = 0f
        repeat(600) {
            assertNull(useCase.evaluate(walking(), now))
            maxProgress = maxOf(maxProgress, useCase.calmProgress(now))
            now += 20L
        }

        assertTrue("Покой не должен набираться при ходьбе, получено $maxProgress", maxProgress < 0.1f)

        assertNull(useCase.evaluate(nod(rate = -6.0), now))
    }

    @Test
    fun `a single loud frame breaks the calm accumulation`() {
        val useCase = ProcessGestureUseCase(config)
        var now = 0L
        repeat(18) {
            useCase.evaluate(calm(), now)
            now += 50L
        }
        assertTrue(useCase.calmProgress(now) >= 1.0f)

        useCase.evaluate(walking(), now)
        assertEquals(0.0f, useCase.calmProgress(now), 1e-6f)
        assertNull(useCase.evaluate(nod(rate = -6.0), now))
    }

    @Test
    fun `nod forward produces next slide`() {
        val useCase = ProcessGestureUseCase(config)
        settle(useCase)

        assertEquals(PresentationCommand.NextSlide, useCase.evaluate(nod(rate = -1.5), nowMs = 9_000L))
    }

    @Test
    fun `nod backward produces previous slide`() {
        val useCase = ProcessGestureUseCase(config)
        settle(useCase)

        assertEquals(PresentationCommand.PreviousSlide, useCase.evaluate(nod(rate = 1.5), nowMs = 9_000L))
    }

    @Test
    fun `nod below threshold is ignored`() {
        val useCase = ProcessGestureUseCase(config)
        settle(useCase)

        assertNull(useCase.evaluate(nod(rate = -0.5), nowMs = 9_000L))
    }

    @Test
    fun `tilt around the other axis does not trigger`() {
        val useCase = ProcessGestureUseCase(config)
        settle(useCase)

        val roll = MotionSample(
            timestampNanos = 0L,
            rawAccel = MotionVector(0.0, MotionVector.GRAVITY, 0.0),
            linearAccel = MotionVector.ZERO,
            filteredAccel = MotionVector.ZERO,
            angularVelocity = MotionVector(0.0, 8.0, 0.0),
        )

        assertNull(useCase.evaluate(roll, nowMs = 9_000L))
    }

    @Test
    fun `rearm window blocks a second nod until the phone settles again`() {
        val useCase = ProcessGestureUseCase(config)
        settle(useCase)

        assertEquals(PresentationCommand.NextSlide, useCase.evaluate(nod(rate = -2.0), nowMs = 5_000L))

        assertNull(useCase.evaluate(nod(rate = -2.0), nowMs = 5_200L))

        var now = 5_200L
        repeat(20) {
            useCase.evaluate(calm(), now)
            now += 50L
        }
        assertEquals(PresentationCommand.NextSlide, useCase.evaluate(nod(rate = -2.0), now))
    }

    @Test
    fun `sensitivity changes the firing threshold`() {
        val useCase = ProcessGestureUseCase(config)
        settle(useCase)

        assertEquals(PresentationCommand.NextSlide, useCase.evaluate(nod(rate = -1.2), nowMs = 9_000L))

        useCase.setSensitivity(GestureSensitivity.SOFT)
        assertEquals(1.6, useCase.nodThreshold, 1e-9)
        settle(useCase, fromMs = 9_500L)
        assertNull(useCase.evaluate(nod(rate = -1.2), nowMs = 20_000L))
    }

    @Test
    fun `sharp sensitivity reacts to a very small nod`() {
        val useCase = ProcessGestureUseCase(config)
        useCase.setSensitivity(GestureSensitivity.SHARP)
        settle(useCase)

        assertEquals(0.55, useCase.nodThreshold, 1e-9)
        assertEquals(PresentationCommand.NextSlide, useCase.evaluate(nod(rate = -0.6), nowMs = 9_000L))
    }

    @Test
    fun `reset closes the detector again`() {
        val useCase = ProcessGestureUseCase(config)
        settle(useCase)
        assertEquals(PresentationCommand.NextSlide, useCase.evaluate(nod(rate = -2.0), nowMs = 5_000L))

        useCase.reset()

        assertEquals(0.0f, useCase.calmProgress(5_000L), 1e-6f)
        assertNull(useCase.evaluate(nod(rate = -2.0), nowMs = 5_000L))
    }

    @Test
    fun `nod magnitude is exposed on the sample`() {
        assertEquals(2.5, nod(rate = -2.5).nodMagnitude, 1e-9)
        assertEquals(-2.5, nod(rate = -2.5).nodRate, 1e-9)
    }

    @Test
    fun `sensitivity levels are ordered from less to more sensitive`() {
        assertTrue(GestureSensitivity.SOFT.nodGyroRadPerSec > GestureSensitivity.NORMAL.nodGyroRadPerSec)
        assertTrue(GestureSensitivity.NORMAL.nodGyroRadPerSec > GestureSensitivity.SHARP.nodGyroRadPerSec)
        assertFalse(GestureSensitivity.entries.isEmpty())
    }

    private fun calm() = sample(rate = 0.05, accel = 0.03)

    private fun walking() = sample(rate = 1.1, accel = 0.3)

    private fun nod(rate: Double) = sample(rate = rate, accel = 0.05)

    private fun sample(rate: Double, accel: Double) = MotionSample(
        timestampNanos = 0L,
        rawAccel = MotionVector(0.0, MotionVector.GRAVITY, 0.0),
        linearAccel = MotionVector(0.0, accel * MotionVector.GRAVITY, 0.0),
        filteredAccel = MotionVector(0.0, accel * MotionVector.GRAVITY, 0.0),
        angularVelocity = MotionVector(rate, 0.0, 0.0),
    )
}

