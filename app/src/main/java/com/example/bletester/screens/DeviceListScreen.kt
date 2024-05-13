package com.example.bletester.screens
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.bletester.viewModels.ScanViewModel

@SuppressLint("MissingPermission")
@Composable
fun DeviceListScreen(scanViewModel: ScanViewModel = hiltViewModel()) {
    var deviceList by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    // LaunchedEffect to update deviceList when scanViewModel.deviceList changes
    LaunchedEffect(scanViewModel.deviceList) {
        deviceList = scanViewModel.deviceList
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = {
                scanViewModel.scanLeDevice()
                deviceList = scanViewModel.deviceList
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Начать сканирование")
        }

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(deviceList) { device ->
                DeviceListItem(deviceName = device.name ?: "Unknown", deviceAddress = device.address)
                Divider(color = Color.LightGray, thickness = 1.dp)
            }
        }
    }
}

@Composable
fun DeviceListItem(deviceName: String, deviceAddress: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(8.dp).
        clickable {
            expanded = !expanded
        }

    ) {
        Text(
            text = "Имя устройства: $deviceName",
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 4.dp),
            color = if (expanded) Color.Blue else Color.Black,
        )
        if (expanded) {
            Text(
                text = "MAC-адрес: $deviceAddress",
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 4.dp) // Добавляем отступ для разделения мак адресса и названия
            )
        }
    }
}
