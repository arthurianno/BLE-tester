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
import com.example.bletester.services.WorkerService
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
            AppNavigation()




        }
        startWorkerService()
    }
    private fun startWorkerService() {
        val serviceIntent = Intent(this, WorkerService::class.java)
        startService(serviceIntent)
    }

}
