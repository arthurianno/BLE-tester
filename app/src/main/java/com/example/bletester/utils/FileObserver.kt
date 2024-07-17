package com.example.bletester.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

class FileObserver @Inject constructor(
    private val sharedData: SharedData
) {
    private var observerJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var lastModifiedTimes = mutableMapOf<String, Long>()

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
            file.isFile && file.name.startsWith("Task")
        }?.associate { it.name to it.lastModified() } ?: emptyMap()

        // Check for new or modified files
        currentFiles.forEach { (fileName, lastModified) ->
            if (!lastModifiedTimes.containsKey(fileName)) {
                Log.d("FileObserver", "New file detected: $fileName")
                onFileAdded?.invoke(fileName)
            } else if (lastModified > lastModifiedTimes[fileName]!!) {
                Log.d("FileObserver", "File modified: $fileName")
                onFileModified?.invoke(fileName)
            }
        }

        // Check for deleted files
        lastModifiedTimes.keys.minus(currentFiles.keys).forEach { deletedFileName ->
            Log.d("FileObserver", "File deleted: $deletedFileName")
            onFileDeleted?.invoke(deletedFileName)
        }

        lastModifiedTimes = currentFiles.toMutableMap()
    }
}