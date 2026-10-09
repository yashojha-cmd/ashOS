package app.ash.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.ash.MainActivity
import app.ash.R
import app.ash.data.local.database.WorkspaceDatabases
import app.ash.data.repository.SettingsRepository
import app.ash.domain.model.ReminderState
import app.ash.logging.AshLogger
import app.ash.workspace.Workspace
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Posts a due reminder as a local notification. No push service is involved. */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databases: WorkspaceDatabases,
    private val settings: SettingsRepository,
) {
    private val amount = Regex("""(?i)(?:₹|\bRs\.?|\bINR)\s?[\d,]+(?:\.\d{1,2})?""")

    suspend fun fire(workspace: Workspace, reminderId: Long) {
        val db = databases.get(workspace)
        val reminderDao = db.reminderDao()
        val reminder = reminderDao.getById(reminderId) ?: return
        if (reminder.state != ReminderState.SCHEDULED && reminder.state != ReminderState.SNOOZED) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            AshLogger.w(TAG, "Reminder ${workspace.id}/$reminderId due but notifications are disabled; shown in app only")
            reminderDao.updateState(reminderId, ReminderState.FIRED, System.currentTimeMillis())
            return
        }
        val hideAmounts = settings.current().hideAmountsInNotifications
        val body = reminder.body?.let { if (hideAmounts) amount.replace(it, "₹•••") else it }
        val title = if (workspace == Workspace.DEMO) "${reminder.title} (demo)" else reminder.title

        val code = ReminderScheduler.requestCode(workspace, reminderId)
        val open = PendingIntent.getActivity(
            context,
            code,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_REMINDERS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_ash)
            .setContentTitle(context.getString(R.string.reminder_public_title))
            .build()

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_ash)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.action_done), actionIntent(workspace, reminderId, ReminderActionReceiver.ACTION_DONE))
            .addAction(0, context.getString(R.string.action_snooze_day), actionIntent(workspace, reminderId, ReminderActionReceiver.ACTION_SNOOZE))
            .build()
        try {
            manager.notify(notificationId(workspace, reminderId), notification)
            reminderDao.updateState(reminderId, ReminderState.FIRED, System.currentTimeMillis())
            db.diagnosticsDao().increment("reminders_fired")
        } catch (e: SecurityException) {
            AshLogger.e(TAG, "POST_NOTIFICATIONS not granted", e)
        }
    }

    fun dismiss(workspace: Workspace, reminderId: Long) =
        NotificationManagerCompat.from(context).cancel(notificationId(workspace, reminderId))

    private fun actionIntent(workspace: Workspace, reminderId: Long, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ReminderScheduler.requestCode(workspace, reminderId) * 2 + if (action == ReminderActionReceiver.ACTION_DONE) 0 else 1,
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderWorker.KEY_ID, reminderId)
                .putExtra(ReminderWorker.KEY_WORKSPACE, workspace.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationId(workspace: Workspace, reminderId: Long) = 10_000 + ReminderScheduler.requestCode(workspace, reminderId)

    private companion object {
        const val TAG = "ReminderNotifier"
    }
}
