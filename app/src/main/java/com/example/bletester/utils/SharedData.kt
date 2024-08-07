package com.example.bletester.utils

import android.os.Environment
import com.example.bletester.items.ReportItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class SharedData {
    var addressRange = MutableStateFlow<Pair<String, String>?>(null)
    val typeOfDevice = MutableStateFlow<String?>(null)
    var approvedItems: List<ReportItem> = emptyList()
    val refreshAdapterValue = MutableStateFlow<Int?>(null)
    var notApprovedItems: List<ReportItem> = emptyList()
    private val externalStorageDirectory: File = Environment.getExternalStorageDirectory()
    val bleTesterDirectory = File(externalStorageDirectory, "BLE Tester Directory")
     val reportDirectory = File(bleTesterDirectory, "REPORT")
}