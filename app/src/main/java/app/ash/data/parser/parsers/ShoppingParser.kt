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
import app.ash.data.parser.shortSummary
import app.ash.data.parser.toEventMillis
import app.ash.domain.model.Category
import app.ash.domain.model.DeliveryState
import app.ash.domain.model.Importance
import app.ash.domain.model.RecordType
import app.ash.domain.model.SourceKind

/** Order placed / confirmed / cancelled from shopping and food apps. Shipping updates go to DeliveryParser. */
class ShoppingParser : NotificationParser {
    override val id = "shopping"

    override fun parse(n: RawNotification, ctx: ParseContext): ParsedSignal? {
        val text = n.fullText
        val cancelled = Keywords.ORDER_CANCELLED.containsMatchIn(text)
        if (!Keywords.ORDER.containsMatchIn(text) && !cancelled) return null
        if (Keywords.DELIVERY.containsMatchIn(text) && !cancelled) return null
        val merchant = MerchantDictionary.find(text)?.name
            ?: n.sourceLabel.takeIf { n.sourceKind == SourceKind.SHOPPING || n.sourceKind == SourceKind.FOOD }
        val shoppingSource = n.sourceKind == SourceKind.SHOPPING || n.sourceKind == SourceKind.FOOD
        val orderId = ReferenceExtractor.orderId(text)
        val amount = AmountExtractor.primary(text)
        val expected = DateExtractor.near(text, ctx, Keywords.DELIVERY_DATE_HINT)

        val evidence = Evidence()
            .add(0.35f, "shopping-source", shoppingSource)
            .add(0.2f, "shopping-merchant", !shoppingSource && MerchantDictionary.categoryOf(merchant) == Category.SHOPPING)
            .add(0.35f, "order-keyword")
            .add(0.15f, "order-id", orderId != null)
            .add(0.1f, "amount", amount != null)
            .add(0.05f, "date", expected != null)
            .add(-0.4f, "promo", Keywords.PROMO.containsMatchIn(text))

        return ParsedSignal(
            parserId = id,
            type = RecordType.DELIVERY,
            category = Category.SHOPPING,
            title = merchant ?: n.sourceLabel,
            summary = n.shortSummary(),
            confidence = evidence.value,
            amountPaise = amount,
            merchant = merchant,
            eventAt = expected?.toEventMillis(ctx),
            reference = orderId,
            importance = Importance.LOW,
            deliveryState = if (cancelled) DeliveryState.CANCELLED else DeliveryState.ORDERED,
            alreadyHandled = cancelled,
        )
    }
}
