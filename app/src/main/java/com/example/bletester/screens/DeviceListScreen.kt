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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.bletester.screens.dialogs.CustomProgressBar
import com.example.bletester.viewModels.ReportViewModel
import com.example.bletester.viewModels.ScanViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterialApi::class)
@SuppressLint("MissingPermission", "MutableCollectionMutableState",
    "StateFlowValueCalledInComposition"
)
@Composable
fun DeviceListScreen(onBluetoothStateChanged: () -> Unit) {
    val scanViewModel: ScanViewModel = hiltViewModel()
    val reportViewModel: ReportViewModel = hiltViewModel()

    SystemBroadcastReceiver(systemAction = BluetoothAdapter.ACTION_STATE_CHANGED) { bluetoothState ->
        val action = bluetoothState?.action ?: return@SystemBroadcastReceiver
        if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            onBluetoothStateChanged()
        }
    }

    val permissionState = rememberMultiplePermissionsState(permissions = PermissionUtils.permissions)
    val addressRange by reportViewModel._addressRange.collectAsState(null)
    val lifecycleOwner = LocalLifecycleOwner.current
    //var startRange by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val globalContext = LocalContext.current.applicationContext
    //var endRange by remember { mutableLongStateOf(0L) }
    val toastMessage by scanViewModel.toastMessage.collectAsState()
    val optionTypeName = listOf(
        DeviceListOption.ALL_DEVICES to "All",
        DeviceListOption.CHECKED_DEVICES to "Approved",
        DeviceListOption.UNCHEKED_DEVICES to "UnCheck"
    )
    var startRange by rememberSaveable { mutableLongStateOf(0L) }
    var endRange by rememberSaveable { mutableLongStateOf(0L) }


    DisposableEffect(key1 = lifecycleOwner) {
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
    }



    val deviceTypes = listOf("SatelliteOnline", "SatelliteVoice", "AnotherDevice")
    var selectedDeviceType by remember { mutableStateOf(deviceTypes[0]) }
    var selectedModeType by remember { mutableStateOf(optionTypeName[0]) }
    var foundedDevice by remember { mutableStateOf(scanViewModel.foundDevices) }
    var checkedDevice by remember { mutableStateOf(scanViewModel.checkedDevices) }
    var uncheckedDevice by remember { mutableStateOf(scanViewModel.unCheckedDevices) }
    var showDropdown by remember { mutableStateOf(false) }
    var showDropdownOption by remember { mutableStateOf(false) }
    val isStartRangeValid = remember { mutableStateOf(true) }
    val isEndRangeValid = remember { mutableStateOf(true) }
    val deviceTypeToLetter = mapOf(
        "SatelliteOnline" to "D",
        "SatelliteVoice" to "E",
        "AnotherDevice" to "F"
    )
    val totalDevices = if (endRange > startRange) {
        (endRange - startRange + 1).toInt()
    } else {
        0
    }
    val progress by scanViewModel.progress.collectAsState()
    val scanning by scanViewModel.scanning.collectAsState()

    LaunchedEffect(scanViewModel.foundDevices, scanViewModel.unCheckedDevices, scanViewModel.checkedDevices) {
        foundedDevice = scanViewModel.foundDevices
        checkedDevice = scanViewModel.checkedDevices
        uncheckedDevice = scanViewModel.unCheckedDevices

        Log.e("ScanCheck", "Items ${foundedDevice.toList()}")
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(globalContext, it, Toast.LENGTH_SHORT).show()
            scanViewModel.toastMessage.value = null // Reset the message after showing it
        }
    }

    val currentLetter by remember {
        derivedStateOf {
            deviceTypeToLetter[selectedDeviceType] ?: ""
        }
    }

    val currentDeviceType by remember {
        derivedStateOf {
            // Преобразование типа устройства в соответствующее значение для отчета
            when (selectedDeviceType) {
                "SatelliteOnline" -> "D"
                "SatelliteVoice" -> "F"
                "AnotherDevice" -> "E"
                else -> ""
            }
        }
    }


    LaunchedEffect(addressRange) {
        Log.e("DevicesListScreen", "Trying to change address")
        addressRange?.let { (start, end) ->
            val newStartRange = start.toLongOrNull() ?: 0L
            val newEndRange = end.toLongOrNull() ?: 0L
            if (newStartRange != startRange || newEndRange != endRange) {
                startRange = newStartRange
                endRange = newEndRange
                Log.e("DevicesListScreen", "Changing address 1 and 2")
                if (startRange != 0L && endRange != 0L) {
                    //scanViewModel.scanLeDevice(currentLetter, startRange, endRange)
                } else {
                    Log.e("DevicesListScreen", "Address is nulls")
                }
            }
        }
    }




    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ExposedDropdownMenuBox(
                    expanded = showDropdown,
                    onExpandedChange = { showDropdown = !showDropdown }
                ) {
                    TextField(
                        modifier = Modifier
                            .clickable { showDropdown = true }
                            .clip(RoundedCornerShape(8.dp)),
                        value = selectedDeviceType,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = "Dropdown Icon") },
                        label = { Text("Device Type") }
                    )

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }
                    ) {
                        deviceTypes.forEachIndexed { index, text ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedDeviceType = deviceTypes[index]
                                    showDropdown = false
                                }
                            ) {
                                Text(text = text)
                            }
                        }
                    }
                }
            }
                CustomProgressBar(progress = progress, currentCount = if (totalDevices > 0) checkedDevice.size else 0, totalCount = totalDevices)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextField(
                    value = if (startRange == 0L) "" else startRange.toString(),
                    onValueChange = { newValue ->
                        val longValue = newValue.toLongOrNull() ?: 0
                        startRange = longValue
                        isStartRangeValid.value = newValue.matches(Regex("^\\d{10}$"))
                                    },
                    label = { Text("Start") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = if (isStartRangeValid.value) Color(0xFFF0F0F0) else Color(0xFFFFCDD2),
                        cursorColor = Color.Black,
                        focusedIndicatorColor = if (isStartRangeValid.value) Color(0xFF6200EE) else Color.Red,
                        unfocusedIndicatorColor = if (isStartRangeValid.value) Color.Gray else Color.Red
                    )
                )

                Spacer(modifier = Modifier.width(16.dp))

                TextField(
                    value = if (endRange == 0L) "" else endRange.toString(),
                    onValueChange = { newValue ->
                        val longValue = newValue.toLongOrNull() ?: 0
                        endRange = longValue
                        isEndRangeValid.value = newValue.matches(Regex("^\\d{10}$"))
                    },
                    label = { Text("End") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = if (isEndRangeValid.value) Color(0xFFF0F0F0) else Color(0xFFFFCDD2),
                        cursorColor = Color.Black,
                        focusedIndicatorColor = if (isEndRangeValid.value) Color(0xFF6200EE) else Color.Red,
                        unfocusedIndicatorColor = if (isEndRangeValid.value) Color.Gray else Color.Red
                    )
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ExposedDropdownMenuBox(
                    expanded = showDropdownOption,
                    onExpandedChange = { showDropdownOption = !showDropdownOption },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    TextField(
                        modifier = Modifier.clickable { showDropdownOption = true },
                        value = selectedModeType.second,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = "Dropdown Icon") },
                        label = { Text("Mode") }
                    )

                    DropdownMenu(
                        expanded = showDropdownOption,
                        onDismissRequest = { showDropdownOption = false }
                    ) {
                        optionTypeName.forEachIndexed { index, deviceListOption ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedModeType = optionTypeName[index]
                                    showDropdownOption = false
                                }
                            ) {
                                Text(
                                    text = deviceListOption.second,
                                    style = TextStyle(fontSize = 14.sp)
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        if (scanning) {
                            Log.e("Click","scanning updt $scanning")
                            // Логика при остановке сканирования
                            scanViewModel.stopScanning()
                            scanViewModel.updateReportViewModel("Manual")
                        } else {
                            // Логика при начале сканирования
                            scanViewModel.scanLeDevice(currentDeviceType,startRange,endRange)
                            Log.e("ScanList","type of dev $currentDeviceType")
                            //reportViewModel._addressRange.value = Pair(startRange.toString(), endRange.toString())
                            //Log.e("DevicesListScreen", "this is range ${Pair(startRange, endRange)}")
                           // reportViewModel.typeOfDevice.value = currentDeviceType
                            //Log.e("DevicesListScreen", "this is letter $currentLetter")
                            scanViewModel.scanning.value = true
                           // scanViewModel.scanning.value = true
                        }

                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = if (scanning) Color.Red else Color(0xFF6200EE))
                ) {
                    val buttonIcon = if (scanning) Icons.Filled.Close else Icons.Filled.Search
                    val buttonLabel = if (scanning) "STOP" else "SCAN IT!"
                    Icon(
                        imageVector = buttonIcon,
                        contentDescription = if (scanning) "Stop Icon" else "Search Icon",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(buttonLabel, color = Color.White)
                }

            }

            Surface(
                modifier = Modifier
                    .padding(16.dp),
                color = Color.White,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 6.dp
            ) {
                LazyColumn {
                    val devicesToShow = when (selectedModeType.first) {
                        DeviceListOption.ALL_DEVICES -> foundedDevice.toList()
                        DeviceListOption.CHECKED_DEVICES -> checkedDevice.toList()
                        DeviceListOption.UNCHEKED_DEVICES -> uncheckedDevice.toList()
                    }
                    itemsIndexed(devicesToShow) { _, device ->
                        DeviceListItem(deviceName = device.name, deviceAddress = device.address)
                    }
                }
            }
        }
    }
}
@Composable
fun DeviceListItem(deviceName: String, deviceAddress: String) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp)
            .clickable { expanded = !expanded }
            .clip(RoundedCornerShape(8.dp)),
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = "Device Name: $deviceName",
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                color = if (expanded) Color.Blue else Color.Black,
            )
            if (expanded) {
                Text(
                    text = "MAC Address: $deviceAddress",
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    color = Color.Gray,
                )
            }
        }
    }
}

enum class DeviceListOption {
    ALL_DEVICES,
    CHECKED_DEVICES,
    UNCHEKED_DEVICES
}
