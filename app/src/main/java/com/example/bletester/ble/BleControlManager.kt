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
import no.nordicsemi.android.ble.Request
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
        Log.println(priority, TAG, message)
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

    @SuppressLint("MissingPermission")
    override fun initialize() {
        super.initialize()
        connectionTime = System.currentTimeMillis()
        Log.i(TAG, "BLE connection initialized")
        setNotificationCallback(controlResponse).with { device: BluetoothDevice, data: Data ->
            coroutineScope.launch {
                handleResponseData(device, data.value)
            }
        }
        Log.i(TAG, "BLE callback set!")
            enableNotifications(controlResponse).done {
                Log.i(TAG,"NOTIFICATION ENABLED for device $connectedDevice")
            }
                .fail { device, status ->
                    Log.i(TAG,"NOTIFICATION  NOT ENABLED FOR $device with status $status")
                }
                .enqueue()
    }

    override fun onServicesInvalidated() {
        removeNotificationCallback(controlResponse)
        controlRequest = null
        controlResponse = null
        connectedDevice = null
        //close()
    }
    fun sendCommand(device: BluetoothDevice, command: String, entireCheck: EntireCheck) {
        if (isConnected && controlRequest != null) {
            entireCheckQueue.add(Pair(device, entireCheck))
            writeCharacteristic(
                controlRequest,
                command.toByteArray(),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
                .done {
                    Log.d(TAG, "Command sent: $command")
                }
                .fail { _, status ->
                    Log.e(TAG, "Failed to send command: $status")
                }
                .enqueue()
        } else {
            Log.e(TAG, "Device is not connected")
        }
    }

     fun sendPinCommand(device: BluetoothDevice, pinCode: String, entireCheck: EntireCheck) {
        if (isConnected && controlRequest != null && pinAttempts != 2) {
            entireCheckQueue.add(Pair(device, entireCheck))
            val formattedPinCode = "pin.$pinCode"
            writeCharacteristic(
                controlRequest,
                formattedPinCode.toByteArray(),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
                .done {
                    Log.i(TAG, "PIN command sent: $formattedPinCode to device $device")
                    pinAttempts = 0
                }
                .fail { _, _ ->
                    Log.e(TAG, "PIN command failed to send to device $device")
                    bleCallbackEvent?.onPinCheck(device, "GATT PIN ATTR ERROR")
                    pinAttempts++
                }
                .enqueue()
        } else {
            Log.e(
                TAG,
                "Device is not connected or controlRequest is null or device is not working"
            )
            bleCallbackEvent?.onPinCheck(device, "pin.error")
        }
    }


    @SuppressLint("MissingPermission")
    private fun handleResponseData(device: BluetoothDevice, data: ByteArray?) {
        Log.d(
            TAG,
            "Handling response data from device: ${device.name}, data: ${data?.contentToString()}"
        )
        val entireCheck = entireCheckQueue.poll()?.second ?: run {
            return
        }
        when (entireCheck) {
            EntireCheck.HW_VER -> handleHwVer(data)
            EntireCheck.default_command -> handleDefaultCommand(data)
            EntireCheck.PIN_C0DE -> handlePinCodeResult(data)
        }
        }

    private fun handleHwVer(data: ByteArray?) {
        Log.d(TAG, "Handling HW version: ${data?.contentToString()}")
        if (data == null || data.size < 4) {
            return
        }
        val endIndex = minOf(20, data.size)
        val hwVer =
            String(data.copyOfRange(4, endIndex)).trim().replace("[\\x00-\\x1F]".toRegex(), "")
        Log.e(TAG, "version is :$hwVer")
        serialNumber = hwVer
        Log.i(TAG, "connectedDevice: $connectedDevice, bleCallbackEvent: $bleCallbackEvent")
        connectedDevice?.let {
            bleCallbackEvent?.onVersionCheck(it, serialNumber)
        }

    }

    @SuppressLint("SuspiciousIndentation")
    private fun handleDefaultCommand(data: ByteArray?) {
        val defaultResponse = data?.toString(Charsets.UTF_8) ?: return
        log(Log.DEBUG, "command $defaultResponse")
        if (defaultResponse.contains("ble.ok")) {
            log(Log.INFO, "DEVICES STARTING TO OFF")
        }
    }

    private fun handlePinCodeResult(data: ByteArray?) {
        val pinResponse = data?.toString(Charsets.UTF_8) ?: return
        if (pinResponse.contains("pin.ok")) {
            connectedDevice?.let { bleCallbackEvent?.onPinCheck(it, "pin.ok") }
        } else if (pinResponse.contains("pin.error")) {
            connectedDevice?.let { bleCallbackEvent?.onPinCheck(it, "pin.error") }
        }
    }


    companion object {
        private const val TAG = "BLE COMPANION"
        private val UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UART_RX_CHARACTERISTIC_UUID =
            UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UART_TX_CHARACTERISTIC_UUID =
            UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    }
}