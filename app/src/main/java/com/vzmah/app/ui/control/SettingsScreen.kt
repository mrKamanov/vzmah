package com.vzmah.app.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vzmah.app.R
import com.vzmah.app.domain.model.MouseSpeed
import com.vzmah.app.domain.model.PointerPreset
import com.vzmah.app.ui.theme.Amber
import com.vzmah.app.ui.theme.VzmahBrand
import com.vzmah.app.ui.theme.VzmahCaption
import com.vzmah.app.ui.theme.VzmahLabel
import com.vzmah.app.ui.theme.VzmahOutline
import com.vzmah.app.ui.theme.VzmahSectionTitle
import com.vzmah.app.ui.theme.VzmahSurfaceCard
import com.vzmah.app.ui.theme.VzmahSurfaceElevated
import com.vzmah.app.ui.theme.VzmahTextPrimary
import com.vzmah.app.ui.theme.VzmahTextSecondary
import com.vzmah.app.ui.theme.VzmahTextTertiary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val SPEED_MIN = 0.2f
private const val SPEED_MAX = 4.0f

@Composable
internal fun SettingsScreen(
    state: ControlUiState,
    onBack: () -> Unit,
    onVibrationsChange: (Boolean) -> Unit,
    onWaveChange: (Boolean) -> Unit,
    onBackgroundModeChange: (Boolean) -> Unit,
    onSpeedChange: (MouseSpeed) -> Unit,
    onCursorSpeedChange: (Float) -> Unit,
    onScrollSpeedChange: (Float) -> Unit,
    onInvertChange: (Boolean) -> Unit,
    onPresetChange: (PointerPreset) -> Unit,
    onShowPairingGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = MaterialTheme.colorScheme.VzmahTextPrimary,
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = VzmahBrand,
                color = MaterialTheme.colorScheme.VzmahTextPrimary,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(text = stringResource(R.string.settings_section_general))
            SettingsCard {
                SettingSwitchRow(
                    title = stringResource(R.string.quick_vibrations),
                    checked = state.vibrationsEnabled,
                    onCheckedChange = onVibrationsChange,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.quick_wave),
                    checked = state.waveNextSlide,
                    onCheckedChange = onWaveChange,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.background_mode_title),
                    hint = stringResource(R.string.background_mode_hint),
                    checked = state.backgroundMode,
                    onCheckedChange = onBackgroundModeChange,
                )
            }

            SectionTitle(text = stringResource(R.string.settings_section_mouse))
            SettingsCard {
                SheetLabel(text = stringResource(R.string.mouse_speed_label))
                SpeedGrid(
                    selected = state.mouseSpeed,
                    onSelect = onSpeedChange,
                )
                SpeedRow(
                    label = stringResource(R.string.mouse_cursor_label),
                    value = state.cursorSpeed,
                    onValueChange = onCursorSpeedChange,
                )
                SpeedRow(
                    label = stringResource(R.string.mouse_scroll_speed_label),
                    value = state.scrollSpeed,
                    onValueChange = onScrollSpeedChange,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.mouse_invert_y),
                    checked = state.mouseInvertedY,
                    onCheckedChange = onInvertChange,
                )
            }

            SectionTitle(text = stringResource(R.string.settings_section_presentation))
            SettingsCard {
                SheetLabel(text = stringResource(R.string.pointer_preset_title))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PointerPreset.entries.forEach { item ->
                        PresetRow(
                            selected = item == state.pointerPreset,
                            title = stringResource(item.labelRes()),
                            subtitle = stringResource(presetHint(item)),
                            onClick = { onPresetChange(item) },
                        )
                    }
                }
            }

            SectionTitle(text = stringResource(R.string.settings_section_links))
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onShowPairingGuide)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.Amber,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.settings_pairing_guide),
                        style = VzmahLabel,
                        color = MaterialTheme.colorScheme.VzmahTextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.VzmahTextTertiary,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = VzmahSectionTitle,
        textAlign = TextAlign.Start,
        color = MaterialTheme.colorScheme.VzmahTextSecondary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.VzmahSurfaceCard)
            .border(1.dp, MaterialTheme.colorScheme.VzmahOutline, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        content = content,
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = title,
                style = VzmahLabel,
                color = MaterialTheme.colorScheme.VzmahTextPrimary,
            )
            if (hint != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = hint,
                    style = VzmahCaption,
                    color = MaterialTheme.colorScheme.VzmahTextTertiary,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SheetLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = VzmahCaption,
        color = MaterialTheme.colorScheme.VzmahTextSecondary,
        modifier = modifier.padding(top = 14.dp, bottom = 10.dp),
    )
}

@Composable
private fun SpeedGrid(
    selected: MouseSpeed,
    onSelect: (MouseSpeed) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MouseSpeed.entries.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { speed ->
                    SpeedCard(
                        selected = speed == selected,
                        label = stringResource(speed.labelRes()),
                        onClick = { onSelect(speed) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SpeedCard(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.Amber.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.VzmahSurfaceElevated
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.Amber.copy(alpha = 0.50f)
                } else {
                    MaterialTheme.colorScheme.VzmahOutline
                },
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.Amber
                    } else {
                        MaterialTheme.colorScheme.VzmahTextTertiary.copy(alpha = 0.25f)
                    },
                )
                .border(
                    width = 1.5.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.Amber
                    } else {
                        MaterialTheme.colorScheme.VzmahOutline
                    },
                    shape = CircleShape,
                ),
        )
        Text(
            text = label,
            style = VzmahLabel,
            textAlign = TextAlign.Center,
            color = if (selected) {
                MaterialTheme.colorScheme.Amber
            } else {
                MaterialTheme.colorScheme.VzmahTextPrimary
            },
        )
    }
}

@Composable
private fun PresetRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.Amber.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = VzmahLabel,
                color = if (selected) {
                    MaterialTheme.colorScheme.Amber
                } else {
                    MaterialTheme.colorScheme.VzmahTextPrimary
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = VzmahCaption,
                color = MaterialTheme.colorScheme.VzmahTextSecondary,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.Amber,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SpeedRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = VzmahLabel,
            color = MaterialTheme.colorScheme.VzmahTextPrimary,
            modifier = Modifier.weight(1f),
        )
        StepButton(
            glyph = "−",
            onClick = {
                val next = ((value - 0.1f) * 10).roundToInt() / 10f
                onValueChange(next.coerceIn(SPEED_MIN, SPEED_MAX))
            },
        )
        Box(
            modifier = Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.VzmahSurfaceElevated)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${"%.1f".format(value)}×",
                style = VzmahLabel,
                color = MaterialTheme.colorScheme.Amber,
            )
        }
        StepButton(
            glyph = "+",
            onClick = {
                val next = ((value + 0.1f) * 10).roundToInt() / 10f
                onValueChange(next.coerceIn(SPEED_MIN, SPEED_MAX))
            },
        )
    }
}

@Composable
private fun StepButton(
    glyph: String,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(400)
            while (pressed) {
                onClick()
                delay(130)
            }
        }
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.VzmahSurfaceElevated)
            .border(1.dp, MaterialTheme.colorScheme.VzmahOutline, CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        waitForUpOrCancellation()
                        pressed = false
                    }
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = VzmahLabel,
            color = MaterialTheme.colorScheme.VzmahTextPrimary,
        )
    }
}

private fun MouseSpeed.labelRes(): Int = when (this) {
    MouseSpeed.SLOW -> R.string.mouse_speed_slow
    MouseSpeed.NORMAL -> R.string.mouse_speed_normal
    MouseSpeed.FAST -> R.string.mouse_speed_fast
    MouseSpeed.CUSTOM -> R.string.mouse_speed_custom
}

private fun PointerPreset.labelRes(): Int = when (this) {
    PointerPreset.POWERPOINT -> R.string.pointer_preset_powerpoint
    PointerPreset.GOOGLE_SLIDES -> R.string.pointer_preset_slides
    PointerPreset.IMPRESS -> R.string.pointer_preset_impress
    PointerPreset.NONE -> R.string.pointer_preset_none
}

private fun presetHint(preset: PointerPreset): Int = when (preset) {
    PointerPreset.POWERPOINT -> R.string.pointer_preset_hint_powerpoint
    PointerPreset.GOOGLE_SLIDES -> R.string.pointer_preset_hint_slides
    PointerPreset.IMPRESS -> R.string.pointer_preset_hint_impress
    PointerPreset.NONE -> R.string.pointer_preset_hint_none
}
