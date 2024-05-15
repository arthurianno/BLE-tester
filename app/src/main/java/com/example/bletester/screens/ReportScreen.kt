package com.example.bletester.screens

import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.bletester.permissions.SystemBroadcastReceiver

@Composable
fun ReportScreen(onBluetoothStateChanged:()->Unit) {
    SystemBroadcastReceiver(systemAction = BluetoothAdapter.ACTION_STATE_CHANGED){ bluetoothState ->
        val action = bluetoothState?.action ?: return@SystemBroadcastReceiver
        if(action == BluetoothAdapter.ACTION_STATE_CHANGED){
            onBluetoothStateChanged()
        }
    }
    Box(modifier = Modifier
        .fillMaxSize(),
        contentAlignment = Alignment.Center
    )
    {
        Text(text = "Экран отчета",
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp
        )
    }
}