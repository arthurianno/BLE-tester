package com.example.bletester.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

class FileObserver @Inject constructor(
    private val sharedData: SharedData
) {
    private var observerJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var checkedFiles = mutableMapOf<String, Long>()
    private val counter = MutableStateFlow(0)

    private var onFileAdded: ((String) -> Unit)? = null
    private var onFileDeleted: ((String) -> Unit)? = null
    private var onFileModified: ((String) -> Unit)? = null

    fun setCallbacks(
        onFileAdded: (String) -> Unit,
        onFileDeleted: (String) -> Unit,
        onFileModified: (String) -> Unit
    ) {
        this.onFileAdded = onFileAdded
        this.onFileDeleted = onFileDeleted
        this.onFileModified = onFileModified
    }

    fun startObserving() {
        observerJob?.cancel()
        observerJob = coroutineScope.launch {
            while (isActive) {
                checkForFileChanges()
                delay(1000)
            }
        }
    }

    fun stopObserving() {
        observerJob?.cancel()
    }

    private fun checkForFileChanges() {
        val currentFiles = sharedData.bleTesterDirectory.listFiles { file ->
            file.isFile && file.name.matches(Regex("Task.ini"))
        }?.associate { it.name to it.lastModified() } ?: emptyMap()

        currentFiles.keys.minus(checkedFiles.keys).forEach { newFileName ->
            handleNewFile(newFileName)
        }

        checkedFiles.keys.minus(currentFiles.keys).forEach { deletedFileName ->
            handleFileDeleted(deletedFileName)
        }

        currentFiles.forEach { (fileName, lastModified) ->
            if (checkedFiles.containsKey(fileName) && lastModified > checkedFiles[fileName]!!) {
                handleFileModify(fileName)
            }
        }

        checkedFiles = currentFiles.toMutableMap()
    }

    private fun handleNewFile(fileName: String) {
        try {
            val file = File(sharedData.bleTesterDirectory, fileName)
            if (file.isFile && file.name.matches(Regex("\\d{8}\\.ini"))) {
                counter.value++
                onFileAdded?.invoke(fileName)
            }
        } catch (e: Exception) {
            Log.e("FileObserver", "Error processing new file: ${e.message}")
        }
    }

    private fun handleFileDeleted(fileName: String) {
        try {
            counter.value--
            onFileDeleted?.invoke(fileName)
        } catch (e: Exception) {
            Log.e("FileObserver", "Error processing deleted file: ${e.message}")
        }
    }

    private fun handleFileModify(fileName: String) {
        onFileModified?.invoke(fileName)
    }
}