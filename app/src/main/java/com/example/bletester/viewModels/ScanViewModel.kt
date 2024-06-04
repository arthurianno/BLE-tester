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
    import com.example.bletester.ReportItem
    import com.example.bletester.ble.BleCallbackEvent
    import com.example.bletester.ble.BleControlManager
    import dagger.hilt.android.lifecycle.HiltViewModel
    import kotlinx.coroutines.flow.MutableStateFlow
    import java.util.LinkedList
    import java.util.Queue
    import javax.inject.Inject


    @Suppress("DEPRECATION")
    @HiltViewModel
    @SuppressLint("StaticFieldLeak")
        class ScanViewModel @Inject constructor (val bleControlManager: BleControlManager,private val reportViewModel: ReportViewModel) : ViewModel() {
            val toastMessage = MutableStateFlow<String?>(null)
            var deviceQueue: Queue<BluetoothDevice> = LinkedList()
            var foundDevices = mutableStateListOf<BluetoothDevice>()
            val unCheckedDevices = mutableStateListOf<BluetoothDevice>()
            val checkedDevices = mutableStateListOf<BluetoothDevice>()
            private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
            private val bluetoothLeScanner = adapter?.bluetoothLeScanner
            private val settings: ScanSettings
            private val filters: List<ScanFilter>
            private var scanning = false
            private val handler = android.os.Handler()
            private val scanPeriod: Long = 10000
            private var isConnecting = false
            private var checkCount = 0L
            private val processedDevices = mutableSetOf<String>()
            private var connectionAttempts = 0
            private var errorMessage: String? = null
            private var stopRequested = false
        init {
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
            if (!scanning) {
                stopRequested = false
                handler.postDelayed({
                    scanning = false
                    bluetoothLeScanner?.stopScan(leScanCallback(letter, start, end))
                }, scanPeriod)
                scanning = true
                bluetoothLeScanner?.startScan(leScanCallback(letter, start, end))
            } else {
                scanning = false
                bluetoothLeScanner?.stopScan(leScanCallback(letter, start, end))
            }
        }

        @SuppressLint("MissingPermission")
        private fun stopScanning() {
            stopRequested = true // Установить флаг остановки
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback("", 0, 0))
            deviceQueue.clear() // Очистить очередь
            Log.e("ScanViewModel", "Сканирование остановлено и очередь устройств очищена")
        }

        @SuppressLint("MissingPermission")
        private fun createReportItems(deviceList: List<BluetoothDevice>, status: String): List<ReportItem> {
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
        private fun updateReportViewModel() {
            stopScanning()
            Log.e("ScanViewModel","Scan Stop!")
            val uncheckedReportItems = createReportItems(unCheckedDevices.distinct(), "Unchecked")
            val approvedReportItems = createReportItems(unCheckedDevices.distinct(), "Checked")
            reportViewModel.updateReportItems(uncheckedReportItems,approvedReportItems)
            Log.e("ScanViewModel","${unCheckedDevices.toList()}")
        }
        @SuppressLint("MissingPermission")
        private fun leScanCallback(letter: String, start: Long, end: Long) = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                synchronized(this) {
                    super.onScanResult(callbackType, result)
                    if (stopRequested) return
                    val device = result.device
                    val deviceName = device.name ?: return

                    val validLastFourDigits = listOf("0001", "0002", "0325")

                    if (deviceName.contains("Satellite")) {
                        val lastFourDigits = deviceName.takeLast(4)
                        if (lastFourDigits in validLastFourDigits) {
                            if (!processedDevices.contains(device.address)) {
                                Log.e("ScanViewModel", "device $deviceName")
                                if (!isConnecting) {
                                    // Проверка, находится ли устройство уже в очереди
                                    if (deviceQueue.any { it.address == device.address }) {
                                        return
                                    }
                                    deviceQueue.add(device)
                                    foundDevices.add(device)
                                    connectToDeviceSequentially()
                                }
                                bleControlManager.setBleCallbackEvent(object : BleCallbackEvent {
                                    override fun onHandleCheck() {
                                        isConnecting = false
                                        checkCount++
                                        val totalCount = end - start + 1
                                        if (totalCount == checkCount) {
                                            checkCount = 0
                                            connectToDeviceSequentially()
                                        }
                                    }

                                    override fun onVersionCheck(version: String) {
                                        val versionPrefix = version.firstOrNull()
                                        val versionNumber = version.substring(1).toLongOrNull()

                                        if (versionPrefix == null || versionNumber == null) {
                                            Log.e("ScanViewModel", "Invalid version format")
                                            return
                                        }

                                        Log.e("ScanViewModel", "Version: $version")

                                        if (versionNumber in start..end && versionPrefix.toString().contains(letter)) {
                                            Log.e("ScanViewModel", "Device serial number in range!")
                                            updateDeviceLists(device, true)
                                        } else {
                                            Log.e("ScanViewModel", "Device serial number out of range!")
                                            updateDeviceLists(device, false)
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }



        @Synchronized
        private fun updateDeviceLists(device: BluetoothDevice, isChecked: Boolean) {
            val iterator = foundDevices.iterator()
            while (iterator.hasNext()) {
                val foundDevice = iterator.next()
                if (foundDevice.address == device.address) {
                    if (isChecked) {
                        if (!checkedDevices.any { it.address == device.address }) {
                            checkedDevices.add(device)
                            Log.e("ScanViewModel", "Device added to checkedDevices: $device")
                        }
                    } else {
                        if (!unCheckedDevices.any { it.address == device.address }) {
                            unCheckedDevices.add(device)
                            Log.e("ScanViewModel", "Device added to unCheckedDevices: $device")
                        }
                    }
                    Log.e("ScanViewModel", "Removed $device from foundDevices")
                    iterator.remove() // Удаление элемента с помощью итератора
                    processedDevices.add(device.address)
                    return // Добавьте эту строку, чтобы выйти из цикла после удаления элемента
                }
            }
        }


        @SuppressLint("MissingPermission")
        fun connectToDeviceSequentially() {
            if (deviceQueue.isNotEmpty() && !isConnecting) {
                isConnecting = true
                val device = deviceQueue.poll()
                device?.let {
                    bleControlManager.connect(it)
                        .done {
                            Log.d("BleControlManager", "Connected to device ${device.name}")
                            bleControlManager.sendPinCommand("master", EntireCheck.PIN_C0DE)
                        }
                        .fail { device, status ->
                            isConnecting = false
                            Log.e("BleControlManager", "Failed to connect to device ${device.name}: $status")
                            errorMessage = "Failed to connect: $status"
                            updateDeviceLists(device, false)
                            connectToDeviceSequentially()
                        }
                        .enqueue()
                }
            } else {
                Log.d("BleControlManager", "Checking unprocessed devices")
                toastMessage.value = "Checking unprocessed devices"
                if (unCheckedDevices.isEmpty()) {
                    Log.d("BleControlManager", "All devices processed")
                    toastMessage.value = "All devices processed"
                    updateReportViewModel()
                }
                connectionAttempts++
                if (unCheckedDevices.isNotEmpty()) {
                    val newDevices = unCheckedDevices.filter { device ->
                        !deviceQueue.any { it.address == device.address }
                    }
                    deviceQueue.addAll(newDevices)
                    unCheckedDevices.clear()
                    connectToDeviceSequentially()
                } else if (foundDevices.isNotEmpty()) {
                    // Если есть еще устройства в foundDevices, приступаем к следующему
                    connectToDeviceSequentially()
                }
            }
        }


        fun clearData() {
            deviceQueue.clear()
            foundDevices.clear()
            processedDevices.clear()

        }

        companion object{
            val entireCheckQueue: Queue<EntireCheck> = LinkedList()
        }

    }