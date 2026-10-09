package app.ash.data.parser.parsers

import app.ash.data.parser.Evidence
import app.ash.data.parser.NotificationParser
import app.ash.data.parser.ParseContext
import app.ash.data.parser.ParsedSignal
import app.ash.data.parser.RawNotification
import app.ash.data.parser.defaultCategory
import app.ash.data.parser.dictionary.Keywords
import app.ash.data.parser.extract.AmountExtractor
import app.ash.data.parser.extract.DateExtractor
import app.ash.data.parser.senderName
import app.ash.data.parser.shortSummary
import app.ash.data.parser.toDeadline
import app.ash.domain.model.Importance
import app.ash.domain.model.RecordType
import app.ash.domain.model.SourceKind

/**
 * Fallback for enabled sources when no specialised parser matched. Deliberately capped below
 * the auto threshold: at best it asks the user to confirm.
 */
class GenericParser : NotificationParser {
    override val id = "generic"

    private val actionWords = Regex("""(?i)\b(reminder|appointment|scheduled|deadline|expires?|renew|due|action required|verify|complete your)\b""")

    override fun parse(n: RawNotification, ctx: ParseContext): ParsedSignal? {
        val text = n.fullText
        val amount = AmountExtractor.primary(text)
        val date = DateExtractor.first(text, ctx)
        val action = actionWords.containsMatchIn(text)
        if (amount == null && date == null && !action) return null
        val evidence = Evidence()
            .add(0.25f, "base")
            .add(0.1f, "amount", amount != null)
            .add(0.1f, "date", date != null)
            .add(0.1f, "action-word", action)
            .add(0.05f, "known-source", n.sourceKind != SourceKind.OTHER)
            .add(-0.3f, "promo", Keywords.PROMO.containsMatchIn(text))
        return ParsedSignal(
            parserId = id,
            type = if (action && date != null) RecordType.APPOINTMENT else RecordType.GENERIC,
            category = n.sourceKind.defaultCategory(),
            title = n.senderName(),
            summary = n.shortSummary(),
            confidence = evidence.value.coerceAtMost(0.6f),
            amountPaise = amount,
            dueAt = date?.toDeadline(ctx),
            importance = Importance.LOW,
        )
    }
}
