package com.example.bletester.utils

import android.os.Environment
import com.example.bletester.items.ReportItem
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class SharedData {
    var addressRange = MutableStateFlow<Pair<String, String>?>(null)
    val typeOfDevice = MutableStateFlow<String?>(null)
    var approvedItems: List<ReportItem> = emptyList()
    var notApprovedItems: List<ReportItem> = emptyList()
    private val downloadDirectory: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val bleTesterDirectory = File(downloadDirectory, "BLE Tester Directory")
    val reportsDirectory = File(bleTesterDirectory, "Reports")
    val tasksDirectory = File(bleTesterDirectory, "Tasks")
}