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
            var deviceQueueProcessed: Queue<BluetoothDevice> = LinkedList()
            var foundDevices = mutableStateListOf<BluetoothDevice>()
            val unCheckedDevices = mutableStateListOf<BluetoothDevice>()
            val checkedDevices = mutableStateListOf<BluetoothDevice>()
            private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
            private val bluetoothLeScanner = adapter?.bluetoothLeScanner
            private val settings: ScanSettings
            private val filters: List<ScanFilter>
            private var scanning = false
            private val handler = android.os.Handler()
            private var connectionAttemps = 0
            private val scanPeriod: Long = 10000
            private var checkCount = 0L
            private var errorMessage: String? = null
            private var stopRequested = false
            var currentDevice: BluetoothDevice? = null
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
            val uncheckedReportItems = createReportItems(unCheckedDevices.distinct(), "Unchecked")
            val approvedReportItems = createReportItems(unCheckedDevices.distinct(), "Checked")
            reportViewModel.updateReportItems(uncheckedReportItems,approvedReportItems)
            Log.e("ScanViewModel","${unCheckedDevices.toList()}")
        }
        @SuppressLint("MissingPermission")
        private fun leScanCallback(letter: String, start: Long, end: Long) = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                    if (stopRequested) return
                    val device = result.device
                    val deviceName = device.name ?: return

                    val validLastFourDigits = listOf("0001", "0002", "0003")

                    if (deviceName.contains("Satellite")) {
                        val lastFourDigits = deviceName.takeLast(4)
                        if (lastFourDigits in validLastFourDigits) {
                                Log.e("ScanViewModel", "device $deviceName")
                            if(!deviceQueue.any{deviceQueue.contains(device)} && !foundDevices.any{foundDevices.contains(device)}){
                                deviceQueue.add(device)
                                foundDevices.add(device)
                                if (currentDevice == null) {
                                    currentDevice = device
                                    connectionToAnotherDevice()
                                }
                            }
                                bleControlManager.setBleCallbackEvent(object : BleCallbackEvent {

                                    override fun onHandleCheck() {
                                        checkCount++
                                        val totalCount = end - start + 1
                                        if (totalCount == checkCount) {
                                            checkCount = 0
                                        }
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

                                        if (versionNumber in start..end && versionPrefix.toString().contains(letter)) {
                                            Log.e("ScanViewModel", "Device serial number in range!")
                                            Log.e("ScanViewModel", "Device added to checkedDevices: $devicesChecked")
                                            checkedDevices.add(devicesChecked)
                                            currentDevice = null
                                        } else {
                                            Log.e("ScanViewModel", "Device serial number out of range!")
                                        }
                                        currentDevice = null
                                        connectionToAnotherDevice()
                                    }
                                })

                        }
                    }
            }
        }

        @SuppressLint("MissingPermission")
        fun connectionToAnotherDevice(){
            if (deviceQueue.isNotEmpty()){
                currentDevice  = deviceQueue.remove()
                currentDevice?.let {
                    bleControlManager.connect(it)
                        .done { device ->
                            Log.d("BleControlManager", "Connected to device ${device.name}")
                            bleControlManager.sendPinCommand("master", EntireCheck.PIN_C0DE)
                            foundDevices.remove(device)
                            if(unCheckedDevices.contains(device)){
                                unCheckedDevices.remove(device)
                            }
                            deviceQueueProcessed.add(device)
                        }
                        .fail{ device, status ->
                            Log.e("BleControlManager", "Failed to connect to device ${device.name}: $status")
                            errorMessage = "Failed to connect: $status"
                            foundDevices.remove(device)
                            if(!unCheckedDevices.contains(device)){
                                unCheckedDevices.add(device)
                            }
                            // Если подключение не удалось, также начните подключение следующего
                            currentDevice = null
                            connectionToAnotherDevice()
                        }
                        .enqueue()
                }
            }
        }
        fun clearData() {
            deviceQueue.clear()
            foundDevices.clear()

        }


        companion object{
            val entireCheckQueue: Queue<EntireCheck> = LinkedList()
        }


    }