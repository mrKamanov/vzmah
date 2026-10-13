package com.vzmah.app.domain.usecase

import com.vzmah.app.data.bluetooth.BluetoothHidManager
import com.vzmah.app.data.bluetooth.HidReportHelper
import com.vzmah.app.domain.model.CommandRequest
import com.vzmah.app.domain.model.MouseAction
import com.vzmah.app.domain.model.MouseButton
import com.vzmah.app.domain.model.PresentationCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

class SendHidCommandUseCase(
    private val hidManager: BluetoothHidManager,
) {

    private val gate = Mutex()

    private val pendingX = AtomicInteger()
    private val pendingY = AtomicInteger()

    private val motionSignal = Channel<Unit>(Channel.CONFLATED)

    private val motionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var mouseButtons = 0

    init {
        motionScope.launch { motionLoop() }
    }

    private suspend fun motionLoop() {
        for (signal in motionSignal) {
            while (true) {
                val dx = pendingX.getAndSet(0)
                val dy = pendingY.getAndSet(0)
                if (dx == 0 && dy == 0) break

                val sx = dx.coerceIn(MIN_AXIS, MAX_AXIS)
                val sy = dy.coerceIn(MIN_AXIS, MAX_AXIS)
                pendingX.addAndGet(dx - sx)
                pendingY.addAndGet(dy - sy)

                gate.withLock {
                    hidManager.sendMouseReport(sx, -sy, mouseButtons)
                }

                if (pendingX.get() == 0 && pendingY.get() == 0) break
                delay(MOTION_REPORT_INTERVAL_MS)
            }
        }
    }

    suspend operator fun invoke(request: CommandRequest): Boolean = gate.withLock {
        hidManager.sendKeyStroke(request.command)
    }

    suspend fun invoke(command: PresentationCommand): Boolean =
        invoke(CommandRequest(command))

    suspend fun sendTypingStroke(usage: Int, modifier: Int = 0): Boolean = gate.withLock {
        val pressed = hidManager.sendKeyboardKey(usage, modifier)
        delay(KEY_TAP_HOLD_MS)
        hidManager.sendKeyboardRelease()
        delay(KEY_TAP_GAP_MS)
        pressed
    }

    suspend fun switchHostLayout(): Boolean = gate.withLock {
        val pressed = hidManager.sendKeyboardKey(HidReportHelper.Key.SPACE, HidReportHelper.Modifier.LEFT_GUI)
        delay(KEY_TAP_HOLD_MS)
        hidManager.sendKeyboardRelease()
        delay(KEY_TAP_GAP_MS)
        pressed
    }

    suspend fun sendMouseAction(action: MouseAction): Boolean = when (action) {

        is MouseAction.Move -> {
            enqueueMove(action.dx, action.dy)
            true
        }

        is MouseAction.Scroll -> gate.withLock {
            hidManager.sendMouseReport(0, 0, buttons = mouseButtons, wheel = action.steps)
        }

        is MouseAction.Button -> {
            val bit = action.button.hidBit
            gate.withLock {
                if (action.pressed) {
                    mouseButtons = mouseButtons or bit
                    hidManager.sendMouseReport(0, 0, buttons = mouseButtons)
                } else {

                    hidManager.sendMouseReport(0, 0, buttons = mouseButtons or bit)
                    delay(CLICK_HOLD_MS)
                    mouseButtons = mouseButtons and bit.inv()
                    hidManager.sendMouseReport(0, 0, buttons = mouseButtons)
                }
            }
        }
    }

    fun enqueueMove(dx: Int, dy: Int) {
        pendingX.addAndGet(dx)
        pendingY.addAndGet(dy)
        motionSignal.trySend(Unit)
    }

    suspend fun pressMouseButton(button: MouseButton): Boolean = gate.withLock {
        mouseButtons = button.hidBit
        val pressed = hidManager.sendMouseReport(0, 0, buttons = mouseButtons)
        delay(CLICK_HOLD_MS)
        mouseButtons = 0
        val released = hidManager.sendMouseReport(0, 0, buttons = 0)
        pressed && released
    }

    suspend fun releaseMouse(): Boolean = gate.withLock {
        mouseButtons = 0
        hidManager.releaseMouseButtons()
        true
    }

    suspend fun releaseAll() = gate.withLock {
        mouseButtons = 0
        hidManager.releaseAllKeys()
    }

    fun releaseAllNow() {
        mouseButtons = 0
        hidManager.releaseAllKeys()
    }

    private companion object {

        const val CLICK_HOLD_MS = 40L

        const val MOTION_REPORT_INTERVAL_MS = 8L

        const val KEY_TAP_HOLD_MS = 40L

        const val KEY_TAP_GAP_MS = 12L

        const val MIN_AXIS = -127
        const val MAX_AXIS = 127
    }
}

