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
import app.ash.domain.model.Importance
import app.ash.domain.model.RecordType
import app.ash.domain.model.SourceKind
import app.ash.domain.model.TripMode

/** Train, flight and bus bookings: PNR, journey date and route. */
class TravelParser : NotificationParser {
    override val id = "travel"

    private val wordRoute = Regex("""(?i)\bfrom\s+([A-Za-z][A-Za-z ]{2,20}?)\s+to\s+([A-Za-z][A-Za-z ]{2,20}?)(?=[\s,.]|$)""")
    private val codeRoute = Regex("""\b([A-Z]{3,5})\s*(?:-|to|→|->|–)\s*([A-Z]{3,5})\b""")
    private val cancelled = Regex("""(?i)\b(cancell?ed|cancellation)\b""")
    private val trainHint = Regex("""(?i)\b(train|irctc|coach|berth|cnf|rac|wl\d*|pnr\s*(?:no\.?)?\s*:?\s*\d{10})\b""")

    override fun parse(n: RawNotification, ctx: ParseContext): ParsedSignal? {
        val text = n.fullText
        if (!Keywords.TRAVEL.containsMatchIn(text)) return null
        val pnr = ReferenceExtractor.pnr(text)
        val travelSource = n.sourceKind == SourceKind.TRAVEL
        val merchant = MerchantDictionary.find(text)?.takeIf { it.category == Category.TRAVEL }?.name
        if (!travelSource && pnr == null && merchant == null) return null

        val mode = when {
            trainHint.containsMatchIn(text) -> TripMode.TRAIN
            Keywords.FLIGHT.containsMatchIn(text) -> TripMode.FLIGHT
            Keywords.BUS.containsMatchIn(text) -> TripMode.BUS
            else -> TripMode.OTHER
        }
        val date = DateExtractor.near(text, ctx, Keywords.TRAVEL_DATE_HINT) ?: DateExtractor.first(text, ctx)
        val route = wordRoute.find(text) ?: codeRoute.find(text)?.takeIf { it.groupValues[1] != "PNR" }
        val isCancelled = cancelled.containsMatchIn(text)

        val evidence = Evidence()
            .add(0.3f, "travel-source", travelSource)
            .add(0.15f, "travel-merchant", merchant != null)
            .add(0.3f, "travel-keyword")
            .add(0.2f, "pnr", pnr != null)
            .add(0.15f, "date", date != null)
            .add(0.05f, "route", route != null)
            .add(-0.4f, "promo", Keywords.PROMO.containsMatchIn(text))

        return ParsedSignal(
            parserId = id,
            type = RecordType.TRIP,
            category = Category.TRAVEL,
            title = merchant ?: n.sourceLabel,
            summary = n.shortSummary(),
            confidence = evidence.value,
            amountPaise = AmountExtractor.primary(text),
            merchant = merchant,
            eventAt = date?.toEventMillis(ctx),
            reference = pnr,
            importance = Importance.HIGH,
            tripMode = mode,
            tripFrom = route?.groupValues?.get(1)?.trim(),
            tripTo = route?.groupValues?.get(2)?.trim(),
            alreadyHandled = isCancelled,
        )
    }
}
