package com.vzmah.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = Color(0xFF965100),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE7C2),
    onPrimaryContainer = Color(0xFF3A1C00),
    secondary = Color(0xFF006D7E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCECF3),
    onSecondaryContainer = Color(0xFF002F36),
    tertiary = Color(0xFFE06A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE8CC),
    onTertiaryContainer = Color(0xFF3A1900),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF141418),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141418),
    surfaceVariant = Color(0xFFE9E9EF),
    onSurfaceVariant = Color(0xFF55555F),
    surfaceDim = Color(0xFFE4E4E9),
    surfaceBright = Color(0xFFF5F5F7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F1F4),
    surfaceContainer = Color(0xFFF7F7F9),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE9E9EF),
    outline = Color(0x1F000000),
    outlineVariant = Color(0x14000000),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2E2E33),
    inverseOnSurface = Color(0xFFF0F0F3),
    inversePrimary = Color(0xFFFFD180),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB703),
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF5C3B00),
    onPrimaryContainer = Color(0xFFFFE7C2),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF00333B),
    secondaryContainer = Color(0xFF004A56),
    onSecondaryContainer = Color(0xFF97F3FF),
    tertiary = Color(0xFFFB8500),
    onTertiary = Color(0xFF3A1A00),
    tertiaryContainer = Color(0xFF7A3B00),
    onTertiaryContainer = Color(0xFFFFE8CC),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF16161A),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF23232A),
    onSurfaceVariant = Color(0xFF9A9AA5),
    surfaceDim = Color(0xFF08080A),
    surfaceBright = Color(0xFF16161A),
    surfaceContainerLowest = Color(0xFF0B0B0D),
    surfaceContainerLow = Color(0xFF111114),
    surfaceContainer = Color(0xFF16161A),
    surfaceContainerHigh = Color(0xFF1E1E24),
    surfaceContainerHighest = Color(0xFF23232A),
    outline = Color(0x24FFFFFF),
    outlineVariant = Color(0x14FFFFFF),
    error = Color(0xFFFF5252),
    onError = Color(0xFF3A0000),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFECECF0),
    inverseOnSurface = Color(0xFF1C1B1F),
    inversePrimary = Color(0xFF965100),
)

private val VzmahTypography = Typography()

@Composable
fun VzmahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect

            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = VzmahTypography,
        content = content,
    )
}
