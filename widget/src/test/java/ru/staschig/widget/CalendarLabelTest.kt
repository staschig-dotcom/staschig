package ru.staschig.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CalendarLabelTest {
    private val msk = TimeZone.getTimeZone("Europe/Moscow")
    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(tz: TimeZone, y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    private fun timed(begin: Long, end: Long) = CalEvent(1, "x", begin, end, false, 0)
    private fun allDay(y: Int, m: Int, d: Int) = CalEvent(2, "x", at(utc, y, m, d), at(utc, y, m, d + 1), true, 0)

    @Test
    fun labels() {
        val now = at(msk, 2026, 10, 13, 12, 0)
        assertEquals("Сегодня 14:00", CalendarRepo.whenLabel(timed(at(msk, 2026, 10, 13, 14), at(msk, 2026, 10, 13, 15)), now, msk))
        assertEquals("Сейчас, до 12:30", CalendarRepo.whenLabel(timed(at(msk, 2026, 10, 13, 11), at(msk, 2026, 10, 13, 12, 30)), now, msk))
        assertEquals("Завтра 09:05", CalendarRepo.whenLabel(timed(at(msk, 2026, 10, 14, 9, 5), at(msk, 2026, 10, 14, 10)), now, msk))
        assertEquals("15.10 08:00", CalendarRepo.whenLabel(timed(at(msk, 2026, 10, 15, 8), at(msk, 2026, 10, 15, 9)), now, msk))
        assertEquals("Сегодня, весь день", CalendarRepo.whenLabel(allDay(2026, 10, 13), now, msk))
        assertEquals("Завтра, весь день", CalendarRepo.whenLabel(allDay(2026, 10, 14), now, msk))
    }

    @Test
    fun allDayRelevantByLocalDate() {
        // 01:00 по Москве 14-го = 22:00 UTC 13-го: вчерашнее событие на весь день уже не показываем.
        val now = at(msk, 2026, 10, 14, 1, 0)
        assertFalse(CalendarRepo.isRelevant(allDay(2026, 10, 13), now, msk))
        assertTrue(CalendarRepo.isRelevant(allDay(2026, 10, 14), now, msk))
        assertFalse(CalendarRepo.isRelevant(timed(now - 7200_000, now - 1), now, msk))
    }
}
