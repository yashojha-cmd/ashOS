package app.ash.data.parser

import app.ash.data.parser.dictionary.Keywords
import app.ash.data.parser.parsers.BankParser
import app.ash.data.parser.parsers.BillParser
import app.ash.data.parser.parsers.DeliveryParser
import app.ash.data.parser.parsers.GenericParser
import app.ash.data.parser.parsers.GovernmentParser
import app.ash.data.parser.parsers.InsuranceParser
import app.ash.data.parser.parsers.ShoppingParser
import app.ash.data.parser.parsers.SubscriptionParser
import app.ash.data.parser.parsers.TravelParser
import app.ash.data.parser.parsers.UpiParser
import app.ash.data.parser.parsers.VehicleParser

/**
 * Runs every specialised parser and keeps the most confident result.
 * Ties go to the parser listed first (more specific parsers are listed first).
 * Sensitive notifications (OTPs, PINs) short-circuit and are never parsed or stored.
 */
class NotificationParserEngine(
    private val parsers: List<NotificationParser> = defaultParsers(),
    private val fallback: NotificationParser = GenericParser(),
) {
    fun parse(n: RawNotification, ctx: ParseContext): ParseOutcome {
        if (Keywords.SENSITIVE.containsMatchIn(n.fullText)) return ParseOutcome.Sensitive
        var best: ParsedSignal? = null
        for (parser in parsers) {
            val signal = runCatching { parser.parse(n, ctx) }.getOrNull() ?: continue
            if (best == null || signal.confidence > best.confidence) best = signal
        }
        if (best == null || best.confidence < ConfidencePolicy.REVIEW) {
            val generic = runCatching { fallback.parse(n, ctx) }.getOrNull()
            if (generic != null && (best == null || generic.confidence > best.confidence)) best = generic
        }
        return best?.let { ParseOutcome.Parsed(it) } ?: ParseOutcome.NoMatch
    }

    companion object {
        fun defaultParsers(): List<NotificationParser> = listOf(
            InsuranceParser(),
            SubscriptionParser(),
            BillParser(),
            TravelParser(),
            DeliveryParser(),
            ShoppingParser(),
            VehicleParser(),
            GovernmentParser(),
            UpiParser(),
            BankParser(),
        )
    }
}
