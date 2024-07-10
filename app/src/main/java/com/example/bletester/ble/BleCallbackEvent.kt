package com.example.bletester.ble

interface BleCallbackEvent {

    fun onPinCheck(pin:String)
    fun onVersionCheck(version:String)
}