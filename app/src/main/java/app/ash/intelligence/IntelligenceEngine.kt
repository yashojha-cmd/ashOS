package app.ash.intelligence

import app.ash.data.parser.NotificationParserEngine
import app.ash.data.parser.ParseContext
import app.ash.data.parser.ParseOutcome
import app.ash.data.parser.RawNotification
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The seam where on-device AI can plug in later without touching the rest of ASH.
 *
 * V1 ships only [RuleBasedIntelligenceEngine]. A future OnDeviceAIEngine must:
 * keep the model file in app storage, run inference locally, never use the network,
 * be switchable off in Settings, and fall back to the rule engine.
 */
interface IntelligenceEngine {
    val id: String

    fun classifyNotification(notification: RawNotification, ctx: ParseContext): ParseOutcome

    fun extractDocumentFields(text: String, ctx: ParseContext): DocumentFields

    fun parseQuery(query: String, ctx: ParseContext): StructuredQuery
}

@Singleton
class RuleBasedIntelligenceEngine @Inject constructor() : IntelligenceEngine {
    override val id: String = "rules-v1"

    private val notifications = NotificationParserEngine()

    override fun classifyNotification(notification: RawNotification, ctx: ParseContext): ParseOutcome =
        notifications.parse(notification, ctx)

    override fun extractDocumentFields(text: String, ctx: ParseContext): DocumentFields =
        DocumentFieldExtractor.extract(text, ctx)

    override fun parseQuery(query: String, ctx: ParseContext): StructuredQuery =
        SearchQueryParser.parse(query, ctx)
}
