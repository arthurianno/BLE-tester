package com.example.bletester.viewModels

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.bletester.ReportItem
import com.example.bletester.ble.FileModifyEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ini4j.Wini
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@SuppressLint("NewApi")
@HiltViewModel
class ReportViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
    val _addressRange = MutableStateFlow<Pair<String, String>?>(null)
    val typeOfDevice = MutableStateFlow<String?>(null)
    private var approvedItems: List<ReportItem> = emptyList()
    val toastMessage = MutableStateFlow<String?>(null)
    val reportItems = MutableStateFlow<List<ReportItem>>(emptyList())
    private var fileObserverJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val counter = MutableStateFlow(0)
    private var checkedFiles = mutableMapOf<String, Long>()
    private val dcimDirectory: File? = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private val bleTesterDirectory = File(dcimDirectory, "BLE Tester Directory")
    private val reportsDirectory = File(bleTesterDirectory, "Reports")
    private val tasksDirectory = File(bleTesterDirectory, "Tasks")
    private var callbackFileModifyEvent : FileModifyEvent? = null
    private var type: String? = null
    private val typeOfError = mapOf(
        "Error 19" to "The device turned off intentionally",
        "Error 8" to "The connection timeout expired and the device disconnected itself",
        "Error 133" to "A low-level connection error that led to disconnection",
        "Error 1" to "Error during connection or operation",
        "Error 1" to "The connection timeout expired"
    )



    init {
        createReportsDirectory()
        startObservingTasksDirectory()
        checkDirectoryPermissions()
        Log.d("ReportViewModel", "ViewModel initialized, file observation should be active")
    }
    fun registerCallback(callbackFileModifyEvent: FileModifyEvent){
        this.callbackFileModifyEvent = callbackFileModifyEvent
    }
    private fun checkDirectoryPermissions() {
        if (!tasksDirectory.canRead() || !tasksDirectory.canWrite()) {
            Log.e("ReportViewModel", "Not enough permissions to work with the directory: ${tasksDirectory.absolutePath}")
        }
    }

    private fun createReportsDirectory() {
        listOf(bleTesterDirectory, reportsDirectory, tasksDirectory).forEach { directory ->
            if (!directory.exists()) {
                directory.mkdirs()
                Log.i("ReportViewModel", "Folder ${directory.name} created: ${directory.absolutePath}")
            } else {
                Log.i("ReportViewModel", "Folder ${directory.name} is exist : ${directory.absolutePath}")
            }
        }
    }
    private fun createAddressArray(range: Pair<String, String>?): List<String> {
        if (range == null) return emptyList()

        val (start, end) = range

        val startNumber = start.toLong()
        val endNumber = end.toLong()

        return (startNumber..endNumber).map { it.toString().padStart(start.length, '0') }
    }
    fun updateReportItems(itemsUnchecked: List<ReportItem>, itemsApproved: List<ReportItem>) {
        if (reportItems.value != itemsUnchecked || approvedItems != itemsApproved) {
            reportItems.value = itemsUnchecked
            approvedItems = itemsApproved

            Log.e("TestReportView", "$reportItems")
            Log.e("TestReportView", "$approvedItems")

            saveReport(reportItems.value)
        } else {

            saveReport(reportItems.value)
            Log.i("ReportViewModel", "The lists have not changed, but the report is still saved.")
        }
    }
    fun updateReportItemsManual(itemsUnchecked: List<ReportItem>, itemsApproved: List<ReportItem>) {
        if (reportItems.value != itemsUnchecked || approvedItems != itemsApproved) {
            reportItems.value = itemsUnchecked
            approvedItems = itemsApproved
            Log.e("TestReportView", "$reportItems")
            Log.e("TestReportView", "$approvedItems")
        } else {
            Log.i("ReportViewModel", "Update devices who failed verification!")
        }
    }


    private fun startObservingTasksDirectory() {
        fileObserverJob?.cancel()
        fileObserverJob = coroutineScope.launch {
            while (isActive) {
                checkForFileChanges()
                delay(1000)
            }
        }
    }

    private fun checkForFileChanges() {
        val currentFiles = tasksDirectory.listFiles()?.associate { it.name to it.lastModified() } ?: emptyMap()


        currentFiles.keys.minus(checkedFiles.keys).forEach { newFileName ->
            handleNewFile(newFileName)
        }


        checkedFiles.keys.minus(currentFiles.keys).forEach { deletedFileName ->
            handleFileDeleted(deletedFileName)
        }


        currentFiles.forEach { (fileName, lastModified) ->
            if (checkedFiles.containsKey(fileName) && lastModified > checkedFiles[fileName]!!) {
                handleFileModify(fileName)
            }
        }

        checkedFiles = currentFiles.toMutableMap()
    }

    private fun handleNewFile(fileName: String) {
        try {
            val file = File(tasksDirectory, fileName)
            if (file.isFile) {
                notifyNewFile(fileName)
                counter.value++
                Log.e("DeletedCheck", "$callbackFileModifyEvent")
                callbackFileModifyEvent?.onEvent("Auto")
            }
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error with processing new file: ${e.message}")
        }
    }

    private fun handleFileDeleted(fileName: String) {
        try {
            Log.i("ReportViewModel", "File deleted: $fileName")
            counter.value--
            Log.e("DeletedCheck", "$callbackFileModifyEvent")
            callbackFileModifyEvent?.onEvent("Deleted")
            toastMessage.value = "Произошло удаление файла, остановка задания и отправка отчета!"
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error with processing file: ${e.message}")
        }
    }

    private fun handleFileModify(fileName: String) {
        toastMessage.value = "Произошло изменение в файле, остановка задания и отправка отчета!"
        callbackFileModifyEvent?.onEvent("Modify")
    }

    private fun notifyNewFile(fileName: String) {
        try {
            toastMessage.value = "Найден новый отчет: $fileName"
            Log.e("ReportViewModel", "New report! : $fileName")
            loadTaskFromIni(fileName)
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error form update file: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        fileObserverJob?.cancel()
        _addressRange.value = null
        Log.i("ReportViewModel", "ViewModel was cleared and task stops")
    }

    fun saveReport(reportItems: List<ReportItem>) {
        try {
            createReportsDirectory()

            val dateFormat = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val detailedReportFile = File(reportsDirectory, "$currentDate.ini")
            val summaryReportFile = File(reportsDirectory, "${"report"}_summary.ini")

            if (!detailedReportFile.exists()) {
                detailedReportFile.createNewFile()
            }
            if (!summaryReportFile.exists()) {
                summaryReportFile.createNewFile()
            }


            if (reportItems.isNotEmpty()) {
                saveIniFile(detailedReportFile.absolutePath,reportItems)
                saveIniFileSummary(summaryReportFile.absolutePath,reportItems)
                Log.i("ReportViewModel", "Reports have been successfully saved locally: ${detailedReportFile.absolutePath}, ${summaryReportFile.absolutePath}")
                toastMessage.value = "Отчеты успешно сохранены локально"
            } else {
                saveIniFile(detailedReportFile.absolutePath,reportItems)
                saveIniFileSummary(summaryReportFile.absolutePath,reportItems)
                Log.e("ReportViewModel", "The report is empty, but the data is saved anyway")
                toastMessage.value = "Отчет сохранен!"

            }
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error saving the report: ${e.message}")
            toastMessage.value = "Ошибка при сохранении отчета: ${e.message}"
        }
    }



    private fun saveIniFile(fileName: String, dataItemsUnchecked: List<ReportItem>) {
        val file = File(fileName)
        val ini: Wini
        val addressArray = createAddressArray(_addressRange.value).toMutableList()


        if (file.exists()) {
            ini = Wini(file)
        } else {
            ini = Wini()
            ini.file = file
        }
        val failedAndNotApproved = approvedItems.map { it.device.takeLast(4) }
        val remainingAddressArray = addressArray.filterNot { failedAndNotApproved.contains(it.takeLast(4)) }.toMutableList()


        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val reportSectionName = "Отчет $timestamp"
        val rangeStart: String? = _addressRange.value?.first?.takeLast(4)
        val rangeStop: String? = _addressRange.value?.second?.takeLast(4)


        Log.e("CheckAddRange","$addressArray")

        ini.put("ERRORS","Error 19","The device turned off intentionally")
        ini.put("ERRORS","Error 8","The connection timeout expired and the device disconnected itself")
        ini.put("ERRORS","Error 133","A low-level connection error that led to disconnection")
        ini.put("ERRORS","Error 1","Error during connection or operation")
        ini.put("ERRORS","Error 5","The connection timeout expired")

        ini.put(reportSectionName, "RangeStart", _addressRange.value?.first)
        ini.put(reportSectionName, "RangeStop", _addressRange.value?.second)
        ini.put(reportSectionName, "Устройств не прошедших проверку", remainingAddressArray.count())

        dataItemsUnchecked.forEach { item ->
            val typeOfDeviceLet = when (typeOfDevice.value) {
                "Online" -> "D"
                "Voice" -> "E"
                else -> "Unknown"
            }
            val errorDescription = typeOfError[item.interpretation] ?: "Error 1"
            when (val itemAddress = item.device.takeLast(4)) {
                rangeStart -> ini.put(reportSectionName, "${typeOfDeviceLet}${_addressRange.value?.first?.take(6)}${itemAddress}", "Devices  on the air : $errorDescription")
                rangeStop -> ini.put(reportSectionName, "${typeOfDeviceLet}${_addressRange.value?.second?.take(6)}${itemAddress}", "Devices  on the air : $errorDescription")
                else -> ini.put(reportSectionName, "Undefined", "Не опознано")
            }

            remainingAddressArray.removeIf { it.takeLast(4) == item.device.takeLast(4) }
            Log.e("CheckAddRange","$remainingAddressArray")
        }

        remainingAddressArray.forEachIndexed { _, address ->
            val typeOfDeviceLet = when (typeOfDevice.value) {
                "Online" -> "D"
                "Voice" -> "E"
                else -> "Unknown"
            }
            ini.put(reportSectionName,"${typeOfDeviceLet}${address.take(6)}${address.takeLast(4)}","Failed: Device are not on the air")
        }

        ini.store()
    }

    private fun saveIniFileSummary(fileName: String, dataItemsApproved: List<ReportItem>) {
        val file = File(fileName)
        val ini: Wini
        val addressArray = createAddressArray(_addressRange.value).toMutableList()

        if (file.exists()) {
            ini = Wini(file)
        } else {
            ini = Wini()
            ini.file = file
        }
        val failedAndNotApproved = approvedItems.map { it.device.takeLast(4) }
        val remainingAddressArray = addressArray.filter { failedAndNotApproved.contains(it.takeLast(4)) }.toMutableList()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val reportSectionName = "Отчет $timestamp"

        ini.put(reportSectionName, "RangeStart", _addressRange.value?.first)
        ini.put(reportSectionName, "RangeStop", _addressRange.value?.second)
        ini.put(reportSectionName, "Устройств прошедших проверку", remainingAddressArray.count())

        ini.store()
    }


    private fun loadTaskFromIni(fileName: String) {
        val file = File(tasksDirectory, fileName)
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
}
