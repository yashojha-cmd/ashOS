package app.ash.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.ash.common.ApplicationScope
import app.ash.logging.AshLogger
import app.ash.workspace.Workspace
import app.ash.workspace.WorkspaceGraphs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Work scheduled before workspaces existed has no workspace extra; it belongs to the real account. */
private fun workspaceOf(id: String?): Workspace = Workspace.fromId(id) ?: Workspace.REAL

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: ReminderNotifier,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        if (id < 0) return Result.failure()
        notifier.fire(workspaceOf(inputData.getString(KEY_WORKSPACE)), id)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "reminder_id"
        const val KEY_WORKSPACE = "workspace"
    }
}

/** Receives exact alarms (only when the user opted in to exact reminders). */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var notifier: ReminderNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderWorker.KEY_ID, -1)
        if (id < 0) return
        val workspace = workspaceOf(intent.getStringExtra(ReminderWorker.KEY_WORKSPACE))
        val pending = goAsync()
        scope.launch {
            try {
                notifier.fire(workspace, id)
            } finally {
                pending.finish()
            }
        }
    }
}

/** "Done" and "Snooze 1 day" buttons on reminder notifications. */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var graphs: WorkspaceGraphs
    @Inject lateinit var notifier: ReminderNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderWorker.KEY_ID, -1)
        if (id < 0) return
        val workspace = workspaceOf(intent.getStringExtra(ReminderWorker.KEY_WORKSPACE))
        notifier.dismiss(workspace, id)
        val pending = goAsync()
        scope.launch {
            try {
                val reminders = graphs.get(workspace).reminders
                when (intent.action) {
                    ACTION_DONE -> reminders.markDone(id)
                    ACTION_SNOOZE -> reminders.snooze(id, System.currentTimeMillis() + 24L * 60 * 60 * 1000)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "app.ash.reminder.DONE"
        const val ACTION_SNOOZE = "app.ash.reminder.SNOOZE"
    }
}

/** Exact alarms are wiped on reboot; WorkManager work survives. Re-register everything, in every workspace. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var graphs: WorkspaceGraphs
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        ) return
        val pending = goAsync()
        scope.launch {
            try {
                for (workspace in Workspace.entries) {
                    if (graphs.exists(workspace)) graphs.get(workspace).reminders.rescheduleAll()
                }
                AshLogger.i("Boot", "Reminders rescheduled after ${intent.action}")
            } finally {
                pending.finish()
            }
        }
    }
}
