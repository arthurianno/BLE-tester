package com.example.bletester.viewModels

import android.content.Context
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.bletester.ReportItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.ini4j.Wini
import java.io.File
import javax.inject.Inject

@Suppress("DEPRECATION")
@HiltViewModel
class ReportViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
    private val _addressRange = MutableStateFlow<Pair<String, String>?>(null)
    val addressRange: Flow<Pair<String, String>?> get() = _addressRange
    private var approvedItems: List<ReportItem> = emptyList()
    val toastMessage = MutableStateFlow<String?>(null)
    var reportItems: MutableState<List<ReportItem>> = mutableStateOf(emptyList())
    //val addressRange = mutableStateOf<Pair<String, String>?>(null)
    val counter = MutableStateFlow(0)
    private val checkedFiles = mutableSetOf<String>()
    private val dcimDirectory: File? = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private val bleTesterDirectory = File(dcimDirectory, "BLE Tester Directory")
    private val reportsDirectory = File(bleTesterDirectory, "Reports")
    private val tasksDirectory = File(bleTesterDirectory, "Tasks")
    private lateinit var tasksDirectoryObserver: FileObserver

    init {

        createReportsDirectory()
        startObservingTasksDirectory()
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
    fun updateReportItems(itemsUnchecked: List<ReportItem>, itemsApproved: List<ReportItem>) {
        if (reportItems.value != itemsUnchecked || approvedItems != itemsApproved) {
            reportItems.value = itemsUnchecked
            approvedItems = itemsApproved
            Log.e("TestReportView", "$reportItems")
            Log.e("TestReportView", "$approvedItems")
            saveReport("04062024",reportItems.value)
        }
    }


    private fun startObservingTasksDirectory() {
        tasksDirectoryObserver = object : FileObserver(tasksDirectory.path, CREATE or DELETE) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null) {
                    if (event == CREATE) {
                        handleNewFile(path)
                    } else if (event == DELETE) {
                        handleFileDeleted(path)
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
            }
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Ошибка при обработке удаления файла: ${e.message}")
        }
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

    fun saveReport(fileName: String, reportItems: List<ReportItem>) {
        try {
            createReportsDirectory()

            val detailedReportFile = File(reportsDirectory, "$fileName.ini")
            val summaryReportFile = File(reportsDirectory, "${fileName}_summary.ini")
            if (!detailedReportFile.exists()) {
                detailedReportFile.createNewFile()
            }
            if (!summaryReportFile.exists()) {
                summaryReportFile.createNewFile()
            }

            if (reportItems.isNotEmpty()) {
                val detailedData = generateIniData(reportItems)
                saveIniFile(detailedReportFile.absolutePath, detailedData)
                val summaryData = generateSummaryData(approvedItems)
                saveIniFile(summaryReportFile.absolutePath, summaryData)
                Log.i("ReportViewModel", "Отчеты успешно сохранены локально: ${detailedReportFile.absolutePath}, ${summaryReportFile.absolutePath}")
                toastMessage.value = "Отчеты успешно сохранены локально"
            } else {
                Log.e("ReportViewModel", "Отчет пустой, данные не сохранены")
                toastMessage.value = "Отчет пустой, данные не сохранены"
            }
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Ошибка при сохранении отчета: ${e.message}")
            toastMessage.value = "Ошибка при сохранении отчета: ${e.message}"
        }
    }

    private fun saveIniFile(fileName: String, data: Map<String, Map<String, String>>) {
        val file = File(fileName)
        val ini = Wini(file)
        data.forEach { (section, properties) ->
            val sectionObj = ini.add(section)
            properties.forEach { (key, value) ->
                sectionObj[key] = value
            }
        }
        ini.store(file)
    }

    private fun generateIniData(reportItems: List<ReportItem>): Map<String, Map<String, String>> {
        Log.e("ReportViewModel", "Number of report items: ${reportItems.size}") // Проверка количества элементов в отчете
        val data = mutableMapOf<String, MutableMap<String, String>>()
        val failedDevicesCount = reportItems.count { it.status == "Не прошло проверку" }
        val headerSection = mutableMapOf(
            "Диапазон адресов" to "${_addressRange.value?.first} - ${_addressRange.value?.second}",
            "Устройств не прошедших проверку" to failedDevicesCount.toString()
        )
        data["ОТЧЕТ"] = headerSection  // Добавление свойств для раздела "ОТЧЕТ"

        reportItems.forEachIndexed { _, item ->
            val sectionName = item.device
            val properties = mutableMapOf(
                "Device" to item.device,
                "Device Address" to item.deviceAddress,
                "Status" to item.status,
                "Interpretation" to item.interpretation
            )
            data[sectionName] = properties
        }

        return data
    }

    private fun generateSummaryData(itemsApproved: List<ReportItem>): Map<String, Map<String, String>> {
        val approvedDevicesCount = itemsApproved.count { it.status == "прошло проверку" }
        val headerSection = mapOf(
            "Start" to "${_addressRange.value?.first}",
            "Stop" to "${_addressRange.value?.second}",
            "Checked devices" to approvedDevicesCount.toString()
        )

        return mapOf("Отчет" to headerSection)
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
            val type: String?
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
            if (rangeStart != null && rangeStop != null) {
                if (_addressRange.value?.first != rangeStart || _addressRange.value?.second != rangeStop) {
                    Log.i("ReportViewModel", "Новые значения rangeStart и rangeStop не совпадают с текущим значением addressRange")
                }
                _addressRange.value = Pair(rangeStart, rangeStop)
                Log.i("ReportViewModel", "addressRange updated: $rangeStart - $rangeStop : addressRange - ${_addressRange.value?.first} - ${_addressRange.value?.second}")
            }
        }
    }
}
