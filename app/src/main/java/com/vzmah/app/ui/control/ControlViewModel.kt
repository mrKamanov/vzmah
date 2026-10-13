package com.vzmah.app.ui.control

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider.Factory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vzmah.app.AppContainer
import com.vzmah.app.R
import com.vzmah.app.data.bluetooth.BluetoothHidManager
import com.vzmah.app.data.bluetooth.BluetoothHost
import com.vzmah.app.data.bluetooth.HidReportHelper
import com.vzmah.app.data.bluetooth.HidState
import com.vzmah.app.data.sensor.GestureSensorManager
import com.vzmah.app.domain.model.CommandOrigin
import com.vzmah.app.domain.model.CommandRequest
import com.vzmah.app.domain.model.KeyboardKey
import com.vzmah.app.domain.model.MouseAction
import com.vzmah.app.domain.model.MouseButton
import com.vzmah.app.domain.model.MouseSpeed
import com.vzmah.app.domain.model.PointerPreset
import com.vzmah.app.domain.model.PresentationCommand
import com.vzmah.app.domain.model.TouchPhase
import com.vzmah.app.domain.model.TrackpadPoint
import com.vzmah.app.domain.model.TrackpadSample
import com.vzmah.app.domain.model.nextShowActive
import com.vzmah.app.domain.usecase.GestureSensitivity
import com.vzmah.app.domain.usecase.ProcessGestureUseCase
import com.vzmah.app.domain.usecase.ProximityPassUseCase
import com.vzmah.app.domain.usecase.SendHidCommandUseCase
import com.vzmah.app.domain.usecase.TrackpadUseCase
import com.vzmah.app.service.PresentationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

    enum class ControlMode { TOUCH, MOUSE, POINTER, KEYBOARD }

enum class PointerTool { ARROW, LASER, PEN }

data class ControlUiState(
    val hidState: HidState = HidState.Idle,
    val hosts: List<BluetoothHost> = emptyList(),

    val activeHostAddress: String? = null,

    val backgroundMode: Boolean = false,
    val mode: ControlMode = ControlMode.MOUSE,
    val elapsedSeconds: Long = 0L,

    val showActive: Boolean = false,

    val proximityAvailable: Boolean = true,

    val proximityNear: Boolean = false,

    val proximityPasses: Int = 0,

    val waveNextSlide: Boolean = true,

    val vibrationsEnabled: Boolean = true,

    val mouseDragging: Boolean = false,
    val mouseSpeed: MouseSpeed = MouseSpeed.NORMAL,

    val mouseInvertedY: Boolean = false,

    val cursorSpeed: Float = 1.0f,

    val scrollSpeed: Float = 1.0f,

    val keyboardText: String = "",

    val keyboardModifiers: Int = 0,

    val pointerTool: PointerTool = PointerTool.ARROW,

    val pointerPreset: PointerPreset = PointerPreset.POWERPOINT,

    val penHeld: Boolean = false,

    val blankActive: Boolean = false,
    val lastCommand: PresentationCommand? = null,
    val lastCommandOrigin: CommandOrigin? = null,

    val pairingGuideVisible: Boolean = true,
    val permissionDenied: Boolean = false,
    val message: String? = null,
)

class ControlViewModel(
    private val appContext: Context,
    private val hidManager: BluetoothHidManager,
    private val sensorManager: GestureSensorManager,
    private val sendCommand: SendHidCommandUseCase,
    private val processGesture: ProcessGestureUseCase,
    private val proximityPass: ProximityPassUseCase,
    private val trackpad: TrackpadUseCase,
    private val haptics: Haptics,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState: StateFlow<ControlUiState> = _uiState.asStateFlow()

    private val prefs = appContext.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)

    init {
        val dismissed = prefs.getBoolean(KEY_PAIRING_GUIDE_DISMISSED, false)
        val wave = prefs.getBoolean(KEY_WAVE_NEXT_SLIDE, true)
        val vibrate = prefs.getBoolean(KEY_VIBRATIONS, true)
        val background = prefs.getBoolean(KEY_BACKGROUND_MODE, true)
        val pointerPreset = PointerPreset.entries
            .firstOrNull { it.name == prefs.getString(KEY_POINTER_PRESET, null) } ?: PointerPreset.POWERPOINT
        haptics.muted = !vibrate
        hidManager.pointerPreset = pointerPreset
        _uiState.update {
            it.copy(
                pairingGuideVisible = !dismissed,
                waveNextSlide = wave,
                vibrationsEnabled = vibrate,
                backgroundMode = background,
                pointerPreset = pointerPreset,
            )
        }

        val preset = MouseSpeed.entries
            .firstOrNull { it.name == prefs.getString(KEY_MOUSE_SPEED, null) } ?: MouseSpeed.NORMAL
        val cursorSpeed = prefs.getFloat(KEY_CURSOR_SPEED, preset.speed)
        val scrollSpeed = prefs.getFloat(KEY_SCROLL_SPEED, DEFAULT_SCROLL_SPEED)
        trackpad.setSpeed(cursorSpeed)
        trackpad.setScrollScale(scrollSpeed)
        _uiState.update {
            it.copy(
                mouseSpeed = preset,
                cursorSpeed = cursorSpeed,
                scrollSpeed = scrollSpeed,
                mouseInvertedY = prefs.getBoolean(KEY_MOUSE_INVERT_Y, false),
            )
        }
    }

    private var timerJob: Job? = null
    private var sessionRunning = false
    private var hidObserved = false
    private var sensorsObserved = false

    fun onHostVisible() {
        hidManager.register()
        hidManager.refreshHosts()
        observeHidState()

        if (_uiState.value.mode != ControlMode.TOUCH) {
            observeSensors()
            sensorManager.acquire(GestureSensorManager.OWNER_UI)
            proximityPass.sync(sensorManager.proximityNear.value)
        }
    }

    fun onHostPaused() {

        sendCommand.releaseAllNow()
        _uiState.update { it.copy(penHeld = false) }

        sensorManager.release(GestureSensorManager.OWNER_UI)
        processGesture.reset()
        proximityPass.reset()
    }

    fun startBackgroundService() = PresentationService.start(appContext)

    fun stopBackgroundService() = PresentationService.stop(appContext)

    fun setBackgroundMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_MODE, enabled).apply()
        _uiState.update { it.copy(backgroundMode = enabled) }
    }

    fun dismissPairingGuide() {
        prefs.edit().putBoolean(KEY_PAIRING_GUIDE_DISMISSED, true).apply()
        _uiState.update { it.copy(pairingGuideVisible = false) }
    }

    fun showPairingGuide() {
        _uiState.update { it.copy(pairingGuideVisible = true) }
    }

    fun showPermissionDenied() {
        _uiState.update { it.copy(permissionDenied = true) }
    }

    fun dismissPermissionDenied() {
        _uiState.update { it.copy(permissionDenied = false) }
    }

    fun dispatch(command: PresentationCommand, origin: CommandOrigin = CommandOrigin.TOUCH) {
        viewModelScope.launch {
            val delivered = sendCommand(CommandRequest(command, origin))
            if (!delivered) {
                _uiState.update { it.copy(message = appContext.getString(R.string.message_not_delivered)) }
                haptics.error()
                return@launch
            }
            onCommandDelivered(command, origin)
        }
    }

    fun toggleBlank() = dispatch(PresentationCommand.ToggleBlank)

    fun nextSlide(origin: CommandOrigin = CommandOrigin.TOUCH) =
        dispatch(PresentationCommand.NextSlide, origin)

    fun previousSlide(origin: CommandOrigin = CommandOrigin.TOUCH) =
        dispatch(PresentationCommand.PreviousSlide, origin)

    fun startShow() = dispatch(PresentationCommand.StartShow)

    fun startShowFromCurrent() = dispatch(PresentationCommand.StartShowFromCurrent)

    fun endShow() = dispatch(PresentationCommand.EndShow)

    fun refreshHosts() = hidManager.refreshHosts()

    fun selectHost(host: BluetoothHost) {
        hidManager.connect(host)
    }

    fun disconnect() = hidManager.disconnect()

    fun reconnect() = hidManager.reconnect()

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun consumeHidError() {
        hidManager.consumeError()?.let { error -> _uiState.update { it.copy(message = error) } }
    }

    fun setMouseSpeed(speed: MouseSpeed) {
        val value = if (speed == MouseSpeed.CUSTOM) {
            _uiState.value.cursorSpeed
        } else {
            speed.speed
        }
        trackpad.setSpeed(value)
        _uiState.update { it.copy(mouseSpeed = speed, cursorSpeed = value) }
        prefs.edit()
            .putString(KEY_MOUSE_SPEED, speed.name)
            .putFloat(KEY_CURSOR_SPEED, value)
            .apply()
    }

    fun setCursorSpeed(value: Float) {
        trackpad.setSpeed(value)
        _uiState.update { it.copy(mouseSpeed = MouseSpeed.CUSTOM, cursorSpeed = value) }
        prefs.edit()
            .putString(KEY_MOUSE_SPEED, MouseSpeed.CUSTOM.name)
            .putFloat(KEY_CURSOR_SPEED, value)
            .apply()
    }

    fun setScrollSpeed(value: Float) {
        trackpad.setScrollScale(value)
        _uiState.update { it.copy(scrollSpeed = value) }
        prefs.edit().putFloat(KEY_SCROLL_SPEED, value).apply()
    }

    fun setMouseInvertedY(inverted: Boolean) {
        _uiState.update { it.copy(mouseInvertedY = inverted) }
        prefs.edit().putBoolean(KEY_MOUSE_INVERT_Y, inverted).apply()
    }

    fun onKeyboardTextChange(newText: String) {
        val old = _uiState.value.keyboardText
        if (newText == old) return
        _uiState.update { it.copy(keyboardText = newText) }
        for (stroke in typingStrokes(old, newText)) {
            sendTypedStroke(stroke.usage, stroke.modifier)
        }
    }

    fun sendKeyboardKey(key: KeyboardKey) = sendTypedStroke(HidReportHelper.keyCodeFor(key), 0)

    fun switchHostLayout() {
        viewModelScope.launch {
            if (!sendCommand.switchHostLayout()) {
                _uiState.update { it.copy(message = appContext.getString(R.string.message_not_delivered)) }
            }
        }
    }

    fun toggleKeyboardModifier(bit: Int) {
        _uiState.update { it.copy(keyboardModifiers = it.keyboardModifiers xor bit) }
    }

    fun clearKeyboardText() {
        _uiState.update { it.copy(keyboardText = "") }
    }

    private fun sendTypedStroke(usage: Int, ownModifier: Int) {
        val sticky = _uiState.value.keyboardModifiers
        if (sticky != 0) _uiState.update { it.copy(keyboardModifiers = 0) }
        viewModelScope.launch {
            if (!sendCommand.sendTypingStroke(usage, sticky or ownModifier)) {
                _uiState.update { it.copy(message = appContext.getString(R.string.message_not_delivered)) }
            }
        }
    }

    private fun typingStrokes(old: String, newText: String): List<HidReportHelper.Stroke> {
        val common = old.commonPrefixWith(newText).length
        val strokes = mutableListOf<HidReportHelper.Stroke>()
        repeat(old.length - common) {
            strokes += HidReportHelper.Stroke(HidReportHelper.Key.BACKSPACE)
        }
        for (char in newText.substring(common)) {
            HidReportHelper.strokeFor(char)?.let(strokes::add)
        }
        return strokes
    }

    fun setWaveNextSlide(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAVE_NEXT_SLIDE, enabled).apply()
        _uiState.update { it.copy(waveNextSlide = enabled) }

        if (enabled) proximityPass.sync(sensorManager.proximityNear.value)
    }

    fun setVibrationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATIONS, enabled).apply()
        haptics.muted = !enabled
        _uiState.update { it.copy(vibrationsEnabled = enabled) }
    }

    fun setPointerPreset(preset: PointerPreset) {
        prefs.edit().putString(KEY_POINTER_PRESET, preset.name).apply()
        hidManager.pointerPreset = preset
        _uiState.update { it.copy(pointerPreset = preset) }
    }

    fun clickMouse(button: MouseButton) {
        viewModelScope.launch {
            if (sendCommand.pressMouseButton(button)) haptics.pulse()
        }
    }

    fun onTrackpadSample(sample: TrackpadSample) {
        if (_uiState.value.mode != ControlMode.MOUSE) return
        val actions = trackpad.onSample(sample)
        for (action in actions) {
            when (action) {

                is MouseAction.Move -> {
                    val dy = if (_uiState.value.mouseInvertedY) action.dy else -action.dy
                    sendCommand.enqueueMove(action.dx, dy)
                }
                else -> viewModelScope.launch { sendCommand.sendMouseAction(action) }
            }
        }
        if (actions.any { it is MouseAction.Button }) haptics.pulse()
        if (_uiState.value.mouseDragging != trackpad.isDragging) {
            _uiState.update { it.copy(mouseDragging = trackpad.isDragging) }
        }
    }

    fun releaseMouse() {
        if (!trackpad.isDragging && !_uiState.value.penHeld) return
        trackpad.cancel()
        _uiState.update { it.copy(mouseDragging = false, penHeld = false) }
        viewModelScope.launch { sendCommand.releaseMouse() }
    }

    private fun observeSensors(): Boolean {
        if (sensorsObserved) return true
        val hasMotion = sensorManager.hasAccelerometer && sensorManager.hasGyroscope
        if (!hasMotion && !sensorManager.hasProximity) {
            _uiState.update { it.copy(message = appContext.getString(R.string.message_sensors_missing)) }
            return false
        }
        sensorsObserved = true
        _uiState.update { it.copy(proximityAvailable = sensorManager.hasProximity) }

        viewModelScope.launch {
            sensorManager.proximityEvents.collect { near -> onProximityEvent(near) }
        }
        viewModelScope.launch {
            sensorManager.proximityNear.collect { near ->
                _uiState.update { it.copy(proximityNear = near) }
            }
        }
        return true
    }

    private fun onProximityEvent(near: Boolean) {
        val state = _uiState.value
        _uiState.update { it.copy(proximityNear = near) }
        if (state.mode != ControlMode.MOUSE || !state.waveNextSlide) return

        val command = proximityPass.onProximity(near, SystemClock.elapsedRealtime()) ?: return
        _uiState.update { it.copy(proximityPasses = it.proximityPasses + 1) }

        viewModelScope.launch {
            if (sendCommand(CommandRequest(command, CommandOrigin.GESTURE))) {
                onCommandDelivered(command, CommandOrigin.GESTURE)
                haptics.gesture()
            }
        }
    }

    fun selectPointerTool(tool: PointerTool) {
        val command = when (tool) {
            PointerTool.ARROW -> PresentationCommand.ArrowTool
            PointerTool.LASER -> PresentationCommand.LaserPointer
            PointerTool.PEN -> PresentationCommand.PenTool
        }
        dispatch(command)
    }

    fun erasePointerInk() = dispatch(PresentationCommand.EraseInk)

    fun onPointerPadDown() {
        if (_uiState.value.mode != ControlMode.POINTER) return
        if (_uiState.value.pointerTool == PointerTool.PEN) setPenHeld(true)
    }

    fun onPointerPadMove(dx: Float, dy: Float) {
        if (_uiState.value.mode != ControlMode.POINTER) return

        val hidDy = if (_uiState.value.mouseInvertedY) dy.roundToInt() else -dy.roundToInt()
        sendCommand.enqueueMove(dx.roundToInt(), hidDy)
    }

    fun onPointerPadUp() {
        if (_uiState.value.penHeld) setPenHeld(false)
    }

    private fun setPenHeld(held: Boolean) {
        if (_uiState.value.penHeld == held) return
        _uiState.update { it.copy(penHeld = held) }
        viewModelScope.launch {
            sendCommand.sendMouseAction(MouseAction.Button(MouseButton.LEFT, held))
        }
        if (held) haptics.pulse()
    }

    private fun onCommandDelivered(command: PresentationCommand, origin: CommandOrigin) {
        _uiState.update {
            it.copy(
                showActive = nextShowActive(it.showActive, command),
                blankActive = if (command is PresentationCommand.ToggleBlank) {
                    !it.blankActive
                } else {
                    it.blankActive
                },
                lastCommand = command,
                lastCommandOrigin = origin,
            )
        }
        PresentationService.publishBlank(_uiState.value.blankActive)
        if (origin != CommandOrigin.GESTURE && origin != CommandOrigin.POINTER) {
            haptics.pulse()
        }

        when (command) {
            PresentationCommand.LaserPointer -> _uiState.update { it.copy(pointerTool = PointerTool.LASER) }
            PresentationCommand.PenTool -> _uiState.update { it.copy(pointerTool = PointerTool.PEN) }
            PresentationCommand.ArrowTool -> _uiState.update { it.copy(pointerTool = PointerTool.ARROW) }
            else -> Unit
        }
        PresentationService.publishElapsed(_uiState.value.elapsedSeconds)

        if (_uiState.value.showActive) startSessionTimer() else stopSessionTimer()
    }

    private fun stopSessionTimer() {
        timerJob?.cancel()
        timerJob = null
        sessionRunning = false
    }

    private fun startSessionTimer() {
        if (sessionRunning) return
        sessionRunning = true
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                PresentationService.publishElapsed(_uiState.value.elapsedSeconds)
            }
        }
    }

    fun setMode(mode: ControlMode) {
        _uiState.update { it.copy(mode = mode) }
        if (mode == ControlMode.MOUSE) {
            observeSensors()
            sensorManager.acquire(GestureSensorManager.OWNER_UI)
        } else {
            sensorManager.release(GestureSensorManager.OWNER_UI)
        }
        if (mode != ControlMode.MOUSE) {

            trackpad.cancel()
            _uiState.update { it.copy(mouseDragging = false) }
            viewModelScope.launch { sendCommand.releaseMouse() }
            proximityPass.reset()
        }
        if (mode == ControlMode.MOUSE) {

            proximityPass.sync(sensorManager.proximityNear.value)
        }
    }

    fun resetSession() {
        stopSessionTimer()
        _uiState.update { it.copy(elapsedSeconds = 0L) }
        PresentationService.publishElapsed(0L)

        if (_uiState.value.showActive) startSessionTimer()
    }

    private fun observeHidState() {
        if (hidObserved) return
        hidObserved = true
        viewModelScope.launch {
            var wasConnected = false
            hidManager.state.collect { hid ->
                val connected = hid is HidState.Connected
                _uiState.update { it.copy(hidState = hid) }
                if (connected && !wasConnected) {
                    sendCommand.releaseAllNow()
                    sendCommand.releaseMouse()
                    _uiState.update { it.copy(penHeld = false, mouseDragging = false) }
                }
                wasConnected = connected
            }
        }
        viewModelScope.launch {
            PresentationService.blankActive.collect { blank ->
                if (_uiState.value.blankActive != blank) {
                    _uiState.update { it.copy(blankActive = blank) }
                }
            }
        }
        viewModelScope.launch {
            hidManager.hosts.collect { hosts -> _uiState.update { it.copy(hosts = hosts) } }
        }
        viewModelScope.launch {
            hidManager.activeHostAddress.collect { address ->
                _uiState.update { it.copy(activeHostAddress = address) }
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        sendCommand.releaseAllNow()
        hidManager.dispose()
        super.onCleared()
    }

    companion object {
        private const val PREFS_UI = "vzmah_ui"
        private const val KEY_PAIRING_GUIDE_DISMISSED = "pairing_guide_dismissed"
        private const val KEY_MOUSE_SPEED = "mouse_speed"
        private const val KEY_MOUSE_INVERT_Y = "mouse_invert_y"
        private const val KEY_CURSOR_SPEED = "cursor_speed"
        private const val KEY_SCROLL_SPEED = "scroll_speed"
        private const val KEY_WAVE_NEXT_SLIDE = "wave_next_slide"
        private const val KEY_VIBRATIONS = "vibrations"
        private const val KEY_BACKGROUND_MODE = "background_mode"
        private const val KEY_POINTER_PRESET = "pointer_preset"

        const val DEFAULT_SCROLL_SPEED = 1.0f

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ControlViewModel(
                    appContext = container.appContext,
                    hidManager = container.hidManager,
                    sensorManager = container.sensorManager,
                    sendCommand = container.sendHidCommand,
                    processGesture = container.processGesture,
                    proximityPass = container.proximityPass,
                    trackpad = container.trackpad,
                    haptics = Haptics(container.appContext),
                )
            }
        }
    }
}
