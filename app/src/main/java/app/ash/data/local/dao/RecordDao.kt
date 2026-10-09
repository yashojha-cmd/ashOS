package app.ash.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.ash.data.local.entities.RecordEntity
import app.ash.domain.model.RecordStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert
    suspend fun insert(record: RecordEntity): Long

    @Update
    suspend fun update(record: RecordEntity)

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getById(id: Long): RecordEntity?

    @Query("SELECT * FROM records WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: Long): Flow<RecordEntity?>

    /** Open items plus anything handled recently; this feeds the Home priority engine. */
    @Query(
        """SELECT * FROM records WHERE deletedAt IS NULL
           AND (status IN ('UNREAD','REVIEW','ACTION_REQUIRED') OR updatedAt >= :handledSince)
           ORDER BY createdAt DESC LIMIT 400""",
    )
    fun observeForAttention(handledSince: Long): Flow<List<RecordEntity>>

    @Query(
        """SELECT * FROM records WHERE deletedAt IS NULL AND status IN (:statuses)
           AND (type != 'TRANSACTION' OR status = 'REVIEW') ORDER BY createdAt DESC LIMIT :limit""",
    )
    fun observeByStatus(statuses: List<RecordStatus>, limit: Int): Flow<List<RecordEntity>>

    @Query(
        """SELECT * FROM records WHERE deletedAt IS NULL AND status IN (:statuses) AND categoryKey = :category
           AND (type != 'TRANSACTION' OR status = 'REVIEW')
           ORDER BY createdAt DESC LIMIT :limit""",
    )
    fun observeByStatusAndCategory(statuses: List<RecordStatus>, category: String, limit: Int): Flow<List<RecordEntity>>

    @Query("SELECT COUNT(*) FROM records WHERE deletedAt IS NULL AND status = :status")
    fun observeCount(status: RecordStatus): Flow<Int>

    @Query("UPDATE records SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: RecordStatus, now: Long)

    @Query("UPDATE records SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("DELETE FROM records WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long): Int

    @Query(
        """SELECT records.* FROM records JOIN records_fts ON records.id = records_fts.rowid
           WHERE records_fts MATCH :match AND records.deletedAt IS NULL ORDER BY records.createdAt DESC LIMIT 200""",
    )
    suspend fun search(match: String): List<RecordEntity>

    @Query("SELECT * FROM records WHERE deletedAt IS NULL AND amountPaise BETWEEN :min AND :max ORDER BY createdAt DESC LIMIT 200")
    suspend fun byAmount(min: Long, max: Long): List<RecordEntity>

    @Query(
        """SELECT * FROM records WHERE deletedAt IS NULL
           AND COALESCE(eventAt, dueAt, expiresAt, createdAt) >= :from AND COALESCE(eventAt, dueAt, expiresAt, createdAt) < :to
           ORDER BY COALESCE(eventAt, dueAt, expiresAt, createdAt) DESC LIMIT 300""",
    )
    suspend fun byDateRange(from: Long, to: Long): List<RecordEntity>

    @Query("SELECT * FROM records WHERE deletedAt IS NULL AND categoryKey IN (:categories) ORDER BY createdAt DESC LIMIT 200")
    suspend fun byCategories(categories: List<String>): List<RecordEntity>

    @Query(
        """SELECT * FROM records WHERE deletedAt IS NULL AND status NOT IN ('HANDLED','ARCHIVED')
           AND COALESCE(dueAt, expiresAt) IS NOT NULL AND COALESCE(dueAt, expiresAt) < :before ORDER BY COALESCE(dueAt, expiresAt)""",
    )
    suspend fun openWithDeadlineBefore(before: Long): List<RecordEntity>

    @Query("SELECT COUNT(*) FROM records WHERE deletedAt IS NULL")
    fun observeTotal(): Flow<Int>

    @Query(
        """SELECT * FROM records WHERE deletedAt IS NULL AND type = :type AND LOWER(title) = LOWER(:title)
           AND status NOT IN ('HANDLED','ARCHIVED') AND createdAt >= :since""",
    )
    suspend fun openByTypeAndTitle(type: app.ash.domain.model.RecordType, title: String, since: Long): List<RecordEntity>
}
