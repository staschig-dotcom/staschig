package ru.staschig.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import java.util.Date

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

        val access = hasNotificationAccess(ctx)
        v.setTextViewText(
            R.id.messages_empty,
            ctx.getString(if (access) R.string.messages_empty else R.string.messages_no_access),
        )
        v.setOnClickPendingIntent(R.id.messages_empty, activity(ctx, 4, Intent(ctx, SettingsActivity::class.java)))
        return v
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
