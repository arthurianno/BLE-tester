package com.example.bletester.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

class FileObserver @Inject constructor(
    private val sharedData: SharedData
) {
    private var observerJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var lastModifiedTimes = mutableMapOf<String, Long>()

    private var onTaskFileAdded: ((String) -> Unit)? = null
    private var onTaskFileDeleted: ((String) -> Unit)? = null
    private var onTaskFileModified: ((String) -> Unit)? = null
    private var onRefreshAdapterDetected: (() -> Unit)? = null

    fun setCallbacks(
        onTaskFileAdded: (String) -> Unit,
        onTaskFileDeleted: (String) -> Unit,
        onTaskFileModified: (String) -> Unit,
        onRefreshAdapterDetected: () -> Unit
    ) {
        this.onTaskFileAdded = onTaskFileAdded
        this.onTaskFileDeleted = onTaskFileDeleted
        this.onTaskFileModified = onTaskFileModified
        this.onRefreshAdapterDetected = onRefreshAdapterDetected
    }

    fun startObserving() {
        observerJob?.cancel()
        observerJob = coroutineScope.launch {
            checkForFileChanges() // Perform initial check
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
            file.isFile && (file.name.startsWith("Task") || file.name == "refreshAdapter.txt")
        }?.associate { it.name to it.lastModified() } ?: emptyMap()

        // Check for new or modified files
        currentFiles.forEach { (fileName, lastModified) ->
            when {
                fileName == "refreshAdapter.txt" -> handleRefreshAdapterFile(lastModified)
                fileName.startsWith("Task") -> handleTaskFile(fileName, lastModified)
            }
        }

        // Check for deleted files
        lastModifiedTimes.keys.minus(currentFiles.keys).forEach { deletedFileName ->
            if (deletedFileName.startsWith("Task")) {
                Log.d("FileObserver", "Task file deleted: $deletedFileName")
                onTaskFileDeleted?.invoke(deletedFileName)
            }
        }

        lastModifiedTimes = currentFiles.toMutableMap()
    }

    private fun handleRefreshAdapterFile(lastModified: Long) {
        val previousLastModified = lastModifiedTimes["refreshAdapter.txt"]
        if (previousLastModified == null || lastModified > previousLastModified) {
            Log.d("FileObserver", "refreshAdapter.txt detected or modified")
            onRefreshAdapterDetected?.invoke()
        }
    }

    private fun handleTaskFile(fileName: String, lastModified: Long) {
        if (!lastModifiedTimes.containsKey(fileName)) {
            Log.d("FileObserver", "New Task file detected: $fileName")
            onTaskFileAdded?.invoke(fileName)
        } else if (lastModified > lastModifiedTimes[fileName]!!) {
            Log.d("FileObserver", "Task file modified: $fileName")
            onTaskFileModified?.invoke(fileName)
        }
    }


}