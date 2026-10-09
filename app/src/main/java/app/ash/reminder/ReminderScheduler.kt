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
import app.ash.workspace.Workspace
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules reminder delivery entirely on-device.
 *
 * Default: WorkManager one-time work (survives reboot, battery friendly, may be deferred a few
 * minutes by Doze). If the user enabled exact reminders and Android allows it, an exact alarm is
 * used instead. Work names, alarm codes and inputs carry the workspace, because each workspace
 * has its own reminder IDs.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val alarmManager get() = context.getSystemService<AlarmManager>()

    fun canUseExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    fun schedule(workspace: Workspace, reminderId: Long, fireAt: Long, exact: Boolean) {
        cancel(workspace, reminderId)
        val delay = (fireAt - System.currentTimeMillis()).coerceAtLeast(0)
        if (exact && canUseExactAlarms()) {
            try {
                alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, alarmIntent(workspace, reminderId))
                return
            } catch (e: SecurityException) {
                AshLogger.w(TAG, "Exact alarm refused, falling back to WorkManager", e)
            }
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_ID to reminderId, ReminderWorker.KEY_WORKSPACE to workspace.id))
            .addTag(tag(workspace))
            .build()
        workManager.enqueueUniqueWork(workName(workspace, reminderId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(workspace: Workspace, reminderId: Long) {
        workManager.cancelUniqueWork(workName(workspace, reminderId))
        alarmManager?.cancel(alarmIntent(workspace, reminderId))
    }

    /** Cancels queued work for one workspace (exact alarms are ignored once their reminder is gone). */
    fun cancelAll(workspace: Workspace) {
        workManager.cancelAllWorkByTag(tag(workspace))
    }

    private fun alarmIntent(workspace: Workspace, reminderId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(workspace, reminderId),
            Intent(context, ReminderAlarmReceiver::class.java)
                .putExtra(ReminderWorker.KEY_ID, reminderId)
                .putExtra(ReminderWorker.KEY_WORKSPACE, workspace.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun workName(workspace: Workspace, id: Long) = "reminder-${workspace.id}-$id"
    private fun tag(workspace: Workspace) = "ash-reminder-${workspace.id}"

    companion object {
        private const val TAG = "ReminderScheduler"

        fun requestCode(workspace: Workspace, reminderId: Long): Int = (reminderId * 2 + workspace.ordinal).toInt()
    }
}
