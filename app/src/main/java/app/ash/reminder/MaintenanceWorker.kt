package app.ash.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ash.common.TimeProvider
import app.ash.data.local.dao.AuditDao
import app.ash.data.local.dao.NotificationDao
import app.ash.data.local.dao.PolicyDao
import app.ash.data.local.dao.RecordDao
import app.ash.data.local.dao.SubscriptionDao
import app.ash.data.local.dao.VehicleDao
import app.ash.data.local.dao.WarrantyDao
import app.ash.data.repository.SettingsRepository
import app.ash.domain.repository.LifeRepository
import app.ash.logging.AshLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Once a day, while idle and charging-agnostic: purge soft-deleted rows older than 30 days,
 * drop unmatched notification logs after 90 days, detect recurring payments, and switch off
 * detailed parser logging once it expires. No network constraint is ever used.
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val records: RecordDao,
    private val subscriptions: SubscriptionDao,
    private val vehicles: VehicleDao,
    private val policies: PolicyDao,
    private val warranties: WarrantyDao,
    private val notifications: NotificationDao,
    private val audit: AuditDao,
    private val life: LifeRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val now = time.nowMillis()
        val purgeBefore = now - 30 * DAY
        val purged = records.purgeDeleted(purgeBefore) + subscriptions.purgeDeleted(purgeBefore) +
            vehicles.purgeDeleted(purgeBefore) + policies.purgeDeleted(purgeBefore) + warranties.purgeDeleted(purgeBefore)
        val dropped = notifications.deleteUnlinkedBefore(now - 90 * DAY)
        audit.deleteBefore(now - 365 * DAY)
        val detected = life.detectSubscriptions()
        val s = settings.current()
        if (s.detailedLogsUntil in 1 until now) {
            settings.setDetailedLogsUntil(0)
            AshLogger.detailedParserLogging = false
        }
        settings.setLastMaintenance(now)
        AshLogger.i(TAG, "Maintenance: purged=$purged droppedNotifications=$dropped subscriptionsDetected=$detected")
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
