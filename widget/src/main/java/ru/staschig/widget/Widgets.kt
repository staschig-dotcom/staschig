package ru.staschig.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import java.util.Date
import java.util.TimeZone

object Widgets {

    fun ids(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, InfoWidgetProvider::class.java))

    fun updateAll(ctx: Context) {
        val manager = AppWidgetManager.getInstance(ctx)
        val ids = ids(ctx)
        if (ids.isEmpty()) return
        ids.forEach { manager.updateAppWidget(it, build(ctx, it)) }
        @Suppress("DEPRECATION")
        manager.notifyAppWidgetViewDataChanged(ids, R.id.messages)
    }

    fun hasNotificationAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun build(ctx: Context, widgetId: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_info)
        v.setInt(R.id.widget_bg, "setImageAlpha", Prefs.bgOpacity(ctx) * 255 / 100)

        // Часы: нажатие открывает будильники.
        v.setOnClickPendingIntent(
            R.id.clock_block,
            activity(ctx, 1, Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)),
        )

        // Погода.
        val w = Prefs.weather(ctx)
        if (w != null) {
            v.setTextViewText(R.id.weather_main, w.headline)
            val time = DateFormat.getTimeFormat(ctx).format(Date(w.updated))
            v.setTextViewText(R.id.weather_desc, "${w.details} · $time")
        } else {
            v.setTextViewText(R.id.weather_main, "🌡️")
            v.setTextViewText(R.id.weather_desc, ctx.getString(R.string.weather_not_set))
        }
        v.setOnClickPendingIntent(R.id.weather_block, activity(ctx, 2, Intent(ctx, SettingsActivity::class.java)))

        // Календарь.
        bindEvents(ctx, v)

        // Сообщения.
        val svc = Intent(ctx, MessagesService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
        @Suppress("DEPRECATION")
        v.setRemoteAdapter(R.id.messages, svc)
        v.setEmptyView(R.id.messages, R.id.messages_empty)

        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        v.setPendingIntentTemplate(
            R.id.messages,
            PendingIntent.getActivity(
                ctx, 3, Intent(ctx, OpenAppActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            ),
        )

        // Нет сообщений — пусто, без надписи. Подсказка остаётся только когда не выдан доступ к уведомлениям.
        if (hasNotificationAccess(ctx)) {
            v.setTextViewText(R.id.messages_empty, "")
        } else {
            v.setTextViewText(R.id.messages_empty, ctx.getString(R.string.messages_no_access))
            v.setOnClickPendingIntent(R.id.messages_empty, activity(ctx, 4, Intent(ctx, SettingsActivity::class.java)))
        }
        return v
    }

    private val eventRows = intArrayOf(R.id.event_0, R.id.event_1, R.id.event_2, R.id.event_3, R.id.event_4)

    private fun bindEvents(ctx: Context, v: RemoteViews) {
        val now = System.currentTimeMillis()
        val events = CalendarRepo.upcoming(ctx, eventRows.size, now)
        eventRows.forEachIndexed { i, row ->
            val e = events.getOrNull(i)
            if (e == null) {
                v.setViewVisibility(row, View.GONE)
                return@forEachIndexed
            }
            val label = CalendarRepo.whenLabel(e, now, TimeZone.getDefault())
            val text = SpannableStringBuilder()
                .append("● ", ForegroundColorSpan(e.color or 0xFF000000.toInt()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                .append(label, ForegroundColorSpan(0xCCFFFFFF.toInt()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                .append("  ")
                .append(e.title)
            v.setTextViewText(row, text)
            v.setViewVisibility(row, View.VISIBLE)
            val open = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(row, activity(ctx, 10 + i, open))
        }

        val hint = when {
            !CalendarRepo.hasPermission(ctx) -> ctx.getString(R.string.events_no_access)
            events.isEmpty() -> ctx.getString(R.string.events_empty)
            else -> null
        }
        v.setViewVisibility(R.id.events_hint, if (hint == null) View.GONE else View.VISIBLE)
        if (hint != null) {
            v.setTextViewText(R.id.events_hint, hint)
            v.setOnClickPendingIntent(R.id.events_hint, activity(ctx, 5, Intent(ctx, SettingsActivity::class.java)))
        }
    }

    private fun activity(ctx: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            ctx, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** «14:05» для сегодняшних, «12 окт.» для более старых. */
    fun shortTime(ctx: Context, time: Long, now: Long = System.currentTimeMillis()): String {
        val sameDay = DateFormat.format("yyyyMMdd", time).toString() == DateFormat.format("yyyyMMdd", now).toString()
        return if (sameDay) {
            DateFormat.getTimeFormat(ctx).format(Date(time))
        } else {
            DateFormat.format(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "dMMM"), time).toString()
        }
    }
}
