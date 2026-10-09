package app.ash.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.ash.logging.AshLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules reminder delivery entirely on-device.
 *
 * Default: WorkManager one-time work (survives reboot, battery friendly, may be deferred a few
 * minutes by Doze). If the user enabled exact reminders and Android allows it, an exact alarm is
 * used instead.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val alarmManager get() = context.getSystemService<AlarmManager>()

    fun canUseExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    fun schedule(reminderId: Long, fireAt: Long, exact: Boolean) {
        cancel(reminderId)
        val delay = (fireAt - System.currentTimeMillis()).coerceAtLeast(0)
        if (exact && canUseExactAlarms()) {
            try {
                alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, alarmIntent(reminderId))
                return
            } catch (e: SecurityException) {
                AshLogger.w(TAG, "Exact alarm refused, falling back to WorkManager", e)
            }
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_ID to reminderId))
            .addTag(TAG_REMINDER)
            .build()
        workManager.enqueueUniqueWork(workName(reminderId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(reminderId: Long) {
        workManager.cancelUniqueWork(workName(reminderId))
        alarmManager?.cancel(alarmIntent(reminderId))
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG_REMINDER)
    }

    private fun alarmIntent(reminderId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            Intent(context, ReminderAlarmReceiver::class.java).putExtra(ReminderWorker.KEY_ID, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun workName(id: Long) = "reminder-$id"

    private companion object {
        const val TAG = "ReminderScheduler"
        const val TAG_REMINDER = "ash-reminder"
    }
}
