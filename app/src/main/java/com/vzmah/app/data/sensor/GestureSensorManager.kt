package com.vzmah.app.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import com.vzmah.app.domain.model.MotionSample
import com.vzmah.app.domain.model.MotionVector
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class GestureSensorManager(context: Context) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager?

    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val proximity: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val stepFilter = StepFilter()
    private var rawAccel = MotionVector.ZERO
    private var angularVelocity = MotionVector.ZERO
    private var listener: SensorEventListener? = null

    private val owners = LinkedHashSet<String>()

    private val _samples = MutableSharedFlow<MotionSample>(
        replay = 0,
        extraBufferCapacity = SAMPLE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val samples: SharedFlow<MotionSample> = _samples.asSharedFlow()

    private val _latest = MutableStateFlow<MotionSample?>(null)

    val latest: StateFlow<MotionSample?> = _latest.asStateFlow()

    private val _proximity = MutableSharedFlow<Boolean>(
        replay = 0,
        extraBufferCapacity = PROXIMITY_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val proximityEvents: SharedFlow<Boolean> = _proximity.asSharedFlow()

    private val _proximityNear = MutableStateFlow(false)
    val proximityNear: StateFlow<Boolean> = _proximityNear.asStateFlow()

    val hasAccelerometer: Boolean get() = accelerometer != null
    val hasGyroscope: Boolean get() = gyroscope != null
    val hasProximity: Boolean get() = proximity != null
    val isListening: Boolean get() = listener != null

    fun acquire(owner: String) {
        if (owners.add(owner)) {
            start()
        }
    }

    fun release(owner: String) {
        if (owners.remove(owner) && owners.isEmpty()) {
            stop()
        }
    }

    fun releaseAll() {
        owners.clear()
        stop()
    }

    private fun start() {
        val manager = sensorManager ?: return
        if (listener != null) return

        stepFilter.reset()
        rawAccel = MotionVector.ZERO
        angularVelocity = MotionVector.ZERO
        _latest.value = null
        _proximityNear.value = false

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> rawAccel = MotionVector(
                        x = event.values[0].toDouble(),
                        y = event.values[1].toDouble(),
                        z = event.values[2].toDouble(),
                    )

                    Sensor.TYPE_GYROSCOPE -> angularVelocity = MotionVector(
                        x = event.values[0].toDouble(),
                        y = event.values[1].toDouble(),
                        z = event.values[2].toDouble(),
                    )

                    Sensor.TYPE_PROXIMITY -> {

                        val maxRange = proximity?.maximumRange?.toDouble() ?: 1.0
                        val near = event.values.isNotEmpty() && event.values[0] < maxRange
                        if (near != _proximityNear.value) {
                            Log.i(TAG, "proximity: ${if (near) "NEAR" else "FAR"} (${event.values[0]})")
                            _proximityNear.value = near
                            _proximity.tryEmit(near)
                        }
                        return
                    }

                    else -> return
                }

                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

                val filtered = stepFilter.process(event.timestamp / NANOS_PER_SEC, rawAccel)
                val sample = MotionSample(
                    timestampNanos = event.timestamp,
                    rawAccel = rawAccel,
                    linearAccel = filtered.linearAccel,
                    filteredAccel = filtered.filteredAccel,
                    angularVelocity = angularVelocity,
                )
                _latest.value = sample
                _samples.tryEmit(sample)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val accelOk = accelerometer?.let {
            manager.registerListener(sensorListener, it, SAMPLING_PERIOD_US) == true
        } ?: false
        val gyroOk = gyroscope?.let {
            manager.registerListener(sensorListener, it, SAMPLING_PERIOD_US) == true
        } ?: false
        val proximityOk = proximity?.let {

            manager.registerListener(sensorListener, it, PROXIMITY_DELAY_US) == true
        } ?: false

        Log.i(
            TAG,
            "sensors: acc=$accelOk gyro=$gyroOk proximity=$proximityOk maxRange=${proximity?.maximumRange}",
        )

        if (accelOk || gyroOk || proximityOk) {
            listener = sensorListener
        } else {
            manager.unregisterListener(sensorListener)
            listener = null
        }
    }

    private fun stop() {
        val sensorListener = listener ?: return
        sensorManager?.unregisterListener(sensorListener)
        listener = null
        stepFilter.reset()
        rawAccel = MotionVector.ZERO
        angularVelocity = MotionVector.ZERO
        _latest.value = null
        _proximityNear.value = false
    }

    companion object {
        private const val TAG = "GestureSensorManager"

        private const val NANOS_PER_SEC = 1_000_000_000.0

        const val OWNER_UI = "ui"

        const val OWNER_SERVICE = "service"

        private const val SAMPLING_PERIOD_US = 20_000

        private const val SAMPLE_BUFFER = 16

        private const val PROXIMITY_DELAY_US = 200_000
        private const val PROXIMITY_BUFFER = 8

        fun nowMs(): Long = SystemClock.elapsedRealtime()
    }
}
