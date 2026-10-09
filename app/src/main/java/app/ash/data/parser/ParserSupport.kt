package app.ash.data.parser

import app.ash.common.Dates
import app.ash.domain.model.Category
import app.ash.domain.model.SourceKind
import java.time.LocalDate

internal fun LocalDate.toDeadline(ctx: ParseContext): Long = Dates.morningOf(this, ctx.zone)

internal fun LocalDate.toEventMillis(ctx: ParseContext): Long = Dates.startOfDay(this, ctx.zone)

/** Short single-line summary kept with the record; the full text is only kept if the user allows it. */
internal fun RawNotification.shortSummary(max: Int = 160): String =
    text.ifBlank { title }.replace(Regex("\\s+"), " ").trim().let { if (it.length > max) it.take(max - 1) + "…" else it }

/** Title fallback: the notification title (often the sender) or the app label. */
internal fun RawNotification.senderName(): String =
    title.trim().takeIf { it.isNotEmpty() && it.length <= 40 && !it.any(Char::isDigit) } ?: sourceLabel

internal fun SourceKind.defaultCategory(): Category = when (this) {
    SourceKind.BANK, SourceKind.UPI -> Category.FINANCE
    SourceKind.SHOPPING, SourceKind.FOOD -> Category.SHOPPING
    SourceKind.TRAVEL -> Category.TRAVEL
    SourceKind.TELECOM, SourceKind.UTILITY -> Category.BILLS
    SourceKind.INSURANCE -> Category.INSURANCE
    SourceKind.OTT -> Category.SUBSCRIPTIONS
    SourceKind.GOVERNMENT -> Category.GOVERNMENT
    SourceKind.SMS, SourceKind.OTHER -> Category.OTHER
}
