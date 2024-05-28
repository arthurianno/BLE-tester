package com.example.bletester.viewModels

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.bletester.ReportItem
import com.example.bletester.services.FileCheckWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
    var reportItems: MutableState<List<ReportItem>> = mutableStateOf(emptyList())
    val toastMessage = MutableStateFlow<String?>(null)
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
     val addressRange = mutableStateOf<Pair<String, String>?>(null)
    private val sharedPreferences = context.getSharedPreferences("FileNames", Context.MODE_PRIVATE)
    var notificationShown by mutableStateOf(false)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "fileNames") {
            val fileNames = sharedPreferences.getStringSet(key, setOf()) ?: setOf()
            Log.i("SharedPreferenceChangeListener", "Updating $fileNames")
            // Установить notificationShown в true, когда получено новое уведомление
            notificationShown = true
        }
        // Дополнительные действия при изменении SharedPreferences, если необходимо
    }


    init {
        scheduleFileCheckWorker()
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun updateReportItems(items: List<ReportItem>) {
        reportItems.value = items
        Log.e("TestReportView","$reportItems")
    }
    override fun onCleared() {
        super.onCleared()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        Log.i("ReportViewModel", "SharedPreferences listener unregistered")
    }

    fun saveReport(fileName: String, reportItems: List<ReportItem>) {
        FirebaseAuth.getInstance().signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val storageRef: StorageReference = storage.reference.child("reports/$fileName.txt")

                    val content = generateReportContent(reportItems)
                    val contentBytes = content.toByteArray()

                    val uploadTask = storageRef.putBytes(contentBytes)
                    uploadTask.addOnSuccessListener {
                        Log.i("ReportViewModel", "Report successfully uploaded to Firebase Storage")
                        toastMessage.value = "Отчет успешно загружен в Firebase Storage"
                    }.addOnFailureListener { exception ->
                        Log.e("ReportViewModel", "Error uploading report: ${exception.message}")
                        toastMessage.value = "Ошибка загрузки отчета: ${exception.message}"
                    }
                } else {
                    // Sign-in failed
                    Log.e("FirebaseAuth", "signInAnonymously:failure", task.exception)
                    // Handle sign-in failure if needed
                }
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

//    fun signInAnonymously() {
//        FirebaseAuth.getInstance().signInAnonymously()
//            .addOnCompleteListener { task ->
//                if (task.isSuccessful) {
//                    // Sign-in successful
//                    Log.d("FirebaseAuth", "signInAnonymously:success")
//                } else {
//                    // Sign-in failed
//                    Log.e("FirebaseAuth", "signInAnonymously:failure", task.exception)
//                }
//            }
//    }
    suspend fun isReportFileExists(fileName: String): Boolean {
        return try {
            val storageRef: StorageReference = storage.reference.child("reports/")
            val result = storageRef.listAll().await()
            result.items.any { it.name == "$fileName.txt" }
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error checking report file existence: ${e.message}")
            false
        }
    }

    // Функция для загрузки файла из Firebase Storage
    suspend fun loadReportFromFile(fileName: String): Pair<String?, String?> {
        return try {
            val storageRef: StorageReference = storage.reference.child("reports/$fileName.txt")
            val maxDownloadSizeBytes: Long = 10 * 1024 * 1024 // 10 MB
            val byteArray = storageRef.getBytes(maxDownloadSizeBytes).await()

            // Convert byte array to string
            val fileContent = byteArray?.let {
                String(it)
            }

            // Save the file to the Downloads directory
            fileContent?.let {
                saveToDownloads(fileName, it)
            }

            // Extract address range from file content
            val rangeRegex = Regex("Диапазон адресов: (\\d+) - (\\d+)")
            val matchResult = rangeRegex.find(fileContent ?: "")
            val range = matchResult?.let {
                val startAddress = it.groupValues[1]
                val endAddress = it.groupValues[2]
                val addressPair = Pair(startAddress, endAddress)
                // Устанавливаем значение addressRange
                addressRange.value = addressPair
                Log.e("ReportCheck","Updating ${addressRange.value}")
                addressPair
            }

            Pair(fileContent, range?.let { "${it.first} - ${it.second}" })
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error loading report from file: ${e.message}")
            Pair(null, null)
        }
    }

    private fun saveToDownloads(fileName: String, content: String) {
        try {
            val downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsPath, "$fileName.txt")
            file.writeText(content)
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error saving report to Downloads: ${e.message}")
        }
    }

    private fun scheduleFileCheckWorker() {
        val workRequest = PeriodicWorkRequestBuilder<FileCheckWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "FileCheckWork",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
    }

}
