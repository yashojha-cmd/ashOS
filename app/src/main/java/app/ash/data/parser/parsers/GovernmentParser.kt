package app.ash.data.parser.parsers

import app.ash.data.parser.Evidence
import app.ash.data.parser.NotificationParser
import app.ash.data.parser.ParseContext
import app.ash.data.parser.ParsedSignal
import app.ash.data.parser.RawNotification
import app.ash.data.parser.dictionary.Keywords
import app.ash.data.parser.extract.AmountExtractor
import app.ash.data.parser.extract.DateExtractor
import app.ash.data.parser.extract.ReferenceExtractor
import app.ash.data.parser.senderName
import app.ash.data.parser.shortSummary
import app.ash.data.parser.toDeadline
import app.ash.domain.model.Category
import app.ash.domain.model.Importance
import app.ash.domain.model.RecordType
import app.ash.domain.model.SourceKind

/** Income tax, Aadhaar/PAN, passport, DigiLocker, EPFO and similar government updates. */
class GovernmentParser : NotificationParser {
    override val id = "government"

    private val deadline = Regex("""(?i)(last date|due|before|by|deadline|expir\w*|valid till)""")

    override fun parse(n: RawNotification, ctx: ParseContext): ParsedSignal? {
        val text = n.fullText
        if (!Keywords.GOVERNMENT.containsMatchIn(text)) return null
        val due = DateExtractor.near(text, ctx, deadline)
        val evidence = Evidence()
            .add(0.25f, "govt-source", n.sourceKind == SourceKind.GOVERNMENT)
            .add(0.4f, "govt-keyword")
            .add(0.15f, "date", due != null || DateExtractor.first(text, ctx) != null)
            .add(0.1f, "reference", ReferenceExtractor.invoiceNumber(text) != null || ReferenceExtractor.upiRef(text) != null)
            .add(0.1f, "sms-source", n.sourceKind == SourceKind.SMS)
            .add(-0.4f, "promo", Keywords.PROMO.containsMatchIn(text))

        return ParsedSignal(
            parserId = id,
            type = RecordType.GOVERNMENT,
            category = Category.GOVERNMENT,
            title = n.senderName(),
            summary = n.shortSummary(),
            confidence = evidence.value,
            amountPaise = AmountExtractor.primary(text),
            dueAt = due?.toDeadline(ctx),
            importance = Importance.HIGH,
            actionRequired = due != null,
        )
    }
}
