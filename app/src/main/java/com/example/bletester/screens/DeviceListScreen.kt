package com.example.bletester.screens
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.bletester.permissions.PermissionUtils
import com.example.bletester.permissions.SystemBroadcastReceiver
import com.example.bletester.viewModels.ScanViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState


@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun DeviceListScreen(onBluetoothStateChanged:()->Unit) {
    val scanViewModel: ScanViewModel = hiltViewModel()

    SystemBroadcastReceiver(systemAction = BluetoothAdapter.ACTION_STATE_CHANGED){ bluetoothState ->
        val action = bluetoothState?.action ?: return@SystemBroadcastReceiver
        if(action == BluetoothAdapter.ACTION_STATE_CHANGED){
            onBluetoothStateChanged()
        }
    }

    val permissionState = rememberMultiplePermissionsState(permissions = PermissionUtils.permissions)
    val lyfecycleOwner = LocalLifecycleOwner.current
    var startRange by remember { mutableStateOf("") }
    var endRange by remember { mutableStateOf("") }

    DisposableEffect(key1 = lyfecycleOwner, effect ={
        val observer = LifecycleEventObserver{_,event ->
            if(event == Lifecycle.Event.ON_START){
                permissionState.launchMultiplePermissionRequest()
                if(permissionState.allPermissionsGranted){
                    Log.e("DeviceListScreen", "All permissions granted!")
                }
            }
            if(event == Lifecycle.Event.ON_STOP){
                Log.e("DeviceListScreen", "STOP")
            }
        }
        lyfecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lyfecycleOwner.lifecycle.removeObserver(observer)
        }
    })

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
        TextField(
        value = startRange,
        onValueChange = {
            if (it.length <= 12 && it.all { char -> char.isDigit() }) {
                startRange = it
            }
        },
        label = { Text("Начальное значение") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
    )

        // TextField для ввода конечного значения диапазона
        TextField(
            value = endRange,
            onValueChange = {
                if (it.length <= 12 && it.all { char -> char.isDigit() }) {
                    endRange = it
                }
            },
            label = { Text("Конечное значение") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        )
        Button(
            onClick = {
                val start = startRange.toLongOrNull()
                val end = endRange.toLongOrNull()

                if (start != null && end != null && start <= end) {
                    // Запустить сканирование с заданным диапазоном

                    scanViewModel.scanLeDevice(start, end)
                } else {
                    Log.e("DeviceListScreen","Data incorrect ")
                }
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
        modifier = Modifier
            .padding(8.dp)
            .clickable {
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