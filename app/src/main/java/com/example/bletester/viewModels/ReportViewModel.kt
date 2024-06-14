package com.example.bletester.viewModels

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.bletester.ReportItem
import com.example.bletester.ble.FileModifyEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.ini4j.Wini
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@Suppress("DEPRECATION")
@SuppressLint("NewApi")
@HiltViewModel
class ReportViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
    val _addressRange = MutableStateFlow<Pair<String, String>?>(null)
    val typeOfDevice = MutableStateFlow<String?>(null)
    private var approvedItems: List<ReportItem> = emptyList()
    val toastMessage = MutableStateFlow<String?>(null)
    var reportItems: MutableState<List<ReportItem>> = mutableStateOf(emptyList())
    //val addressRange = mutableStateOf<Pair<String, String>?>(null)
    private val counter = MutableStateFlow(0)
    private val checkedFiles = mutableSetOf<String>()
    private val dcimDirectory: File? = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private val bleTesterDirectory = File(dcimDirectory, "BLE Tester Directory")
    private val reportsDirectory = File(bleTesterDirectory, "Reports")
    private val tasksDirectory = File(bleTesterDirectory, "Tasks")
    private lateinit var tasksDirectoryObserver: FileObserver
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
    }
    fun registerCallback(callbackFileModifyEvent: FileModifyEvent){
        this.callbackFileModifyEvent = callbackFileModifyEvent
    }

    private fun createReportsDirectory() {
        listOf(bleTesterDirectory, reportsDirectory, tasksDirectory).forEach { directory ->
            if (!directory.exists()) {
                directory.mkdirs()
                Log.i("YourTag", "Папка ${directory.name} создана: ${directory.absolutePath}")
            } else {
                Log.i("YourTag", "Папка ${directory.name} уже существует: ${directory.absolutePath}")
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
        // Проверяем, изменились ли списки отчетных элементов или нет
        if (reportItems.value != itemsUnchecked || approvedItems != itemsApproved) {
            reportItems.value = itemsUnchecked
            approvedItems = itemsApproved

            Log.e("TestReportView", "$reportItems")
            Log.e("TestReportView", "$approvedItems")

            saveReport(reportItems.value)  // Сохраняем отчет даже если списки пустые
        } else {
            // Если списки не изменились, все равно сохраняем отчет
            saveReport(reportItems.value)  // Сохраняем отчет даже если списки пустые
            Log.i("ReportViewModel", "Списки не изменились, но отчет все равно сохранен")
        }
    }
    fun updateReportItemsManual(itemsUnchecked: List<ReportItem>, itemsApproved: List<ReportItem>) {
        // Проверяем, изменились ли списки отчетных элементов или нет
        if (reportItems.value != itemsUnchecked || approvedItems != itemsApproved) {
            reportItems.value = itemsUnchecked
            approvedItems = itemsApproved

            Log.e("TestReportView", "$reportItems")
            Log.e("TestReportView", "$approvedItems")

            //saveReport(reportItems.value)  // Сохраняем отчет даже если списки пустые
        } else {
            // Если списки не изменились, все равно сохраняем отчет
            //saveReport(reportItems.value)  // Сохраняем отчет даже если списки пустые
            Log.i("ReportViewModel", "Списки не изменились, но отчет все равно сохранен")
        }
    }


    private fun startObservingTasksDirectory() {
        tasksDirectoryObserver = object : FileObserver(tasksDirectory.path, CREATE or DELETE) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null) {
                    when (event) {
                        CREATE -> {
                            handleNewFile(path)
                        }
                        DELETE -> {
                            handleFileDeleted(path)
                        }
                        MODIFY -> {
                            handleFIleModify(path)
                        }
                    }
                }
            }
        }
        tasksDirectoryObserver.startWatching()
    }

    private fun handleNewFile(fileName: String) {
        try {
            val file = File(tasksDirectory, fileName)
            if (file.isFile && !checkedFiles.contains(file.name)) {
                checkedFiles.add(file.name)
                notifyNewFile(file.name)
                counter.value++
            }
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Ошибка при обработке нового файла: ${e.message}")
        }
    }

    private fun handleFileDeleted(fileName: String) {
        try {
            val file = File(tasksDirectory, fileName)
            if (checkedFiles.contains(file.name)) {
                checkedFiles.remove(file.name)
                Log.i("ReportViewModel", "Файл удален: $fileName")
                counter.value--
                Log.e("DeletedCheck","$callbackFileModifyEvent")
                callbackFileModifyEvent?.onEvent("Deleted")
                toastMessage.value = "Произошло удаление  файла, остановка задания и отправка отчета!"
            }
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Ошибка при обработке удаления файла: ${e.message}")
        }
    }

    private fun handleFIleModify(fileName: String){
        toastMessage.value = "Произошло изменение в файле, остановка задания и отправка отчета!"
        callbackFileModifyEvent?.onEvent("Modify")
    }

    private fun notifyNewFile(fileName: String) {
        try {
            toastMessage.value = "Найден новый отчет: $fileName"
            Log.e("ReportViewModel", "Новый Отчет! : $fileName")
            loadTaskFromIni(fileName)
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Ошибка при уведомлении о новом файле: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        tasksDirectoryObserver.stopWatching()
        _addressRange.value = null
        Log.i("ReportViewModel", "ViewModel был очищен и все задачи остановлены")
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

            // Проверка, если список reportItems пуст
            if (reportItems.isNotEmpty()) {
                saveIniFile(detailedReportFile.absolutePath,reportItems)
                saveIniFileSummary(summaryReportFile.absolutePath,reportItems)
                Log.i("ReportViewModel", "Отчеты успешно сохранены локально: ${detailedReportFile.absolutePath}, ${summaryReportFile.absolutePath}")
                toastMessage.value = "Отчеты успешно сохранены локально"
            } else {
                saveIniFile(detailedReportFile.absolutePath,reportItems)
                saveIniFileSummary(summaryReportFile.absolutePath,reportItems)
                Log.e("ReportViewModel", "Отчет пустой, но данные все равно сохранены")
                toastMessage.value = "Отчет пустой, но данные все равно сохранены"

            }
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Ошибка при сохранении отчета: ${e.message}")
            toastMessage.value = "Ошибка при сохранении отчета: ${e.message}"
        }
    }



    private fun saveIniFile(fileName: String, dataItemsUnchecked: List<ReportItem>) {
        val file = File(fileName)
        val ini: Wini
        val addressArray = createAddressArray(_addressRange.value).toMutableList()

        // Check if the file already exists
        if (file.exists()) {
            ini = Wini(file)
        } else {
            ini = Wini()
            ini.file = file
        }
        val failedAndNotApproved = approvedItems.map { it.device.takeLast(4) } // Оно содержит все подвтержденные их 4 последние числа
        val remainingAddressArray = addressArray.filterNot { failedAndNotApproved.contains(it.takeLast(4)) }.toMutableList() // Здесь я из всего числа адрессов убираю подтвержденные!

        // Generate a unique section name based on the current date and time
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
                "Voice" -> "V"
                else -> "Unknown"
            }
            val errorDescription = typeOfError[item.interpretation] ?: "Error 1"
            when (val itemAddress = item.device.takeLast(4)) {
                rangeStart -> ini.put(reportSectionName, "${typeOfDeviceLet}${_addressRange.value?.first?.take(6)}${itemAddress}", "Devices  on the air : $errorDescription")
                rangeStop -> ini.put(reportSectionName, "${typeOfDeviceLet}${_addressRange.value?.second?.take(6)}${itemAddress}", "Devices  on the air : $errorDescription")
                else -> ini.put(reportSectionName, "Undefined", "Не опознано")
            }
            // Удаляем адрес из массива адресов, так как он уже обработан
            remainingAddressArray.removeIf { it.takeLast(4) == item.device.takeLast(4) }
            Log.e("CheckAddRange","$remainingAddressArray")
        }

        remainingAddressArray.forEachIndexed { _, address ->
            val typeOfDeviceLet = when (typeOfDevice.value) {
                "Online" -> "D"
                "Voice" -> "V"
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

        // Check if the file already exists
        if (file.exists()) {
            ini = Wini(file)
        } else {
            ini = Wini()
            ini.file = file
        }
        val failedAndNotApproved = approvedItems.map { it.device.takeLast(4) } // Оно содержит все подвтержденные их 4 последние числа
        val remainingAddressArray = addressArray.filter { failedAndNotApproved.contains(it.takeLast(4)) }.toMutableList() // Здесь я из всего числа адрессов убираю подтвержденные!
        // Generate a unique section name based on the current date and time
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val reportSectionName = "Отчет $timestamp"

        ini.put(reportSectionName, "RangeStart", _addressRange.value?.first)
        ini.put(reportSectionName, "RangeStop", _addressRange.value?.second)
        ini.put(reportSectionName, "Устройств прошедших проверку", remainingAddressArray.count())

        ini.store()
    }
    fun isReportFileExists(fileName: String): Boolean {
        return try {
            val file = File(reportsDirectory, fileName)
            file.exists()
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Ошибка при проверке существования файла отчета: ${e.message}")
            false
        }
    }

    fun loadTaskFromIni(fileName: String) {
        val file = File(tasksDirectory, fileName)
        if (!file.exists()) {
            Log.e("ReportViewModel", "Файл не найден: $fileName")
            return
        }
        val ini = Wini(file)
        // Пройти по всем секциям в INI файле
        ini.forEach { sectionName, section ->
            var rangeStart: String? = null
            var rangeStop: String? = null
            // Проверить, начинается ли имя секции с "Task"
            if (sectionName.startsWith("Task")) {
                type = section["Type"]
                rangeStart = section["RangeStart"]
                rangeStop = section["RangeStop"]
                // Вывести в лог соответствующие значения
                Log.i("ReportItem", "Type: $type")
                Log.i("ReportItem", "RangeStart: $rangeStart")
                Log.i("ReportItem", "RangeStop: $rangeStop")
            }
            if (rangeStart != null && rangeStop != null && type != null) {
                if (_addressRange.value?.first != rangeStart || _addressRange.value?.second != rangeStop) {
                    Log.i("ReportViewModel", "Новые значения rangeStart и rangeStop не совпадают с текущим значением addressRange")
                    _addressRange.value = Pair(rangeStart, rangeStop)
                    typeOfDevice.value = type
                    Log.i("ReportViewModel", "addressRange updated: $rangeStart - $rangeStop : addressRange - ${_addressRange.value?.first} - ${_addressRange.value?.second}")
                }else{
                    Log.i("ReportViewModel", "Новые значения rangeStart и rangeStop  совпадают с текущим значением addressRange обновление не требуется!")
                }
            }
        }
    }
}
