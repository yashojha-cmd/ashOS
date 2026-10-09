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
import app.ash.domain.model.InsuranceKind
import app.ash.domain.model.RecordType
import app.ash.domain.model.SourceKind

/** Policy expiry, renewal and premium reminders. */
class InsuranceParser : NotificationParser {
    override val id = "insurance"

    override fun parse(n: RawNotification, ctx: ParseContext): ParsedSignal? {
        val text = n.fullText
        if (!Keywords.INSURANCE.containsMatchIn(text) || !Keywords.INSURANCE_EVENT.containsMatchIn(text)) return null
        val expiry = DateExtractor.near(text, ctx, Keywords.EXPIRY_HINT) ?: DateExtractor.first(text, ctx)
        val policyNo = ReferenceExtractor.policyNumber(text)
        val insurer = MerchantDictionary.find(text)?.takeIf { it.category == Category.INSURANCE }?.name
        val kind = when {
            Keywords.VEHICLE_INSURANCE.containsMatchIn(text) || ReferenceExtractor.vehicleReg(text) != null -> InsuranceKind.VEHICLE
            Keywords.HEALTH_INSURANCE.containsMatchIn(text) -> InsuranceKind.HEALTH
            Keywords.LIFE_INSURANCE.containsMatchIn(text) -> InsuranceKind.LIFE
            else -> InsuranceKind.OTHER
        }
        val evidence = Evidence()
            .add(0.3f, "insurance-keyword")
            .add(0.2f, "expiry-keyword")
            .add(0.25f, "date", expiry != null)
            .add(0.1f, "policy-number", policyNo != null)
            .add(0.15f, "insurance-source", n.sourceKind == SourceKind.INSURANCE || insurer != null)
            .add(0.05f, "amount", AmountExtractor.primary(text) != null)
            .add(-0.4f, "promo", Keywords.PROMO.containsMatchIn(text) && policyNo == null)

        return ParsedSignal(
            parserId = id,
            type = RecordType.INSURANCE,
            category = Category.INSURANCE,
            title = insurer ?: n.senderName(),
            summary = n.shortSummary(),
            confidence = evidence.value,
            amountPaise = AmountExtractor.primary(text),
            merchant = insurer,
            expiresAt = expiry?.toDeadline(ctx),
            reference = policyNo,
            importance = Importance.CRITICAL,
            insuranceKind = kind,
            vehicleReg = ReferenceExtractor.vehicleReg(text),
        )
    }
}
