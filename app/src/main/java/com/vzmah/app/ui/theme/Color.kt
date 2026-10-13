package com.vzmah.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val LightTertiaryText = Color(0xFF63636D)
private val DarkTertiaryText = Color(0xFF7E7E89)

val ColorScheme.Amber: Color
    get() = primary

val ColorScheme.AmberDeep: Color
    get() = tertiary

val ColorScheme.Cyan: Color
    get() = secondary

val ColorScheme.DangerSoft: Color
    get() = error

val ColorScheme.LaserRed: Color
    get() = error

val ColorScheme.VzmahBackground: Color
    get() = background

val ColorScheme.VzmahSurfaceCard: Color
    get() = surface

val ColorScheme.VzmahSurfaceElevated: Color
    get() = surfaceVariant

val ColorScheme.VzmahOutline: Color
    get() = outline

val ColorScheme.VzmahTextPrimary: Color
    get() = onSurface

val ColorScheme.VzmahTextSecondary: Color
    get() = onSurfaceVariant

val ColorScheme.VzmahTextTertiary: Color
    get() = if (background.luminance() > 0.5f) LightTertiaryText else DarkTertiaryText
