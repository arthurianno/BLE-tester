package com.example.bletester.core

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.bletester.navigation.AppNavigation
import com.example.bletester.receivers.SystemBroadcastReceiver
import com.example.bletester.ui.theme.report.ReportViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var bluetoothAdapter: BluetoothAdapter

    private var isBtDialogShowed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val reportViewModel: ReportViewModel = hiltViewModel()
            AppNavigation(
                onBluetoothStateChanged = {
                    showBluetoothDialog()
                }
            )

            // Check Bluetooth state on launch
            LaunchedEffect(Unit) {
                if (!bluetoothAdapter.isEnabled) {
                    showBluetoothDialog()
                }
            }

            // Listen for Bluetooth state changes
            SystemBroadcastReceiver(
                systemAction = BluetoothAdapter.ACTION_STATE_CHANGED,
                onSystemEvent = { intent ->
                    val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        showBluetoothDialog()
                    }
                }
            )
        }
    }

    private fun showBluetoothDialog() {
        if (!bluetoothAdapter.isEnabled) {
            if (!isBtDialogShowed) {
                val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startBluetoothIntentForResult.launch(enableBluetoothIntent)
                isBtDialogShowed = true
            }
        }
    }

    private val startBluetoothIntentForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            isBtDialogShowed = false
            if (result.resultCode != RESULT_OK) {
                showBluetoothDialog()
            }
        }
}
