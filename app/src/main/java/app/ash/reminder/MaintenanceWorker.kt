package app.ash.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ash.common.TimeProvider
import app.ash.data.repository.SettingsRepository
import app.ash.logging.AshLogger
import app.ash.workspace.Workspace
import app.ash.workspace.WorkspaceGraphs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Once a day, while the battery is not low: purge soft-deleted rows older than 30 days, drop
 * unmatched notification logs after 90 days, detect recurring payments, roll renewals forward,
 * and switch off detailed parser logging once it expires. Runs for every workspace that exists.
 * No network constraint is ever used.
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val graphs: WorkspaceGraphs,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val now = time.nowMillis()
        val purgeBefore = now - 30 * DAY
        for (workspace in Workspace.entries) {
            if (!graphs.exists(workspace)) continue
            val graph = graphs.get(workspace)
            val db = graph.db
            val purged = db.recordDao().purgeDeleted(purgeBefore) + db.subscriptionDao().purgeDeleted(purgeBefore) +
                db.vehicleDao().purgeDeleted(purgeBefore) + db.policyDao().purgeDeleted(purgeBefore) + db.warrantyDao().purgeDeleted(purgeBefore)
            val dropped = db.notificationDao().deleteUnlinkedBefore(now - 90 * DAY)
            db.auditDao().deleteBefore(now - 365 * DAY)
            val detected = graph.life.detectSubscriptions()
            val rolled = graph.life.rollRenewals()
            AshLogger.i(TAG, "Maintenance ${workspace.id}: purged=$purged droppedNotifications=$dropped subscriptionsDetected=$detected renewalsRolled=$rolled")
        }
        val s = settings.current()
        if (s.detailedLogsUntil in 1 until now) {
            settings.setDetailedLogsUntil(0)
            AshLogger.detailedParserLogging = false
        }
        settings.setLastMaintenance(now)
        Result.success()
    } catch (e: Exception) {
        AshLogger.e(TAG, "Maintenance failed", e)
        Result.retry()
    }

    companion object {
        private const val TAG = "Maintenance"
        private const val DAY = 24L * 60 * 60 * 1000

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .setConstraints(androidx.work.Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("ash-maintenance", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
