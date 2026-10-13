package ru.staschig.widget

import android.app.Notification
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * Получает уведомления других приложений (после того как пользователь выдал доступ
 * в настройках) и складывает текстовые в [NotificationStore] для виджета.
 */
class NotificationsListener : NotificationListenerService() {

    override fun onListenerConnected() {
        val active = try {
            activeNotifications?.toList().orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }
        intents.clear()
        val items = active.mapNotNull { sbn -> toMessage(sbn)?.also { remember(sbn) } }
        NotificationStore.replaceAll(this, items)
    }

    override fun onListenerDisconnected() {
        intents.clear()
        Widgets.updateAll(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val m = toMessage(sbn) ?: return
        remember(sbn)
        NotificationStore.upsert(this, m)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        intents.remove(sbn.key)
        NotificationStore.remove(this, sbn.key)
    }

    private fun remember(sbn: StatusBarNotification) {
        sbn.notification.contentIntent?.let { intents[sbn.key] = it }
    }

    private fun toMessage(sbn: StatusBarNotification): AppMessage? {
        if (sbn.packageName == packageName) return null
        val n = sbn.notification
        // Музыка, загрузки, навигация, «приложение работает в фоне» — не сообщения.
        if (sbn.isOngoing) return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val category = n.category
        if (category != null && category in IGNORED_CATEGORIES) return null

        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty().trim()
        if (title.isEmpty() && text.isEmpty()) return null

        return AppMessage(sbn.key, sbn.packageName, appLabel(sbn.packageName), title, text, sbn.postTime)
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    companion object {
        private val IGNORED_CATEGORIES = setOf(
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SYSTEM,
        )

        /** PendingIntent нельзя сохранить на диск — держим в памяти, пока сервис жив. */
        val intents = ConcurrentHashMap<String, PendingIntent>()
    }
}
