package com.vzmah.app.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.vzmah.app.R
import com.vzmah.app.domain.model.PointerPreset
import com.vzmah.app.domain.model.PresentationCommand
import com.vzmah.app.domain.model.title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothHidManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hostMemory = HostMemoryStore(appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> onAdapterTurnedOn()
                BluetoothAdapter.STATE_OFF -> onAdapterTurnedOff()
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            adapterStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private val _state = MutableStateFlow<HidState>(HidState.Idle)
    val state: StateFlow<HidState> = _state.asStateFlow()

    private val _hosts = MutableStateFlow<List<BluetoothHost>>(emptyList())
    val hosts: StateFlow<List<BluetoothHost>> = _hosts.asStateFlow()

    private val _activeHostAddress = MutableStateFlow<String?>(null)
    val activeHostAddress: StateFlow<String?> = _activeHostAddress.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var hidDevice: BluetoothHidDevice? = null
    private var pendingRegister = false
    private var registered = false
    private var connectedDevice: BluetoothDevice? = null
    private var releaseScheduled = false
    private var autoReconnectAddress: String? = null

    private var pendingConnectAddress: String? = null

    private var reconnectAttempts = 0
    private var reconnectRunnable: Runnable? = null

    @Volatile
    var pointerPreset: PointerPreset = PointerPreset.POWERPOINT

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = proxy as? BluetoothHidDevice
            if (pendingRegister) {
                pendingRegister = false
                performRegister()
            }

            if (connectPending()) pendingConnectAddress = null
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = null
            registered = false
            connectedDevice = null
            _activeHostAddress.value = null
            _state.value = HidState.Idle
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(device: BluetoothDevice?, registeredNow: Boolean) {
            registered = registeredNow
            _state.value = if (registeredNow) HidState.Registered else HidState.Idle
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, newState: Int) {
            val name = device.safeName()
            val connected = newState == BluetoothProfile.STATE_CONNECTED
            connectedDevice = if (connected) device else null
            _activeHostAddress.value = if (connected || newState == BluetoothProfile.STATE_CONNECTING) {
                device.address
            } else {
                null
            }
            _state.value = when (newState) {
                BluetoothProfile.STATE_CONNECTING -> HidState.Connecting(name)
                BluetoothProfile.STATE_CONNECTED -> HidState.Connected(name)
                BluetoothProfile.STATE_DISCONNECTING -> HidState.Disconnected("Отключение…")
                else -> HidState.Disconnected("Нет связи с $name")
            }
            if (connected) {
                reconnectAttempts = 0
                cancelReconnect()
                autoReconnectAddress = device.address
                hostMemory.remember(device.address, name, nowMs())
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                scheduleReconnect()
            }
        }

        override fun onGetReport(
            device: BluetoothDevice,
            type: Byte,
            id: Byte,
            bufferSize: Int,
        ) {

            hidDevice?.replyReport(device, type, id, HidReportHelper.emptyFeatureReport())
        }

        override fun onSetReport(
            device: BluetoothDevice,
            reportType: Byte,
            reportId: Byte,
            data: ByteArray,
        ) {
            hidDevice?.replyReport(device, reportType, reportId, data)
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            Log.d(TAG, "HID protocol switched to ${if (protocol == BluetoothHidDevice.PROTOCOL_BOOT_MODE) "boot" else "report"}")
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            Log.d(TAG, "HID interrupt data: report=$reportId size=${data.size}")
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            connectedDevice = null
            registered = false
            _activeHostAddress.value = null
            _state.value = HidState.Disconnected("Хост разорвал HID-кабель")
        }
    }

    private var hostVisible = false

    fun register() {
        onMain {
            hostVisible = true
            val adapter = adapter()
            if (adapter == null) {
                Log.i(TAG, "register: адаптер недоступен (выключен или нет поддержки)")
                _state.value = HidState.BluetoothUnavailable
                return@onMain
            }
            if (!hasConnectPermission()) {
                _state.value = HidState.PermissionRequired
                return@onMain
            }
            if (hidDevice != null) {
                if (!registered) performRegister()
                return@onMain
            }
            _state.value = HidState.Registering
            pendingRegister = true
            if (!adapter.getProfileProxy(appContext, serviceListener, BluetoothProfile.HID_DEVICE)) {
                pendingRegister = false
                fail(appContext.getString(R.string.hid_profile_unavailable))
            }
        }
    }

    fun unregister() {
        onMain {
            hostVisible = false
            pendingRegister = false
            registered = false
            connectedDevice = null
            releaseScheduled = false
            autoReconnectAddress = null
            pendingConnectAddress = null
            cancelReconnect()
            _activeHostAddress.value = null

            hidDevice?.unregisterApp()
            hidDevice = null
            _state.value = HidState.Idle
        }
    }

    private fun onAdapterTurnedOn() {
        onMain {
            Log.i(TAG, "adapter ON, hostVisible=$hostVisible")
            if (hostVisible) {
                register()
                refreshHosts()
            } else if (_state.value is HidState.BluetoothUnavailable) {
                _state.value = HidState.Idle
            }
        }
    }

    private fun onAdapterTurnedOff() {
        onMain {
            Log.i(TAG, "adapter OFF -> BluetoothUnavailable")
            pendingRegister = false
            registered = false
            connectedDevice = null
            pendingConnectAddress = null
            autoReconnectAddress = null
            cancelReconnect()
            _activeHostAddress.value = null
            hidDevice = null
            _state.value = HidState.BluetoothUnavailable
        }
    }

    fun dispose() {
        runCatching { appContext.unregisterReceiver(adapterStateReceiver) }
    }

    @SuppressLint("MissingPermission")
    fun refreshHosts() {
        onMain {
            if (!hasConnectPermission()) {
                _hosts.value = emptyList()
                return@onMain
            }
            val adapter = adapter()
            if (adapter == null) {
                _hosts.value = emptyList()
                return@onMain
            }
            val bonded = runCatching { adapter.bondedDevices.orEmpty() }.getOrNull()
            if (bonded == null) {
                Log.w(TAG, "refreshHosts: bonded devices unavailable")
                return@onMain
            }

            val bondedAddresses = bonded.mapTo(HashSet()) { it.address }
            hostMemory.all().filterNot { it.address in bondedAddresses }.forEach { hostMemory.forget(it.address) }

            val rememberedAt = hostMemory.all().associate { it.address to it.lastUsedAtMs }
            _hosts.value = bonded
                .map {
                    BluetoothHost(
                        name = it.safeName(),
                        address = it.address,
                        remembered = it.address in rememberedAt,
                        lastUsedAtMs = rememberedAt[it.address] ?: 0L,
                    )
                }
                .sortedWith(hostOrdering())

            autoReconnect()
        }
    }

    private fun hostOrdering(): Comparator<BluetoothHost> {
        val active = _activeHostAddress.value
        val lastUsed = hostMemory.lastUsed()?.address
        return compareBy<BluetoothHost> { host ->
            when {
                host.address == active -> 0
                host.address == lastUsed -> 1
                host.remembered -> 2
                else -> 3
            }
        }.thenByDescending { it.lastUsedAtMs }.thenBy { it.name.lowercase() }
    }

    fun connect(host: BluetoothHost) {
        onMain {
            autoReconnectAddress = host.address
            reconnectAttempts = 0
            cancelReconnect()

            runCatching { doConnect(host) }.onFailure { error ->
                Log.w(TAG, "connect ${host.name} failed", error)
                fail(
                    appContext.getString(
                        R.string.connect_error,
                        error.javaClass.simpleName,
                    ),
                )
            }
        }
    }

    private fun doConnect(host: BluetoothHost) {
        val device = resolveDevice(host.address) ?: run {
            hostMemory.forget(host.address)
            fail(appContext.getString(R.string.device_not_bonded, host.name))
            refreshHosts()
            return
        }
        pendingConnectAddress = device.address
        if (connectPending()) pendingConnectAddress = null
    }

    private fun connectPending(): Boolean {
        val proxy = hidDevice ?: return false
        val address = pendingConnectAddress ?: return true
        val device = resolveDevice(address) ?: return true

        return runCatching {
            val current = proxy.getConnectionState(device)
            if (current == BluetoothProfile.STATE_CONNECTED ||
                current == BluetoothProfile.STATE_CONNECTING
            ) {
                true
            } else {
                if (!proxy.connect(device)) {
                    fail(appContext.getString(R.string.connect_start_failed, device.safeName()))
                }
                true
            }
        }.getOrElse { error ->
            Log.w(TAG, "connectPending $address failed", error)
            fail(
                appContext.getString(
                    R.string.connect_error,
                    error.javaClass.simpleName,
                ),
            )
            true
        }
    }

    fun disconnect() {
        val device = connectedDevice ?: return
        onMain {
            autoReconnectAddress = null
            cancelReconnect()
            hidDevice?.disconnect(device)
        }
    }

    fun reconnect() {
        onMain {
            reconnectAttempts = 0
            cancelReconnect()
            val address = autoReconnectAddress ?: hostMemory.lastUsed()?.address
            if (address == null) {
                refreshHosts()
                return@onMain
            }
            autoReconnectAddress = address
            if (!registered || hidDevice == null) {
                register()
                return@onMain
            }
            attemptReconnect(address)
        }
    }

    fun sendKeyStroke(command: PresentationCommand): Boolean {

        fun bail(reason: String): Boolean {
            Log.w(TAG, "keyStroke ${command.title}: DROPPED ($reason)")
            return false
        }

        val stroke = HidReportHelper.toolStroke(command, pointerPreset)
        if (stroke == null && command.isPointerTool) {
            Log.i(TAG, "tool ${command.title}: no binding for $pointerPreset, skipping")
            return true
        }

        val usage = stroke?.usage ?: HidReportHelper.keyCodeFor(command) ?: return bail("нет HID-кода")
        val device = connectedDevice ?: return bail("нет подключённого хоста")
        val proxy = hidDevice ?: return bail("нет прокси HID")
        if (releaseScheduled) return bail("предыдущая клавиша ещё не отпущена")

        val modifier = stroke?.modifier ?: HidReportHelper.modifierFor(command)
        val pressed = runCatching {
            proxy.sendReport(
                device,
                HidReportHelper.KEYBOARD_REPORT_ID,
                HidReportHelper.keyDown(usage, modifier),
            )
        }.getOrDefault(false)

        Log.i(
            TAG,
            "keyStroke ${command.title}: usage=0x${usage.toString(16)} modifier=0x${modifier.toString(16)} " +
                "host=${device.safeName()} sent=$pressed",
        )
        if (!pressed) return false

        releaseScheduled = true
        mainHandler.postDelayed({
            releaseScheduled = false
            val stillConnected = connectedDevice
            if (stillConnected != null) {
                runCatching {
                    proxy.sendReport(stillConnected, HidReportHelper.KEYBOARD_REPORT_ID, HidReportHelper.keyRelease())
                }
            }
        }, KEY_HOLD_MS)
        return true
    }

    fun sendKeyboardKey(usage: Int, modifier: Int = HidReportHelper.Modifier.NONE): Boolean {
        val device = connectedDevice ?: return false
        val proxy = hidDevice ?: return false
        return runCatching {
            proxy.sendReport(device, HidReportHelper.KEYBOARD_REPORT_ID, HidReportHelper.keyDown(usage, modifier))
        }.getOrDefault(false)
    }

    fun sendKeyboardRelease(): Boolean {
        val device = connectedDevice ?: return false
        val proxy = hidDevice ?: return false
        return runCatching {
            proxy.sendReport(device, HidReportHelper.KEYBOARD_REPORT_ID, HidReportHelper.keyRelease())
        }.getOrDefault(false)
    }

    fun sendPointerDelta(dx: Int, dy: Int, wheel: Int = 0): Boolean {
        if (dx == 0 && dy == 0 && wheel == 0) return true
        val device = connectedDevice ?: return false
        val proxy = hidDevice ?: return false
        return runCatching {
            proxy.sendReport(device, HidReportHelper.POINTER_REPORT_ID, HidReportHelper.pointerMove(dx, dy, wheel = wheel))
        }.getOrDefault(false)
    }

    fun sendMouseReport(dx: Int, dy: Int, buttons: Int, wheel: Int = 0): Boolean {
        val device = connectedDevice ?: return false
        val proxy = hidDevice ?: return false
        return runCatching {
            proxy.sendReport(
                device,
                HidReportHelper.POINTER_REPORT_ID,
                HidReportHelper.pointerMove(dx, dy, buttons = buttons, wheel = wheel),
            )
        }.getOrDefault(false)
    }

    fun releaseMouseButtons() {
        onMain {
            val device = connectedDevice ?: return@onMain
            val proxy = hidDevice ?: return@onMain
            runCatching {
                proxy.sendReport(
                    device,
                    HidReportHelper.POINTER_REPORT_ID,
                    HidReportHelper.pointerMove(0, 0, buttons = 0, wheel = 0),
                )
            }
        }
    }

    fun releaseAllKeys() {
        onMain {
            val device = connectedDevice ?: return@onMain
            val proxy = hidDevice ?: return@onMain
            releaseScheduled = false
            runCatching {
                proxy.sendReport(device, HidReportHelper.KEYBOARD_REPORT_ID, HidReportHelper.keyRelease())
            }
        }
    }

    fun consumeError(): String? = _lastError.value?.also { _lastError.value = null }

    private fun performRegister() {
        val proxy = hidDevice ?: return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            DEVICE_NAME,
            DEVICE_DESCRIPTION,
            DEVICE_PROVIDER,
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidReportHelper.REPORT_DESCRIPTOR,
        )
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            QOS_TOKEN_RATE,
            QOS_TOKEN_BUCKET,
            QOS_PEAK_BANDWIDTH,
            QOS_LATENCY_MS,
            QOS_DELAY_VARIATION_MS,
        )
        val result = runCatching {
            proxy.registerApp(sdp, qos, qos, ContextCompat.getMainExecutor(appContext), hidCallback)
        }.onFailure { Log.e(TAG, "registerApp failed", it) }.getOrDefault(false)

        if (result) {
            registered = true
            _state.value = HidState.Registered
            requestAutoReconnect()
        } else {
            _state.value = HidState.Error(appContext.getString(R.string.hid_profile_busy))
        }
    }

private fun requestAutoReconnect() {
        autoReconnectAddress = hostMemory.lastUsed()?.address
        autoReconnect()
    }

    private fun autoReconnect() {
        val address = autoReconnectAddress ?: return
        if (!registered || hidDevice == null) return
        val device = resolveDevice(address) ?: run {
            hostMemory.forget(address)
            autoReconnectAddress = null
            return
        }
        when (hidDevice?.getConnectionState(device)) {
            BluetoothProfile.STATE_DISCONNECTED -> hidDevice?.connect(device)

            else -> Unit
        }
    }

    private fun scheduleReconnect() {
        if (!hostVisible) return
        val address = autoReconnectAddress ?: return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.i(TAG, "reconnect: giving up after $reconnectAttempts attempts")
            return
        }
        if (!registered || hidDevice == null) {
            register()
            return
        }
        val delay = RECONNECT_DELAYS_MS[minOf(reconnectAttempts, RECONNECT_DELAYS_MS.size - 1)]
        reconnectAttempts++
        cancelReconnect()
        val runnable = Runnable { attemptReconnect(address) }
        reconnectRunnable = runnable
        Log.i(TAG, "reconnect: attempt #$reconnectAttempts in ${delay}ms")
        mainHandler.postDelayed(runnable, delay)
    }

    private fun attemptReconnect(address: String) {
        reconnectRunnable = null
        if (!hostVisible) return
        if (_state.value is HidState.Connected || _state.value is HidState.Connecting) return
        if (!registered || hidDevice == null) {
            register()
            return
        }
        val device = resolveDevice(address) ?: run {
            hostMemory.forget(address)
            autoReconnectAddress = null
            return
        }
        runCatching {
            val current = hidDevice?.getConnectionState(device)
            if (current == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "reconnect: connecting to ${device.safeName()}")
                if (hidDevice?.connect(device) != true) {
                    scheduleReconnect()
                }
            }
        }.onFailure {
            Log.w(TAG, "reconnect failed", it)
            scheduleReconnect()
        }
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    @SuppressLint("MissingPermission")
    private fun resolveDevice(address: String): BluetoothDevice? =
        runCatching { adapter()?.bondedDevices.orEmpty().firstOrNull { it.address == address } }
            .getOrNull()

    private fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun adapter(): BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter?.takeIf { it.isEnabled }

    private fun fail(message: String) {
        Log.w(TAG, message)
        _lastError.value = message
        _state.value = HidState.Error(message)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun BluetoothDevice.safeName(): String =
        runCatching { name }.getOrNull()?.takeIf { it.isNotBlank() } ?: address

    companion object {
        private const val TAG = "BluetoothHidManager"
        private const val PREFS = "vzmah_hid"

        private const val DEVICE_NAME = "Vzmah"
        private const val DEVICE_DESCRIPTION = "Presentation remote"
        private const val DEVICE_PROVIDER = "Vzmah"

        const val KEY_HOLD_MS = 40L

        private const val MAX_RECONNECT_ATTEMPTS = 12
        private val RECONNECT_DELAYS_MS = longArrayOf(500, 1000, 2000, 4000, 8000, 15000, 15000, 15000, 15000, 15000, 15000, 15000)

        private const val QOS_TOKEN_RATE = 800
        private const val QOS_TOKEN_BUCKET = 800
        private const val QOS_PEAK_BANDWIDTH = 800
        private const val QOS_LATENCY_MS = 500
        private const val QOS_DELAY_VARIATION_MS = 500

        fun nowMs(): Long = SystemClock.elapsedRealtime()
    }
}

sealed interface HidState {

    data object BluetoothUnavailable : HidState

    data object PermissionRequired : HidState

    data object Idle : HidState

    data object Registering : HidState

    data object Registered : HidState

    data class Connecting(val hostName: String) : HidState

    data class Connected(val hostName: String) : HidState

    data class Disconnected(val reason: String) : HidState

    data class Error(val message: String) : HidState
}

data class BluetoothHost(
    val name: String,
    val address: String,
    val remembered: Boolean = false,
    val lastUsedAtMs: Long = 0L,
)

val HidState.isReady: Boolean
    get() = this is HidState.Connected
