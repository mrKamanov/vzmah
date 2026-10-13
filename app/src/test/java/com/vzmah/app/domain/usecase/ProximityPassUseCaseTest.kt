package com.vzmah.app.domain.usecase

import com.vzmah.app.domain.model.PresentationCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProximityPassUseCaseTest {

    private val config = ProximityPassConfig(rearmMs = 400L)

    @Test
    fun `a pass of the hand produces next slide`() {
        val useCase = ProximityPassUseCase(config)

        assertNull(useCase.onProximity(nearNow = false, nowMs = 0L))
        assertEquals(PresentationCommand.NextSlide, useCase.onProximity(nearNow = true, nowMs = 100L))
    }

    @Test
    fun `holding the hand there fires only once`() {
        val useCase = ProximityPassUseCase(config)

        useCase.onProximity(nearNow = true, nowMs = 100L)

        assertNull(useCase.onProximity(nearNow = true, nowMs = 150L))
        assertNull(useCase.onProximity(nearNow = true, nowMs = 900L))
    }

    @Test
    fun `removing and returning the hand fires again`() {
        val useCase = ProximityPassUseCase(config)

        assertEquals(PresentationCommand.NextSlide, useCase.onProximity(nearNow = true, nowMs = 100L))
        useCase.onProximity(nearNow = false, nowMs = 200L)
        assertEquals(PresentationCommand.NextSlide, useCase.onProximity(nearNow = true, nowMs = 900L))
    }

    @Test
    fun `rearm window swallows a second pass that is too close`() {
        val useCase = ProximityPassUseCase(config)

        assertEquals(PresentationCommand.NextSlide, useCase.onProximity(nearNow = true, nowMs = 100L))
        useCase.onProximity(nearNow = false, nowMs = 200L)

        assertNull(useCase.onProximity(nearNow = true, nowMs = 400L))
    }

    @Test
    fun `far events never fire`() {
        val useCase = ProximityPassUseCase(config)

        repeat(10) { index ->
            assertNull(useCase.onProximity(nearNow = false, nowMs = index * 500L))
        }
    }

    @Test
    fun `reset requires the hand to be removed before firing again`() {
        val useCase = ProximityPassUseCase(config)
        useCase.onProximity(nearNow = true, nowMs = 100L)

        useCase.reset()

        assertNull(useCase.onProximity(nearNow = true, nowMs = 5_000L))
        useCase.onProximity(nearNow = false, nowMs = 5_100L)
        assertEquals(PresentationCommand.NextSlide, useCase.onProximity(nearNow = true, nowMs = 5_200L))
    }

    @Test
    fun `entering the mode with a hand already there does not fire`() {
        val useCase = ProximityPassUseCase(config)

        useCase.sync(nearNow = true)

        assertNull(useCase.onProximity(nearNow = true, nowMs = 0L))
        assertNull(useCase.onProximity(nearNow = true, nowMs = 3_000L))
    }

    @Test
    fun `after syncing with a hand there the first real pass fires`() {
        val useCase = ProximityPassUseCase(config)
        useCase.sync(nearNow = true)

        useCase.onProximity(nearNow = false, nowMs = 200L)
        assertEquals(PresentationCommand.NextSlide, useCase.onProximity(nearNow = true, nowMs = 400L))
    }
}

