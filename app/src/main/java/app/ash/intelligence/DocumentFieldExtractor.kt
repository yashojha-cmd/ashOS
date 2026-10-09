package app.ash.intelligence

import app.ash.data.parser.ParseContext
import app.ash.data.parser.extract.AmountExtractor
import app.ash.data.parser.extract.DateExtractor
import app.ash.data.parser.extract.ReferenceExtractor
import app.ash.domain.model.DocType
import java.time.LocalDate

/** Fields read from OCR text. Every field is a suggestion; the user confirms or corrects it. */
data class DocumentFields(
    val docType: DocType? = null,
    val title: String? = null,
    val documentNumber: String? = null,
    val expiresOn: LocalDate? = null,
    val issuedOn: LocalDate? = null,
    val dates: List<LocalDate> = emptyList(),
    val amountsPaise: List<Long> = emptyList(),
    val policyNumber: String? = null,
    val invoiceNumber: String? = null,
    val vehicleReg: String? = null,
    val confidence: Float = 0f,
)

object DocumentFieldExtractor {
    private data class TypeRule(val type: DocType, val pattern: Regex, val title: String)

    private val typeRules = listOf(
        TypeRule(DocType.AADHAAR, Regex("""(?i)(aadhaa?r|आधार|unique identification authority|uidai)"""), "Aadhaar"),
        TypeRule(DocType.PAN, Regex("""(?i)(income tax department|permanent account number|आयकर विभाग)"""), "PAN card"),
        TypeRule(DocType.PASSPORT, Regex("""(?i)(republic of india.{0,20}passport|passport no|P<IND)"""), "Passport"),
        TypeRule(DocType.DRIVING_LICENCE, Regex("""(?i)(driving licen[cs]e|\bDL\s*No|licence no)"""), "Driving licence"),
        TypeRule(DocType.VEHICLE_RC, Regex("""(?i)(certificate of registration|registration certificate|chassis no|engine no)"""), "Vehicle RC"),
        TypeRule(DocType.INSURANCE, Regex("""(?i)(policy (?:no|number|schedule)|sum insured|insured declared value|premium)"""), "Insurance policy"),
        TypeRule(DocType.TICKET, Regex("""(?i)(\bPNR\b|e-?ticket|boarding pass|electronic reservation slip)"""), "Ticket"),
        TypeRule(DocType.WARRANTY, Regex("""(?i)(warranty|guarantee card)"""), "Warranty card"),
        TypeRule(DocType.MEDICAL, Regex("""(?i)(prescription|diagnosis|patient name|lab report|\bRx\b)"""), "Medical record"),
        TypeRule(DocType.BILL, Regex("""(?i)(bill (?:no|number|date)|due date|amount payable)"""), "Bill"),
        TypeRule(DocType.RECEIPT, Regex("""(?i)(tax invoice|invoice no|receipt|gstin)"""), "Receipt"),
        TypeRule(DocType.CERTIFICATE, Regex("""(?i)(certificate|this is to certify)"""), "Certificate"),
    )

    private val pan = Regex("""\b([A-Z]{5}\d{4}[A-Z])\b""")
    private val aadhaar = Regex("""\b(\d{4})\s?(\d{4})\s?(\d{4})\b""")
    private val passport = Regex("""\b([A-PR-WY][1-9]\d{6})\b""")
    private val dl = Regex("""\b([A-Z]{2}[- ]?\d{2}[- ]?\d{4}\s?\d{7})\b""")
    private val expiryHint = Regex("""(?i)(valid (?:till|upto|until|thru)|validity|expiry|expires?|date of expiry|exp\.? date|renewal date|policy end|period of insurance.{0,30}to|to\b)""")
    private val issueHint = Regex("""(?i)(date of issue|issued on|issue date|\bDOI\b|invoice date|bill date|dated)""")

    fun extract(text: String, ctx: ParseContext): DocumentFields {
        if (text.isBlank()) return DocumentFields()
        val rule = typeRules.firstOrNull { it.pattern.containsMatchIn(text) }
        val type = rule?.type
        val number = when (type) {
            DocType.PAN -> pan.find(text)?.value
            // Only the last four Aadhaar digits are ever kept.
            DocType.AADHAAR -> aadhaar.find(text)?.let { "XXXX XXXX ${it.groupValues[3]}" }
            DocType.PASSPORT -> passport.find(text)?.value
            DocType.DRIVING_LICENCE -> dl.find(text)?.value
            DocType.VEHICLE_RC -> ReferenceExtractor.vehicleReg(text)
            DocType.INSURANCE -> ReferenceExtractor.policyNumber(text)
            DocType.TICKET -> ReferenceExtractor.pnr(text)
            DocType.RECEIPT, DocType.BILL -> ReferenceExtractor.invoiceNumber(text)
            else -> null
        }
        val dates = DateExtractor.all(text, ctx, preferFuture = false).map { it.date }.distinct()
        val expiry = DateExtractor.near(text, ctx, expiryHint)
            ?: dates.filter { it.isAfter(ctx.today) }.maxOrNull().takeIf { type in EXPIRING_TYPES }
        val issued = DateExtractor.near(text, ctx, issueHint, preferFuture = false)
        val amounts = AmountExtractor.all(text).filterNot { it.isBalance }.map { it.paise }

        var confidence = 0f
        if (type != null) confidence += 0.4f
        if (number != null) confidence += 0.3f
        if (expiry != null || issued != null) confidence += 0.2f
        if (text.length > 80) confidence += 0.1f

        return DocumentFields(
            docType = type,
            title = rule?.title,
            documentNumber = number,
            expiresOn = expiry,
            issuedOn = issued,
            dates = dates,
            amountsPaise = amounts,
            policyNumber = ReferenceExtractor.policyNumber(text),
            invoiceNumber = ReferenceExtractor.invoiceNumber(text),
            vehicleReg = ReferenceExtractor.vehicleReg(text),
            confidence = confidence.coerceAtMost(0.99f),
        )
    }

    private val EXPIRING_TYPES = setOf(
        DocType.PASSPORT, DocType.DRIVING_LICENCE, DocType.INSURANCE, DocType.VEHICLE_RC, DocType.WARRANTY,
    )
}
