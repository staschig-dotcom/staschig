package ru.staschig.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/** Настройки MIUI, без которых сервис уведомлений и фоновое обновление живут недолго. */
object Xiaomi {
    val isXiaomi: Boolean
        get() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("Redmi", ignoreCase = true) || Build.BRAND.equals("POCO", ignoreCase = true)

    fun openAutostart(ctx: Context) {
        val intent = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
            Toast.makeText(ctx, "Найдите «${ctx.getString(R.string.app_name)}» и включите", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            openAppDetails(ctx)
        }
    }

    /** В карточке приложения MIUI: «Контроль активности» → «Нет ограничений», там же «Автозапуск». */
    fun openAppDetails(ctx: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
        Toast.makeText(ctx, "Контроль активности → Нет ограничений", Toast.LENGTH_LONG).show()
    }
}
