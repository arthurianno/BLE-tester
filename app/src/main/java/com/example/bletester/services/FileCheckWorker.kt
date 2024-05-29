package com.example.bletester.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

@HiltWorker
class FileCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private var lastProcessedFiles: Set<String> = emptySet()

    override suspend fun doWork(): Result {
        try {
            while (true) {
                val newFiles = checkForNewFiles()
                val filteredNewFiles = newFiles - lastProcessedFiles
                if (filteredNewFiles.isNotEmpty()) {
                    lastProcessedFiles = newFiles
                    Log.i("MinuteCheckWorker", "updating state $newFiles")
                    sendNotification(filteredNewFiles)

                    // Преобразование набора новых файлов в строку
                    val filteredTaskList = filteredNewFiles.joinToString(separator = ",")
                    val outputData = workDataOf(MY_KEY_DATA_FROM_WORKER to filteredTaskList)
                    return Result.success(outputData)
                } else {
                    Log.i("MinuteCheckWorker", "No new files detected")
                }
                delay(TimeUnit.MINUTES.toMillis(1))
            }

        } catch (e: Exception) {
            Log.e("MinuteCheckWorker", "Error checking for new files", e)
            return Result.retry()
        }
    }

    private fun sendNotification(newFiles: Set<String>) {
        Log.i("MinuteCheckWorker", "New files detected: $newFiles")
    }

    private suspend fun checkForNewFiles(): Set<String> {
        val storageRef: StorageReference = storage.reference.child("reports/")
        val result = storageRef.listAll().await()
        val allFiles = result.items

        val prefix = "task_"
        return allFiles.filter { it.name.startsWith(prefix) }.map { it.name }.toSet()
    }

    companion object {
        const val MY_KEY_DATA_FROM_WORKER = "MY_KEY_DATA_FROM_WORKER"
    }
}
