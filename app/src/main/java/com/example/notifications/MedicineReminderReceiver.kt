package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class MedicineReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicineId = intent.getStringExtra(EXTRA_MEDICINE_ID) ?: ""
        val medicineName = intent.getStringExtra(EXTRA_MEDICINE_NAME) ?: "Your Medication"
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""
        val instructions = intent.getStringExtra(EXTRA_INSTRUCTIONS) ?: ""
        val timeSlot = intent.getStringExtra(EXTRA_TIME_SLOT) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1001)
        val isDailyAlarm = intent.getBooleanExtra(EXTRA_IS_DAILY_ALARM, false)

        ensureChannel(context)

        // Automatically schedule the next day's occurrence for repeating daily medication reminders.
        if (isDailyAlarm && medicineId.isNotBlank() && timeSlot.isNotBlank()) {
            MedicineReminderScheduler.scheduleNextDayReminder(
                context = context,
                medicineId = medicineId,
                medicineName = medicineName,
                dosage = dosage,
                instructions = instructions,
                timeSlot = timeSlot,
                requestCode = notificationId
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shortBody = buildString {
            append("Time to take $medicineName")
            if (dosage.isNotBlank()) append(" ($dosage)")
            if (timeSlot.isNotBlank()) append(" • $timeSlot")
        }
        val expandedBody = buildString {
            append(shortBody)
            if (instructions.isNotBlank()) append("\n$instructions")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Medication Reminder")
            .setContentText(shortBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_ID = "medicine_reminders"
        const val EXTRA_MEDICINE_ID = "extra_medicine_id"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_DOSAGE = "extra_dosage"
        const val EXTRA_INSTRUCTIONS = "extra_instructions"
        const val EXTRA_TIME_SLOT = "extra_time_slot"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_IS_DAILY_ALARM = "extra_is_daily_alarm"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Medicine Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Daily reminders for scheduled medications."
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        }
    }
}
