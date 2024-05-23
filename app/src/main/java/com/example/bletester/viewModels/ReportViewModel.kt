package com.example.bletester.viewModels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.bletester.ReportItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
    private var reportItems: MutableState<List<ReportItem>> = mutableStateOf(emptyList())
    val toastMessage = MutableStateFlow<String?>(null)
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    fun updateReportItems(items: List<ReportItem>) {
        reportItems.value = items
        Log.e("TestReportView","$reportItems")
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

    fun signInAnonymously() {
        FirebaseAuth.getInstance().signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Sign-in successful
                    Log.d("FirebaseAuth", "signInAnonymously:success")
                } else {
                    // Sign-in failed
                    Log.e("FirebaseAuth", "signInAnonymously:failure", task.exception)
                }
            }
    }
}
