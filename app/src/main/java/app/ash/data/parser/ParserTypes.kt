package app.ash.data.parser

import app.ash.domain.model.Category
import app.ash.domain.model.DeliveryState
import app.ash.domain.model.Importance
import app.ash.domain.model.InsuranceKind
import app.ash.domain.model.RecurrencePeriod
import app.ash.domain.model.RecordType
import app.ash.domain.model.SourceKind
import app.ash.domain.model.TripMode
import app.ash.domain.model.TxnDirection
import java.time.LocalDate
import java.time.ZoneId

/** A notification as seen by the parsers. Built from a StatusBarNotification, or from fixtures in tests. */
data class RawNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val sourceKind: SourceKind = SourceKind.OTHER,
    val sourceLabel: String = packageName,
) {
    /** Title and body joined; most Indian bank SMS put the sender in the title. */
    val fullText: String = listOf(title, text).filter { it.isNotBlank() }.joinToString(". ")
    val lower: String = fullText.lowercase()
}

data class ParseContext(
    val today: LocalDate,
    val zone: ZoneId,
    val nowMillis: Long,
)

data class ParsedSignal(
    val parserId: String,
    val type: RecordType,
    val category: Category,
    val title: String,
    val summary: String,
    val confidence: Float,
    val amountPaise: Long? = null,
    val direction: TxnDirection? = null,
    val merchant: String? = null,
    val accountRef: String? = null,
    val dueAt: Long? = null,
    val eventAt: Long? = null,
    val expiresAt: Long? = null,
    val reference: String? = null,
    val importance: Importance = Importance.MEDIUM,
    val deliveryState: DeliveryState? = null,
    val tripMode: TripMode? = null,
    val tripFrom: String? = null,
    val tripTo: String? = null,
    val recurrence: RecurrencePeriod? = null,
    val insuranceKind: InsuranceKind? = null,
    val vehicleReg: String? = null,
    val actionRequired: Boolean = false,
    val alreadyHandled: Boolean = false,
)

sealed interface ParseOutcome {
    data class Parsed(val signal: ParsedSignal) : ParseOutcome
    /** OTPs, passwords and similar: ashOS never stores these, not even the raw notification. */
    data object Sensitive : ParseOutcome
    data object NoMatch : ParseOutcome
}

interface NotificationParser {
    val id: String
    fun parse(n: RawNotification, ctx: ParseContext): ParsedSignal?
}

/** Additive, explainable confidence: each piece of evidence adds or removes a fixed weight. */
class Evidence {
    private var score = 0f
    private val reasons = mutableListOf<String>()

    fun add(points: Float, reason: String, condition: Boolean = true): Evidence {
        if (condition) {
            score += points
            reasons += "$reason(${if (points >= 0) "+" else ""}$points)"
        }
        return this
    }

    val value: Float get() = score.coerceIn(0f, 0.99f)
    val explanation: String get() = reasons.joinToString(" ")
}

object ConfidencePolicy {
    /** At or above: record is created automatically. */
    const val AUTO = 0.85f
    /** Between REVIEW and AUTO: "ashOS found something that may be important. Please confirm." */
    const val REVIEW = 0.5f
}
