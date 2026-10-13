package com.vzmah.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NextShowActiveTest {

    @Test
    fun `show session opens with either start key and closes only with escape`() {
        assertEquals(true, nextShowActive(false, PresentationCommand.StartShow))
        assertEquals(true, nextShowActive(false, PresentationCommand.StartShowFromCurrent))
        assertEquals(false, nextShowActive(true, PresentationCommand.EndShow))
    }

    @Test
    fun `slide navigation and blank keep the session state`() {
        assertEquals(true, nextShowActive(true, PresentationCommand.NextSlide))
        assertEquals(true, nextShowActive(true, PresentationCommand.PreviousSlide))
        assertEquals(true, nextShowActive(true, PresentationCommand.ToggleBlank))
        assertEquals(false, nextShowActive(false, PresentationCommand.NextSlide))
    }

    @Test
    fun `state is frozen from start to finish`() {
        var active = false
        active = nextShowActive(active, PresentationCommand.StartShow)
        assertEquals(true, active)
        active = nextShowActive(active, PresentationCommand.NextSlide)
        active = nextShowActive(active, PresentationCommand.EndShow)
        assertEquals(false, active)
    }
}