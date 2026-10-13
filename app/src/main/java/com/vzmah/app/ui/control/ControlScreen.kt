package com.vzmah.app.ui.control

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.MoreVert

import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vzmah.app.R
import com.vzmah.app.data.bluetooth.BluetoothHost
import com.vzmah.app.domain.model.KeyMods
import com.vzmah.app.domain.model.KeyboardKey
import com.vzmah.app.domain.model.MouseButton
import com.vzmah.app.data.bluetooth.HidState
import com.vzmah.app.domain.model.TouchPhase
import com.vzmah.app.domain.model.TrackpadPoint
import com.vzmah.app.domain.model.TrackpadSample
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalViewConfiguration
import com.vzmah.app.domain.usecase.GestureSensitivity
import com.vzmah.app.domain.model.PresentationCommand
import com.vzmah.app.ui.theme.Amber
import com.vzmah.app.ui.theme.AmberDeep
import com.vzmah.app.ui.theme.Cyan
import com.vzmah.app.ui.theme.DangerSoft
import com.vzmah.app.ui.theme.LaserRed
import com.vzmah.app.ui.theme.VzmahBrand
import com.vzmah.app.ui.theme.VzmahCaption
import com.vzmah.app.ui.theme.VzmahCounter
import com.vzmah.app.ui.theme.VzmahLabel
import com.vzmah.app.ui.theme.VzmahOutline
import com.vzmah.app.ui.theme.VzmahSectionTitle
import com.vzmah.app.ui.theme.VzmahSubtle
import com.vzmah.app.ui.theme.VzmahSurfaceCard
import com.vzmah.app.ui.theme.VzmahSurfaceElevated
import com.vzmah.app.ui.theme.VzmahTextPrimary
import com.vzmah.app.ui.theme.VzmahTextSecondary
import com.vzmah.app.ui.theme.VzmahTextTertiary
import com.vzmah.app.ui.theme.VzmahTimer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
private fun menuHeightBudget(): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    return (screenHeight * 0.6f).coerceAtLeast(300.dp)
}

@Composable
fun ControlScreen(
    viewModel: ControlViewModel,
    onSetBackgroundMode: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(ERROR_POLL_MS)
            viewModel.consumeHidError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (showSettings) {
            SettingsScreen(
                state = state,
                onBack = { showSettings = false },
                onVibrationsChange = viewModel::setVibrationsEnabled,
                onWaveChange = viewModel::setWaveNextSlide,
                onBackgroundModeChange = { enabled ->
                    viewModel.setBackgroundMode(enabled)
                    onSetBackgroundMode(enabled)
                },
                onSpeedChange = viewModel::setMouseSpeed,
                onCursorSpeedChange = viewModel::setCursorSpeed,
                onScrollSpeedChange = viewModel::setScrollSpeed,
                onInvertChange = viewModel::setMouseInvertedY,
                onPresetChange = viewModel::setPointerPreset,
                onShowPairingGuide = viewModel::showPairingGuide,
                modifier = Modifier.padding(padding),
            )
        } else {
            ControlContent(
                state = state,
                viewModel = viewModel,
                context = context,
                padding = padding,
                onOpenSettings = { showSettings = true },
            )
        }
    }

    if (state.pairingGuideVisible) {
        PairingGuideDialog(
            onOpenSettings = {
                openBluetoothSettings(context)
                viewModel.dismissPairingGuide()
            },
            onDismiss = viewModel::dismissPairingGuide,
        )
    }

    if (state.permissionDenied) {
        PermissionDeniedDialog(
            onOpenSettings = { openAppSettings(context) },
            onDismiss = viewModel::dismissPermissionDenied,
        )
    }
}

@Composable
private fun ControlContent(
    state: ControlUiState,
    viewModel: ControlViewModel,
    context: Context,
    padding: PaddingValues,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Header(
            hidState = state.hidState,
            hosts = state.hosts,
            activeHostAddress = state.activeHostAddress,
            onSelectHost = viewModel::selectHost,
            onRefreshHosts = viewModel::refreshHosts,
            onDisconnect = viewModel::disconnect,
            onReconnect = viewModel::reconnect,
            onShowPairingGuide = viewModel::showPairingGuide,
            onOpenSettings = onOpenSettings,
        )

        Spacer(Modifier.height(14.dp))

        if (state.hidState is HidState.BluetoothUnavailable) {
            BluetoothOffBanner(
                onEnable = { requestEnableBluetooth(context) },
            )
            Spacer(Modifier.height(12.dp))
        }

        SessionCard(
            elapsedSeconds = state.elapsedSeconds,
            showActive = state.showActive,
            onReset = viewModel::resetSession,
        )

        Spacer(Modifier.height(10.dp))

        ModeTabs(mode = state.mode, onSelect = viewModel::setMode)

        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (state.mode) {
                ControlMode.TOUCH -> TouchModePanel(
                    onPrevious = { viewModel.previousSlide() },
                    onNext = { viewModel.nextSlide() },
                )

                ControlMode.MOUSE -> TrackpadPanel(
                    state = state,
                    onLeftClick = { viewModel.clickMouse(MouseButton.LEFT) },
                    onRightClick = { viewModel.clickMouse(MouseButton.RIGHT) },
                    onTrackpadSample = viewModel::onTrackpadSample,
                )

                ControlMode.POINTER -> PointerModePanel(
                    tool = state.pointerTool,
                    penHeld = state.penHeld,
                    onToolChange = viewModel::selectPointerTool,
                    onErase = viewModel::erasePointerInk,
                    onDown = viewModel::onPointerPadDown,
                    onMove = viewModel::onPointerPadMove,
                    onUp = viewModel::onPointerPadUp,
                )

                ControlMode.KEYBOARD -> KeyboardPanel(
                    text = state.keyboardText,
                    modifiers = state.keyboardModifiers,
                    onTextChange = viewModel::onKeyboardTextChange,
                    onKey = viewModel::sendKeyboardKey,
                    onToggleModifier = viewModel::toggleKeyboardModifier,
                    onClear = viewModel::clearKeyboardText,
                    onSwitchLayout = viewModel::switchHostLayout,
                )
            }
        }

        BottomBar(
            blankActive = state.blankActive,
            onToggleBlank = viewModel::toggleBlank,
            onStartShow = viewModel::startShow,
            onStartShowFromCurrent = viewModel::startShowFromCurrent,
            onEndShow = viewModel::endShow,
        )
    }
}

@Composable
private fun Header(
    hidState: HidState,
    hosts: List<BluetoothHost>,
    activeHostAddress: String?,
    onSelectHost: (BluetoothHost) -> Unit,
    onRefreshHosts: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onShowPairingGuide: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.Amber, MaterialTheme.colorScheme.AmberDeep))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "V",
                    color = MaterialTheme.colorScheme.VzmahSurfaceCard,
                    fontWeight = FontWeight.Black,
                    style = VzmahBrand,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.brand_name),
                style = VzmahBrand,
                color = MaterialTheme.colorScheme.VzmahTextPrimary,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConnectionChip(
                hidState = hidState,
                hosts = hosts,
                activeHostAddress = activeHostAddress,
                onSelect = onSelectHost,
                onRefresh = onRefreshHosts,
                onDisconnect = onDisconnect,
                onReconnect = onReconnect,
                onShowPairingGuide = onShowPairingGuide,
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.VzmahSurfaceElevated),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.VzmahTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ConnectionChip(
    hidState: HidState,
    hosts: List<BluetoothHost>,
    activeHostAddress: String?,
    onSelect: (BluetoothHost) -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onShowPairingGuide: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = hidState.statusLabel()
    val tone = hidState.statusTone()
    val showReconnect = hidState is HidState.Disconnected ||
        hidState is HidState.Error ||
        hidState is HidState.Idle

    Box {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(tone.copy(alpha = 0.10f))
                .border(1.dp, tone.copy(alpha = 0.32f), CircleShape)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tone),
            )
            Text(text = label, style = VzmahCaption.copy(fontSize = VzmahLabel.fontSize), color = tone)
            if (showReconnect) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(tone.copy(alpha = 0.18f))
                        .clickable(onClick = onReconnect),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.hosts_reconnect),
                        modifier = Modifier.size(14.dp),
                        tint = tone,
                    )
                }
            }
        }

        val menuBudget = menuHeightBudget()
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = menuBudget),
        ) {
            Column(Modifier.heightIn(max = menuBudget)) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.hosts_dialog_title),
                        style = VzmahCaption,
                        color = MaterialTheme.colorScheme.VzmahTextTertiary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    hosts.forEach { host ->
                        val isActive = host.address == activeHostAddress
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = host.name,
                                        color = if (isActive) MaterialTheme.colorScheme.Amber else MaterialTheme.colorScheme.VzmahTextPrimary,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (isActive || host.remembered) {
                                        Text(
                                            text = stringResource(
                                                if (isActive) R.string.host_connected else R.string.host_remembered,
                                            ),
                                            style = VzmahCaption,
                                            color = if (isActive) MaterialTheme.colorScheme.Amber else MaterialTheme.colorScheme.VzmahTextTertiary,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelect(host)
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = when {
                                        isActive -> Icons.Filled.BluetoothConnected
                                        host.remembered -> Icons.Filled.History
                                        else -> Icons.Filled.Bluetooth
                                    },
                                    contentDescription = null,
                                    tint = if (isActive) MaterialTheme.colorScheme.Amber else MaterialTheme.colorScheme.Cyan.copy(alpha = 0.7f),
                                )
                            },
                        )
                    }
                    if (hosts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.hosts_empty),
                            style = VzmahSubtle,
                            color = MaterialTheme.colorScheme.VzmahTextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hosts_guide_open), color = MaterialTheme.colorScheme.VzmahTextPrimary) },
                    onClick = {
                        expanded = false
                        onShowPairingGuide()
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.Amber)
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.VzmahOutline)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hosts_refresh), color = MaterialTheme.colorScheme.VzmahTextPrimary) },
                    onClick = {
                        onRefresh()
                    },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.Cyan) },
                )
                if (hidState is HidState.Connected || hidState is HidState.Connecting) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hosts_disconnect), color = MaterialTheme.colorScheme.VzmahTextPrimary) },
                        onClick = {
                            expanded = false
                            onDisconnect()
                        },
                        leadingIcon = { Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.DangerSoft) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HidState.statusLabel(): String = when (this) {
    HidState.BluetoothUnavailable -> stringResource(R.string.status_bluetooth_off)
    HidState.PermissionRequired -> stringResource(R.string.status_permission)
    HidState.Registering -> stringResource(R.string.status_connecting)
    HidState.Registered -> stringResource(R.string.status_registered)
    is HidState.Connecting -> stringResource(R.string.status_connecting)
    is HidState.Connected -> stringResource(R.string.status_connected)
    is HidState.Disconnected -> stringResource(R.string.status_disconnected)
    is HidState.Error -> stringResource(R.string.status_error)
    HidState.Idle -> stringResource(R.string.status_disconnected)
}

@Composable
private fun HidState.statusTone(): Color = when (this) {
    HidState.Registered -> MaterialTheme.colorScheme.Cyan
    is HidState.Connected -> MaterialTheme.colorScheme.Amber
    HidState.Registering, is HidState.Connecting -> MaterialTheme.colorScheme.Amber
    HidState.BluetoothUnavailable, HidState.PermissionRequired, is HidState.Error -> MaterialTheme.colorScheme.DangerSoft
    HidState.Idle, is HidState.Disconnected -> MaterialTheme.colorScheme.VzmahTextSecondary
}

@Composable
private fun SessionCard(
    elapsedSeconds: Long,
    showActive: Boolean,
    onReset: () -> Unit,
) {

    val caption = stringResource(
        if (showActive) R.string.session_caption else R.string.session_caption_idle,
    )
    val textColor = if (showActive) MaterialTheme.colorScheme.VzmahTextPrimary else MaterialTheme.colorScheme.VzmahTextTertiary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(if (showActive) MaterialTheme.colorScheme.VzmahSurfaceCard else MaterialTheme.colorScheme.VzmahSurfaceElevated)
            .border(1.dp, MaterialTheme.colorScheme.VzmahOutline, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = caption,
                style = VzmahCaption,
                color = MaterialTheme.colorScheme.VzmahTextSecondary,
            )
            Text(
                text = formatElapsed(elapsedSeconds),
                style = VzmahCounter,
                color = textColor,
            )
        }

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.VzmahSurfaceElevated)
                .border(1.dp, MaterialTheme.colorScheme.VzmahOutline, CircleShape)
                .clickable(enabled = showActive, onClick = onReset)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.session_reset),
                style = VzmahTimer,
                color = textColor,
            )
        }
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun ModeTabs(mode: ControlMode, onSelect: (ControlMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.VzmahSurfaceCard)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeTab(ControlMode.TOUCH, stringResource(R.string.mode_touch), mode, onSelect, Modifier.weight(1f))
        ModeTab(ControlMode.MOUSE, stringResource(R.string.mode_mouse), mode, onSelect, Modifier.weight(1f))
        ModeTab(ControlMode.POINTER, stringResource(R.string.mode_pointer), mode, onSelect, Modifier.weight(1f))
        ModeTab(ControlMode.KEYBOARD, stringResource(R.string.mode_keyboard), mode, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun ModeTab(
    target: ControlMode,
    label: String,
    current: ControlMode,
    onSelect: (ControlMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = target == current
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.Amber else MaterialTheme.colorScheme.VzmahTextSecondary,
        label = "tabTint",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.Amber.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onSelect(target) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = VzmahLabel,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TouchModePanel(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActionButton(
            modifier = Modifier
                .weight(0.32f)
                .fillMaxWidth(),
            onClick = onPrevious,
            label = stringResource(R.string.touch_prev),
            labelColor = MaterialTheme.colorScheme.VzmahTextSecondary,
        )

        ActionButton(
            modifier = Modifier
                .weight(0.68f)
                .fillMaxWidth(),
            onClick = onNext,
            label = stringResource(R.string.touch_next_label),
            labelColor = MaterialTheme.colorScheme.Amber,
            emphasized = true,
            icon = {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.Amber,
                    modifier = Modifier.size(30.dp),
                )
            },
        )
    }
}

@Composable
private fun TrackpadPanel(
    state: ControlUiState,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onTrackpadSample: (TrackpadSample) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.VzmahSurfaceCard)
                .border(
                    width = 1.dp,
                    color = if (state.mouseDragging) MaterialTheme.colorScheme.Amber.copy(alpha = 0.45f) else MaterialTheme.colorScheme.VzmahOutline,
                    shape = RoundedCornerShape(24.dp),
                )
                .pointerInput(Unit) { trackpadGesture(onTrackpadSample) },
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = if (state.mouseDragging) {
                    stringResource(R.string.mouse_dragging)
                } else {
                    stringResource(R.string.mouse_ready)
                },
                style = VzmahLabel,
                color = if (state.mouseDragging) MaterialTheme.colorScheme.Amber else MaterialTheme.colorScheme.VzmahTextSecondary,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.mouseDragging) MaterialTheme.colorScheme.Amber.copy(alpha = 0.15f) else MaterialTheme.colorScheme.VzmahSurfaceElevated,
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                onClick = onLeftClick,
                label = stringResource(R.string.mouse_left_click),
                labelColor = MaterialTheme.colorScheme.Amber,
                emphasized = true,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Mouse,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.Amber,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                onClick = onRightClick,
                label = stringResource(R.string.mouse_right_click),
                labelColor = MaterialTheme.colorScheme.VzmahTextSecondary,
                icon = {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.VzmahTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun KeyboardPanel(
    text: String,
    modifiers: Int,
    onTextChange: (String) -> Unit,
    onKey: (KeyboardKey) -> Unit,
    onToggleModifier: (Int) -> Unit,
    onClear: () -> Unit,
    onSwitchLayout: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val ime = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        ime?.show()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeyChip("Ctrl", selected = modifiers and KeyMods.CTRL != 0) {
                onToggleModifier(KeyMods.CTRL)
            }
            KeyChip("Alt", selected = modifiers and KeyMods.ALT != 0) {
                onToggleModifier(KeyMods.ALT)
            }
            KeyChip("Shift", selected = modifiers and KeyMods.SHIFT != 0) {
                onToggleModifier(KeyMods.SHIFT)
            }
            KeyChip(stringResource(R.string.kb_layout_chip)) { onSwitchLayout() }
            KeyChip("Esc") { onKey(KeyboardKey.Esc) }
            KeyChip("Tab") { onKey(KeyboardKey.Tab) }
            KeyChip("Enter") { onKey(KeyboardKey.Enter) }
            KeyChip("⌫") { onKey(KeyboardKey.Backspace) }
            KeyChip("←") { onKey(KeyboardKey.ArrowLeft) }
            KeyChip("→") { onKey(KeyboardKey.ArrowRight) }
            KeyChip("↑") { onKey(KeyboardKey.ArrowUp) }
            KeyChip("↓") { onKey(KeyboardKey.ArrowDown) }
        }

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.kb_hint)) },
            trailingIcon = if (text.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.kb_clear),
                        )
                    }
                }
            },
        )

        Text(
            text = stringResource(R.string.kb_modifier_hint),
            style = VzmahCaption,
            color = MaterialTheme.colorScheme.VzmahTextTertiary,
        )
        Text(
            text = stringResource(R.string.kb_layout_hint),
            style = VzmahCaption,
            color = MaterialTheme.colorScheme.VzmahTextTertiary,
        )
    }
}

@Composable
private fun KeyChip(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.Amber.copy(alpha = 0.16f) else MaterialTheme.colorScheme.VzmahSurfaceCard)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.Amber else MaterialTheme.colorScheme.VzmahOutline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = VzmahLabel,
            color = if (selected) MaterialTheme.colorScheme.Amber else MaterialTheme.colorScheme.VzmahTextPrimary,
            maxLines = 1,
        )
    }
}

private suspend fun PointerInputScope.trackpadGesture(emit: (TrackpadSample) -> Unit) {
    var gestureActive = false
    try {
        awaitEachGesture {
            val down = awaitFirstDown()
            gestureActive = true
            emit(
                TrackpadSample(
                    phase = TouchPhase.DOWN,
                    points = listOf(TrackpadPoint(0, 0f, 0f)),
                    nowMs = SystemClock.elapsedRealtime(),
                ),
            )

            var twoFinger = false
            var scrollAccPx = 0f

            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }

                if (pressed.isEmpty()) {
                    emit(
                        TrackpadSample(
                            phase = TouchPhase.UP,
                            points = listOf(TrackpadPoint(0, 0f, 0f)),
                            nowMs = SystemClock.elapsedRealtime(),
                        ),
                    )
                    gestureActive = false
                    break
                }

                if (!twoFinger && pressed.size >= 2) {

                    twoFinger = true
                    scrollAccPx = 0f
                    emit(
                        TrackpadSample(
                            phase = TouchPhase.DOWN,
                            points = listOf(
                                TrackpadPoint(0, 0f, 0f),
                                TrackpadPoint(1, 0f, 0f),
                            ),
                            nowMs = SystemClock.elapsedRealtime(),
                        ),
                    )
                }

                if (twoFinger) {
                    var dx = 0f
                    var dy = 0f
                    for (ch in pressed) {
                        val d = ch.positionChange()
                        dx += d.x
                        dy += d.y
                    }
                    dx /= pressed.size
                    dy /= pressed.size

                    scrollAccPx += dy
                    val steps = (-scrollAccPx / SCROLL_STEP_PX).toInt()
                    if (steps != 0) scrollAccPx -= steps * SCROLL_STEP_PX

                    emit(
                        TrackpadSample(
                            phase = TouchPhase.SCROLL,

                            points = listOf(TrackpadPoint(0, steps.toFloat(), abs(dx) + abs(dy))),
                            nowMs = SystemClock.elapsedRealtime(),
                        ),
                    )
                } else {
                    val finger = pressed.firstOrNull { it.id == down.id }
                    if (finger != null) {
                        val delta = finger.positionChange()
                        if (delta != Offset.Zero) {
                            emit(
                                TrackpadSample(
                                    phase = TouchPhase.MOVE,
                                    points = listOf(TrackpadPoint(0, delta.x, delta.y)),
                                    nowMs = SystemClock.elapsedRealtime(),
                                ),
                            )
                        }
                    }
                }

                for (ch in event.changes) ch.consume()
            }
        }
    } catch (e: CancellationException) {

        if (gestureActive) {
            emit(
                TrackpadSample(
                    phase = TouchPhase.CANCEL,
                    points = emptyList(),
                    nowMs = SystemClock.elapsedRealtime(),
                ),
            )
        }
        throw e
    }
}

@Composable
private fun PointerModePanel(
    tool: PointerTool,
    penHeld: Boolean,
    onToolChange: (PointerTool) -> Unit,
    onErase: () -> Unit,
    onDown: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onUp: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val slopPx = LocalViewConfiguration.current.touchSlop
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.VzmahSurfaceCard)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolChip(
                label = stringResource(R.string.pointer_laser),
                selected = tool == PointerTool.LASER,
                modifier = Modifier.weight(1f),
                onClick = { onToolChange(PointerTool.LASER) },
            )
            ToolChip(
                label = stringResource(R.string.pointer_pen),
                selected = tool == PointerTool.PEN,
                modifier = Modifier.weight(1f),
                onClick = { onToolChange(PointerTool.PEN) },
            )
            ToolChip(
                label = stringResource(R.string.pointer_arrow),
                selected = tool == PointerTool.ARROW,
                modifier = Modifier.weight(1f),
                onClick = { onToolChange(PointerTool.ARROW) },
            )
            ToolChip(
                label = stringResource(R.string.pointer_erase),
                selected = false,
                modifier = Modifier.weight(1f),
                onClick = onErase,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.LaserRed.copy(alpha = 0.22f), MaterialTheme.colorScheme.VzmahSurfaceCard),
                    ),
                )
                .border(
                    width = 2.dp,
                    color = if (penHeld) MaterialTheme.colorScheme.Amber else MaterialTheme.colorScheme.DangerSoft.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(24.dp),
                )
                .pointerPad(slopPx, onDown, onMove, onUp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.NearMe,
                    contentDescription = null,
                    tint = if (penHeld) MaterialTheme.colorScheme.Amber else MaterialTheme.colorScheme.DangerSoft,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.pointer_hold),
                    style = VzmahSectionTitle,
                    color = MaterialTheme.colorScheme.DangerSoft,
                )
                Text(
                    text = stringResource(
                        if (tool == PointerTool.PEN) R.string.pointer_pen_hint else R.string.pointer_hold_hint,
                    ),
                    style = VzmahSubtle,
                    color = MaterialTheme.colorScheme.DangerSoft.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.Amber.copy(alpha = 0.16f) else MaterialTheme.colorScheme.VzmahSurfaceElevated)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.Amber else MaterialTheme.colorScheme.VzmahOutline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = VzmahLabel,
            color = if (selected) MaterialTheme.colorScheme.Amber else MaterialTheme.colorScheme.VzmahTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BluetoothOffBanner(onEnable: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.DangerSoft.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.DangerSoft.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.BluetoothDisabled,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.DangerSoft,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.bt_off_title),
                style = VzmahLabel,
                color = MaterialTheme.colorScheme.VzmahTextPrimary,
            )
            Text(
                text = stringResource(R.string.bt_off_hint),
                style = VzmahCaption,
                color = MaterialTheme.colorScheme.VzmahTextSecondary,
            )
        }
        TextButton(onClick = onEnable) {
            Text(
                text = stringResource(R.string.bt_off_action),
                style = VzmahLabel,
                color = MaterialTheme.colorScheme.Amber,
            )
        }
    }
}

private fun requestEnableBluetooth(context: Context) {
    fun open(action: String): Boolean = runCatching {
        val intent = Intent(action)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.isSuccess

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (open(ACTION_PANEL_BLUETOOTH)) return
    } else if (open(BluetoothAdapter.ACTION_REQUEST_ENABLE)) {
        return
    }

    open(Settings.ACTION_BLUETOOTH_SETTINGS)
}

private const val ACTION_PANEL_BLUETOOTH = "android.settings.PANEL_BLUETOOTH"

@Composable
private fun PairingGuideDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.Amber,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.pairing_title),
                style = VzmahSectionTitle,
                color = MaterialTheme.colorScheme.VzmahTextPrimary,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.pairing_intro),
                    style = VzmahCaption,
                    color = MaterialTheme.colorScheme.VzmahTextSecondary,
                )

                Spacer(modifier = Modifier.height(16.dp))

                PairingStep(1, stringResource(R.string.pairing_step_1_title), stringResource(R.string.pairing_step_1_text))
                PairingStep(2, stringResource(R.string.pairing_step_2_title), stringResource(R.string.pairing_step_2_text))
                PairingStep(3, stringResource(R.string.pairing_step_3_title), stringResource(R.string.pairing_step_3_text))
                PairingStep(4, stringResource(R.string.pairing_step_4_title), stringResource(R.string.pairing_step_4_text))

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.pairing_note),
                    style = VzmahCaption,
                    color = MaterialTheme.colorScheme.VzmahTextTertiary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = stringResource(R.string.pairing_open_settings),
                    style = VzmahLabel,
                    color = MaterialTheme.colorScheme.Amber,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.pairing_dismiss),
                    style = VzmahLabel,
                    color = MaterialTheme.colorScheme.VzmahTextSecondary,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.VzmahSurfaceCard,
    )
}

@Composable
private fun PairingStep(number: Int, title: String, text: String) {
    Row(modifier = Modifier.padding(bottom = 12.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.Amber.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = VzmahLabel,
                color = MaterialTheme.colorScheme.Amber,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = VzmahLabel, color = MaterialTheme.colorScheme.VzmahTextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = text, style = VzmahCaption, color = MaterialTheme.colorScheme.VzmahTextSecondary)
        }
    }
}

private fun openBluetoothSettings(context: Context) {
    runCatching {
        val intent: Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", context.packageName, null),
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@Composable
private fun PermissionDeniedDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.BluetoothDisabled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.DangerSoft,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.permission_title),
                style = VzmahSectionTitle,
                color = MaterialTheme.colorScheme.VzmahTextPrimary,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.permission_text),
                style = VzmahCaption,
                color = MaterialTheme.colorScheme.VzmahTextSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = stringResource(R.string.permission_open_settings),
                    style = VzmahLabel,
                    color = MaterialTheme.colorScheme.Amber,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = VzmahLabel,
                    color = MaterialTheme.colorScheme.VzmahTextSecondary,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.VzmahSurfaceCard,
    )
}

@Composable
private fun BottomBar(
    blankActive: Boolean,
    onToggleBlank: () -> Unit,
    onStartShow: () -> Unit,
    onStartShowFromCurrent: () -> Unit,
    onEndShow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {

        BottomBarButton(
            icon = Icons.Filled.VisibilityOff,
            description = stringResource(R.string.bottom_blank),
            modifier = Modifier.weight(1f),
            highlighted = blankActive,
            onClick = onToggleBlank,
        )

        BottomBarButton(
            icon = Icons.Filled.PlayArrow,
            description = stringResource(R.string.bottom_start),
            modifier = Modifier.weight(1f),
            onClick = onStartShow,
        )

        BottomBarButton(
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            description = stringResource(R.string.bottom_start_current),
            modifier = Modifier.weight(1f),
            onClick = onStartShowFromCurrent,
        )

        BottomBarButton(
            icon = Icons.Filled.Stop,
            description = stringResource(R.string.bottom_end),
            modifier = Modifier.weight(1f),
            onClick = onEndShow,
        )
    }
}

@Composable
private fun BottomBarButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(BOTTOM_BAR_BUTTON_HEIGHT)
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.Amber.copy(alpha = 0.16f) else MaterialTheme.colorScheme.VzmahSurfaceCard)
            .border(
                width = 1.dp,
                color = if (highlighted) MaterialTheme.colorScheme.Amber.copy(alpha = 0.40f) else MaterialTheme.colorScheme.VzmahOutline,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) MaterialTheme.colorScheme.Amber else MaterialTheme.colorScheme.VzmahTextPrimary,
            modifier = Modifier.size(BOTTOM_BAR_ICON_SIZE),
        )
    }
}

private val BOTTOM_BAR_BUTTON_HEIGHT = 60.dp
private val BOTTOM_BAR_ICON_SIZE = 26.dp

@Composable
private fun ActionButton(
    modifier: Modifier,
    onClick: () -> Unit,
    label: String,
    labelColor: Color,
    emphasized: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .heightIn(min = 64.dp)
            .shadow(2.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (emphasized) {
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.VzmahSurfaceElevated, MaterialTheme.colorScheme.VzmahSurfaceCard))
                } else {
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.VzmahSurfaceCard, MaterialTheme.colorScheme.VzmahSurfaceCard))
                },
            )
            .border(
                width = 1.dp,
                color = if (emphasized) MaterialTheme.colorScheme.Amber.copy(alpha = 0.22f) else MaterialTheme.colorScheme.VzmahOutline,
                shape = RoundedCornerShape(24.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon?.invoke()
            Spacer(Modifier.height(6.dp))
            Text(text = label, style = VzmahSectionTitle, color = labelColor)
        }
    }
}

private fun Modifier.pointerPad(
    slopPx: Float,
    onDown: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onUp: () -> Unit,
): Modifier = pointerInput(slopPx, onDown, onMove, onUp) {
    awaitEachGesture {
        awaitFirstDown().also { it.consume() }
        onDown()
        var dragging = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.pressed } ?: break
                val delta = change.positionChange()
                if (!dragging && (abs(delta.x) > slopPx || abs(delta.y) > slopPx)) {
                    dragging = true
                }
                if (dragging && delta != Offset.Zero) {
                    onMove(delta.x, delta.y)
                }
                event.changes.forEach { it.consume() }
            }
        } finally {
            onUp()
        }
    }
}

private const val SCROLL_STEP_PX = 20f
private const val ERROR_POLL_MS = 1000L
private const val INDICATOR_RADIUS_PX = 44f
