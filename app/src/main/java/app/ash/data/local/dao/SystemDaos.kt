package app.ash.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import app.ash.data.local.entities.AppSourceEntity
import app.ash.data.local.entities.AuditRecordEntity
import app.ash.data.local.entities.CategoryEntity
import app.ash.data.local.entities.DiagnosticCounterEntity
import app.ash.data.local.entities.NotificationRecordEntity
import app.ash.data.local.entities.ProfileEntity
import app.ash.data.local.entities.ReminderEntity
import app.ash.domain.model.ReminderState
import app.ash.domain.model.ReminderTarget
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE state IN ('SCHEDULED','SNOOZED','FIRED') ORDER BY fireAt LIMIT :limit")
    fun observeUpcoming(limit: Int): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE state IN ('SCHEDULED','SNOOZED') AND fireAt > :after")
    suspend fun pendingAfter(after: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE target = :target AND targetId = :targetId")
    suspend fun forTarget(target: ReminderTarget, targetId: Long): List<ReminderEntity>

    @Query("DELETE FROM reminders WHERE target = :target AND targetId = :targetId AND auto = 1 AND state IN ('SCHEDULED','SNOOZED')")
    suspend fun deletePendingAuto(target: ReminderTarget, targetId: Long): Int

    @Query("UPDATE reminders SET state = :state, updatedAt = :now WHERE id = :id")
    suspend fun updateState(id: Long, state: ReminderState, now: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM reminders WHERE state IN ('SCHEDULED','SNOOZED')")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id AND relation != 'SELF'")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM profiles ORDER BY relation != 'SELF', name")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY relation != 'SELF', name")
    suspend fun getAll(): List<ProfileEntity>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories ORDER BY sortOrder")
    suspend fun getAll(): List<CategoryEntity>
}

@Dao
interface AppSourceDao {
    @Upsert
    suspend fun upsert(source: AppSourceEntity)

    @Query("SELECT * FROM app_sources WHERE packageName = :packageName")
    suspend fun get(packageName: String): AppSourceEntity?

    @Query("SELECT * FROM app_sources ORDER BY enabled DESC, label")
    fun observeAll(): Flow<List<AppSourceEntity>>

    @Query("UPDATE app_sources SET enabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("UPDATE app_sources SET lastSeenAt = :now, notificationCount = notificationCount + 1 WHERE packageName = :packageName")
    suspend fun markSeen(packageName: String, now: Long)

    @Query("SELECT COUNT(*) FROM app_sources WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>
}

@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(record: NotificationRecordEntity): Long

    @Query("UPDATE notification_records SET recordId = :recordId WHERE id = :id")
    suspend fun linkRecord(id: Long, recordId: Long)

    @Query("SELECT COUNT(*) FROM notification_records WHERE packageName = :packageName AND contentHash = :hash AND postedAt >= :since")
    suspend fun countSimilar(packageName: String, hash: Int, since: Long): Int

    @Query("SELECT * FROM notification_records WHERE id = :id")
    suspend fun getById(id: Long): NotificationRecordEntity?

    @Query("SELECT COUNT(*) FROM notification_records")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM notification_records WHERE postedAt < :before AND recordId IS NULL")
    suspend fun deleteUnlinkedBefore(before: Long): Int
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(record: AuditRecordEntity)

    @Query("SELECT * FROM audit_records ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditRecordEntity>>

    @Query("DELETE FROM audit_records WHERE at < :before")
    suspend fun deleteBefore(before: Long)
}

@Dao
interface DiagnosticsDao {
    @Query("SELECT * FROM diagnostic_counters WHERE `key` = :key")
    suspend fun get(key: String): DiagnosticCounterEntity?

    @Upsert
    suspend fun put(counter: DiagnosticCounterEntity)

    @Transaction
    suspend fun increment(key: String, by: Long = 1) {
        put(DiagnosticCounterEntity(key, (get(key)?.value ?: 0) + by))
    }

    @Query("SELECT * FROM diagnostic_counters")
    fun observeAll(): Flow<List<DiagnosticCounterEntity>>
}

/** Row counts for the Privacy Center. */
@Dao
interface StatsDao {
    @Query("SELECT COUNT(*) FROM notification_records")
    fun notifications(): Flow<Int>

    @Query("SELECT COUNT(*) FROM records WHERE deletedAt IS NULL")
    fun records(): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE deletedAt IS NULL")
    fun documents(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions")
    fun transactions(): Flow<Int>

    @Query("SELECT COUNT(*) FROM reminders")
    fun reminders(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vehicles WHERE deletedAt IS NULL")
    fun vehicles(): Flow<Int>

    @Query("SELECT COUNT(*) FROM insurance_policies WHERE deletedAt IS NULL")
    fun policies(): Flow<Int>

    @Query("SELECT COUNT(*) FROM subscriptions WHERE deletedAt IS NULL")
    fun subscriptions(): Flow<Int>
}
