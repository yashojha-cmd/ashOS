package app.ash.data.repository

import android.content.Context
import app.ash.R
import app.ash.common.Money
import app.ash.common.TimeProvider
import app.ash.data.local.dao.ReminderDao
import app.ash.data.local.entities.ReminderEntity
import app.ash.domain.model.Reminder
import app.ash.domain.model.ReminderState
import app.ash.domain.model.ReminderTarget
import app.ash.domain.repository.ReminderRepository
import app.ash.reminder.ReminderPlanner
import app.ash.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ReminderRepository {

    override fun observeUpcoming(limit: Int): Flow<List<Reminder>> = dao.observeUpcoming(limit).map { list -> list.map { it.toDomain() } }

    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    override suspend fun get(id: Long): Reminder? = dao.getById(id)?.toDomain()

    override suspend fun save(reminder: Reminder): Long {
        val now = time.nowMillis()
        val entity = ReminderEntity(
            id = reminder.id, title = reminder.title, body = reminder.body, fireAt = reminder.fireAt,
            target = reminder.target, targetId = reminder.targetId, leadDays = reminder.leadDays,
            state = ReminderState.SCHEDULED, auto = reminder.auto, createdAt = now, updatedAt = now,
        )
        val id = if (reminder.id == 0L) dao.insert(entity) else entity.id.also { dao.update(entity) }
        scheduler.schedule(id, reminder.fireAt, settings.current().exactReminders)
        return id
    }

    override suspend fun delete(id: Long) {
        scheduler.cancel(id)
        dao.delete(id)
    }

    override suspend fun markDone(id: Long) {
        scheduler.cancel(id)
        dao.updateState(id, ReminderState.DONE, time.nowMillis())
    }

    override suspend fun snooze(id: Long, untilMillis: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(fireAt = untilMillis, state = ReminderState.SNOOZED, updatedAt = time.nowMillis()))
        scheduler.schedule(id, untilMillis, settings.current().exactReminders)
    }

    override suspend fun rescheduleAll() {
        val exact = settings.current().exactReminders
        val now = time.nowMillis()
        for (reminder in dao.pendingAfter(now - 60_000)) scheduler.schedule(reminder.id, reminder.fireAt, exact)
    }

    /** Replaces pending automatic reminders for a target with a fresh plan. Idempotent. */
    suspend fun planAuto(
        target: ReminderTarget,
        targetId: Long,
        kind: ReminderPlanner.Kind,
        deadlineAt: Long?,
        title: String,
        bodyFor: (leadDays: Int) -> String,
    ) {
        for (existing in dao.forTarget(target, targetId)) {
            if (existing.auto && (existing.state == ReminderState.SCHEDULED || existing.state == ReminderState.SNOOZED)) {
                scheduler.cancel(existing.id)
            }
        }
        dao.deletePendingAuto(target, targetId)
        if (deadlineAt == null) return
        val now = time.nowMillis()
        val exact = settings.current().exactReminders
        for (planned in ReminderPlanner.plan(deadlineAt, kind, now, time.zone())) {
            val id = dao.insert(
                ReminderEntity(
                    title = title, body = bodyFor(planned.leadDays), fireAt = planned.fireAt, target = target,
                    targetId = targetId, leadDays = planned.leadDays, state = ReminderState.SCHEDULED, auto = true,
                    createdAt = now, updatedAt = now,
                ),
            )
            scheduler.schedule(id, planned.fireAt, exact)
        }
    }

    suspend fun cancelForTarget(target: ReminderTarget, targetId: Long) {
        for (existing in dao.forTarget(target, targetId)) {
            scheduler.cancel(existing.id)
            dao.delete(existing.id)
        }
    }
}

/** Localised wording for automatic reminders. */
@Singleton
class ReminderText @Inject constructor(@ApplicationContext private val context: Context) {
    fun due(leadDays: Int, amountPaise: Long?): String {
        val whenText = relative(leadDays, R.string.reminder_due_today, R.plurals.reminder_due_in_days)
        return amountPaise?.let { "$whenText · ${Money.format(it)}" } ?: whenText
    }

    fun expires(leadDays: Int): String = relative(leadDays, R.string.reminder_expires_today, R.plurals.reminder_expires_in_days)

    fun renews(leadDays: Int, amountPaise: Long?): String {
        val whenText = relative(leadDays, R.string.reminder_renews_today, R.plurals.reminder_renews_in_days)
        return amountPaise?.let { "$whenText · ${Money.format(it)}" } ?: whenText
    }

    fun event(leadDays: Int): String = relative(leadDays, R.string.reminder_event_today, R.plurals.reminder_event_in_days)

    fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun relative(leadDays: Int, todayRes: Int, pluralRes: Int): String =
        if (leadDays <= 0) context.getString(todayRes) else context.resources.getQuantityString(pluralRes, leadDays, leadDays)
}
