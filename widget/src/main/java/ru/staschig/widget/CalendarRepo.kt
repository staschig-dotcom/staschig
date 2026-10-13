package ru.staschig.widget

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class CalEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
)

/**
 * События из календарей, синхронизированных на телефоне (Google, Exchange, локальные).
 * Читаем системный CalendarContract — без OAuth, ключей и интернета.
 */
object CalendarRepo {
    private const val DAY = 24L * 60 * 60 * 1000

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun upcoming(ctx: Context, limit: Int, now: Long = System.currentTimeMillis()): List<CalEvent> {
        if (!hasPermission(ctx)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            // −1 день: события «на весь день» хранятся от полуночи UTC.
            ContentUris.appendId(it, now - DAY)
            ContentUris.appendId(it, now + 2 * DAY)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
        val result = ArrayList<CalEvent>()
        try {
            ctx.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val e = CalEvent(
                        id = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(без названия)",
                        begin = c.getLong(2),
                        end = c.getLong(3),
                        allDay = c.getInt(4) == 1,
                        color = c.getInt(5),
                    )
                    if (isRelevant(e, now, TimeZone.getDefault())) result += e
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        // Сначала идущие и ближайшие по времени, события «на весь день» — после тех, что с часами, в тот же день.
        return result.sortedWith(compareBy({ dayKey(it, TimeZone.getDefault()) }, { it.allDay }, { it.begin })).take(limit)
    }

    /** Ещё не закончилось (для «весь день» — по локальной дате, а не по UTC). */
    fun isRelevant(e: CalEvent, now: Long, tz: TimeZone): Boolean {
        if (!e.allDay) return e.end > now
        val today = dayKey(now, tz)
        // У события на весь день END — полночь UTC следующего дня после последнего дня.
        val lastDay = dayKey(e.end - 1, TimeZone.getTimeZone("UTC"))
        return lastDay >= today
    }

    /** «Сейчас, до 15:00», «Сегодня 14:00», «Завтра, весь день», «16.10 09:30». */
    fun whenLabel(e: CalEvent, now: Long, tz: TimeZone): String {
        if (!e.allDay && e.begin <= now && now < e.end) return "Сейчас, до ${hm(e.end, tz)}"
        val eventDay = if (e.allDay) {
            maxOf(dayKey(e.begin, TimeZone.getTimeZone("UTC")), dayKey(now, tz))
        } else {
            dayKey(e.begin, tz)
        }
        val today = dayKey(now, tz)
        val tomorrow = dayKey(now + DAY, tz)
        val day = when (eventDay) {
            today -> "Сегодня"
            tomorrow -> "Завтра"
            else -> String.format(Locale.US, "%02d.%02d", eventDay % 100, eventDay % 10000 / 100)
        }
        return if (e.allDay) "$day, весь день" else "$day ${hm(e.begin, tz)}"
    }

    private fun dayKey(e: CalEvent, tz: TimeZone): Int =
        if (e.allDay) dayKey(e.begin, TimeZone.getTimeZone("UTC")) else dayKey(e.begin, tz)

    /** yyyyMMdd как число — удобно сравнивать дни. */
    fun dayKey(time: Long, tz: TimeZone): Int {
        val c = Calendar.getInstance(tz).apply { timeInMillis = time }
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }

    private fun hm(time: Long, tz: TimeZone): String {
        val c = Calendar.getInstance(tz).apply { timeInMillis = time }
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
}
