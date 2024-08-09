package com.example.bletester.ble


import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.example.bletester.core.EntireCheck
import com.example.bletester.ui.theme.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

class BleControlManager @Inject constructor(context: Context) : BleManager(context) {

    private var serialNumber: String = ""
    private var bleCallbackEvent: BleCallbackEvent? = null
    private var connectionTime: Long = 0
    private var controlRequest: BluetoothGattCharacteristic? = null
    private var controlResponse: BluetoothGattCharacteristic? = null
    private var connectedDevice: BluetoothDevice? = null
    private val entireCheckQueue = ConcurrentLinkedQueue<Pair<BluetoothDevice, EntireCheck>>()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pinAttempts = 0


    fun setBleCallbackEvent(bleCallbackEvent: BleCallbackEvent?) {
        this.bleCallbackEvent = bleCallbackEvent
    }

    override fun log(priority: Int, message: String) {
        Log.println(priority, "BleControlManager", message)
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        gatt.getService(UART_SERVICE_UUID)?.let { service ->
            controlRequest = service.getCharacteristic(UART_RX_CHARACTERISTIC_UUID)
            controlResponse = service.getCharacteristic(UART_TX_CHARACTERISTIC_UUID)
        }
        connectedDevice = gatt.device
        Log.i("BleControlManager", "Required service supported: ${gatt.services}")
        return controlRequest != null && controlResponse != null
    }

    override fun initialize() {
        super.initialize()
        connectionTime = System.currentTimeMillis()
        Log.i("BleControlManager", "BLE connection initialized")
        setNotificationCallback(controlResponse).with { device: BluetoothDevice, data: Data ->
            coroutineScope.launch {
                handleResponseData(device, data.value)
            }
            Log.i("BleControlManager", "BLE callback set!")
        }
        updateConnectionParameters()
        enableNotifications(controlResponse).enqueue()
    }

    override fun onServicesInvalidated() {
        controlRequest = null
        controlResponse = null
        connectedDevice = null
        close()
    }
    private fun updateConnectionParameters() {

        requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
    }

    fun getConnectedDevice(): BluetoothDevice? = connectedDevice

    fun sendCommand(device: BluetoothDevice, command: String, entireCheck: EntireCheck) {
        if (isConnected && controlRequest != null) {
            entireCheckQueue.add(Pair(device, entireCheck))
            writeCharacteristic(controlRequest, command.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .done {
                    Log.d("BleControlManager", "Command sent: $command")
                }
                .fail { _, status ->
                    Log.e("BleControlManager", "Failed to send command: $status")
                }
                .enqueue()
        } else {
            Log.e("BleControlManager", "Device is not connected")
        }
    }

       fun sendPinCommand(device: BluetoothDevice, pinCode: String, entireCheck: EntireCheck) {
        if (isConnected && controlRequest != null && pinAttempts != 2) {
            entireCheckQueue.add(Pair(device, entireCheck))
            val formattedPinCode = "pin.$pinCode"
            writeCharacteristic(controlRequest, formattedPinCode.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .done {
                    Log.i("BleControlManager", "PIN command sent: $formattedPinCode")
                    pinAttempts = 0
                }
                .fail { _, _ ->
                    Log.e("BleControlManager", "PIN command failed to send")
                    coroutineScope.launch {
                        bleCallbackEvent?.onPinCheck(device, "GATT PIN ATTR ERROR")
                    }
                    pinAttempts++
                }
                .enqueue()
        } else {
            Log.e("BleControlManager", "Device is not connected or controlRequest is null or device is not working")
            coroutineScope.launch {
                bleCallbackEvent?.onPinCheck(device, "pin.error")
            }
        }
    }


    private  fun handleResponseData(device: BluetoothDevice, data: ByteArray?) {
        Log.d("BleControlManager", "Handling response data from device: ${device.address}, data: ${data?.contentToString()}")
        val entireCheck = entireCheckQueue.poll()?.second ?: run {
            Logger.d("BleControlManager", "Entire check is null for device: ${device.address}")
            return
        }
        Log.d("BleControlManager", "Handling response for check: $entireCheck on device: ${device.address}")
        when (entireCheck) {
            EntireCheck.HW_VER -> handleHwVer(data)
            EntireCheck.default_command -> handleDefaultCommand(data)
            EntireCheck.PIN_C0DE -> handlePinCodeResult(data)
        }
    }

    private  fun handleHwVer(data: ByteArray?) {
        Log.d("BleControlManager", "Handling HW version: ${data?.contentToString()}")
        if (data == null || data.size < 4) {
            Logger.e("BleControlManager", "Received invalid HW version data")
            return
        }
        val endIndex = minOf(20, data.size)
        val hwVer = String(data.copyOfRange(4, endIndex)).trim().replace("[\\x00-\\x1F]".toRegex(), "")
        Log.e("BleControlManager","version is :$hwVer")
        serialNumber = hwVer
        Log.i("BleControlManager","callback: $bleCallbackEvent")
        coroutineScope.launch {
            connectedDevice?.let { bleCallbackEvent?.onVersionCheck(it, serialNumber) }
        }

    }

    @SuppressLint("SuspiciousIndentation")
    private fun handleDefaultCommand(data: ByteArray?) {
        val defaultResponse = data?.toString(Charsets.UTF_8) ?: return
        log(Log.DEBUG, "command $defaultResponse")
        if (defaultResponse.contains("ble.ok")) {
            log(Log.INFO, "DEVICES STARTING TO OFF")
            Logger.i("BleControlManager", "DEVICES STARTING TO OFF")
        }
    }

    private  fun handlePinCodeResult(data: ByteArray?) {
        val pinResponse = data?.toString(Charsets.UTF_8) ?: return
        if (pinResponse.contains("pin.ok")) {
            Logger.d("BleControlManager", "Pin code is correct")
            Log.i("BleControlManager", "callback: $bleCallbackEvent")
            coroutineScope.launch {
                connectedDevice?.let { bleCallbackEvent?.onPinCheck(it, "pin.ok") }
            }
        } else if (pinResponse.contains("pin.error")) {
            coroutineScope.launch {
                connectedDevice?.let { bleCallbackEvent?.onPinCheck(it, "pin.error") }
            }
        }
        }

    fun cleanup() {
        coroutineScope.cancel()
    }

    companion object {
        private val UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UART_RX_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UART_TX_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    }
}
