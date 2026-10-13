package com.vzmah.app.ui.control

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val enabled: Boolean get() = vibrator?.hasVibrator() == true

    var muted: Boolean = false

    fun pulse() = play(28L, DEFAULT_AMPLITUDE)

    fun gesture() = play(longArrayOf(0L, 30L, 40L, 45L), intArrayOf(0, 120, 0, 160))

    fun calibration() = play(longArrayOf(0L, 40L, 30L, 40L), intArrayOf(0, 200, 0, 200))

    fun error() = play(longArrayOf(0L, 60L, 60L, 60L), intArrayOf(0, 255, 0, 255))

    private fun play(timings: LongArray, amplitudes: IntArray) {
        val device = vibrator ?: return
        if (!enabled || muted) return
        runCatching {
            device.vibrate(VibrationEffect.createWaveform(timings, amplitudes, NO_REPEAT))
        }
    }

    private fun play(durationMs: Long, amplitude: Int) {
        val device = vibrator ?: return
        if (!enabled || muted) return
        runCatching {
            device.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        }
    }

    private companion object {
        const val DEFAULT_AMPLITUDE = 120
        const val NO_REPEAT = -1
    }
}
