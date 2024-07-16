package com.example.bletester.utils

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.bletester.items.ReportItem
import org.ini4j.Wini
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class IniUtil @Inject constructor( private val sharedData: SharedData){

    private val _addressRange = sharedData.addressRange
    private val typeOfDevice = sharedData.typeOfDevice
    private var type: String? = null
    private val typeOfError = mapOf(
        "Error 19" to "The device turned off intentionally",
        "Error 8" to "The connection timeout expired and the device disconnected itself",
        "Error 133" to "A low-level connection error that led to disconnection",
        "Error 1" to "Error during connection or operation",
        "Error 1" to "The connection timeout expired",
        "Не было в эфире!" to "The device is not on the air"
    )
    @SuppressLint("NewApi")
    fun saveIniFile(fileName: String, itemsNotApproved: List<ReportItem>,itemsApproved: List<ReportItem>) {
        val file = File(fileName)
        val ini: Wini

        if (file.exists()) {
            ini = Wini(file)
        } else {
            ini = Wini()
            ini.file = file
        }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val reportSectionName = "Отчет $timestamp"

        // Get the approved items from SharedData
        val approvedItems = itemsApproved

        // Create a set of approved device addresses for quick lookup
        ini.put("ERRORS", "Error 19", "The device turned off intentionally")
        ini.put("ERRORS", "Error 8", "The connection timeout expired and the device disconnected itself")
        ini.put("ERRORS", "Error 133", "A low-level connection error that led to disconnection")
        ini.put("ERRORS", "Error 1", "Error during connection or operation")
        ini.put("ERRORS", "Error 5", "The connection timeout expired")

        ini.put(reportSectionName, "RangeStart", _addressRange.value?.first)
        ini.put(reportSectionName, "RangeStop", _addressRange.value?.second)

        // Count only the unchecked devices
        ini.put(reportSectionName, "Устройств не прошедших проверку", (itemsNotApproved.size))

        // Process unchecked devices
        itemsNotApproved.forEach { item ->
            val typeOfDeviceLet = when (typeOfDevice.value) {
                "Online" -> "D"
                "Voice" -> "E"
                else -> "Unknown"
            }
            val errorDescription = typeOfError[item.interpretation] ?: "Error 1"
            ini.put(reportSectionName, "${typeOfDeviceLet}${item.deviceAddress}", "Failed: $errorDescription")

        }



        // Log the results
        Log.d("IniUtil", "NotApproved devices: ${itemsNotApproved.size}")
        Log.d("IniUtil", "Approved devices: ${approvedItems.size}")

        ini.store()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun saveIniFileSummary(fileName: String, dataItemsApproved: List<ReportItem>) {
        val file = File(fileName)
        val ini: Wini
        Log.i("INI","Approved items: $dataItemsApproved")

        if (file.exists()) {
            ini = Wini(file)
        } else {
            ini = Wini()
            ini.file = file
        }
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val reportSectionName = "Отчет $timestamp"

        ini.put(reportSectionName, "RangeStart", _addressRange.value?.first)
        ini.put(reportSectionName, "RangeStop", _addressRange.value?.second)
        ini.put(reportSectionName, "Устройств прошедших проверку", dataItemsApproved.count())

        ini.store()
    }

    fun loadTaskFromIni(fileName: String) {
        val file = File(sharedData.bleTesterDirectory, fileName)
        if (!file.exists()) {
            Log.e("ReportViewModel", "file not found: $fileName")
            return
        }
        val ini = Wini(file)
        ini.forEach { sectionName, section ->
            var rangeStart: String? = null
            var rangeStop: String? = null
            if (sectionName.startsWith("Task")) {
                type = section["Type"]
                rangeStart = section["RangeStart"]
                rangeStop = section["RangeStop"]
                Log.i("ReportItem", "Type: $type")
                Log.i("ReportItem", "RangeStart: $rangeStart")
                Log.i("ReportItem", "RangeStop: $rangeStop")
            }
            if (rangeStart != null && rangeStop != null && type != null) {
                Log.i("ReportViewModel", "The new RangeStart and rangeStop values do not match the current addressRange value")
                _addressRange.value = Pair(rangeStart, rangeStop)
                typeOfDevice.value = type
                Log.i("ReportViewModel", "addressRange updated: $rangeStart - $rangeStop : addressRange - ${_addressRange.value?.first} - ${_addressRange.value?.second}")
            }
        }
    }

    /*private fun createAddressArray(range: Pair<String, String>?): List<String> {
        if (range == null) return emptyList()

        val (start, end) = range

        val startNumber = start.toLong()
        val endNumber = end.toLong()

        return (startNumber..endNumber).map { it.toString().padStart(start.length, '0') }
    }*/
}