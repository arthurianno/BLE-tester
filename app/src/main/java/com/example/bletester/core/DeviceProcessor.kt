package com.example.bletester.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import com.example.bletester.items.ReportItem
import com.example.bletester.ui.theme.report.ReportViewModel
import com.example.bletester.utils.SharedData
import javax.inject.Inject

class DeviceProcessor @Inject constructor(
    private val reportViewModel: ReportViewModel,
    private val sharedData: SharedData
) {
    private var errorMessage: String? = null

    @SuppressLint("MissingPermission")
    fun createReportItems(deviceList: List<BluetoothDevice>, status: String, bannedDevices: List<BluetoothDevice>): List<ReportItem> {
        return deviceList.map { device ->
            ReportItem(
                device = device.name ?: "Unknown Device",
                deviceAddress = device.address,
                status = status,
                interpretation = if (device in bannedDevices) {
                    "Устройство не проверено по причине ошибки в отправке команд!"
                } else {
                    errorMessage.toString()
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun updateReportViewModel(command: String, unCheckedDevices: List<BluetoothDevice>, checkedDevices: List<BluetoothDevice>, bannedDevices: List<BluetoothDevice>, isScanning: Boolean) {
        if (!isScanning) {
            val discoveredAddresses = (unCheckedDevices + checkedDevices + bannedDevices).map { it.name.takeLast(4) }.toSet()
            Log.d("DeviceProcessor","discoveredAddresses: $discoveredAddresses")
            val addressArray = createAddressArray(sharedData.addressRange.value).toMutableList()
            Log.d("DeviceProcessor","addressArray: $addressArray")
            val deviceTypeToLetter = mapOf(
                "Online" to "SatelliteOnline",
                "Voice" to "VoiceOnline",
                "AnotherDevice" to "F"
            )
            addressArray.removeAll { it.takeLast(4) in discoveredAddresses }
            val notApprovedItemsDevice = addressArray.map { address ->
                ReportItem(
                    device = deviceTypeToLetter[sharedData.typeOfDevice.value] + address.takeLast(4),
                    deviceAddress = address,
                    status = "Не найдено",
                    interpretation = errorMessage?.takeIf { it.isNotBlank() } ?: "Не было в эфире!"
                )
            }

            val approvedReportItems = createReportItems(checkedDevices.distinct(), "Checked", bannedDevices)
            val bannedReportItems = createReportItems(bannedDevices.distinct(), "Banned", bannedDevices)
            Log.d("DeviceProcessor", "Unchecked devices: ${notApprovedItemsDevice.size}, Checked devices: ${approvedReportItems.size}, Banned devices: ${bannedReportItems.size}")

            sharedData.notApprovedItems = notApprovedItemsDevice
            sharedData.approvedItems = approvedReportItems + bannedReportItems

            if (command.contains("Manual")) {
                Log.d("DeviceProcessor", "Report updated MANUAL with ${notApprovedItemsDevice.size} unchecked, ${approvedReportItems.size} checked, and ${bannedReportItems.size} banned devices")
                reportViewModel.updateReportItemsManual(notApprovedItemsDevice, approvedReportItems + bannedReportItems)
            } else if (command.contains("Auto")) {
                Log.d("DeviceProcessor", "Report updated AUTO with ${notApprovedItemsDevice.size} unchecked, ${approvedReportItems.size} checked, and ${bannedReportItems.size} banned devices")
                reportViewModel.updateReportItems(notApprovedItemsDevice, approvedReportItems + bannedReportItems)
            }
        } else {
            Log.w("DeviceProcessor", "Scanning is still in progress, report not updated")
        }
    }

    private fun createAddressArray(range: Pair<String, String>?): List<String> {
        if (range == null) return emptyList()

        val (start, end) = range

        // Используем BigInteger вместо Long для больших чисел
        val startNumber = java.math.BigInteger(start)
        val endNumber = java.math.BigInteger(end)

        val result = mutableListOf<String>()
        var current = startNumber
        while (current <= endNumber) {
            result.add(current.toString().padStart(start.length, '0'))
            current = current.add(java.math.BigInteger.ONE)
        }

        Log.w("DeviceProcessor", "Created address array: $result")
        return result
    }



    fun setErrorMessage(message: String?) {
        errorMessage = message
    }
}