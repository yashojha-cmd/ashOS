package app.ash.data.parser.parsers

import app.ash.data.parser.Evidence
import app.ash.data.parser.NotificationParser
import app.ash.data.parser.ParseContext
import app.ash.data.parser.ParsedSignal
import app.ash.data.parser.RawNotification
import app.ash.data.parser.dictionary.Keywords
import app.ash.data.parser.dictionary.MerchantDictionary
import app.ash.data.parser.extract.AmountExtractor
import app.ash.data.parser.extract.MerchantExtractor
import app.ash.data.parser.extract.ReferenceExtractor
import app.ash.data.parser.senderName
import app.ash.data.parser.shortSummary
import app.ash.domain.model.Category
import app.ash.domain.model.Importance
import app.ash.domain.model.RecordType
import app.ash.domain.model.SourceKind
import app.ash.domain.model.TxnDirection

/** UPI app payments: "You paid ₹250 to Raju", "Received ₹500 from Amit", "₹649 sent to Netflix". */
class UpiParser : NotificationParser {
    override val id = "upi"

    private val paidTo = Regex("""(?i)\b(?:you\s+)?(?:paid|sent)\s+(?:₹|rs\.?|inr)?\s*[\d,.]+\s+(?:to|for)\b""")
    private val receivedFrom = Regex("""(?i)\b(?:received|got)\s+(?:₹|rs\.?|inr)?\s*[\d,.]+\s+from\b|\bsent you\b|\bpaid you\b""")
    private val success = Regex("""(?i)\b(successful|success|completed|done)\b""")

    override fun parse(n: RawNotification, ctx: ParseContext): ParsedSignal? {
        val text = n.fullText
        val isUpiContext = n.sourceKind == SourceKind.UPI || Keywords.UPI.containsMatchIn(text)
        if (!isUpiContext) return null
        if (Keywords.FAILED.containsMatchIn(text) || Keywords.REQUEST.containsMatchIn(text)) return null
        if (Keywords.FUTURE_DEBIT.containsMatchIn(text)) return null
        val amount = AmountExtractor.primary(text) ?: return null
        val direction = when {
            receivedFrom.containsMatchIn(text) -> TxnDirection.INCOME
            paidTo.containsMatchIn(text) -> TxnDirection.EXPENSE
            else -> BankParser.direction(text)
        } ?: return null

        val evidence = Evidence()
            .add(0.35f, "amount")
            .add(0.25f, "direction")
            .add(0.25f, "upi-app", n.sourceKind == SourceKind.UPI)
            .add(0.1f, "upi-keyword", Keywords.UPI.containsMatchIn(text))
            .add(0.05f, "success", success.containsMatchIn(text))
            .add(0.05f, "reference", ReferenceExtractor.upiRef(text) != null)
            .add(-0.4f, "promo", Keywords.PROMO.containsMatchIn(text))

        val merchant = MerchantExtractor.extract(text, direction)
        return ParsedSignal(
            parserId = id,
            type = RecordType.TRANSACTION,
            category = MerchantDictionary.categoryOf(merchant) ?: Category.FINANCE,
            title = merchant ?: n.senderName(),
            summary = n.shortSummary(),
            confidence = evidence.value,
            amountPaise = amount,
            direction = direction,
            merchant = merchant,
            accountRef = ReferenceExtractor.account(text),
            eventAt = n.postedAt,
            reference = ReferenceExtractor.upiRef(text),
            importance = Importance.LOW,
            alreadyHandled = true,
        )
    }
}
