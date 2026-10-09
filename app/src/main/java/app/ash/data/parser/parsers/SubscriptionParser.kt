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
import app.ash.data.parser.shortSummary
import app.ash.data.parser.toDeadline
import app.ash.domain.model.Category
import app.ash.domain.model.Importance
import app.ash.domain.model.RecordType
import app.ash.domain.model.RecurrencePeriod
import app.ash.domain.model.SourceKind
import app.ash.domain.model.TxnDirection

/** Subscription renewals and autopay mandates (Netflix, Spotify, YouTube Premium, ...). */
class SubscriptionParser : NotificationParser {
    override val id = "subscription"

    override fun parse(n: RawNotification, ctx: ParseContext): ParsedSignal? {
        val text = n.fullText
        if (!Keywords.SUBSCRIPTION.containsMatchIn(text)) return null
        val merchant = MerchantDictionary.find(text)?.takeIf { it.category == Category.SUBSCRIPTIONS }
        val ottSource = n.sourceKind == SourceKind.OTT
        if (merchant == null && !ottSource) return null
        val amount = AmountExtractor.primary(text)
        val renewal = DateExtractor.near(text, ctx, Keywords.RENEWAL_HINT)
        val period = when {
            Keywords.YEARLY.containsMatchIn(text) -> RecurrencePeriod.YEARLY
            Keywords.QUARTERLY.containsMatchIn(text) -> RecurrencePeriod.QUARTERLY
            else -> RecurrencePeriod.MONTHLY
        }
        val charged = Keywords.DEBIT.containsMatchIn(text) && !Keywords.FUTURE_DEBIT.containsMatchIn(text) &&
            !Regex("(?i)will be charged").containsMatchIn(text)

        val evidence = Evidence()
            .add(0.3f, "subscription-merchant", merchant != null || ottSource)
            .add(0.3f, "subscription-keyword")
            .add(0.2f, "amount", amount != null)
            .add(0.15f, "renewal-date", renewal != null)
            .add(0.05f, "ott-source", ottSource && merchant != null)
            .add(-0.4f, "promo", Keywords.PROMO.containsMatchIn(text))

        val name = merchant?.name ?: n.sourceLabel
        return ParsedSignal(
            parserId = id,
            type = RecordType.SUBSCRIPTION,
            category = Category.SUBSCRIPTIONS,
            title = name,
            summary = n.shortSummary(),
            confidence = evidence.value,
            amountPaise = amount,
            direction = if (charged) TxnDirection.EXPENSE else null,
            merchant = name,
            dueAt = renewal?.toDeadline(ctx),
            importance = Importance.MEDIUM,
            recurrence = period,
            alreadyHandled = charged,
        )
    }
}
