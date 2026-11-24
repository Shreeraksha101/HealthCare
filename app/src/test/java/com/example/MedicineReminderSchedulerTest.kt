package com.example

import com.example.notifications.MedicineReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MedicineReminderSchedulerTest {

    @Test
    fun `parseNextTriggerMillis handles user-defined 12-hour and 24-hour times`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 24, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // 02:45 PM is later today
        val afternoon = MedicineReminderScheduler.parseNextTriggerMillis("02:45 PM", nowMillis = now)
        val afternoonCal = Calendar.getInstance().apply { timeInMillis = afternoon }
        assertEquals(24, afternoonCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, afternoonCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, afternoonCal.get(Calendar.MINUTE))

        // 07:30 AM already passed at 10:00 AM -> rolls over to tomorrow (25th)
        val morning = MedicineReminderScheduler.parseNextTriggerMillis("07:30 AM", nowMillis = now)
        val morningCal = Calendar.getInstance().apply { timeInMillis = morning }
        assertEquals(25, morningCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, morningCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, morningCal.get(Calendar.MINUTE))

        // 12:15 AM -> midnight hour 0
        val midnight = MedicineReminderScheduler.parseNextTriggerMillis("12:15 AM", nowMillis = now)
        val midnightCal = Calendar.getInstance().apply { timeInMillis = midnight }
        assertEquals(25, midnightCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, midnightCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, midnightCal.get(Calendar.MINUTE))

        // 24-hour string "21:10" -> 9:10 PM today
        val evening24 = MedicineReminderScheduler.parseNextTriggerMillis("21:10", nowMillis = now)
        val eveningCal = Calendar.getInstance().apply { timeInMillis = evening24 }
        assertEquals(24, eveningCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(21, eveningCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(10, eveningCal.get(Calendar.MINUTE))
        assertTrue(evening24 > now)
    }

    @Test
    fun `formatTime12Hour formats hour and minute cleanly`() {
        assertEquals("08:05 AM", MedicineReminderScheduler.formatTime12Hour(8, 5))
        assertEquals("12:00 PM", MedicineReminderScheduler.formatTime12Hour(12, 0))
        assertEquals("09:45 PM", MedicineReminderScheduler.formatTime12Hour(21, 45))
    }
}
