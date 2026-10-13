package ru.staschig.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AppMessage(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
)

/** Последние сообщения для виджета. Хранятся только на телефоне, никуда не отправляются. */
object NotificationStore {
    private const val FILE = "messages"
    private const val KEY = "items"
    const val MAX = 15

    @Synchronized
    fun load(ctx: Context): List<AppMessage> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AppMessage(
                    o.getString("key"), o.getString("pkg"), o.getString("app"),
                    o.getString("title"), o.getString("text"), o.getLong("time"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun replaceAll(ctx: Context, items: List<AppMessage>) {
        val arr = JSONArray()
        items.sortedByDescending { it.time }.take(MAX).forEach { m ->
            arr.put(
                JSONObject().put("key", m.key).put("pkg", m.pkg).put("app", m.app)
                    .put("title", m.title).put("text", m.text).put("time", m.time),
            )
        }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).commit()
        Widgets.updateAll(ctx)
    }

    @Synchronized
    fun upsert(ctx: Context, m: AppMessage) {
        replaceAll(ctx, listOf(m) + load(ctx).filter { it.key != m.key })
    }

    @Synchronized
    fun remove(ctx: Context, key: String) {
        val items = load(ctx)
        if (items.any { it.key == key }) replaceAll(ctx, items.filter { it.key != key })
    }
}
