package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.Medicine
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MedicineReminderScheduler {

    private const val PREFS_NAME = "medicine_reminders_prefs"
    private const val KEY_ACTIVE_REMINDERS = "active_reminders_json"
    private val TIME_REGEX = Regex("""^(\d{1,2}):(\d{2})(?:\s*(AM|PM))?$""", RegexOption.IGNORE_CASE)

    fun syncReminders(
        context: Context,
        medicines: List<Medicine>,
        globalNotificationsEnabled: Boolean = true
    ) {
        val appContext = context.applicationContext
        MedicineReminderReceiver.ensureChannel(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Cancel previously registered alarms first so deleted or edited times never linger.
        cancelPersistedAlarms(appContext, alarmManager)

        if (!globalNotificationsEnabled) {
            savePersistedAlarms(appContext, emptyList())
            return
        }

        val scheduledRecords = mutableListOf<PersistedReminder>()
        medicines.filter { it.active && it.remindersEnabled }.forEach { med ->
            med.scheduleTimes.forEach { slot ->
                val cleanSlot = slot.trim()
                if (cleanSlot.isNotEmpty()) {
                    val requestCode = buildRequestCode(med.id, cleanSlot)
                    val triggerAtMillis = parseNextTriggerMillis(cleanSlot)
                    val record = PersistedReminder(
                        medicineId = med.id,
                        medicineName = med.name,
                        dosage = med.dosage,
                        instructions = med.instructions,
                        timeSlot = cleanSlot,
                        requestCode = requestCode
                    )
                    scheduleAlarm(appContext, alarmManager, record, triggerAtMillis)
                    scheduledRecords.add(record)
                }
            }
        }
        savePersistedAlarms(appContext, scheduledRecords)
    }

    fun cancelMedicineReminders(context: Context, medicine: Medicine) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        medicine.scheduleTimes.forEach { slot ->
            cancelSingleAlarm(appContext, alarmManager, buildRequestCode(medicine.id, slot.trim()))
        }
        val remaining = loadPersistedAlarms(appContext).filterNot { it.medicineId == medicine.id }
        savePersistedAlarms(appContext, remaining)
    }

    fun rescheduleFromStorage(context: Context) {
        val appContext = context.applicationContext
        MedicineReminderReceiver.ensureChannel(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val stored = loadPersistedAlarms(appContext)
        stored.forEach { record ->
            val triggerAtMillis = parseNextTriggerMillis(record.timeSlot)
            scheduleAlarm(appContext, alarmManager, record, triggerAtMillis)
        }
    }

    fun scheduleNextDayReminder(
        context: Context,
        medicineId: String,
        medicineName: String,
        dosage: String,
        instructions: String,
        timeSlot: String,
        requestCode: Int
    ) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val activeStored = loadPersistedAlarms(appContext)
        // Only reschedule if this reminder is still active in local storage.
        if (activeStored.isNotEmpty() && activeStored.none { it.requestCode == requestCode }) return

        val nextMillis = parseNextTriggerMillis(timeSlot = timeSlot, forceTomorrowIfClose = true)
        scheduleAlarm(
            context = appContext,
            alarmManager = alarmManager,
            record = PersistedReminder(
                medicineId = medicineId,
                medicineName = medicineName,
                dosage = dosage,
                instructions = instructions,
                timeSlot = timeSlot,
                requestCode = requestCode
            ),
            triggerAtMillis = nextMillis
        )
    }

    fun scheduleTestReminder(context: Context, delaySeconds: Int = 5) {
        val appContext = context.applicationContext
        MedicineReminderReceiver.ensureChannel(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAtMillis = System.currentTimeMillis() + (delaySeconds.coerceIn(2, 60) * 1000L)
        val requestCode = 900_999
        val intent = Intent(appContext, MedicineReminderReceiver::class.java).apply {
            putExtra(MedicineReminderReceiver.EXTRA_MEDICINE_ID, "test_reminder")
            putExtra(MedicineReminderReceiver.EXTRA_MEDICINE_NAME, "Daily Medication Check")
            putExtra(MedicineReminderReceiver.EXTRA_DOSAGE, "Scheduled Dose")
            putExtra(MedicineReminderReceiver.EXTRA_INSTRUCTIONS, "AlarmManager test alert delivered successfully.")
            putExtra(MedicineReminderReceiver.EXTRA_TIME_SLOT, SimpleDateFormat("hh:mm a", Locale.US).format(Date(triggerAtMillis)))
            putExtra(MedicineReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode)
            putExtra(MedicineReminderReceiver.EXTRA_IS_DAILY_ALARM, false)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setDozeSafeAlarm(alarmManager, triggerAtMillis, pendingIntent)
    }

    fun formatTime12Hour(hourOfDay: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
        }
        return SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)
    }

    fun formatNextTriggerSummary(scheduleTimes: List<String>): String {
        if (scheduleTimes.isEmpty()) return "No times set"
        val nextMillis = scheduleTimes.minOf { parseNextTriggerMillis(it) }
        val nowCal = Calendar.getInstance()
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextMillis }
        val dayPrefix = if (
            nowCal.get(Calendar.YEAR) == nextCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == nextCal.get(Calendar.DAY_OF_YEAR)
        ) "Today" else "Tomorrow"
        val formattedTime = SimpleDateFormat("hh:mm a", Locale.US).format(Date(nextMillis))
        return "$dayPrefix at $formattedTime"
    }

    internal fun parseNextTriggerMillis(
        timeSlot: String,
        nowMillis: Long = System.currentTimeMillis(),
        forceTomorrowIfClose: Boolean = false
    ): Long {
        val trimmed = timeSlot.trim().uppercase(Locale.US)
        val match = TIME_REGEX.matchEntire(trimmed)
        val rawHour = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 8
        val minute = (match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, 59)
        val amPm = match?.groupValues?.getOrNull(3).orEmpty()

        val hour24 = when {
            amPm == "PM" && rawHour in 1..11 -> rawHour + 12
            amPm == "AM" && rawHour == 12 -> 0
            amPm == "PM" && rawHour == 12 -> 12
            else -> rawHour.coerceIn(0, 23)
        }

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val threshold = if (forceTomorrowIfClose) nowMillis + 60_000L else nowMillis
        if (cal.timeInMillis <= threshold) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        record: PersistedReminder,
        triggerAtMillis: Long
    ) {
        val intent = Intent(context, MedicineReminderReceiver::class.java).apply {
            putExtra(MedicineReminderReceiver.EXTRA_MEDICINE_ID, record.medicineId)
            putExtra(MedicineReminderReceiver.EXTRA_MEDICINE_NAME, record.medicineName)
            putExtra(MedicineReminderReceiver.EXTRA_DOSAGE, record.dosage)
            putExtra(MedicineReminderReceiver.EXTRA_INSTRUCTIONS, record.instructions)
            putExtra(MedicineReminderReceiver.EXTRA_TIME_SLOT, record.timeSlot)
            putExtra(MedicineReminderReceiver.EXTRA_NOTIFICATION_ID, record.requestCode)
            putExtra(MedicineReminderReceiver.EXTRA_IS_DAILY_ALARM, true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            record.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setDozeSafeAlarm(alarmManager, triggerAtMillis, pendingIntent)
    }

    private fun setDozeSafeAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelPersistedAlarms(context: Context, alarmManager: AlarmManager) {
        loadPersistedAlarms(context).forEach { record ->
            cancelSingleAlarm(context, alarmManager, record.requestCode)
        }
    }

    private fun cancelSingleAlarm(context: Context, alarmManager: AlarmManager, requestCode: Int) {
        val intent = Intent(context, MedicineReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun buildRequestCode(medicineId: String, timeSlot: String): Int =
        "${medicineId}_${timeSlot.uppercase(Locale.US)}".hashCode() and 0x7FFFFFFF

    private fun savePersistedAlarms(context: Context, list: List<PersistedReminder>) {
        val jsonArray = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("medicineId", item.medicineId)
                put("medicineName", item.medicineName)
                put("dosage", item.dosage)
                put("instructions", item.instructions)
                put("timeSlot", item.timeSlot)
                put("requestCode", item.requestCode)
            }
            jsonArray.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_REMINDERS, jsonArray.toString())
            .apply()
    }

    private fun loadPersistedAlarms(context: Context): List<PersistedReminder> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_REMINDERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        PersistedReminder(
                            medicineId = obj.optString("medicineId"),
                            medicineName = obj.optString("medicineName"),
                            dosage = obj.optString("dosage"),
                            instructions = obj.optString("instructions"),
                            timeSlot = obj.optString("timeSlot"),
                            requestCode = obj.optInt("requestCode")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private data class PersistedReminder(
        val medicineId: String,
        val medicineName: String,
        val dosage: String,
        val instructions: String,
        val timeSlot: String,
        val requestCode: Int
    )
}
