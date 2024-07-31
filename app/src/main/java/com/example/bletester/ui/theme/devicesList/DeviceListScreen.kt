package com.example.bletester.ui.theme.devicesList

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.bletester.items.DeviceListItem
import com.example.bletester.receivers.SystemBroadcastReceiver
import com.example.bletester.ui.theme.components.CustomProgressBar
import com.example.bletester.ui.theme.report.ReportViewModel
import com.example.bletester.utils.PermissionUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState


@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterialApi::class)
@SuppressLint("MissingPermission", "StateFlowValueCalledInComposition",
    "MutableCollectionMutableState"
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
    val allPermissionsGranted = permissionState.allPermissionsGranted
    val addressRange by reportViewModel.addressRange.collectAsState(null)
    val lifecycleOwner = LocalLifecycleOwner.current
    val globalContext = LocalContext.current.applicationContext
    val toastMessage by scanViewModel.toastMessage.collectAsState()
    val optionTypeName = listOf(
        DeviceListOption.ALL_DEVICES to "Все устройства",
        DeviceListOption.CHECKED_DEVICES to "Проверенные",
        DeviceListOption.UNCHEKED_DEVICES to "Не прошедшие проверку"
    )

    val selectedDeviceType by scanViewModel.selectedDeviceType.collectAsState()
    val startRange by scanViewModel.startRange.collectAsState()
    val endRange by scanViewModel.endRange.collectAsState()
    val isStartRangeValid by scanViewModel.isStartRangeValid.collectAsState()
    val isEndRangeValid by scanViewModel.isEndRangeValid.collectAsState()
    val totalDevices by scanViewModel.totalDevices.collectAsState()
    val scanning by scanViewModel.scanning.collectAsState()
    val progress by scanViewModel.progress.collectAsState()
    val context = LocalContext.current

    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                permissionState.launchMultiplePermissionRequest()
                if (!allPermissionsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    !Environment.isExternalStorageManager()) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
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
    var selectedModeType by remember { mutableStateOf(optionTypeName[0]) }
    var foundedDevice by remember { mutableStateOf(scanViewModel.foundDevices) }
    var checkedDevice by remember { mutableStateOf(scanViewModel.checkedDevices) }
    var uncheckedDevice by remember { mutableStateOf(scanViewModel.unCheckedDevices) }
    var showDropdown by remember { mutableStateOf(false) }
    var showDropdownOption by remember { mutableStateOf(false) }

    LaunchedEffect(scanViewModel.foundDevices, scanViewModel.unCheckedDevices, scanViewModel.checkedDevices) {
        foundedDevice = scanViewModel.foundDevices
        checkedDevice = scanViewModel.checkedDevices
        uncheckedDevice = scanViewModel.unCheckedDevices
        Log.e("ScanCheck", "Items ${foundedDevice.toList()}")
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(globalContext, it, Toast.LENGTH_SHORT).show()
            scanViewModel.toastMessage.value = null
        }
    }

    LaunchedEffect(addressRange) {
        Log.e("DevicesListScreen", "Trying to change address")
        addressRange?.let { (start, end) ->
            val newStartRange = start.toLongOrNull() ?: 0L
            val newEndRange = end.toLongOrNull() ?: 0L
            if (newStartRange != startRange || newEndRange != endRange) {
                scanViewModel.setStartRange(newStartRange.toString())
                scanViewModel.setEndRange(newEndRange.toString())
                Log.e("DevicesListScreen", "Changing address 1 and 2")
                if (startRange != 0L && endRange != 0L) {
                    // Логика при изменении диапазона
                } else {
                    Log.e("DevicesListScreen", "Address is nulls")
                }
            }
        }
    }

    Scaffold { innerPadding ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        }
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
                        label = { Text("Тип устройства") }
                    )

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }
                    ) {
                        deviceTypes.forEachIndexed { index, text ->
                            DropdownMenuItem(
                                onClick = {
                                    scanViewModel.updateSelectedDeviceType(when(index) {
                                        0 -> "D"
                                        1 -> "E"
                                        2 -> "F"
                                        else -> "D"
                                    })
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
                    onValueChange = { scanViewModel.setStartRange(it) },
                    label = { Text("Начало диапазона") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = if (isStartRangeValid) Color(0xFFF0F0F0) else Color(0xFFFFCDD2),
                        cursorColor = Color.Black,
                        focusedIndicatorColor = if (isStartRangeValid) Color(0xFF6200EE) else Color.Red,
                        unfocusedIndicatorColor = if (isStartRangeValid) Color.Gray else Color.Red
                    )
                )

                Spacer(modifier = Modifier.width(16.dp))

                TextField(
                    value = if (endRange == 0L) "" else endRange.toString(),
                    onValueChange = { scanViewModel.setEndRange(it) },
                    label = { Text("Конец диапазона") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = if (isEndRangeValid) Color(0xFFF0F0F0) else Color(0xFFFFCDD2),
                        cursorColor = Color.Black,
                        focusedIndicatorColor = if (isEndRangeValid) Color(0xFF6200EE) else Color.Red,
                        unfocusedIndicatorColor = if (isEndRangeValid) Color.Gray else Color.Red
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
                        label = { Text("Фильтр") }
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
                            scanViewModel.stopScanning()
                            scanViewModel.updateReportViewModel("Manual")
                        } else {
                            scanViewModel.scanLeDevice()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = if (scanning) Color.Red else Color(0xFF6200EE))
                ) {
                    val buttonIcon = if (scanning) Icons.Filled.Close else Icons.Filled.Search
                    val buttonLabel = if (scanning) "Завершить" else "Начать"
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
                    .fillMaxSize()
                    .padding(16.dp)
                    .border(BorderStroke(1.dp, Color.Gray), shape = RoundedCornerShape(16.dp)),
                color = Color(0xFFFFFFFF),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 0.dp
            ) {
                Column {
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        val devicesToShow = when (selectedModeType.first) {
                            DeviceListOption.ALL_DEVICES -> foundedDevice.toList()
                            DeviceListOption.CHECKED_DEVICES -> checkedDevice.toList()
                            DeviceListOption.UNCHEKED_DEVICES -> uncheckedDevice.toList()
                        }
                        itemsIndexed(devicesToShow) { index, device ->
                            DeviceListItem(device.name ?: "Unknown Device", deviceAddress = device.address)
                            if (index < devicesToShow.lastIndex) {
                                Divider(color = Color.LightGray, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class DeviceListOption {
    ALL_DEVICES,
    CHECKED_DEVICES,
    UNCHEKED_DEVICES
}