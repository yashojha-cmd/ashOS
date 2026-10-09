package app.ash.data.parser.parsers

import app.ash.data.parser.Evidence
import app.ash.data.parser.NotificationParser
import app.ash.data.parser.ParseContext
import app.ash.data.parser.ParsedSignal
import app.ash.data.parser.RawNotification
import app.ash.data.parser.dictionary.Keywords
import app.ash.data.parser.dictionary.MerchantDictionary
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

/** Utility, telecom, credit-card and EMI bills with a due date. */
class BillParser : NotificationParser {
    override val id = "bill"

    private val creditCard = Regex("""(?i)\b(credit card|card statement|card ending)\b""")

    override fun parse(n: RawNotification, ctx: ParseContext): ParsedSignal? {
        val text = n.fullText
        if (!Keywords.BILL.containsMatchIn(text)) return null
        val paid = Keywords.BILL_PAID.containsMatchIn(text)
        val amount = AmountExtractor.primary(text)
        val due = DateExtractor.near(text, ctx, Keywords.DUE_HINT) ?: DateExtractor.first(text, ctx).takeIf { !paid }
        if (amount == null && due == null) return null
        val biller = MerchantDictionary.find(text)?.takeIf { it.category == Category.BILLS }?.name
        val overdue = Keywords.OVERDUE.containsMatchIn(text)

        val evidence = Evidence()
            .add(0.3f, "bill-keyword")
            .add(0.2f, "amount", amount != null)
            .add(0.3f, "due-date", due != null)
            .add(0.1f, "biller-source", n.sourceKind in setOf(SourceKind.TELECOM, SourceKind.UTILITY, SourceKind.BANK, SourceKind.SMS))
            .add(0.1f, "known-biller", biller != null)
            .add(0.05f, "utility-keyword", Keywords.UTILITY.containsMatchIn(text))
            .add(-0.4f, "promo", Keywords.PROMO.containsMatchIn(text))

        return ParsedSignal(
            parserId = id,
            type = RecordType.BILL,
            category = if (creditCard.containsMatchIn(text)) Category.FINANCE else Category.BILLS,
            title = biller ?: n.senderName(),
            summary = n.shortSummary(),
            confidence = evidence.value,
            amountPaise = amount,
            merchant = biller,
            accountRef = ReferenceExtractor.account(text),
            dueAt = due?.toDeadline(ctx),
            reference = ReferenceExtractor.invoiceNumber(text),
            importance = Importance.CRITICAL,
            actionRequired = overdue,
            alreadyHandled = paid,
        )
    }
}
