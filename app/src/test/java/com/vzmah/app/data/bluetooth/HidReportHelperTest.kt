package com.vzmah.app.data.bluetooth

import com.vzmah.app.domain.model.PointerPreset
import com.vzmah.app.domain.model.PresentationCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HidReportHelperTest {

    @Test
    fun `report descriptor fits the SDP payload limit`() {
        assertTrue(
            "Дескриптор не должен превышать 2048 байт, получено ${HidReportHelper.REPORT_DESCRIPTOR.size}",
            HidReportHelper.REPORT_DESCRIPTOR.size <= 2048,
        )
    }

    @Test
    fun `report descriptor starts with generic desktop keyboard`() {
        val descriptor = HidReportHelper.REPORT_DESCRIPTOR

        assertEquals(0x05.toByte(), descriptor[0])
        assertEquals(0x01.toByte(), descriptor[1])
        assertEquals(0x09.toByte(), descriptor[2])
        assertEquals(0x06.toByte(), descriptor[3])
    }

    @Test
    fun `keyboard report has the standard 8 byte layout`() {
        val report = HidReportHelper.keyDown(HidReportHelper.Key.PAGE_DOWN)

        assertEquals(HidReportHelper.KEYBOARD_REPORT_SIZE, report.size)
        assertEquals(0x00.toByte(), report[0])
        assertEquals(0x00.toByte(), report[1])
        assertEquals(HidReportHelper.Key.PAGE_DOWN.toByte(), report[2])
        assertEquals(0, report.drop(3).count { it != 0.toByte() })
    }

    @Test
    fun `modifier byte is placed in the first slot`() {
        val report = HidReportHelper.keyDown(HidReportHelper.Key.B, HidReportHelper.Modifier.LEFT_CTRL)

        assertEquals(HidReportHelper.Modifier.LEFT_CTRL.toByte(), report[0])
        assertEquals(HidReportHelper.Key.B.toByte(), report[2])
    }

    @Test
    fun `key release report clears all keys`() {
        val report = HidReportHelper.keyRelease()

        assertEquals(HidReportHelper.KEYBOARD_REPORT_SIZE, report.size)
        assertTrue(report.all { it == 0.toByte() })
    }

    @Test
    fun `usage codes match the USB HID keyboard page table`() {

        assertEquals(0x05, HidReportHelper.Key.B)
        assertEquals(0x08, HidReportHelper.Key.E)
        assertEquals(0x0F, HidReportHelper.Key.L)
        assertEquals(0x13, HidReportHelper.Key.P)
        assertEquals(0x29, HidReportHelper.Key.ESC)
        assertEquals(0x3A, HidReportHelper.Key.F1)
        assertEquals(0x3B, HidReportHelper.Key.F2)
        assertEquals(0x3C, HidReportHelper.Key.F3)
        assertEquals(0x3D, HidReportHelper.Key.F4)
        assertEquals(0x3E, HidReportHelper.Key.F5)
        assertEquals(0x3F, HidReportHelper.Key.F6)
        assertEquals(0x40, HidReportHelper.Key.F7)
        assertEquals(0x41, HidReportHelper.Key.F8)
        assertEquals(0x42, HidReportHelper.Key.F9)
        assertEquals(0x43, HidReportHelper.Key.F10)
        assertEquals(0x44, HidReportHelper.Key.F11)
        assertEquals(0x45, HidReportHelper.Key.F12)
        assertEquals(0x4B, HidReportHelper.Key.PAGE_UP)
        assertEquals(0x4E, HidReportHelper.Key.PAGE_DOWN)
        assertEquals(0x4F, HidReportHelper.Key.ARROW_RIGHT)
        assertEquals(0x50, HidReportHelper.Key.ARROW_LEFT)
    }

    @Test
    fun `f5 is not confused with f3`() {

        assertNotEquals(HidReportHelper.Key.F3, HidReportHelper.Key.F5)
        assertNotEquals(HidReportHelper.Key.F3, HidReportHelper.keyCodeFor(PresentationCommand.StartShow))
        assertNotEquals(HidReportHelper.Key.F3, HidReportHelper.keyCodeFor(PresentationCommand.StartShowFromCurrent))
    }

    @Test
    fun `escape and function keys stay inside the keyboard usage page`() {

        val commands = listOf(
            PresentationCommand.NextSlide,
            PresentationCommand.PreviousSlide,
            PresentationCommand.ToggleBlank,
            PresentationCommand.StartShow,
            PresentationCommand.StartShowFromCurrent,
            PresentationCommand.EndShow,
            PresentationCommand.LaserPointer,
            PresentationCommand.PenTool,
            PresentationCommand.ArrowTool,
            PresentationCommand.EraseInk,
        )
        for (command in commands) {
            val code = HidReportHelper.keyCodeFor(command)!!
            assertTrue("usage 0x${code.toString(16)} вне диапазона", code in 0x00..0x65)
        }
    }

    @Test
    fun `key codes match the presentation mapping from the spec`() {
        assertEquals(HidReportHelper.Key.PAGE_DOWN, HidReportHelper.keyCodeFor(PresentationCommand.NextSlide))
        assertEquals(HidReportHelper.Key.PAGE_UP, HidReportHelper.keyCodeFor(PresentationCommand.PreviousSlide))
        assertEquals(HidReportHelper.Key.B, HidReportHelper.keyCodeFor(PresentationCommand.ToggleBlank))
        assertEquals(HidReportHelper.Key.F5, HidReportHelper.keyCodeFor(PresentationCommand.StartShow))
        assertNull(HidReportHelper.keyCodeFor(PresentationCommand.PointerMove(1, 1)))
    }

    @Test
    fun `end show maps to escape`() {
        assertEquals(HidReportHelper.Key.ESC, HidReportHelper.keyCodeFor(PresentationCommand.EndShow))
        assertEquals(HidReportHelper.Modifier.NONE, HidReportHelper.modifierFor(PresentationCommand.EndShow))
    }

    @Test
    fun `start from current uses shift modifier with F5`() {
        val command = PresentationCommand.StartShowFromCurrent
        assertEquals(HidReportHelper.Key.F5, HidReportHelper.keyCodeFor(command))
        assertEquals(HidReportHelper.Modifier.LEFT_SHIFT, HidReportHelper.modifierFor(command))
    }

    @Test
    fun `powerpoint pointer tools map to ctrl combos`() {

        assertEquals(HidReportHelper.Key.L, HidReportHelper.keyCodeFor(PresentationCommand.LaserPointer))
        assertEquals(HidReportHelper.Modifier.LEFT_CTRL, HidReportHelper.modifierFor(PresentationCommand.LaserPointer))

        assertEquals(HidReportHelper.Key.P, HidReportHelper.keyCodeFor(PresentationCommand.PenTool))
        assertEquals(HidReportHelper.Modifier.LEFT_CTRL, HidReportHelper.modifierFor(PresentationCommand.PenTool))

        assertEquals(HidReportHelper.Key.A, HidReportHelper.keyCodeFor(PresentationCommand.ArrowTool))
        assertEquals(HidReportHelper.Modifier.LEFT_CTRL, HidReportHelper.modifierFor(PresentationCommand.ArrowTool))
    }

    @Test
    fun `erase ink is a plain e key`() {
        assertEquals(HidReportHelper.Key.E, HidReportHelper.keyCodeFor(PresentationCommand.EraseInk))
        assertEquals(HidReportHelper.Modifier.NONE, HidReportHelper.modifierFor(PresentationCommand.EraseInk))
    }

    @Test
    fun `google slides preset maps laser to plain L and eraser to shift A`() {
        val laser = HidReportHelper.toolStroke(PresentationCommand.LaserPointer, PointerPreset.GOOGLE_SLIDES)!!
        assertEquals(HidReportHelper.Key.L, laser.usage)
        assertEquals(HidReportHelper.Modifier.NONE, laser.modifier)

        val erase = HidReportHelper.toolStroke(PresentationCommand.EraseInk, PointerPreset.GOOGLE_SLIDES)!!
        assertEquals(HidReportHelper.Key.A, erase.usage)
        assertEquals(HidReportHelper.Modifier.LEFT_SHIFT, erase.modifier)
    }

    @Test
    fun `google slides toggles pen with shift L and has no arrow binding`() {
        val pen = HidReportHelper.toolStroke(PresentationCommand.PenTool, PointerPreset.GOOGLE_SLIDES)!!
        assertEquals(HidReportHelper.Key.L, pen.usage)
        assertEquals(HidReportHelper.Modifier.LEFT_SHIFT, pen.modifier)
        assertNull(HidReportHelper.toolStroke(PresentationCommand.ArrowTool, PointerPreset.GOOGLE_SLIDES))
    }

    @Test
    fun `impress preset has no laser and maps pen arrow and eraser`() {
        assertNull(HidReportHelper.toolStroke(PresentationCommand.LaserPointer, PointerPreset.IMPRESS))
        val pen = HidReportHelper.toolStroke(PresentationCommand.PenTool, PointerPreset.IMPRESS)!!
        assertEquals(HidReportHelper.Key.P, pen.usage)
        assertEquals(HidReportHelper.Modifier.NONE, pen.modifier)
        val arrow = HidReportHelper.toolStroke(PresentationCommand.ArrowTool, PointerPreset.IMPRESS)!!
        assertEquals(HidReportHelper.Key.A, arrow.usage)
        assertEquals(HidReportHelper.Modifier.LEFT_CTRL, arrow.modifier)
        val erase = HidReportHelper.toolStroke(PresentationCommand.EraseInk, PointerPreset.IMPRESS)!!
        assertEquals(HidReportHelper.Key.E, erase.usage)
    }

    @Test
    fun `none preset sends no pointer tools and navigation still maps`() {
        for (command in listOf(
            PresentationCommand.LaserPointer,
            PresentationCommand.PenTool,
            PresentationCommand.ArrowTool,
            PresentationCommand.EraseInk,
        )) {
            assertNull(HidReportHelper.toolStroke(command, PointerPreset.NONE))
        }
        assertEquals(HidReportHelper.Key.PAGE_DOWN, HidReportHelper.keyCodeFor(PresentationCommand.NextSlide))
    }

    @Test
    fun `pointer tool detection covers only the four tools`() {
        assertTrue(PresentationCommand.LaserPointer.isPointerTool)
        assertTrue(PresentationCommand.PenTool.isPointerTool)
        assertTrue(PresentationCommand.ArrowTool.isPointerTool)
        assertTrue(PresentationCommand.EraseInk.isPointerTool)
        assertFalse(PresentationCommand.NextSlide.isPointerTool)
        assertFalse(PresentationCommand.StartShow.isPointerTool)
    }

    @Test
    fun `start from first slide does not send shift`() {
        assertEquals(HidReportHelper.Modifier.NONE, HidReportHelper.modifierFor(PresentationCommand.StartShow))
    }

    @Test
    fun `shift F5 report carries the modifier in the first byte`() {
        val report = HidReportHelper.keyDown(
            HidReportHelper.keyCodeFor(PresentationCommand.StartShowFromCurrent)!!,
            HidReportHelper.modifierFor(PresentationCommand.StartShowFromCurrent),
        )
        assertEquals(HidReportHelper.Modifier.LEFT_SHIFT.toByte(), report[0])
        assertEquals(HidReportHelper.Key.F5.toByte(), report[2])
    }

    @Test
    fun `pointer report is clamped to the logical descriptor range`() {
        val report = HidReportHelper.pointerMove(dx = 900, dy = -900)

        assertEquals(HidReportHelper.POINTER_REPORT_SIZE, report.size)
        assertEquals(127.toByte(), report[1])
        assertEquals((-127).toByte(), report[2])
    }

    @Test
    fun `pointer report keeps small deltas untouched`() {
        val report = HidReportHelper.pointerMove(dx = 3, dy = -4)

        assertEquals(3.toByte(), report[1])
        assertEquals((-4).toByte(), report[2])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `key code out of range is rejected`() {
        HidReportHelper.keyDown(0x1FF)
    }
}
