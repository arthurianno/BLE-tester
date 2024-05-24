package com.example.bletester.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
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


@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission", "MutableCollectionMutableState")
@Composable
fun DeviceListScreen(onBluetoothStateChanged: () -> Unit) {
    val scanViewModel: ScanViewModel = hiltViewModel()

    SystemBroadcastReceiver(systemAction = BluetoothAdapter.ACTION_STATE_CHANGED) { bluetoothState ->
        val action = bluetoothState?.action ?: return@SystemBroadcastReceiver
        if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            onBluetoothStateChanged()
        }
    }

    val permissionState =
        rememberMultiplePermissionsState(permissions = PermissionUtils.permissions)
    val lifecycleOwner = LocalLifecycleOwner.current
    var startRange by remember { mutableStateOf(0L) }
    val context = LocalContext.current
    var endRange by remember { mutableStateOf(0L) }
    val toastMessage by scanViewModel.toastMessage.collectAsState()
    val optionTypeName= listOf(
        DeviceListOption.ALL_DEVICES to "All",
        DeviceListOption.CHECKED_DEVICES to "Approved",
        DeviceListOption.UNCHEKED_DEVICES to "UnCheck"
    )

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
    var selectedModeType by remember { mutableStateOf(optionTypeName[0]) }
    var foundedDevice = scanViewModel.foundDevices
    var checkedDevice = scanViewModel.checkedDevices
    var uncheckedDevice = scanViewModel.unCheckedDevices
    var showDropdown by remember { mutableStateOf(false) }
    var showDropdownOption by remember { mutableStateOf(false) }
    val isStartRangeValid = remember { mutableStateOf(true) }
    val isEndRangeValid = remember { mutableStateOf(true) }
    val deviceTypeToLetter = mapOf(
        "SatelliteOnline" to "D",
        "SatelliteVoice" to "E",
        "AnotherDevice" to "F"
    )


    // LaunchedEffect to update deviceList when scanViewModel.deviceQueue changes
    LaunchedEffect(scanViewModel.foundDevices,scanViewModel.unCheckedDevices,scanViewModel.checkedDevices) {
        foundedDevice = scanViewModel.foundDevices
        checkedDevice = scanViewModel.checkedDevices
        uncheckedDevice = scanViewModel.unCheckedDevices

        Log.e("ScanCheck","Items ${foundedDevice.toList()}")
    }
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            scanViewModel.toastMessage.value = null // Сбросить сообщение после показа
        }
    }
    val currentLetter by remember {
        derivedStateOf {
            deviceTypeToLetter[selectedDeviceType] ?: ""
        }
    }
    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = showDropdown,
            onExpandedChange = { showDropdown = !showDropdown }) {
            TextField(
                modifier = Modifier.menuAnchor(),
                value = selectedDeviceType,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown) }
            )

            ExposedDropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false }) {
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextField(
                value = if (startRange == 0.toLong()) "" else startRange.toString(),
                onValueChange = { newValue ->
                    val longValue = newValue.toLongOrNull() ?: 0
                    startRange = longValue
                    isStartRangeValid.value = newValue.matches(Regex("^\\d{10}$"))
                    Log.e("DevicesListScreen", isStartRangeValid.value.toString())
                },
                label = { Text("Start") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.colors(
                    unfocusedLabelColor = if (isStartRangeValid.value) Color.Gray else Color.Red,
                    focusedIndicatorColor = if (isStartRangeValid.value) Color.Blue else Color.Red,
                    unfocusedIndicatorColor = if (isStartRangeValid.value) Color.Gray else Color.Red
                )
            )

            Spacer(modifier = Modifier.width(16.dp))

            TextField(
                value = if (endRange == 0.toLong()) "" else endRange.toString(),
                onValueChange = { newValue ->
                    val longValue = newValue.toLongOrNull() ?: 0
                    endRange = longValue
                    isEndRangeValid.value = newValue.matches(Regex("^\\d{10}$"))
                    Log.e("DevicesListScreen", isEndRangeValid.value.toString())
                    Log.e("DevicesListScreen", "this is $currentLetter")
                },
                label = { Text("End") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.colors(
                    unfocusedLabelColor = if (isEndRangeValid.value) Color.Gray else Color.Red,
                    focusedIndicatorColor = if (isEndRangeValid.value) Color.Blue else Color.Red,
                    unfocusedIndicatorColor = if (isEndRangeValid.value) Color.Gray else Color.Red
                )
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ExposedDropdownMenuBox(
                expanded = showDropdownOption,
                onExpandedChange = { showDropdownOption = !showDropdownOption },
                modifier = Modifier.weight(1f)) {
                TextField(
                    modifier = Modifier.menuAnchor(),
                    value = selectedModeType.second,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdownOption) }
                )

                ExposedDropdownMenu(
                    expanded = showDropdownOption,
                    onDismissRequest = { showDropdownOption = false }) {
                    optionTypeName.forEachIndexed { index, deviceListOption ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = deviceListOption.second,
                                    style = TextStyle(fontSize = 14.sp)
                                )
                            },
                            onClick = {
                                selectedModeType = optionTypeName[index]
                                showDropdownOption = false
                               },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
            Button(onClick = {
                // Очистка данных из ScanViewModel
                scanViewModel.clearData()
                foundedDevice.clear()
                checkedDevice.clear()
                // Очистка списков на экране
                scanViewModel.scanLeDevice(currentLetter,startRange,endRange)
                showToast(context,"Сканирование начато")
            }) {
                Text("SCAN IT!")

            }
        }

        LazyColumn {
            val devicesToShow = when (selectedModeType.first) {
                DeviceListOption.ALL_DEVICES -> foundedDevice.toList()
                DeviceListOption.CHECKED_DEVICES -> checkedDevice.toList()
                DeviceListOption.UNCHEKED_DEVICES -> uncheckedDevice.toList()
            }
            itemsIndexed(devicesToShow) { index, device ->
                DeviceListItem(deviceName = device.name, deviceAddress = device.address)
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
enum class DeviceListOption {
    ALL_DEVICES,
    CHECKED_DEVICES,
    UNCHEKED_DEVICES
}
