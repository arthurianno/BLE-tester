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
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.bletester.screens.dialogs.AnimatedCounter
import com.example.bletester.viewModels.ReportViewModel
import com.example.bletester.viewModels.ScanViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterialApi::class)
@SuppressLint("MissingPermission", "MutableCollectionMutableState")
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var startRange by remember { mutableStateOf(0L) }
    val context = LocalContext.current
    var endRange by remember { mutableStateOf(0L) }
    val toastMessage by scanViewModel.toastMessage.collectAsState()
    val optionTypeName = listOf(
        DeviceListOption.ALL_DEVICES to "All",
        DeviceListOption.CHECKED_DEVICES to "Approved",
        DeviceListOption.UNCHEKED_DEVICES to "UnCheck"
    )
    val counterState by reportViewModel.counter.collectAsState()

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

    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(scanViewModel.foundDevices, scanViewModel.unCheckedDevices, scanViewModel.checkedDevices) {
        foundedDevice = scanViewModel.foundDevices
        checkedDevice = scanViewModel.checkedDevices
        uncheckedDevice = scanViewModel.unCheckedDevices

        Log.e("ScanCheck", "Items ${foundedDevice.toList()}")
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            scanViewModel.toastMessage.value = null // Reset the message after showing it
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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

            AnimatedCounter(
                count = counterState,
                Modifier.clickable {
                    scope.launch {
                        snackbarHostState.showSnackbar("Задания: $counterState")
                    }
                }
            )

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
                        Log.e("DevicesListScreen", isStartRangeValid.value.toString())
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
                        Log.e("DevicesListScreen", isEndRangeValid.value.toString())
                        Log.e("DevicesListScreen", "this is $currentLetter")
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

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        scanViewModel.clearData()
                        foundedDevice.clear()
                        checkedDevice.clear()
                        scanViewModel.scanLeDevice(currentLetter, startRange, endRange)
                        showToast(context, "Сканирование начато")
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF6200EE))
                ) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Search Icon", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SCAN IT!", color = Color.White)
                }
            }

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

@Composable
fun DeviceListItem(deviceName: String, deviceAddress: String) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { expanded = !expanded }
            .clip(RoundedCornerShape(8.dp)),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Device Name: $deviceName",
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                color = if (expanded) Color.Blue else Color.Black,
            )
            if (expanded) {
                Text(
                    text = "MAC Address: $deviceAddress",
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
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
