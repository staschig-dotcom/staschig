package ru.staschig.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap

class MessagesService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val ctx: Context) : RemoteViewsFactory {
        private var items: List<AppMessage> = emptyList()
        private var maxLines = 5
        private val icons = HashMap<String, Bitmap?>()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            items = NotificationStore.load(ctx)
            maxLines = Prefs.messageLines(ctx).let { if (it <= 0) Int.MAX_VALUE else it }
        }

        override fun onDestroy() {
            icons.clear()
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_message_item)
            val m = items.getOrNull(position) ?: return v
            v.setTextViewText(R.id.msg_app, m.app)
            v.setTextViewText(R.id.msg_time, Widgets.shortTime(ctx, m.time))
            v.setTextViewText(R.id.msg_title, m.title)
            v.setTextViewText(R.id.msg_text, m.text)
            v.setInt(R.id.msg_text, "setMaxLines", maxLines)
            icon(m.pkg)?.let { v.setImageViewBitmap(R.id.msg_icon, it) }
            v.setOnClickFillInIntent(
                R.id.msg_root,
                Intent()
                    .putExtra(OpenAppActivity.EXTRA_KEY, m.key)
                    .putExtra(OpenAppActivity.EXTRA_PKG, m.pkg),
            )
            return v
        }

        private fun icon(pkg: String): Bitmap? = icons.getOrPut(pkg) {
            try {
                ctx.packageManager.getApplicationIcon(pkg).toBitmap(64, 64)
            } catch (e: Exception) {
                null
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            items.getOrNull(position)?.key?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }
}
