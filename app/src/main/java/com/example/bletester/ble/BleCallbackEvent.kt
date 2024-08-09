package com.example.bletester.ble

import android.bluetooth.BluetoothDevice

interface BleCallbackEvent {
    suspend fun onPinCheck(device: BluetoothDevice, pin: String)
    suspend fun onVersionCheck(device: BluetoothDevice, version: String)
}