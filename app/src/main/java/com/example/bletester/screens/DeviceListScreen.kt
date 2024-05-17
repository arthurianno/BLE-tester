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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import java.util.LinkedList
import java.util.Queue


@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun DeviceListScreen(onBluetoothStateChanged: () -> Unit) {
    val scanViewModel: ScanViewModel = hiltViewModel()

    SystemBroadcastReceiver(systemAction = BluetoothAdapter.ACTION_STATE_CHANGED) { bluetoothState ->
        val action = bluetoothState?.action ?: return@SystemBroadcastReceiver
        if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            onBluetoothStateChanged()
        }
    }

    val permissionState = rememberMultiplePermissionsState(permissions = PermissionUtils.permissions)
    val lifecycleOwner = LocalLifecycleOwner.current
    var startRange by remember { mutableStateOf("") }
    var endRange by remember { mutableStateOf("") }

    DisposableEffect(key1 = lifecycleOwner, effect = {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                permissionState.launchMultiplePermissionRequest()
                if (permissionState.allPermissionsGranted) {
                    Log.e("DeviceListScreen", "All permissions granted!")
                }
            }
            if (event == Lifecycle.Event.ON_STOP) {
                Log.e("DeviceListScreen", "STOP")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    })

    val deviceTypes = listOf("SatelliteOnline", "SatelliteVoice", "AnotherDevice")
    var selectedDeviceType by remember { mutableStateOf(deviceTypes[0]) }
    var showDropdown by remember { mutableStateOf(false) }
    var deviceQueue by remember { mutableStateOf<Queue<BluetoothDevice>>(LinkedList()) }
    val isStartRangeValid = remember { mutableStateOf(true) }
    val isEndRangeValid = remember { mutableStateOf(true) }
    val deviceTypeToLetter = mapOf(
        "SatelliteOnline" to "D",
        "SatelliteVoice" to "E",
        "AnotherDevice" to "F"
    )

    // LaunchedEffect to update deviceList when scanViewModel.deviceQueue changes
    LaunchedEffect(scanViewModel.deviceQueue) {
        deviceQueue = scanViewModel.deviceQueue
    }
    val currentLetter by remember {
        derivedStateOf {
            deviceTypeToLetter[selectedDeviceType] ?: ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExposedDropdownMenuBox(expanded = showDropdown, onExpandedChange = { showDropdown = !showDropdown }) {
            TextField(
                modifier = Modifier.menuAnchor(),
                value = selectedDeviceType,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown) }
            )

            ExposedDropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                deviceTypes.forEachIndexed { index, text ->
                    DropdownMenuItem(
                        text = { Text(text = text) },
                        onClick = {
                            selectedDeviceType = deviceTypes[index]
                            showDropdown = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
        TextField(
            value = startRange,
            onValueChange = { newValue ->
                startRange = newValue
                isStartRangeValid.value = newValue.matches(Regex("^$currentLetter\\d{10}$"))
                Log.e("DevicesListScreen", isStartRangeValid.value.toString())
            },
            label = { Text("Начальное значение") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
            colors = TextFieldDefaults.colors(
                unfocusedLabelColor = if (isStartRangeValid.value) Color.Gray else Color.Red,
                focusedIndicatorColor = if (isStartRangeValid.value) Color.Blue else Color.Red,
                unfocusedIndicatorColor = if (isStartRangeValid.value) Color.Gray else Color.Red
            )
        )

        TextField(
            value = endRange,
            onValueChange = { newValue ->
                endRange = newValue
                isEndRangeValid.value = newValue.matches(Regex("^$currentLetter\\d{10}$"))
                Log.e("DevicesListScreen", isEndRangeValid.value.toString())
                Log.e("DevicesListScreen", "this is $currentLetter")
            },
            label = { Text("Конечное значение") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
            colors = TextFieldDefaults.colors(
                unfocusedLabelColor = if (isEndRangeValid.value) Color.Gray else Color.Red,
                focusedIndicatorColor = if (isEndRangeValid.value) Color.Blue else Color.Red,
                unfocusedIndicatorColor = if (isEndRangeValid.value) Color.Gray else Color.Red
            )
        )
        Button(
            onClick = {
                if (scanViewModel.deviceQueue.isNotEmpty()) {
                    scanViewModel.clearData()
                }
                val startNumber = startRange.drop(1)
                val endNumber = endRange.drop(1)

                if (startNumber.length >= 4 && endNumber.length >= 4) {
                    val startLastFour = startNumber.takeLast(4)
                    val endLastFour = endNumber.takeLast(4)

                    // Передаем текущую букву, последние 4 цифры начального и конечного диапазона
                    scanViewModel.scanLeDevice(currentLetter, startLastFour, endLastFour)
                } else {
                    Log.e("DeviceListScreen", "Data incorrect")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Начать сканирование")
        }

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(deviceQueue.toList()) { device ->
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
            .clickable { expanded = !expanded }
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
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}