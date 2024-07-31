package com.example.bletester.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import com.example.bletester.items.ReportItem
import org.ini4j.Wini
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class IniUtil @Inject constructor( private val sharedData: SharedData) {

    private val _addressRange = sharedData.addressRange
    private val typeOfDevice = sharedData.typeOfDevice
    private var type: String? = null
    var isFirstUpdate = true
    private var lastKnownCount = 0
    private val typeOfError = mapOf(
        "Error 19" to "The device turned off intentionally",
        "Error 8" to "The connection timeout expired and the device disconnected itself",
        "Error 133" to "A low-level connection error that led to disconnection",
        "Error 1" to "Error during connection or operation",
        "Error 1" to "The connection timeout expired",
        "Не было в эфире!" to "The device is not on the air"
    )

    @SuppressLint("NewApi")
    fun saveIniFile(
        fileName: String,
        itemsNotApproved: List<ReportItem>,
        itemsApproved: List<ReportItem>
    ) {
        val reportCurrentFile = File(sharedData.bleTesterDirectory, "report_current.ini")
        if (reportCurrentFile.exists()) {
            if (reportCurrentFile.delete()) {
                Log.i("IniUtil", "Файл report_current.ini успешно удален")
            } else {
                Log.e("IniUtil", "Не удалось удалить файл report_current.ini")
            }
        }
        val file = File(fileName)
        val ini: Wini

        if (file.exists()) {
            ini = Wini(file)
        } else {
            ini = Wini()
            ini.file = file
        }

        val timestamp =
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val reportSectionName = "Report $timestamp"

        ini.put("ERRORS", "Error 19", "The device turned off intentionally")
        ini.put(
            "ERRORS",
            "Error 8",
            "The connection timeout expired and the device disconnected itself"
        )
        ini.put("ERRORS", "Error 133", "A low-level connection error that led to disconnection")
        ini.put("ERRORS", "Error 1", "Error during connection or operation")
        ini.put("ERRORS", "Error 5", "The connection timeout expired")

        ini.put(reportSectionName, "RangeStart", _addressRange.value?.first)
        ini.put(reportSectionName, "RangeStop", _addressRange.value?.second)

        ini.put(reportSectionName, "Устройств не прошедших проверку", (itemsNotApproved.size))

        itemsNotApproved.forEach { item ->
            val typeOfDeviceLet = when (typeOfDevice.value) {
                "Online" -> "D"
                "Voice" -> "E"
                else -> "Unknown"
            }
            val errorDescription = typeOfError[item.interpretation] ?: "Error 1"
            ini.put(
                reportSectionName,
                "${typeOfDeviceLet}${item.deviceAddress}",
                "Failed: $errorDescription"
            )

        }
        Log.d("IniUtil", "NotApprovedDevices: ${itemsNotApproved.size}")
        Log.d("IniUtil", "ApprovedDevices: ${itemsApproved.size}")

        ini.store()
    }


    @SuppressLint("NewApi")
    fun updateSummaryFileDynamically(approvedDevice: Int) {
        val file = File(sharedData.bleTesterDirectory, "report_summary.ini")
        try {
            if (!file.exists() || file.length() == 0L) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            val ini = Wini(file)
            val reportSectionName = "Report"

            if (isFirstUpdate) {
                ini.clear()
                isFirstUpdate = false
            }
            ini.put(reportSectionName, "RangeStart", sharedData.addressRange.value?.first)
            ini.put(reportSectionName, "RangeStop", sharedData.addressRange.value?.second)


            ini.put(reportSectionName, "TestedDevices", approvedDevice)
            ini.store()
            Log.i("IniUtil", "Обновлен файл сводки для устройства: $approvedDevice. Общее количество: $approvedDevice")
        } catch (e: Exception) {
            Log.e("IniUtil", "Ошибка при обновлении файла сводки: ${e.message}", e)

        }
    }

    @SuppressLint("NewApi")
    fun updateCurrentFileDynamically(approvedDevice: BluetoothDevice) {
        val file = File(sharedData.bleTesterDirectory, "report_current.ini")
        try {
            if (!file.exists() || file.length() == 0L) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            val ini = Wini(file)
            val reportSectionName = "Report"

            if (isFirstUpdate) {
                ini.clear()
                isFirstUpdate = false
            }

            ini.put(reportSectionName, "RangeStart", sharedData.addressRange.value?.first)
            ini.put(reportSectionName, "RangeStop", sharedData.addressRange.value?.second)

            // Получаем текущий список устройств
            val currentDevices = ini.get(reportSectionName, "TestedDevices", String::class.java) ?: ""

            // Добавляем новое устройство к списку, разделяя запятыми
            val updatedDevices = if (currentDevices.isEmpty()) {
                approvedDevice.address
            } else {
                "$currentDevices,${approvedDevice.address}"
            }

            ini.put(reportSectionName, "TestedDevices", updatedDevices)
            ini.store()

            Log.i("IniUtil", "Обновлен файл поддержки, добавлено устройство: ${approvedDevice.address}")
        } catch (e: Exception) {
            Log.e("IniUtil", "Ошибка при обновлении файла поддержки: ${e.message}", e)
        }
    }

    fun loadApprovedDevicesFromCurrentReport(): Pair<List<String>, Pair<String, String>?> {
        val file = File(sharedData.bleTesterDirectory, "report_current.ini")
        val approvedDevices = mutableListOf<String>()
        var range: Pair<String, String>? = null

        try {
            if (!file.exists()) {
                Log.w("IniUtil", "File report_current.ini не существует")
                return Pair(approvedDevices, range)
            }

            val ini = Wini(file)
            val reportSection = ini["Report"]

            if (reportSection != null) {
                val rangeStart = reportSection["RangeStart"]
                val rangeStop = reportSection["RangeStop"]
                if (rangeStart != null && rangeStop != null) {
                    range = Pair(rangeStart, rangeStop)
                }

                val testedDevices = reportSection["TestedDevices"]
                if (testedDevices != null) {
                    approvedDevices.addAll(testedDevices.split(","))
                }
            }
        } catch (e: Exception) {
            Log.e("IniUtil", "Ошибка при чтении файла report_current.ini: ${e.message}", e)
        }

        return Pair(approvedDevices, range)
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
                Log.i(
                    "ReportViewModel",
                    "The new RangeStart and rangeStop values do not match the current addressRange value"
                )
                _addressRange.value = Pair(rangeStart, rangeStop)
                typeOfDevice.value = type
                Log.i(
                    "ReportViewModel",
                    "addressRange updated: $rangeStart - $rangeStop : addressRange - ${_addressRange.value?.first} - ${_addressRange.value?.second}"
                )
            }
        }
    }
}