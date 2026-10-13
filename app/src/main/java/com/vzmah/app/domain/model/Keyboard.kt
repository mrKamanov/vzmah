package com.vzmah.app.domain.model

enum class KeyboardKey {
    Esc,
    Tab,
    Enter,
    Backspace,
    ArrowLeft,
    ArrowRight,
    ArrowUp,
    ArrowDown,
}

object KeyMods {
    const val CTRL = 0x01
    const val SHIFT = 0x02
    const val ALT = 0x04
}

