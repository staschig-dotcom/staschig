package ru.staschig.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class InfoWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        WeatherWorker.schedule(context)
        WeatherWorker.refreshNow(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, Widgets.build(context, it)) }
        @Suppress("DEPRECATION")
        manager.notifyAppWidgetViewDataChanged(ids, R.id.messages)
        WeatherWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WeatherWorker.cancel(context)
    }
}
