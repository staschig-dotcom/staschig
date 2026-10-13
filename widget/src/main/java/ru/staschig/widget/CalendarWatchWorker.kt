package ru.staschig.widget

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Перерисовывает виджет, когда меняется календарь (новое событие, синхронизация с Google).
 * Триггер по content URI срабатывает один раз, поэтому воркер переставляет себя заново.
 */
class CalendarWatchWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        Widgets.updateAll(applicationContext)
        enqueue(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val NAME = "calendar_watch"

        fun schedule(ctx: Context) {
            if (CalendarRepo.hasPermission(ctx)) enqueue(ctx, ExistingWorkPolicy.KEEP)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
        }

        private fun enqueue(ctx: Context, policy: ExistingWorkPolicy) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                .build()
            val req = OneTimeWorkRequestBuilder<CalendarWatchWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, policy, req)
        }
    }
}
