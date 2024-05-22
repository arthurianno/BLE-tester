package com.example.bletester.viewModels

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.bletester.ReportItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
    private var reportItems: MutableState<List<ReportItem>> = mutableStateOf(emptyList())


    fun updateReportItems(items: List<ReportItem>) {
        reportItems.value = items
        Log.e("TestReportView","$reportItems")
    }

    fun saveReport(fileName: String, directoryUri: Uri, reportItems: List<ReportItem>) {
        val contentResolver: ContentResolver = context.contentResolver

        try {
            val mimeType = "text/plain"
            val newFileUri = DocumentsContract.createDocument(contentResolver, directoryUri, mimeType, "$fileName.txt")

            if (newFileUri != null) {
                contentResolver.openOutputStream(newFileUri).use { outputStream ->
                    outputStream?.let { writeReport(it, reportItems) }
                }
                Log.i("ReportViewModel", "Report saved to $newFileUri")
            } else {
                Log.e("ReportViewModel", "Error creating new document")
            }
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error saving report: ${e.message}")
        }
    }

    private fun writeReport(outputStream: OutputStream, reportItems: List<ReportItem>) {
        val header = "Device,Device Address,Status,Interpretation\n"
        try {
            outputStream.write(header.toByteArray())

            for (item in reportItems) {
                val line = "Device: ${item.device}\nDevice Address: ${item.deviceAddress}\nStatus: ${item.status}\nInterpretation: ${item.interpretation}\n\n"
                outputStream.write(line.toByteArray())
            }
        } catch (e: IOException) {
            Log.e("ReportViewModel", "Error writing report: ${e.message}")
        } finally {
            try {
                outputStream.close()
            } catch (e: IOException) {
                Log.e("ReportViewModel", "Error closing OutputStream: ${e.message}")
            }
        }
    }
}


