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
import app.ash.data.parser.extract.MerchantExtractor
import app.ash.data.parser.extract.ReferenceExtractor
import app.ash.data.parser.senderName
import app.ash.data.parser.shortSummary
import app.ash.data.parser.toEventMillis
import app.ash.domain.model.Category
import app.ash.domain.model.Importance
import app.ash.domain.model.RecordType
import app.ash.domain.model.SourceKind
import app.ash.domain.model.TxnDirection

/** Completed debits, credits and refunds from bank apps and bank SMS. */
class BankParser : NotificationParser {
    override val id = "bank"

    override fun parse(n: RawNotification, ctx: ParseContext): ParsedSignal? {
        val text = n.fullText
        if (Keywords.FUTURE_DEBIT.containsMatchIn(text)) return null
        if (Keywords.FAILED.containsMatchIn(text) || Keywords.REQUEST.containsMatchIn(text)) return null
        val direction = direction(text) ?: return null
        val amount = AmountExtractor.primary(text) ?: return null
        val account = ReferenceExtractor.account(text)

        val evidence = Evidence()
            .add(0.35f, "amount")
            .add(0.25f, "direction")
            .add(0.15f, "bank-source", n.sourceKind == SourceKind.BANK || n.sourceKind == SourceKind.SMS)
            .add(0.15f, "account", account != null)
            .add(0.1f, "bank-name", Keywords.BANK_NAMES.containsMatchIn(text))
            .add(0.05f, "reference", ReferenceExtractor.upiRef(text) != null)
            .add(-0.4f, "promo", Keywords.PROMO.containsMatchIn(text))
            .add(-0.2f, "non-bank-source", n.sourceKind in setOf(SourceKind.SHOPPING, SourceKind.FOOD, SourceKind.OTT, SourceKind.TRAVEL))

        val merchant = MerchantExtractor.extract(text, direction)
        val category = MerchantDictionary.categoryOf(merchant) ?: Category.FINANCE
        val occurred = DateExtractor.first(text, ctx, preferFuture = false)?.toEventMillis(ctx) ?: n.postedAt
        return ParsedSignal(
            parserId = id,
            type = RecordType.TRANSACTION,
            category = category,
            title = merchant ?: n.senderName(),
            summary = n.shortSummary(),
            confidence = evidence.value,
            amountPaise = amount,
            direction = direction,
            merchant = merchant,
            accountRef = account,
            eventAt = occurred,
            reference = ReferenceExtractor.upiRef(text),
            importance = Importance.LOW,
            alreadyHandled = true,
        )
    }

    companion object {
        fun direction(text: String): TxnDirection? = when {
            Keywords.REFUND.containsMatchIn(text) -> TxnDirection.REFUND
            Keywords.TRANSFER_SELF.containsMatchIn(text) -> TxnDirection.TRANSFER
            Keywords.DEBIT.containsMatchIn(text) -> TxnDirection.EXPENSE
            Keywords.CREDIT.containsMatchIn(text) -> TxnDirection.INCOME
            else -> null
        }
    }
}
