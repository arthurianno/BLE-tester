package com.example.bletester.services

import android.content.Context
import android.util.Log
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bletester.viewModels.ReportViewModel
import com.google.firebase.database.core.Repo
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
    private val sharedPreferences = context.getSharedPreferences("FileNames", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        try {
            while (true) {
                val newFiles = checkForNewFiles()
                val filteredNewFiles = newFiles - lastProcessedFiles
                if (filteredNewFiles.isNotEmpty()) {
                    lastProcessedFiles = newFiles
                    Log.i("MinuteCheckWorker", "updating state $newFiles")
                    sendNotification(filteredNewFiles)
                    updateSharedPreferences(newFiles)
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
    private fun updateSharedPreferences(newFiles: Set<String>) {
        val editor = sharedPreferences.edit()

        // Обновляем список всех файлов
        val fileNames = sharedPreferences.getStringSet("fileNames", mutableSetOf()) ?: mutableSetOf()
        fileNames.addAll(newFiles)
        editor.putStringSet("fileNames", fileNames)

        // Применяем изменения
        editor.apply()
        Log.i("ReportViewModel", "SharedPreferences updated with $newFiles")
    }

    private suspend fun checkForNewFiles(): Set<String> {
        val storageRef: StorageReference = storage.reference.child("reports/")
        val result = storageRef.listAll().await()
        val allFiles = result.items

        val prefix = "task_"
        return allFiles.filter { it.name.startsWith(prefix) }.map { it.name }.toSet()
    }
}