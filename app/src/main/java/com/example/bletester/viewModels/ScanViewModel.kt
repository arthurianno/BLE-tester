    package com.example.bletester.viewModels

    import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bletester.EntireCheck
import com.example.bletester.ReportItem
import com.example.bletester.ble.BleCallbackEvent
import com.example.bletester.ble.BleControlManager
import com.example.bletester.ble.FileModifyEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Queue
import javax.inject.Inject


    @Suppress("DEPRECATION")
    @HiltViewModel
    @SuppressLint("StaticFieldLeak")
        class ScanViewModel @Inject constructor (val bleControlManager: BleControlManager,private val reportViewModel: ReportViewModel) : ViewModel(),FileModifyEvent {
            val toastMessage = MutableStateFlow<String?>(null)
            var deviceQueue: Queue<BluetoothDevice> = LinkedList()
            var deviceQueueProcessed: Queue<BluetoothDevice> = LinkedList()
            var foundDevices = mutableStateListOf<BluetoothDevice>()
            val unCheckedDevices = mutableStateListOf<BluetoothDevice>()
            val checkedDevices = mutableStateListOf<BluetoothDevice>()
            private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
            private val bluetoothLeScanner = adapter?.bluetoothLeScanner
            private val settings: ScanSettings
            private val filters: List<ScanFilter>
            var _scanning = MutableStateFlow(false)
            private var errorMessage: String? = null
            private var stopRequested = false
            private var startR : Long = 0L
            private var endR : Long = 0L
            private var diffRanges : Int? = 0
            private var counter = 0
            var currentDevice: BluetoothDevice? = null
            var bannedDevices = mutableStateListOf<BluetoothDevice>()
            val progress = MutableStateFlow(0f)
        init {
            reportViewModel.registerCallback(this)
            observeAddressRange()
            settings = buildSettings()
            filters = buildFilter()
        }

        private fun buildSettings() =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()

        private fun buildFilter() =
            listOf(
                ScanFilter.Builder()
                    .build()
            )

        @SuppressLint("MissingPermission")
        private fun observeAddressRange() {
            val deviceTypeToLetter = mapOf(
                "Online" to "D",
                "Voice" to "E",
                "AnotherDevice" to "F"
            )

            viewModelScope.launch {
                combine(reportViewModel._addressRange, reportViewModel.typeOfDevice) { range, type ->
                    Pair(range, type)
                }.collect { (range, type) ->
                    if (range != null && !type.isNullOrEmpty()) {
                        val (first, second) = range
                        Log.e("ScanCheckCollect", "$first and $second, Type: $type")

                        val firstLong = first.toLongOrNull()
                        val secondLong = second.toLongOrNull()

                        if (firstLong != null && secondLong != null) {
                            val letter = deviceTypeToLetter[type]
                            if (letter != null) {
                                if(_scanning.value){
                                    clearData()
                                    stopScanning()
                                    adapter?.disable()
                                    }else{
                                    scanLeDevice(letter, firstLong, secondLong)
                                    }
                            } else {
                                Log.e("ScanCheckCollect", "Unknown device type: $type")
                            }
                        } else {
                            Log.e("ScanCheckCollect", "Invalid address range: $first to $second")
                        }
                    } else {
                        Log.e("ScanCheckCollect", "Range or type is null or empty. Range: $range, Type: $type")
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        fun scanLeDevice(letter: String, start: Long, end: Long) {
            clearData()
            Log.e("ScanCheck1", "this is scan state $ _scanning.value")
            toastMessage.value = "Сканирование!"
            startR = start
            endR = end
            diffRanges = (end - start + 1).toInt()
            if (!_scanning.value) {
                stopRequested = false
                _scanning.value = true
                bluetoothLeScanner?.startScan(leScanCallback(letter, start, end))
                Log.e("ScanCheck2", "this is scan state ${_scanning.value}")
            } else {
                _scanning.value = false
                bluetoothLeScanner?.stopScan(leScanCallback(letter, start, end))
                Log.e("ScanCheck3", "this is scan state ${_scanning.value}")
            }
        }

        @SuppressLint("MissingPermission")
        fun stopScanning() {
            toastMessage.value = "Остановка сканирования!"
            stopRequested = true
            bluetoothLeScanner?.stopScan(leScanCallback("", 0, 0))
            _scanning.value = false
            deviceQueue.clear()
            Log.e("ScanViewModel", "Сканирование остановлено и очередь устройств очищена")
        }


        @SuppressLint("MissingPermission")
         fun createReportItems(deviceList: List<BluetoothDevice>, status: String): List<ReportItem> {
            return deviceList.map { device ->
                ReportItem(
                    device = device.name ?: "Unknown Device",
                    deviceAddress = device.address,
                    status = status,
                    interpretation = errorMessage?.let { errorMessage } ?: "Устройство не в эфире!"

                )
            }
        }
        @SuppressLint("MissingPermission")
         fun updateReportViewModel(command:String) {
            val uncheckedReportItems = createReportItems(unCheckedDevices.distinct(), "Unchecked")
            val approvedReportItems = createReportItems(checkedDevices.distinct(), "Checked")
            if (command.contains("Manual")){
                reportViewModel.updateReportItemsManual(uncheckedReportItems,approvedReportItems)
            }else{
                reportViewModel.updateReportItems(uncheckedReportItems, approvedReportItems)
            }
            Log.e("ScanViewModel", "${unCheckedDevices.toList()}")
        }


        @SuppressLint("MissingPermission")
        private fun leScanCallback(letter: String, start: Long, end: Long) = object : ScanCallback() {
            @SuppressLint("MissingPermission", "SuspiciousIndentation")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                if (stopRequested) return
                val device = result.device
                val deviceName = device.name ?: return
                val startLastFour = start.toString().takeLast(4)
                val endLastFour = end.toString().takeLast(4)
                    if (deviceName.contains("Satellite")) {
                        //Log.e("","Devices filters : ${device.name}")
                        val lastFourDigits = deviceName.takeLast(4).toInt()
                        if (lastFourDigits in (startLastFour.toInt()..endLastFour.toInt())) {
                            if(device.address !in bannedDevices.map { it.address }) {
                                Log.e("ScanViewModel", "device $deviceName")
                                if (device !in deviceQueue && device !in foundDevices && device !in checkedDevices) {
                                    deviceQueue.add(device)
                                    foundDevices.add(device)
                                    if (currentDevice == null) {
                                        currentDevice = device
                                        connectionToAnotherDevice()
                                    }
                                }
                            }
                            bleControlManager.setBleCallbackEvent(object : BleCallbackEvent {

                                override fun onHandleCheck() {
                                    Log.e("onHandleCheck","Callback")
                                    connectionToAnotherDevice()
                                }

                                override fun onVersionCheck(version: String) {
                                    val versionPrefix = version.firstOrNull()
                                    val versionNumber = version.substring(1).toLongOrNull()
                                    val devicesChecked = deviceQueueProcessed.remove()

                                    if (versionPrefix == null || versionNumber == null) {
                                        Log.e("ScanViewModel", "Invalid version format")
                                        currentDevice = null
                                        connectionToAnotherDevice()
                                        return
                                    }

                                    Log.e("ScanViewModel", "Version: $version")

                                    if (versionNumber in start..end && versionPrefix.toString()
                                            .contains(letter)
                                    ) {
                                        Log.e("ScanViewModel", "Device serial number in range!")
                                        Log.e(
                                            "ScanViewModel",
                                            "Device added to checkedDevices: $devicesChecked"
                                        )
                                        checkedDevices.add(devicesChecked)
                                        bleControlManager.sendCommand("ble.off",EntireCheck.default_command)
                                        currentDevice = null
                                    } else {
                                        Log.e("ScanViewModel", "Device serial number out of range!")
                                        Log.e("ScanViewModel", "Device added to BANNED LIST!")
                                        bannedDevices.add(device)
                                        bleControlManager.disconnect().enqueue()
                                        counter--
                                        currentDevice = null
                                        connectionToAnotherDevice()
                                    }
                                }
                            })

                        }
                    }
                }
            }

            @SuppressLint("MissingPermission")
            fun connectionToAnotherDevice() {
                if (diffRanges!! != counter) {
                    Log.e("diffRanges","$diffRanges")
                    Log.e("Counter","$counter")
                    if (deviceQueue.isNotEmpty()) {
                        currentDevice = deviceQueue.remove()
                        currentDevice?.let {
                            counter++
                            bleControlManager.connect(it)
                                .done { device ->
                                    Log.d("BleControlManager", "Connected to device ${device.name}")
                                    bleControlManager.sendPinCommand("master", EntireCheck.PIN_C0DE)
                                    foundDevices.remove(device)
                                    if (unCheckedDevices.contains(device)) {
                                        unCheckedDevices.remove(device)
                                    }
                                    deviceQueueProcessed.add(device)
                                }
                                .fail { device, status ->
                                    Log.e("BleControlManager", "Failed to connect to device ${device.name}: $status")
                                    errorMessage = "Failed to connect: $status"
                                    foundDevices.remove(device)
                                    if (!unCheckedDevices.contains(device)) {
                                        unCheckedDevices.add(device)
                                    }
                                    deviceQueue.addAll(unCheckedDevices)
                                    counter--
                                    currentDevice = null
                                    connectionToAnotherDevice()
                                }
                                .enqueue()
                        }
                    }
                } else {
                    stopScanning()
                    updateReportViewModel("")
                    foundDevices.clear()
                    deviceQueue.clear()
                }
            }

            private fun clearData() {
                deviceQueue.clear()
                reportViewModel.reportItems.value = emptyList()
                foundDevices.clear()
                unCheckedDevices.clear()
                checkedDevices.clear()
                bannedDevices.clear()

            }

            companion object {
                val entireCheckQueue: Queue<EntireCheck> = LinkedList()
            }

        override fun onEvent(event: String) {
            if(event.contains("Modify")){
                stopScanning()
                updateReportViewModel("")
            }else if(event.contains("Deleted")){
                stopScanning()
                updateReportViewModel("")
            }
        }
    }

