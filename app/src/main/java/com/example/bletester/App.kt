package com.example.bletester

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.bletester.services.WorkerService
import dagger.hilt.android.HiltAndroidApp


@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(
            this,
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .build()
        )
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                WorkerService.CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
