package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MedicineBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MedicineReminderScheduler.rescheduleFromStorage(context.applicationContext)
        }
    }
}
