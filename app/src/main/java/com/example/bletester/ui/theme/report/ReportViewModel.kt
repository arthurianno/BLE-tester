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
    private val tasksDirectory = sharedData.tasksDirectory
    private val reportsDirectory = sharedData.reportsDirectory
    private var callbackFileModifyEvent: FileModifyEvent? = null

    init {
        createReportsDirectory()
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
        if (!tasksDirectory.canRead() || !tasksDirectory.canWrite()) {
            Log.e("ReportViewModel", "Not enough permissions to work with the directory: ${tasksDirectory.absolutePath}")
        }
    }

    private fun createReportsDirectory() {
        listOf(bleTesterDirectory, reportsDirectory, tasksDirectory).forEach { directory ->
            if (!directory.exists()) {
                directory.mkdirs()
                Log.i("ReportViewModel", "Folder ${directory.name} created: ${directory.absolutePath}")
            } else {
                Log.i("ReportViewModel", "Folder ${directory.name} exists: ${directory.absolutePath}")
            }
        }
    }

    private fun initializeFileObserver() {
        fileObserver.setCallbacks(
            onFileAdded = { fileName -> handleNewFile(fileName) },
            onFileDeleted = { fileName -> handleFileDeleted(fileName) },
            onFileModified = { fileName -> handleFileModify(fileName) }
        )
        fileObserver.startObserving()
    }

    private fun handleNewFile(fileName: String) {
        try {
            notifyNewFile(fileName)
            Log.d("ReportViewModel", "New file detected: $fileName")
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
        Log.i("ReportViewModel", fileName)
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
            createReportsDirectory()

            val dateFormat = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val detailedReportFile = File(reportsDirectory, "$currentDate.ini")
            val summaryReportFile = File(reportsDirectory, "report_summary.ini")

            if (!detailedReportFile.exists()) {
                detailedReportFile.createNewFile()
            }
            if (!summaryReportFile.exists()) {
                summaryReportFile.createNewFile()
            }

            if (reportItemsNotApproved.isNotEmpty() && reportItemsApproved.isNotEmpty()) {
                iniUtil.saveIniFile(detailedReportFile.absolutePath, reportItemsNotApproved,reportItemsApproved)
                iniUtil.saveIniFileSummary(summaryReportFile.absolutePath, reportItemsApproved)
                Log.i("ReportViewModel", "Reports have been successfully saved locally: ${detailedReportFile.absolutePath}, ${summaryReportFile.absolutePath}")
                toastMessage.value = "Отчеты успешно сохранены локально"
            } else {
                iniUtil.saveIniFile(detailedReportFile.absolutePath, reportItemsNotApproved,reportItemsApproved)
                iniUtil.saveIniFileSummary(summaryReportFile.absolutePath, reportItemsApproved)
                Log.e("ReportViewModel", "The report is empty, but the data is saved anyway")
                toastMessage.value = "Отчет сохранен!"
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