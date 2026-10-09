package app.ash.notification

import app.ash.common.TimeProvider
import app.ash.data.local.dao.NotificationDao
import app.ash.data.local.entities.NotificationOutcome
import app.ash.data.local.entities.NotificationRecordEntity
import app.ash.data.parser.ConfidencePolicy
import app.ash.data.parser.ParseContext
import app.ash.data.parser.ParseOutcome
import app.ash.data.parser.RawNotification
import app.ash.data.repository.Counters
import app.ash.data.repository.DiagnosticsRepository
import app.ash.data.repository.RecordRepositoryImpl
import app.ash.data.repository.SettingsRepository
import app.ash.data.repository.SourceRepositoryImpl
import app.ash.intelligence.IntelligenceEngine
import app.ash.logging.AshLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The notification pipeline (§7): source allow-list → sensitive filter → de-duplication →
 * rule-based parsing → record creation by confidence band. Runs off the main thread.
 */
@Singleton
class NotificationIngestor @Inject constructor(
    private val sources: SourceRepositoryImpl,
    private val notifications: NotificationDao,
    private val records: RecordRepositoryImpl,
    private val engine: IntelligenceEngine,
    private val settings: SettingsRepository,
    private val diagnostics: DiagnosticsRepository,
    private val time: TimeProvider,
) {
    enum class Result { SOURCE_DISABLED, SENSITIVE, DUPLICATE, NO_MATCH, LOW_CONFIDENCE, REVIEW, CREATED }

    // Serialise ingestion so duplicate checks and merges never race.
    private val mutex = Mutex()

    /**
     * @param historical true for SMS history import: past deadlines are filed as handled, and uncertain
     *   finds are only logged (an old backlog should not flood the review queue).
     */
    suspend fun ingest(
        packageName: String,
        notificationKey: String?,
        title: String,
        text: String,
        postedAt: Long,
        historical: Boolean = false,
    ): Result = mutex.withLock {
        try {
            val source = sources.recordSeen(packageName)
            if (!source.enabled) return Result.SOURCE_DISABLED

            val raw = RawNotification(packageName, title.trim(), text.trim(), postedAt, source.kind, source.label)
            if (raw.fullText.isBlank()) return Result.NO_MATCH
            diagnostics.increment(Counters.NOTIFICATIONS_PROCESSED)

            val ctx = ParseContext(time.today(), time.zone(), time.nowMillis())
            val outcome = engine.classifyNotification(raw, ctx)
            if (outcome is ParseOutcome.Sensitive) {
                diagnostics.increment(Counters.NOTIFICATIONS_SENSITIVE)
                AshLogger.d(TAG, "Skipped sensitive notification from $packageName")
                return Result.SENSITIVE
            }

            // Body text only, so an SMS seen live (titled with the contact name) and imported later matches.
            val hash = raw.text.ifBlank { raw.fullText }.hashCode()
            if (notifications.countSimilar(packageName, hash, postedAt - DUPLICATE_WINDOW_MS) > 0) {
                diagnostics.increment(Counters.NOTIFICATIONS_DUPLICATE)
                return Result.DUPLICATE
            }

            val storeText = settings.current().storeOriginalText
            val signal = (outcome as? ParseOutcome.Parsed)?.signal
            val notificationOutcome = when {
                signal == null || signal.confidence < ConfidencePolicy.REVIEW -> NotificationOutcome.LOW_CONFIDENCE
                signal.confidence < ConfidencePolicy.AUTO -> NotificationOutcome.REVIEW
                else -> NotificationOutcome.RECORD_CREATED
            }
            val notificationId = notifications.insert(
                NotificationRecordEntity(
                    packageName = packageName,
                    notificationKey = notificationKey,
                    postedAt = postedAt,
                    title = if (storeText) raw.title else null,
                    text = if (storeText) raw.text else null,
                    contentHash = hash,
                    parserId = signal?.parserId,
                    confidence = signal?.confidence,
                    outcome = notificationOutcome,
                    recordId = null,
                    createdAt = time.nowMillis(),
                ),
            )
            AshLogger.sensitive(TAG, "Parsed $packageName -> ${signal?.parserId}:${signal?.confidence} | ${raw.fullText}")

            if (signal == null || notificationOutcome == NotificationOutcome.LOW_CONFIDENCE) return Result.LOW_CONFIDENCE
            if (historical && notificationOutcome == NotificationOutcome.REVIEW) return Result.LOW_CONFIDENCE

            val (recordId, _) = records.createFromSignal(
                signal = signal,
                notificationId = notificationId,
                sourcePackage = packageName,
                sourceLabel = source.label,
                originalText = if (storeText) raw.fullText else null,
                postedAt = postedAt,
                historical = historical,
            )
            notifications.linkRecord(notificationId, recordId)
            return if (notificationOutcome == NotificationOutcome.REVIEW) {
                diagnostics.increment(Counters.RECORDS_REVIEW)
                Result.REVIEW
            } else {
                diagnostics.increment(Counters.RECORDS_CREATED)
                Result.CREATED
            }
        } catch (e: Exception) {
            diagnostics.increment(Counters.ERRORS)
            AshLogger.e(TAG, "Failed to ingest notification from $packageName", e)
            Result.NO_MATCH
        }
    }

    private companion object {
        const val TAG = "Ingest"
        const val DUPLICATE_WINDOW_MS = 24L * 60 * 60 * 1000
    }
}
