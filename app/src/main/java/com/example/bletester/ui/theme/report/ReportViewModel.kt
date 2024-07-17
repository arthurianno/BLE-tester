package com.example.bletester.ui.theme.report

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.bletester.items.ReportItem
import com.example.bletester.utils.FileModifyEvent
import com.example.bletester.utils.FileObserver
import com.example.bletester.utils.IniUtil
import com.example.bletester.utils.SharedData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@SuppressLint("NewApi")
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val sharedData: SharedData,
    private val iniUtil: IniUtil,
    private val fileObserver: FileObserver
) : ViewModel() {

    var addressRange = sharedData.addressRange.asSharedFlow()
    val toastMessage = MutableStateFlow<String?>(null)
    val reportItems = MutableStateFlow<List<ReportItem>>(emptyList())
    private val bleTesterDirectory = sharedData.bleTesterDirectory
    private var callbackFileModifyEvent: FileModifyEvent? = null
    private val reportDirectory = sharedData.reportDirectory


    init {
        createBleTesterDirectory()
        createReportDirectory()
        initializeFileObserver()
        checkDirectoryPermissions()
        Log.d("ReportViewModel", "ViewModel initialized, file observation should be active")
    }

    fun registerCallback(callbackFileModifyEvent: FileModifyEvent) {
        this.callbackFileModifyEvent = callbackFileModifyEvent
    }

    fun getAddressRange(): Pair<String, String>? {
        return sharedData.addressRange.value
    }


    private fun checkDirectoryPermissions() {
        if (!bleTesterDirectory.canRead() || !bleTesterDirectory.canWrite()) {
            Log.e("ReportViewModel", "Not enough permissions to work with the directory: ${bleTesterDirectory.absolutePath}")
        }
    }

    private fun createBleTesterDirectory() {
        if (!bleTesterDirectory.exists()) {
            bleTesterDirectory.mkdirs()
            Log.i("ReportViewModel", "Folder BLE Tester Directory created: ${bleTesterDirectory.absolutePath}")
        } else {
            Log.i("ReportViewModel", "Folder BLE Tester Directory exists: ${bleTesterDirectory.absolutePath}")
        }
    }

    private fun createReportDirectory() {
        if (!reportDirectory.exists()) {
            reportDirectory.mkdirs()
            Log.i("ReportViewModel", "Folder REPORT created: ${reportDirectory.absolutePath}")
        } else {
            Log.i("ReportViewModel", "Folder REPORT exists: ${reportDirectory.absolutePath}")
        }
    }

    private fun initializeFileObserver() {
        fileObserver.setCallbacks(
            onFileAdded = { fileName ->
                Log.d("ReportViewModel", "File added callback: $fileName")
                handleNewFile(fileName)
            },
            onFileDeleted = { fileName ->
                Log.d("ReportViewModel", "File deleted callback: $fileName")
                handleFileDeleted(fileName)
            },
            onFileModified = { fileName ->
                Log.d("ReportViewModel", "File modified callback: $fileName")
                handleFileModify(fileName)
            }
        )
        fileObserver.startObserving()
    }

    private fun handleNewFile(fileName: String) {
        try {
            Log.d("ReportViewModel", "Handling new file: $fileName")
            notifyNewFile(fileName)
            callbackFileModifyEvent?.onEvent("Auto")
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error processing new file: ${e.message}")
        }
    }

    private fun handleFileDeleted(fileName: String) {
        try {
            Log.i("ReportViewModel", "File deleted: $fileName")
            callbackFileModifyEvent?.onEvent("Deleted")
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error processing deleted file: ${e.message}")
        }
    }

    private fun handleFileModify(fileName: String) {
        Log.i("ReportViewModel", "File modified: $fileName")
        callbackFileModifyEvent?.onEvent("Modify")
    }

    private fun notifyNewFile(fileName: String) {
        try {
            toastMessage.value = "Найден новый отчет: $fileName"
            Log.e("ReportViewModel", "New report!: $fileName")
            iniUtil.loadTaskFromIni(fileName)
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error updating file: ${e.message}")
        }
    }

    fun updateReportItems(itemsNotApproved: List<ReportItem>, itemsApproved: List<ReportItem>) {
        reportItems.value = itemsNotApproved
        sharedData.notApprovedItems = itemsNotApproved
        sharedData.approvedItems = itemsApproved
        Log.e("TestReportView", "UnApproved items${reportItems.value}")
        Log.e("TestReportView", "Approved Items${sharedData.approvedItems}")
        saveReport(itemsNotApproved, itemsApproved)
    }

    fun updateReportItemsManual(itemsNotApproved: List<ReportItem>, itemsApproved: List<ReportItem>) {
        reportItems.value = itemsNotApproved
        Log.d("ReportViewModel", "Unchecked devices shared: ${sharedData.notApprovedItems}, Checked devices shared: ${sharedData.approvedItems}")
        sharedData.notApprovedItems = itemsNotApproved
        sharedData.approvedItems = itemsApproved
        Log.e("TestReportView", "${reportItems.value}")
        Log.e("TestReportView", "$itemsApproved")
        saveReport(itemsNotApproved,itemsApproved)
    }

    private fun saveReport(reportItemsNotApproved: List<ReportItem>, reportItemsApproved: List<ReportItem>) {
        try {
            createBleTesterDirectory()
            createReportDirectory()

            val dateFormat = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val additionalReportFile = File(reportDirectory, "report-$currentDate.ini")

            if (!additionalReportFile.exists()) {
                additionalReportFile.createNewFile()
            }

            if (reportItemsNotApproved.isNotEmpty() || reportItemsApproved.isNotEmpty()) {
                iniUtil.saveIniFile(additionalReportFile.absolutePath, reportItemsNotApproved, reportItemsApproved)

                Log.i("ReportViewModel", "Reports have been successfully saved locally: " +
                        additionalReportFile.absolutePath
                )
                toastMessage.value = "Отчеты успешно сохранены локально"
            } else {
                Log.e("ReportViewModel", "The report is empty, no data to save")
                toastMessage.value = "Отчет пуст, нет данных для сохранения"
            }
        } catch (e: Exception) {
            Log.e("ReportViewModel", "Error saving the report: ${e.message}")
            toastMessage.value = "Ошибка при сохранении отчета: ${e.message}"
        }
    }

    override fun onCleared() {
        super.onCleared()
        fileObserver.stopObserving()
        Log.i("ReportViewModel", "ViewModel was cleared and task stops")
    }

}