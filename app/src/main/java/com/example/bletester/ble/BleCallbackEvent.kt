package com.example.bletester.ble

import android.bluetooth.BluetoothDevice

interface BleCallbackEvent {

    fun onPinCheck(device: BluetoothDevice, pin:String)
    fun onVersionCheck(device: BluetoothDevice,version:String)
}