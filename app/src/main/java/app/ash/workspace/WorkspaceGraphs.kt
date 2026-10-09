package app.ash.workspace

import android.content.Context
import app.ash.common.TimeProvider
import app.ash.data.local.database.AshDatabase
import app.ash.data.local.database.WorkspaceDatabases
import app.ash.data.repository.DiagnosticsRepository
import app.ash.data.repository.LifeRepositoryImpl
import app.ash.data.repository.RecordRepositoryImpl
import app.ash.data.repository.ReminderRepositoryImpl
import app.ash.data.repository.ReminderText
import app.ash.data.repository.SettingsRepository
import app.ash.data.repository.SourceRepositoryImpl
import app.ash.intelligence.IntelligenceEngine
import app.ash.notification.NotificationIngestor
import app.ash.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The data-layer object graph for one workspace, built explicitly against that workspace's
 * database. Background work (notification capture, SMS import, reminder delivery, maintenance)
 * uses this to target a workspace regardless of which one the UI is showing.
 */
class WorkspaceGraph internal constructor(
    val workspace: Workspace,
    val db: AshDatabase,
    context: Context,
    scheduler: ReminderScheduler,
    settings: SettingsRepository,
    reminderText: ReminderText,
    engine: IntelligenceEngine,
    time: TimeProvider,
) {
    val reminders = ReminderRepositoryImpl(db.reminderDao(), scheduler, settings, time, workspace)
    val diagnostics = DiagnosticsRepository(db.diagnosticsDao(), db.auditDao(), db.statsDao(), time)
    val life = LifeRepositoryImpl(
        db.vehicleDao(), db.policyDao(), db.warrantyDao(), db.subscriptionDao(), db.transactionDao(), db.documentDao(),
        reminders, reminderText, diagnostics, time,
    )
    val records = RecordRepositoryImpl(
        db, db.recordDao(), db.transactionDao(), db.billDao(), db.deliveryDao(), db.tripDao(), db.merchantDao(),
        life, reminders, reminderText, diagnostics, time,
    )
    val sources = SourceRepositoryImpl(context, db.appSourceDao(), time)
    val ingestor = NotificationIngestor(sources, db.notificationDao(), records, engine, settings, diagnostics, time)
}

@Singleton
class WorkspaceGraphs @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databases: WorkspaceDatabases,
    private val scheduler: ReminderScheduler,
    private val settings: SettingsRepository,
    private val reminderText: ReminderText,
    private val engine: IntelligenceEngine,
    private val time: TimeProvider,
) {
    private val graphs = ConcurrentHashMap<Workspace, WorkspaceGraph>()

    fun get(workspace: Workspace): WorkspaceGraph = graphs.getOrPut(workspace) {
        WorkspaceGraph(workspace, databases.get(workspace), context, scheduler, settings, reminderText, engine, time)
    }

    /** Live notifications and SMS import always belong to the real account, never the demo. */
    val real: WorkspaceGraph get() = get(Workspace.REAL)

    fun exists(workspace: Workspace) = databases.exists(workspace)
}
