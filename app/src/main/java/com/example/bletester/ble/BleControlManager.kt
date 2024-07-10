package com.example.bletester.ble


import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.example.bletester.core.EntireCheck
import com.example.bletester.ui.theme.log.Logger
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import java.util.LinkedList
import java.util.Queue
import java.util.UUID

class BleControlManager(context: Context) : BleManager(context) {

    private var serialNumber: String = ""
    private var bleCallbackEvent: BleCallbackEvent? = null
    private var connectionTime: Long = 0
    private var controlRequest: BluetoothGattCharacteristic? = null
    private var controlResponse: BluetoothGattCharacteristic? = null
    private var connectedDevice: BluetoothDevice? = null
    private val entireCheckQueue: Queue<EntireCheck> = LinkedList()

    fun setBleCallbackEvent(bleCallbackEvent: BleCallbackEvent) {
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
            connectedDevice = device
            handleResponseData(data.value)
            Log.i("BleControlManager", "BLE callback set!")
        }
        enableNotifications(controlResponse).enqueue()
    }


    override fun onServicesInvalidated() {
        controlRequest = null
        controlResponse = null
        connectedDevice = null
        disconnect().enqueue()
    }

    fun getConnectedDevice(): BluetoothDevice? = connectedDevice
    fun sendCommand(command: String, entireCheck: EntireCheck) {
        if (isConnected && controlRequest != null) {
            entireCheckQueue.add(entireCheck)
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

    fun sendPinCommand(pinCode: String, entireCheck: EntireCheck) {
        if (isConnected && controlRequest != null) {
            entireCheckQueue.add(entireCheck)
            val formattedPinCode = "pin.$pinCode"
            writeCharacteristic(controlRequest, formattedPinCode.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .done {
                    Log.i("BleControlManager", "PIN command sent")
                }
                .fail { _, _ ->
                    Log.e("BleControlManager", "PIN command incorrect")
                }
                .enqueue()
        }
    }


    private fun handleResponseData(data: ByteArray?) {
        Log.d("BleControlManager", "Handling response data: ${data?.contentToString()}")
        val entireCheck = entireCheckQueue.poll() ?: run {
            Logger.d("BleControlManager", "Entire is null")
            return
        }
        Log.d("BleControlManager", "Handling response for check: $entireCheck")
        when (entireCheck) {
            EntireCheck.HW_VER -> handleHwVer(data)
            EntireCheck.default_command -> handleDefaultCommand(data)
            EntireCheck.PIN_C0DE -> handlePinCodeResult(data)
        }
    }

    private fun handleHwVer(data: ByteArray?) {
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
        bleCallbackEvent?.onVersionCheck(serialNumber)
        log(Log.DEBUG, "VERSION: $hwVer")
        Logger.d("BleControlManager", "VERSION: $hwVer")
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

    private fun handlePinCodeResult(data: ByteArray?) {
        val pinResponse = data?.toString(Charsets.UTF_8) ?: return
        if (pinResponse.contains("pin.ok")) {
            Logger.d("BleControlManager", "Pin code is correct")
            Log.i("BleControlManager","callback: $bleCallbackEvent")
            bleCallbackEvent?.onPinCheck("pin.ok")
        }else if (pinResponse.contains("pin.error")){
            bleCallbackEvent?.onPinCheck("pin.error")
        }
    }

    companion object {
        private val UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UART_RX_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UART_TX_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    }
}