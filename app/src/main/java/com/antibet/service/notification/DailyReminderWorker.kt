package com.antibet.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.antibet.MainActivity
import com.antibet.R

class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
): CoroutineWorker(context, params){

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork(): Result {
        showDailyReminderNotification()
        return Result.success()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showDailyReminderNotification() {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lembretes Diários",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Lembretes para registrar economia com apostas."
        }

        notificationManager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hora de registrar economia com apostas!")
            .setContentText("Não se esqueça de registrar economia com apostas hoje!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID,notification)
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder_channel"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "daily_reminder_work"
    }
}