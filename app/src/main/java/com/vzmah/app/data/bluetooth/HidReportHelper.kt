package com.vzmah.app.data.bluetooth

import com.vzmah.app.domain.model.KeyboardKey
import com.vzmah.app.domain.model.PointerPreset
import com.vzmah.app.domain.model.PresentationCommand

object HidReportHelper {

    const val KEYBOARD_REPORT_ID = 0x01
    const val POINTER_REPORT_ID = 0x02

    const val KEYBOARD_REPORT_SIZE = 8

    const val POINTER_REPORT_SIZE = 4

    object Key {
        const val A = 0x04
        const val B = 0x05
        const val E = 0x08
        const val L = 0x0F
        const val P = 0x13
        const val ESC = 0x29
        const val ENTER = 0x28
        const val BACKSPACE = 0x2A
        const val TAB = 0x2B
        const val SPACE = 0x2C
        const val F1 = 0x3A
        const val F2 = 0x3B
        const val F3 = 0x3C
        const val F4 = 0x3D
        const val F5 = 0x3E
        const val F6 = 0x3F
        const val F7 = 0x40
        const val F8 = 0x41
        const val F9 = 0x42
        const val F10 = 0x43
        const val F11 = 0x44
        const val F12 = 0x45
        const val PAGE_UP = 0x4B
        const val PAGE_DOWN = 0x4E
        const val ARROW_RIGHT = 0x4F
        const val ARROW_LEFT = 0x50
        const val ARROW_DOWN = 0x51
        const val ARROW_UP = 0x52
    }

    object Modifier {
        const val NONE = 0x00
        const val LEFT_CTRL = 0x01
        const val LEFT_SHIFT = 0x02
        const val LEFT_ALT = 0x04
        const val LEFT_GUI = 0x08
    }

    val REPORT_DESCRIPTOR: ByteArray = intArrayOf(

        0x05, 0x01,
        0x09, 0x06,
        0xA1, 0x01,
        0x85, 0x01,
        0x05, 0x07,
        0x19, 0xE0,
        0x29, 0xE7,
        0x15, 0x00,
        0x25, 0x01,
        0x75, 0x01,
        0x95, 0x08,
        0x81, 0x02,
        0x95, 0x01,
        0x75, 0x08,
        0x81, 0x03,
        0x95, 0x05,
        0x75, 0x01,
        0x05, 0x08,
        0x19, 0x01,
        0x29, 0x05,
        0x91, 0x02,
        0x95, 0x01,
        0x75, 0x03,
        0x91, 0x03,
        0x95, 0x06,
        0x75, 0x08,
        0x15, 0x00,
        0x25, 0x65,
        0x05, 0x07,
        0x19, 0x00,
        0x29, 0x65,
        0x81, 0x00,
        0xC0,

        0x05, 0x01,
        0x09, 0x02,
        0xA1, 0x01,
        0x85, 0x02,
        0x09, 0x01,
        0xA1, 0x00,
        0x05, 0x09,
        0x19, 0x01,
        0x29, 0x05,
        0x15, 0x00,
        0x25, 0x01,
        0x95, 0x05,
        0x75, 0x01,
        0x81, 0x02,
        0x95, 0x01,
        0x75, 0x03,
        0x81, 0x01,
        0x05, 0x01,
        0x09, 0x30,
        0x09, 0x31,
        0x09, 0x38,
        0x15, 0x81,
        0x25, 0x7F,
        0x75, 0x08,
        0x95, 0x03,
        0x81, 0x06,
        0xC0,
        0xC0,
    ).map { it.toByte() }.toByteArray()

    fun keyDown(keyCode: Int, modifier: Int = Modifier.NONE): ByteArray {
        require(keyCode in 0..0xFF) { "HID usage code out of range: $keyCode" }
        val report = ByteArray(KEYBOARD_REPORT_SIZE)
        report[0] = modifier.toByte()
        report[2] = keyCode.toByte()
        return report
    }

    fun keyRelease(): ByteArray = ByteArray(KEYBOARD_REPORT_SIZE)

    fun keyCodeFor(command: PresentationCommand): Int? = when (command) {
        PresentationCommand.NextSlide -> Key.PAGE_DOWN
        PresentationCommand.PreviousSlide -> Key.PAGE_UP
        PresentationCommand.ToggleBlank -> Key.B
        PresentationCommand.StartShow -> Key.F5
        PresentationCommand.StartShowFromCurrent -> Key.F5
        PresentationCommand.EndShow -> Key.ESC
        PresentationCommand.LaserPointer -> Key.L
        PresentationCommand.PenTool -> Key.P
        PresentationCommand.ArrowTool -> Key.A
        PresentationCommand.EraseInk -> Key.E
        is PresentationCommand.PointerMove -> null
    }

    fun modifierFor(command: PresentationCommand): Int = when (command) {
        PresentationCommand.StartShowFromCurrent -> Modifier.LEFT_SHIFT
        PresentationCommand.LaserPointer -> Modifier.LEFT_CTRL
        PresentationCommand.PenTool -> Modifier.LEFT_CTRL
        PresentationCommand.ArrowTool -> Modifier.LEFT_CTRL
        else -> Modifier.NONE
    }

    fun keyCodeFor(key: KeyboardKey): Int = when (key) {
        KeyboardKey.Esc -> Key.ESC
        KeyboardKey.Tab -> Key.TAB
        KeyboardKey.Enter -> Key.ENTER
        KeyboardKey.Backspace -> Key.BACKSPACE
        KeyboardKey.ArrowLeft -> Key.ARROW_LEFT
        KeyboardKey.ArrowRight -> Key.ARROW_RIGHT
        KeyboardKey.ArrowUp -> Key.ARROW_UP
        KeyboardKey.ArrowDown -> Key.ARROW_DOWN
    }

    fun toolStroke(command: PresentationCommand, preset: PointerPreset): Stroke? = when (preset) {
        PointerPreset.POWERPOINT -> when (command) {
            PresentationCommand.LaserPointer -> Stroke(Key.L, Modifier.LEFT_CTRL)
            PresentationCommand.PenTool -> Stroke(Key.P, Modifier.LEFT_CTRL)
            PresentationCommand.ArrowTool -> Stroke(Key.A, Modifier.LEFT_CTRL)
            PresentationCommand.EraseInk -> Stroke(Key.E)
            else -> null
        }
        PointerPreset.GOOGLE_SLIDES -> when (command) {
            PresentationCommand.LaserPointer -> Stroke(Key.L)
            PresentationCommand.PenTool -> Stroke(Key.L, Modifier.LEFT_SHIFT)
            PresentationCommand.ArrowTool -> null
            PresentationCommand.EraseInk -> Stroke(Key.A, Modifier.LEFT_SHIFT)
            else -> null
        }
        PointerPreset.IMPRESS -> when (command) {
            PresentationCommand.LaserPointer -> null
            PresentationCommand.PenTool -> Stroke(Key.P)
            PresentationCommand.ArrowTool -> Stroke(Key.A, Modifier.LEFT_CTRL)
            PresentationCommand.EraseInk -> Stroke(Key.E)
            else -> null
        }
        PointerPreset.NONE -> null
    }

    data class Stroke(val usage: Int, val modifier: Int = Modifier.NONE)

    fun strokeFor(char: Char): Stroke? = when (char) {
        in 'a'..'z' -> Stroke(0x04 + (char - 'a'))
        in 'A'..'Z' -> Stroke(0x04 + (char - 'A'), Modifier.LEFT_SHIFT)
        in '1'..'9' -> Stroke(0x1E + (char - '1'))
        '0' -> Stroke(0x27)
        ' ' -> Stroke(Key.SPACE)
        '\n' -> Stroke(Key.ENTER)
        '\t' -> Stroke(Key.TAB)

        '-' -> Stroke(0x2D)
        '_' -> Stroke(0x2D, Modifier.LEFT_SHIFT)
        '=' -> Stroke(0x2E)
        '+' -> Stroke(0x2E, Modifier.LEFT_SHIFT)
        '[' -> Stroke(0x2F)
        '{' -> Stroke(0x2F, Modifier.LEFT_SHIFT)
        ']' -> Stroke(0x30)
        '}' -> Stroke(0x30, Modifier.LEFT_SHIFT)
        '\\' -> Stroke(0x31)
        '|' -> Stroke(0x31, Modifier.LEFT_SHIFT)
        ';' -> Stroke(0x33)
        ':' -> Stroke(0x33, Modifier.LEFT_SHIFT)
        '\'' -> Stroke(0x34)
        '"' -> Stroke(0x34, Modifier.LEFT_SHIFT)
        '`' -> Stroke(0x35)
        '~' -> Stroke(0x35, Modifier.LEFT_SHIFT)
        ',' -> Stroke(0x36)
        '<' -> Stroke(0x36, Modifier.LEFT_SHIFT)
        '.' -> Stroke(0x37)
        '>' -> Stroke(0x37, Modifier.LEFT_SHIFT)
        '/' -> Stroke(0x38)
        '?' -> Stroke(0x38, Modifier.LEFT_SHIFT)
        '!' -> Stroke(0x1E, Modifier.LEFT_SHIFT)
        '@' -> Stroke(0x1F, Modifier.LEFT_SHIFT)
        '#' -> Stroke(0x20, Modifier.LEFT_SHIFT)
        '$' -> Stroke(0x21, Modifier.LEFT_SHIFT)
        '%' -> Stroke(0x22, Modifier.LEFT_SHIFT)
        '^' -> Stroke(0x23, Modifier.LEFT_SHIFT)
        '&' -> Stroke(0x24, Modifier.LEFT_SHIFT)
        '*' -> Stroke(0x25, Modifier.LEFT_SHIFT)
        '(' -> Stroke(0x26, Modifier.LEFT_SHIFT)
        ')' -> Stroke(0x27, Modifier.LEFT_SHIFT)

        'ё' -> Stroke(0x35)
        'Ё' -> Stroke(0x35, Modifier.LEFT_SHIFT)

        in 'а'..'я' -> Stroke(CYRILLIC_USAGE[char - 'а'])
        in 'А'..'Я' -> Stroke(CYRILLIC_USAGE[char - 'А'], Modifier.LEFT_SHIFT)
        else -> null
    }

    private val CYRILLIC_USAGE = intArrayOf(
        0x09,
        0x36,
        0x07,
        0x18,
        0x0F,
        0x17,
        0x33,
        0x13,
        0x05,
        0x14,
        0x15,
        0x0E,
        0x19,
        0x1C,
        0x0D,
        0x0A,
        0x0B,
        0x06,
        0x11,
        0x08,
        0x04,
        0x2F,
        0x1A,
        0x1B,
        0x0C,
        0x12,
        0x30,
        0x16,
        0x10,
        0x34,
        0x37,
        0x1D,
    )

    fun pointerMove(dx: Int, dy: Int, buttons: Int = 0, wheel: Int = 0): ByteArray = byteArrayOf(
        buttons.toByte(),
        dx.coerceIn(MIN_AXIS, MAX_AXIS).toByte(),
        dy.coerceIn(MIN_AXIS, MAX_AXIS).toByte(),
        wheel.coerceIn(MIN_AXIS, MAX_AXIS).toByte(),
    )

    fun buttonsMask(vararg bits: Int): Int = bits.fold(0) { acc, bit -> acc or bit }

    fun emptyFeatureReport(): ByteArray = ByteArray(1)

    private const val MIN_AXIS = -127
    private const val MAX_AXIS = 127
}

val PresentationCommand.isPointerTool: Boolean
    get() = this is PresentationCommand.LaserPointer ||
        this is PresentationCommand.PenTool ||
        this is PresentationCommand.ArrowTool ||
        this is PresentationCommand.EraseInk
