package com.example.bletester.ble

interface BleCallbackEvent {

    fun onHandleCheck()
    fun onVersionCheck(version:String)
}