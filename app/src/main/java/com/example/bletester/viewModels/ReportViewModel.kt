package com.example.bletester.viewModels

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bletester.ReportItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ini4j.Wini
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
    var reportItems: MutableState<List<ReportItem>> = mutableStateOf(emptyList())
    val toastMessage = MutableStateFlow<String?>(null)
    private val newFilesState = MutableStateFlow<List<String>>(emptyList())
    private val hasNewFiles = MutableStateFlow(false)
    private val addressRange = mutableStateOf<Pair<String, String>?>(null)
    val counter = MutableStateFlow(0)
    private val checkedFiles = mutableSetOf<String>()
    private var checkJob: Job? = null
    private val dcimDirectory: File? = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private val bleTesterDirectory = File(dcimDirectory, "BLE Tester Directory")
    private val reportsDirectory = File(bleTesterDirectory, "Reports")
    private val tasksDirectory = File(bleTesterDirectory, "Tasks")

    init {
        loadCheckedFiles()
        createReportsDirectory()
        startCheckingForNewFiles()
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


    private fun loadCheckedFiles() {
        val sharedPreferences = context.getSharedPreferences("checked_files", Context.MODE_PRIVATE)
        val filesSet = sharedPreferences.getStringSet("checked_files_set", emptySet()) ?: emptySet()
        checkedFiles.addAll(filesSet.sorted())
        Log.i("ReportViewModel", "Загружены проверенные файлы: $checkedFiles")
    }

    private fun saveCheckedFiles() {
        val sharedPreferences = context.getSharedPreferences("checked_files", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val sortedFiles = checkedFiles.sorted().toSet()
        editor.putStringSet("checked_files_set", sortedFiles)
        editor.apply()
        Log.i("ReportViewModel", "Сохранены проверенные файлы: $sortedFiles")
    }

    fun updateReportItems(items: List<ReportItem>) {
        reportItems.value = items
        Log.e("TestReportView", "$reportItems")
    }

    private fun startCheckingForNewFiles() {
        checkJob = viewModelScope.launch {
            while (isActive) {
                checkForNewFiles()
                delay(60000) // 1 минута
            }
        }
    }

    private fun checkForNewFiles() {
        try {
            val newFiles = mutableListOf<String>()
            tasksDirectory.listFiles()?.forEach { file ->
                if (file.isFile && !checkedFiles.contains(file.name)) {
                    checkedFiles.add(file.name)
                    newFiles.add(file.name)
                    notifyNewFile(file.name)
                    counter.value++
                }
            }

            if (newFiles.isEmpty()) {
                Log.i("ReportViewModel", "Новых файлов не найдено")
            } else {
                newFilesState.value = newFiles.sorted()
                hasNewFiles.value = true
                saveCheckedFiles()
            }
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Ошибка при проверке новых файлов: ${e.message}")
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
        checkJob?.cancel()
        Log.i("ReportViewModel", "ViewModel был очищен и все задачи остановлены")
    }

    fun saveReport(fileName: String, reportItems: List<ReportItem>) {
        try {
            createReportsDirectory()

            val file = File(reportsDirectory, "$fileName.ini")

            if (!file.exists()) {
                file.createNewFile()
            }

            if (reportItems.isNotEmpty()) {
                val data = generateIniData(reportItems)
                saveIniFile(file.absolutePath.toString(), data)
                Log.i("ReportViewModel", "Отчет успешно сохранен локально: ${file.absolutePath}")
                toastMessage.value = "Отчет успешно сохранен локально"
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
            "Диапазон адресов" to "24050000001 - 24050000002",
            "Устройств не прошедших проверку" to failedDevicesCount.toString()
        )
        data["ОТЧЕТ"] = headerSection  // Добавление свойств для раздела "ОТЧЕТ"

        reportItems.forEachIndexed { index, item ->
            val sectionName = "Device${index + 1}"
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
            // Проверить, начинается ли имя секции с "Task"
            if (sectionName.startsWith("Task")) {
                val type = section["Type"]
                val rangeStart = section["RangeStart"]
                val rangeStop = section["RangeStop"]

                // Вывести в лог соответствующие значения
                Log.i("ReportItem", "Type: $type")
                Log.i("ReportItem", "RangeStart: $rangeStart")
                Log.i("ReportItem", "RangeStop: $rangeStop")
            }
        }
    }
}
