package com.vzmah.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.vzmah.app.ui.control.ControlScreen
import com.vzmah.app.ui.control.ControlViewModel
import com.vzmah.app.ui.theme.VzmahTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as VzmahApp).container

    private val viewModel: ControlViewModel by viewModels { ControlViewModel.factory(container) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->

        if (granted[Manifest.permission.BLUETOOTH_CONNECT] != false) {
            viewModel.dismissPermissionDenied()
            viewModel.onHostVisible()
        } else {
            viewModel.showPermissionDenied()
        }
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {  }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VzmahTheme {
                ControlScreen(viewModel = viewModel, onSetBackgroundMode = ::setBackgroundMode)
            }
        }

        if (!hasBluetoothPermission()) {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasBluetoothPermission()) {
            viewModel.dismissPermissionDenied()
            viewModel.onHostVisible()
            if (viewModel.uiState.value.backgroundMode) {
                if (needsNotificationPermission()) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.startBackgroundService()
            }
        }
    }

    override fun onStop() {
        viewModel.onHostPaused()
        super.onStop()
    }

    private fun setBackgroundMode(enabled: Boolean) {
        if (!enabled) {
            viewModel.stopBackgroundService()
            return
        }
        if (needsNotificationPermission()) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.startBackgroundService()
    }

    private fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothPermission(): Boolean = requiredPermissions().all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
