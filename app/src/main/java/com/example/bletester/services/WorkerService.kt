package com.example.bletester.services

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.bletester.R
import com.example.bletester.core.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
class WorkerService(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "ForegroundServiceChannel"
        private const val NOTIFICATION_ID = 1000
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        try {
            setForeground(getForegroundInfo())
            launchMainActivity()
            Result.success()
        } catch (e: Exception) {
            Log.e("WorkerService", "Error in doWork", e)
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Приложение запускается")
            .setContentText("Нажмите, чтобы открыть")
            .setSmallIcon(R.drawable.baseline_notifications_active_24)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(getPendingIntent(), true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private suspend fun launchMainActivity() = withContext(Dispatchers.Main) {
        val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningTasks = activityManager.getRunningTasks(Int.MAX_VALUE)

        val isActivityRunning = runningTasks.any { it.topActivity?.className == MainActivity::class.java.name }

        if (!isActivityRunning) {
            val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            applicationContext.startActivity(launchIntent)
            Log.d("WorkerService", "MainActivity запущена")
        } else {
            Log.d("WorkerService", "MainActivity уже запущена")
        }
    }
}