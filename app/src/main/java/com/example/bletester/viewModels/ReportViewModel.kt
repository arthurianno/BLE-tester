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
import java.io.File
import java.io.IOException
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
    private val yourDirectory = File(dcimDirectory, "BLE Tester Directory")


    init {
        loadCheckedFiles()
        createReportsDirectory()
        startCheckingForNewFiles()
    }

    private fun createReportsDirectory() {
        if (!yourDirectory.exists()) {
            yourDirectory.mkdirs()
            Log.i("YourTag", "Папка создана: ${yourDirectory.absolutePath}")
        } else {
            Log.i("YourTag", "Папка уже существует: ${yourDirectory.absolutePath}")
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
            val files = yourDirectory.listFiles()
            Log.i("ReportViewModel", "Проверка директории: ${yourDirectory.absolutePath}, Файлы: ${files?.map { it.name }}")
            files?.forEach { file ->
                Log.i("ReportViewModel", "Проверка файла: ${file.name}")
                if (file.isFile && !checkedFiles.contains(file.name)) {
                    checkedFiles.add(file.name)
                    newFiles.add(file.name)
                    notifyNewFile(file.name)
                    Log.i("ReportViewModel", "Найден новый файл: ${file.name}")
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


    private  fun notifyNewFile(fileName: String) {
        try {
            toastMessage.value = "Найден новый отчет: $fileName"
            loadReportFromFile(fileName)
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
            val file = File(yourDirectory, fileName)
            val content = generateReportContent(reportItems)
            file.writeText(content)
            Log.i("ReportViewModel", "Отчет успешно сохранен локально: ${file.absolutePath}")
            toastMessage.value = "Отчет успешно сохранен локально"
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Ошибка при сохранении отчета: ${e.message}")
            toastMessage.value = "Ошибка при сохранении отчета: ${e.message}"
        }
    }

    private fun generateReportContent(reportItems: List<ReportItem>): String {
        val separator = "--------------------------------------------------------------------\n"
        val failedDevicesCount = reportItems.count { it.status == "Не прошло проверку" }
        val header = "ОТЧЕТ\nДиапазон адресов: 24050000001 - 24050000002 \nУстройств не прошедших проверку: $failedDevicesCount\n\n"

        val content = StringBuilder(header)
        for (item in reportItems) {
            content.append(separator)
            val line = "Device: ${item.device}\nDevice Address: ${item.deviceAddress}\nStatus: ${item.status}\nInterpretation: ${item.interpretation}\n\n"
            content.append(line)
            content.append(separator)
        }
        return content.toString()
    }

     fun isReportFileExists(fileName: String): Boolean {
        return try {
            val file = File(yourDirectory, fileName)
            file.exists()
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Ошибка при проверке существования файла отчета: ${e.message}")
            false
        }
    }

     fun loadReportFromFile(fileName: String): Pair<String?, String?> {
        return try {
            val file = File(yourDirectory, fileName)
            if (!file.exists()) {
                Log.e("ReportViewModel", "Файл не найден: $fileName")
                return Pair(null, null)
            }

            val fileContent = file.readText()
            //saveToDownloads(fileName, fileContent)

            val rangeRegex = Regex("Диапазон адресов: (\\d+) - (\\d+)")
            val matchResult = rangeRegex.find(fileContent)
            val range = matchResult?.let {
                val startAddress = it.groupValues[1]
                val endAddress = it.groupValues[2]
                val addressPair = Pair(startAddress, endAddress)
                addressRange.value = addressPair
                Log.e("ReportCheck", "Обновление ${addressRange.value}")
                addressPair
            }

            Pair(fileContent, range?.let { "${it.first} - ${it.second}" })
        } catch (e: IOException) {
            Log.e("ReportViewModel", "Ошибка при загрузке отчета из файла: ${e.message}")
            Pair(null, null)
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Ошибка при загрузке отчета из файла: ${e.message}")
            Pair(null, null)
        }
    }

//    private fun saveToDownloads(fileName: String, content: String) {
//        try {
//            val downloadsPath = yourDirectory
//            val file = File(downloadsPath, "$fileName.txt")
//            file.writeText(content)
//            Log.i("ReportViewModel", "Отчет сохранен в Downloads: ${file.absolutePath}")
//        } catch (e: Exception) {
//            Log.e("ReportViewModel", "Ошибка при сохранении отчета в Downloads: ${e.message}")
//        }
//    }
}
