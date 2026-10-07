package ru.staschig.guitar.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.staschig.guitar.MainActivity
import ru.staschig.guitar.R
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.Settings
import java.time.LocalDateTime
import java.time.ZoneId

object Reminders {
    private const val CHANNEL = "practice"
    private const val REQUEST = 1001

    /** Ближайший момент напоминания после [now] или null, если выключено. */
    fun nextTime(s: Settings, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        if (!s.reminderOn || s.reminderDays.isEmpty()) return null
        for (d in 0..7) {
            val day = now.toLocalDate().plusDays(d.toLong())
            val t = day.atTime(s.reminderHour, s.reminderMinute)
            if (t.isAfter(now) && day.dayOfWeek.value in s.reminderDays) return t
        }
        return null
    }

    fun schedule(context: Context, s: Settings) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        val next = nextTime(s) ?: return
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Неточный будильник: точность ±несколько минут, зато не нужно особое разрешение.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    fun notify(context: Context, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Напоминания о занятиях", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, n) } // без разрешения — молча пропускаем
    }
}

/** Срабатывает в назначенное время: напоминает, если дневная цель ещё не выполнена. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = AppStore(context.applicationContext)
        val s = store.settings
        val done = store.minutesToday()
        if (done < s.dailyMinutes) {
            val streak = store.streak()
            val text = when {
                done > 0 -> "Сегодня уже $done мин — осталось ${s.dailyMinutes - done} до цели."
                streak > 0 -> "Серия $streak дн. — не прерывайте! Сегодня цель ${s.dailyMinutes} мин."
                else -> "Время позаниматься: ${s.dailyMinutes} мин сегодня."
            }
            Reminders.notify(context, "Гитара ждёт 🎸", text)
        }
        Reminders.schedule(context, s)
    }
}

/** После перезагрузки телефона будильники сбрасываются — ставим заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminders.schedule(context, AppStore(context.applicationContext).settings)
        }
    }
}
