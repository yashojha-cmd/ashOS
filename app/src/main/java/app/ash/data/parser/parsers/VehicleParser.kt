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

/** PUC expiry, service due, FASTag balance and e-challans. */
class VehicleParser : NotificationParser {
    override val id = "vehicle"

    private val challan = Regex("""(?i)\b(e-?challan|challan)\b""")

    override fun parse(n: RawNotification, ctx: ParseContext): ParsedSignal? {
        val text = n.fullText
        if (!Keywords.VEHICLE.containsMatchIn(text)) return null
        val reg = ReferenceExtractor.vehicleReg(text)
        val date = DateExtractor.near(text, ctx, Keywords.VEHICLE_DATE_HINT) ?: DateExtractor.first(text, ctx)
        val isChallan = challan.containsMatchIn(text)
        val evidence = Evidence()
            .add(0.4f, "vehicle-keyword")
            .add(0.25f, "registration", reg != null)
            .add(0.2f, "date", date != null)
            .add(0.1f, "govt-source", n.sourceKind == SourceKind.GOVERNMENT || n.sourceKind == SourceKind.SMS)
            .add(0.05f, "amount", AmountExtractor.primary(text) != null)
            .add(-0.4f, "promo", Keywords.PROMO.containsMatchIn(text))

        return ParsedSignal(
            parserId = id,
            type = RecordType.VEHICLE,
            category = Category.VEHICLE,
            title = reg ?: n.senderName(),
            summary = n.shortSummary(),
            confidence = evidence.value,
            amountPaise = AmountExtractor.primary(text),
            dueAt = date?.toDeadline(ctx),
            reference = reg,
            importance = if (isChallan) Importance.CRITICAL else Importance.HIGH,
            vehicleReg = reg,
            actionRequired = isChallan,
        )
    }
}
