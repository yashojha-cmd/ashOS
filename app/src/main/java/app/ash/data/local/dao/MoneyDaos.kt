package app.ash.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import app.ash.data.local.entities.BillEntity
import app.ash.data.local.entities.MerchantEntity
import app.ash.data.local.entities.SubscriptionEntity
import app.ash.data.local.entities.TransactionEntity
import app.ash.domain.model.RecordStatus
import app.ash.domain.model.RecurrencePeriod
import app.ash.domain.model.TxnDirection
import kotlinx.coroutines.flow.Flow

data class TransactionRow(
    val recordId: Long,
    val amountPaise: Long,
    val direction: TxnDirection,
    val merchant: String?,
    val accountRef: String?,
    val occurredAt: Long,
    val categoryKey: String,
    val confidence: Float,
    val userCorrected: Boolean,
    val vehicleId: Long?,
    val sourceLabel: String?,
)

data class PaymentRow(val merchant: String, val amountPaise: Long, val occurredAt: Long)

data class DirectionTotal(val direction: TxnDirection, val total: Long)

data class BillRow(
    val recordId: Long,
    val biller: String,
    val amountPaise: Long?,
    val dueAt: Long,
    val paidAt: Long?,
    val recurrence: RecurrencePeriod?,
    val status: RecordStatus,
)

@Dao
interface TransactionDao {
    @Upsert
    suspend fun upsert(txn: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE recordId = :recordId")
    suspend fun get(recordId: Long): TransactionEntity?

    @Query(
        """SELECT t.recordId, t.amountPaise, t.direction, COALESCE(m.canonicalName, r.merchantName) AS merchant, t.accountRef,
                  t.occurredAt, r.categoryKey, r.confidence, t.userCorrected, t.vehicleId, r.sourceLabel
           FROM transactions t JOIN records r ON r.id = t.recordId LEFT JOIN merchants m ON m.id = t.merchantId
           WHERE r.deletedAt IS NULL ORDER BY t.occurredAt DESC LIMIT :limit""",
    )
    fun observeRecent(limit: Int): Flow<List<TransactionRow>>

    @Query(
        """SELECT t.recordId, t.amountPaise, t.direction, COALESCE(m.canonicalName, r.merchantName) AS merchant, t.accountRef,
                  t.occurredAt, r.categoryKey, r.confidence, t.userCorrected, t.vehicleId, r.sourceLabel
           FROM transactions t JOIN records r ON r.id = t.recordId LEFT JOIN merchants m ON m.id = t.merchantId
           WHERE r.deletedAt IS NULL AND t.vehicleId = :vehicleId ORDER BY t.occurredAt DESC""",
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<TransactionRow>>

    @Query(
        """SELECT COALESCE(m.canonicalName, r.merchantName) AS merchant, t.amountPaise, t.occurredAt
           FROM transactions t JOIN records r ON r.id = t.recordId LEFT JOIN merchants m ON m.id = t.merchantId
           WHERE r.deletedAt IS NULL AND t.direction = 'EXPENSE' AND t.occurredAt >= :since
           AND COALESCE(m.canonicalName, r.merchantName) IS NOT NULL""",
    )
    suspend fun expensesSince(since: Long): List<PaymentRow>

    @Query(
        """SELECT t.direction AS direction, SUM(t.amountPaise) AS total FROM transactions t JOIN records r ON r.id = t.recordId
           WHERE r.deletedAt IS NULL AND t.occurredAt >= :from AND t.occurredAt < :to GROUP BY t.direction""",
    )
    fun observeTotals(from: Long, to: Long): Flow<List<DirectionTotal>>
}

@Dao
interface BillDao {
    @Upsert
    suspend fun upsert(bill: BillEntity)

    @Query("SELECT * FROM bills WHERE recordId = :recordId")
    suspend fun get(recordId: Long): BillEntity?

    @Query(
        """SELECT b.recordId, b.biller, b.amountPaise, b.dueAt, b.paidAt, b.recurrence, r.status FROM bills b
           JOIN records r ON r.id = b.recordId WHERE r.deletedAt IS NULL ORDER BY b.paidAt IS NOT NULL, b.dueAt LIMIT :limit""",
    )
    fun observeAll(limit: Int): Flow<List<BillRow>>

    @Query("UPDATE bills SET paidAt = :paidAt WHERE recordId = :recordId")
    suspend fun markPaid(recordId: Long, paidAt: Long?)
}

@Dao
interface SubscriptionDao {
    @Insert
    suspend fun insert(sub: SubscriptionEntity): Long

    @Update
    suspend fun update(sub: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions WHERE deletedAt IS NULL ORDER BY active DESC, amountPaise DESC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: Long): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE deletedAt IS NULL AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): SubscriptionEntity?

    @Query("UPDATE subscriptions SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("DELETE FROM subscriptions WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long): Int
}

@Dao
interface MerchantDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(merchant: MerchantEntity): Long

    @Query("SELECT * FROM merchants WHERE LOWER(canonicalName) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): MerchantEntity?
}
