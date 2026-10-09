package app.ash.data.repository

import app.ash.common.TimeProvider
import app.ash.data.local.dao.AuditDao
import app.ash.data.local.dao.DiagnosticsDao
import app.ash.data.local.dao.StatsDao
import app.ash.data.local.entities.AuditAction
import app.ash.data.local.entities.AuditRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

object Counters {
    const val NOTIFICATIONS_PROCESSED = "notifications_processed"
    const val NOTIFICATIONS_SENSITIVE = "notifications_sensitive_skipped"
    const val NOTIFICATIONS_DUPLICATE = "notifications_duplicate"
    const val RECORDS_CREATED = "records_created"
    const val RECORDS_REVIEW = "records_review"
    const val PARSER_CONFIRMATIONS = "parser_confirmations"
    const val OCR_DOCUMENTS = "ocr_documents"
    const val ERRORS = "errors"
    const val REMINDERS_FIRED = "reminders_fired"
    const val BILLS_AUTO_PAID = "bills_auto_paid"
    const val DUPLICATES_MERGED = "transactions_merged"
}

data class StoredCounts(
    val notifications: Int = 0,
    val records: Int = 0,
    val documents: Int = 0,
    val transactions: Int = 0,
    val reminders: Int = 0,
    val vehicles: Int = 0,
    val policies: Int = 0,
    val subscriptions: Int = 0,
)

@Singleton
class DiagnosticsRepository @Inject constructor(
    private val diagnostics: DiagnosticsDao,
    private val audit: AuditDao,
    private val stats: StatsDao,
    private val time: TimeProvider,
) {
    val counters: Flow<Map<String, Long>> = diagnostics.observeAll().map { list -> list.associate { it.key to it.value } }

    val storedCounts: Flow<StoredCounts> = combine(
        combine(stats.notifications(), stats.records(), stats.documents(), stats.transactions()) { n, r, d, t -> listOf(n, r, d, t) },
        combine(stats.reminders(), stats.vehicles(), stats.policies(), stats.subscriptions()) { rem, v, p, s -> listOf(rem, v, p, s) },
    ) { a, b -> StoredCounts(a[0], a[1], a[2], a[3], b[0], b[1], b[2], b[3]) }

    val recentAudit = audit.observeRecent(100)

    suspend fun increment(key: String, by: Long = 1) = diagnostics.increment(key, by)

    suspend fun audit(action: AuditAction, entity: String, entityId: Long? = null) =
        audit.insert(AuditRecordEntity(at = time.nowMillis(), action = action, entity = entity, entityId = entityId))
}
