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
        val reportCurrentFile = File(sharedData.bleTesterDirectory, "current.ini")
        if (reportCurrentFile.exists()) {
            if (reportCurrentFile.delete()) {
                Log.i("IniUtil", "Файл current.ini успешно удален")
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
        val file = File(sharedData.bleTesterDirectory, "summary.ini")
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

    @SuppressLint("NewApi", "MissingPermission")
    fun updateCurrentFileDynamically(approvedDevice: BluetoothDevice) {
        val file = File(sharedData.bleTesterDirectory, "current.ini")
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

            // Записываем диапазон адресов
            ini.put(reportSectionName, "RangeStart", sharedData.addressRange.value?.first)
            ini.put(reportSectionName, "RangeStop", sharedData.addressRange.value?.second)

            // Получаем текущие списки MAC-адресов и имен устройств
            val currentMacs = ini.get(reportSectionName, "TestedDevicesMacs", String::class.java) ?: ""
            val currentNames = ini.get(reportSectionName, "TestedDevicesNames", String::class.java) ?: ""

            // Добавляем новое устройство к спискам, разделяя запятыми
            val updatedMacs = if (currentMacs.isEmpty()) {
                approvedDevice.address
            } else {
                "$currentMacs,${approvedDevice.address}"
            }

            val updatedNames = if (currentNames.isEmpty()) {
                approvedDevice.name ?: ""
            } else {
                "$currentNames,${approvedDevice.name ?: ""}"
            }

            // Записываем обновленные списки в INI файл
            ini.put(reportSectionName, "TestedDevicesMacs", updatedMacs)
            ini.put(reportSectionName, "TestedDevicesNames", updatedNames)

            ini.store()

            Log.i("IniUtil", "Обновлен файл поддержки, добавлено устройство: MAC - ${approvedDevice.address}, Имя - ${approvedDevice.name ?: "Неизвестно"}")
        } catch (e: Exception) {
            Log.e("IniUtil", "Ошибка при обновлении файла поддержки: ${e.message}", e)
        }
    }

    fun loadApprovedDevicesFromCurrentReport(): Triple<List<String>, List<String>, Pair<String, String>?> {
        val file = File(sharedData.bleTesterDirectory, "current.ini")
        val approvedMacs = mutableListOf<String>()
        val approvedNames = mutableListOf<String>()
        var range: Pair<String, String>? = null

        try {
            if (!file.exists()) {
                Log.w("IniUtil", "Файл current.ini не существует")
                return Triple(approvedMacs, approvedNames, range)
            }

            val ini = Wini(file)
            val reportSection = ini["Report"]
            if (reportSection != null) {
                val rangeStart = reportSection["RangeStart"]
                val rangeStop = reportSection["RangeStop"]
                if (rangeStart != null && rangeStop != null) {
                    range = Pair(rangeStart, rangeStop)
                }

                val testedDevicesMacs = reportSection["TestedDevicesMacs"]
                val testedDevicesNames = reportSection["TestedDevicesNames"]

                if (testedDevicesMacs != null) {
                    approvedMacs.addAll(testedDevicesMacs.split(","))
                }
                if (testedDevicesNames != null) {
                    approvedNames.addAll(testedDevicesNames.split(","))
                }
            }
        } catch (e: Exception) {
            Log.e("IniUtil", "Ошибка при чтении файла current.ini: ${e.message}", e)
        }

        return Triple(approvedMacs, approvedNames, range)
    }
    fun loadTaskFromIni(fileName: String) {
        try {
            val file = File(sharedData.bleTesterDirectory, fileName)
            if (!file.exists()) {
                Log.e("ReportViewModel", "file not found: $fileName")
                return
            }
            val ini = Wini(file)
            ini.forEach { sectionName, section ->
                if (sectionName.startsWith("Task")) {
                    type = section["Type"]
                    val rangeStart = section["RangeStart"]
                    val rangeStop = section["RangeStop"]

                    if (rangeStart != null && rangeStop != null && type != null) {
                        _addressRange.value = Pair(rangeStart, rangeStop)
                        typeOfDevice.value = type
                        Log.i("ReportViewModel", "addressRange updated: $rangeStart - $rangeStop")
                    } else {
                        Log.w("ReportViewModel", "Missing required values in Task section")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error loading task from INI: ${e.message}", e)
        }
    }
}