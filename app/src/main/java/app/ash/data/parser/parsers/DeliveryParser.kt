package app.ash.data.parser.parsers

import app.ash.data.parser.Evidence
import app.ash.data.parser.NotificationParser
import app.ash.data.parser.ParseContext
import app.ash.data.parser.ParsedSignal
import app.ash.data.parser.RawNotification
import app.ash.data.parser.dictionary.Keywords
import app.ash.data.parser.dictionary.MerchantDictionary
import app.ash.data.parser.extract.DateExtractor
import app.ash.data.parser.extract.ReferenceExtractor
import app.ash.data.parser.shortSummary
import app.ash.data.parser.toEventMillis
import app.ash.domain.model.Category
import app.ash.domain.model.DeliveryState
import app.ash.domain.model.Importance
import app.ash.domain.model.RecordType
import app.ash.domain.model.SourceKind

/** Shipping progress: shipped, out for delivery, arriving tomorrow, delivered. */
class DeliveryParser : NotificationParser {
    override val id = "delivery"

    private val outFor = Regex("""(?i)\bout for delivery\b|\bon (?:its|the) way\b|\barriving today\b""")
    private val delivered = Regex("""(?i)\b(?:has been |was |is )?delivered\b(?! (?:by|on|tomorrow|today))""")
    private val shipped = Regex("""(?i)\b(shipped|dispatched|in transit|picked up)\b""")
    private val courier = Regex("""(?i)\b(delhivery|blue ?dart|ekart|shadowfax|xpressbees|dtdc|india post|ecom express|amazon logistics)\b""")

    override fun parse(n: RawNotification, ctx: ParseContext): ParsedSignal? {
        val text = n.fullText
        if (!Keywords.DELIVERY.containsMatchIn(text)) return null
        val state = when {
            delivered.containsMatchIn(text) && !Regex("(?i)will be delivered|to be delivered").containsMatchIn(text) -> DeliveryState.DELIVERED
            outFor.containsMatchIn(text) -> DeliveryState.OUT_FOR_DELIVERY
            shipped.containsMatchIn(text) -> DeliveryState.SHIPPED
            else -> DeliveryState.SHIPPED
        }
        val merchant = MerchantDictionary.find(text)?.name ?: n.sourceLabel
        val expected = when (state) {
            DeliveryState.OUT_FOR_DELIVERY -> ctx.today
            DeliveryState.DELIVERED -> null
            else -> DateExtractor.near(text, ctx, Keywords.DELIVERY_DATE_HINT) ?: DateExtractor.first(text, ctx)
        }
        val shoppingSource = n.sourceKind == SourceKind.SHOPPING || n.sourceKind == SourceKind.FOOD
        val evidence = Evidence()
            .add(0.3f, "shopping-source", shoppingSource)
            .add(0.2f, "courier", courier.containsMatchIn(text))
            .add(0.4f, "delivery-keyword")
            .add(0.15f, "date", expected != null || state == DeliveryState.DELIVERED)
            .add(0.1f, "order-id", ReferenceExtractor.orderId(text) != null)
            .add(0.05f, "merchant", MerchantDictionary.find(text) != null)
            .add(-0.4f, "promo", Keywords.PROMO.containsMatchIn(text))

        return ParsedSignal(
            parserId = id,
            type = RecordType.DELIVERY,
            category = Category.DELIVERIES,
            title = merchant,
            summary = n.shortSummary(),
            confidence = evidence.value,
            merchant = merchant,
            eventAt = expected?.toEventMillis(ctx) ?: n.postedAt.takeIf { state == DeliveryState.DELIVERED },
            reference = ReferenceExtractor.orderId(text),
            importance = if (n.sourceKind == SourceKind.FOOD) Importance.LOW else Importance.MEDIUM,
            deliveryState = state,
            alreadyHandled = state == DeliveryState.DELIVERED,
        )
    }
}
