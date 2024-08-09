package com.example.bletester.ble

import android.content.Context

class BleManagerPool(context: Context) {
    private val managers = List(3) { BleControlManager(context) }
    private val availableManagers = managers.toMutableList()

    fun getManager(): BleControlManager? {
        return availableManagers.removeFirstOrNull()
    }

    fun releaseManager(manager: BleControlManager) {
        manager.disconnect().enqueue()
        manager.close()
        availableManagers.add(manager)
    }
}