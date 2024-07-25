package com.example.bletester.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.bletester.services.WorkerService

class AutoStart : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("AutoStart", "Received intent: ${intent?.action}")
        when (intent?.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                Log.d("AutoStart", "Power ON")
                enqueueWork(context)
            }
            "android.hardware.usb.action.USB_STATE" -> {
                val usbConnected = intent.getBooleanExtra("connected", false)
                if (usbConnected) {
                    Log.d("AutoStart", "USB not connected")
                    enqueueWork(context)
                }
            }
        }
    }
    private fun enqueueWork(context: Context) {
        try {
            val workRequest = OneTimeWorkRequestBuilder<WorkerService>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "workerService",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d("AutoStart", "The work has been successfully queued")
        } catch (ex: Exception) {
            Log.e("AutoStart", "Error when queuing work", ex)
        }
    }
}