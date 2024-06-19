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
import com.example.bletester.EntireCheck
    import com.example.bletester.Logger
    import com.example.bletester.ReportItem
import com.example.bletester.ble.BleCallbackEvent
import com.example.bletester.ble.BleControlManager
import com.example.bletester.ble.FileModifyEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
            var scanning = MutableStateFlow(false)
            private var errorMessage: String? = null
            private var stopRequested = false
            private var startR : Long = 0L
            private var endR : Long = 0L
            private var diffRanges : Int? = 0
            private var counter = 0
            var currentDevice: BluetoothDevice? = null
            var bannedDevices = mutableStateListOf<BluetoothDevice>()
            val progress = MutableStateFlow(0f)
            private var letter = ""
            private var isScanning: Boolean = false
        private val deviceTypeToLetter = mapOf(
            "Online" to "D",
            "Voice" to "E",
            "AnotherDevice" to "F"
        )
        private val deviceTypeToLetterToReport = mapOf(
            "D" to "Online",
            "E" to "Voice",
            "F" to "AnotherDevices"
        )

        init {
            reportViewModel.registerCallback(this)
            //observeAddressRange()
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
        fun scanLeDevice(letter: String, start: Long, end: Long) {
            val typeOfLetterForReport = deviceTypeToLetterToReport[letter]
            reportViewModel._addressRange.value = Pair(start.toString(), end.toString())
            Log.e("DevicesListScreen", "this is range ${Pair(start, end)}")
            reportViewModel.typeOfDevice.value = typeOfLetterForReport
            Log.e("DevicesListScreen", "this is letter to report $typeOfLetterForReport")
            toastMessage.value = "Сканирование!"
            startR = start
            endR = end
            currentDevice = null
            deviceQueue.clear()
            foundDevices.clear()
            unCheckedDevices.clear()
            checkedDevices.clear()
            deviceQueueProcessed.clear()
            bannedDevices.clear()
            counter = 0
            diffRanges = (end - start + 1).toInt()
            if (!scanning.value) {
                stopRequested = false
                scanning.value = true
                bluetoothLeScanner?.startScan(leScanCallback(letter, start, end))
                Log.i("SCAN", "SCANNING")
                Logger.i("ScanViewModel","SCANNING")
            } else {
                Log.e("ScanCheckLog","VALUE OF SCAN ${scanning.value}")
            }
        }

        @SuppressLint("MissingPermission")
        fun stopScanning() {
            if(deviceQueueProcessed.isNotEmpty()){
                bleControlManager.disconnect()
            }
            toastMessage.value = "Остановка сканирования!"
            isScanning = false
            stopRequested = true
            scanning.value = false
            Log.i("SCAN", " STOP SCANNING")
            foundDevices.clear()
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
            Log.i("ScanViewModel", "${unCheckedDevices.toList()}")

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
                                if (device.address !in deviceQueue.map { it.address } && device.address !in foundDevices.map { it.address } && device.address !in checkedDevices.map { it.address }) {
                                    deviceQueue.add(device)
                                    foundDevices.add(device)
                                    if (currentDevice == null) {
                                        currentDevice = device
                                        connectionToAnotherDevice()
                                    }
                                }else{
                                    Log.e("Filters  in massivs","Devices : $device in massivs type!")
                                }
                            }else{
                                Log.e("Filters banned","Devices : $device in banned type!")
                            }
                            bleControlManager.setBleCallbackEvent(object : BleCallbackEvent {

                                override fun onHandleCheck() {
                                    connectionToAnotherDevice()
                                    Log.e("onHandleCheck","connection To Another Dev after Callback")
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
                                        counter--
                                        bleControlManager.disconnect().enqueue()
                                        Log.e("Counter","$counter after adding from banned")
                                        currentDevice = null
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
                    if (deviceQueue.isNotEmpty() && scanning.value) {
                        Log.e("BleQueue","$deviceQueue")
                       var currentDevice = deviceQueue.remove()
                        if (currentDevice.address !in bannedDevices.map { it.address }) {
                            counter++
                            currentDevice?.let {
                                bleControlManager.connect(it)
                                    .done { device ->
                                        Log.d(
                                            "BleControlManager",
                                            "Connected to device ${device.name}"
                                        )
                                        bleControlManager.sendPinCommand(
                                            "master",
                                            EntireCheck.PIN_C0DE
                                        )
                                        foundDevices.remove(device)
                                        if (unCheckedDevices.contains(device)) {
                                            unCheckedDevices.remove(device)
                                        }
                                        deviceQueueProcessed.add(device)
                                    }
                                    .fail { device, status ->
                                        counter--
                                        Log.e(
                                            "BleControlManager",
                                            "Failed to connect to device ${device.name}: $status"
                                        )
                                        errorMessage = "Failed to connect: $status"
                                        foundDevices.remove(device)
                                        if (!unCheckedDevices.contains(device) && !stopRequested) {
                                            unCheckedDevices.add(device)
                                        }
                                        if(!deviceQueue.contains(device) && !stopRequested){
                                            deviceQueue.add(device)
                                        }
                                        Log.e("Counter", "$counter after mining")
                                        currentDevice = null
                                        connectionToAnotherDevice()
                                    }
                                    .enqueue()
                            }
                        }else{
                            connectionToAnotherDevice()
                        }

                    }else{
                        Log.e("ScanViewModel","Коннект не возможен так как сканирование окончено или очередь пуста!")
                    }
                } else {
                    stopScanning()
                    updateReportViewModel("")
                }
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
            }else if (event.contains("Auto")){
                letter = deviceTypeToLetter[reportViewModel.typeOfDevice.value.toString()].toString()
                reportViewModel._addressRange.value?.first?.toLong()?.let {
                    reportViewModel._addressRange.value?.second?.toLong()?.let { it1 ->
                        scanLeDevice(
                            letter,
                            it,
                            it1
                        )
                    }
                }
            }
        }
    }

