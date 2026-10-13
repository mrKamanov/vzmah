package com.vzmah.app

import android.app.Application
import android.content.Context
import com.vzmah.app.data.bluetooth.BluetoothHidManager
import com.vzmah.app.data.sensor.GestureSensorManager
import com.vzmah.app.domain.usecase.ProcessGestureUseCase
import com.vzmah.app.domain.usecase.ProximityPassUseCase
import com.vzmah.app.domain.usecase.SendHidCommandUseCase
import com.vzmah.app.domain.usecase.TrackpadUseCase

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val hidManager: BluetoothHidManager by lazy { BluetoothHidManager(appContext) }

    val sensorManager: GestureSensorManager by lazy { GestureSensorManager(appContext) }

    val sendHidCommand: SendHidCommandUseCase by lazy { SendHidCommandUseCase(hidManager) }

    val processGesture: ProcessGestureUseCase by lazy { ProcessGestureUseCase() }

    val proximityPass: ProximityPassUseCase by lazy { ProximityPassUseCase() }

    val trackpad: TrackpadUseCase by lazy { TrackpadUseCase() }
}

class VzmahApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
