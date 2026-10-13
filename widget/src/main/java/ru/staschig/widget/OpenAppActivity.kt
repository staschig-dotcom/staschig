package ru.staschig.widget

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle

/** Нажатие на сообщение в виджете: открывает тот же экран, что и уведомление, иначе — само приложение. */
class OpenAppActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra(EXTRA_KEY)
        val pkg = intent.getStringExtra(EXTRA_PKG)

        val pi = key?.let { NotificationsListener.intents[it] }
        val opened = pi != null && send(pi)
        if (!opened && pkg != null) {
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(it) }
            }
        }
        finish()
    }

    private fun send(pi: PendingIntent): Boolean = try {
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14: явно разрешаем запуск activity из чужого PendingIntent — мы на переднем плане.
            val opts = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pi.send(this, 0, null, null, null, null, opts.toBundle())
        } else {
            pi.send()
        }
        true
    } catch (e: PendingIntent.CanceledException) {
        false
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_PKG = "pkg"
    }
}
