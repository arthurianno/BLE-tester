package com.example.bletester.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.bletester.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import javax.inject.Inject


@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MyFirebaseMessagingService @Inject constructor(): FirebaseMessagingService() {


    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        getFireBaseMessage(
            message.notification?.title ?: "",
            message.notification?.body ?: ""
        )
    }

    @SuppressLint("MissingPermission")
    fun getFireBaseMessage(title: String, body: String) {
        val channelId = "notify"
        val notificationId = 102

        val notificationManager = NotificationManagerCompat.from(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Default", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.baseline_notifications_active_24)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }





    override fun onNewToken(token: String) {
        Log.d("FCM", "Refreshed token: $token")
        // Отправьте токен на ваш сервер, если необходимо.
    }
}