package app.ash.intelligence

import app.ash.common.Dates
import app.ash.data.parser.ParseContext
import app.ash.domain.model.Category
import app.ash.domain.model.InsuranceKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A free-text search turned into structured filters without AI. */
data class StructuredQuery(
    val raw: String,
    val terms: List<String> = emptyList(),
    val categories: Set<Category> = emptySet(),
    val insuranceKind: InsuranceKind? = null,
    val amountPaise: Long? = null,
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val expiring: Boolean = false,
    val dueSoon: Boolean = false,
) {
    val isEmpty: Boolean
        get() = terms.isEmpty() && categories.isEmpty() && amountPaise == null && fromMillis == null && !expiring && !dueSoon
}

object SearchQueryParser {
    private val amount = Regex("""(?i)(?:₹|rs\.?|inr)\s*([\d,]+(?:\.\d{1,2})?)|\b(\d{3,}(?:\.\d{1,2})?)\b""")
    private val months = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

    private val monthAbbreviations = mapOf(
        "jan" to 0, "feb" to 1, "apr" to 3, "jun" to 5, "jul" to 6, "aug" to 7,
        "sep" to 8, "sept" to 8, "oct" to 9, "nov" to 10, "dec" to 11,
    )

    private val categoryWords: Map<String, Category> = mapOf(
        "bill" to Category.BILLS, "bills" to Category.BILLS, "electricity" to Category.BILLS, "recharge" to Category.BILLS,
        "insurance" to Category.INSURANCE, "policy" to Category.INSURANCE, "policies" to Category.INSURANCE,
        "document" to Category.DOCUMENTS, "documents" to Category.DOCUMENTS, "docs" to Category.DOCUMENTS,
        "subscription" to Category.SUBSCRIPTIONS, "subscriptions" to Category.SUBSCRIPTIONS,
        "travel" to Category.TRAVEL, "train" to Category.TRAVEL, "flight" to Category.TRAVEL, "ticket" to Category.TRAVEL, "trip" to Category.TRAVEL,
        "delivery" to Category.DELIVERIES, "deliveries" to Category.DELIVERIES, "order" to Category.SHOPPING, "orders" to Category.SHOPPING,
        "shopping" to Category.SHOPPING, "vehicle" to Category.VEHICLE, "bike" to Category.VEHICLE, "car" to Category.VEHICLE,
        "transaction" to Category.FINANCE, "transactions" to Category.FINANCE, "payment" to Category.FINANCE, "payments" to Category.FINANCE,
        "health" to Category.HEALTHCARE, "medical" to Category.HEALTHCARE, "government" to Category.GOVERNMENT,
        "warranty" to Category.DOCUMENTS, "reminder" to Category.REMINDERS, "reminders" to Category.REMINDERS,
    )
    private val expiringWords = setOf("expiring", "expire", "expires", "expiry", "renewal", "renew")
    private val dueWords = setOf("due", "upcoming", "pending")
    private val stopWords = setOf("the", "a", "an", "my", "of", "in", "on", "for", "this", "what", "which", "show", "me", "all", "is", "are")

    fun parse(query: String, ctx: ParseContext): StructuredQuery {
        val raw = query.trim()
        if (raw.isEmpty()) return StructuredQuery(raw)
        var rest = raw.lowercase()

        var amountPaise: Long? = null
        amount.find(rest)?.let { m ->
            val digits = (m.groups[1] ?: m.groups[2])!!.value.replace(",", "")
            // A bare 4-digit number between 1900 and 2100 is more likely a year than an amount.
            val isYear = m.groups[1] == null && digits.length == 4 && digits.toInt() in 1900..2100
            if (!isYear) {
                amountPaise = digits.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
                rest = rest.removeRange(m.range)
            }
        }

        var from: Long? = null
        var to: Long? = null
        fun range(start: LocalDate, endExclusive: LocalDate) {
            from = Dates.startOfDay(start, ctx.zone)
            to = Dates.startOfDay(endExclusive, ctx.zone)
        }
        when {
            "this month" in rest -> {
                val start = ctx.today.withDayOfMonth(1)
                range(start, start.plusMonths(1)); rest = rest.replace("this month", " ")
            }
            "this week" in rest -> {
                val start = ctx.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                range(start, start.plusWeeks(1)); rest = rest.replace("this week", " ")
            }
            "last month" in rest -> {
                val start = ctx.today.withDayOfMonth(1).minusMonths(1)
                range(start, start.plusMonths(1)); rest = rest.replace("last month", " ")
            }
            "today" in rest -> {
                range(ctx.today, ctx.today.plusDays(1)); rest = rest.replace("today", " ")
            }
        }

        val tokens = rest.split(Regex("""[^\p{L}\p{N}@.]+""")).filter { it.isNotBlank() }
        val terms = mutableListOf<String>()
        val categories = mutableSetOf<Category>()
        var insuranceKind: InsuranceKind? = null
        var expiring = false
        var dueSoon = false
        for (token in tokens) {
            val monthIndex = months.indexOf(token).takeIf { it >= 0 } ?: monthAbbreviations[token] ?: -1
            when {
                token in stopWords -> Unit
                monthIndex >= 0 && from == null -> {
                    val month = monthIndex + 1
                    var start = LocalDate.of(ctx.today.year, month, 1)
                    if (start.isAfter(ctx.today)) start = start.minusYears(1)
                    range(start, start.plusMonths(1))
                }
                token in expiringWords -> expiring = true
                token in dueWords -> dueSoon = true
                token in categoryWords -> {
                    val category = categoryWords.getValue(token)
                    categories += category
                    if (token == "bike" || token == "car" || token == "vehicle") insuranceKind = InsuranceKind.VEHICLE
                    if (token == "health" || token == "medical") insuranceKind = InsuranceKind.HEALTH
                    // Specific words still help ranking, so keep them as terms too.
                    if (token !in setOf("bills", "documents", "docs", "subscriptions", "transactions", "payments", "policies", "reminders", "deliveries", "orders")) terms += token
                }
                else -> terms += token
            }
        }
        return StructuredQuery(
            raw = raw,
            terms = terms.distinct(),
            categories = categories,
            insuranceKind = insuranceKind,
            amountPaise = amountPaise,
            fromMillis = from,
            toMillis = to,
            expiring = expiring,
            dueSoon = dueSoon,
        )
    }

    /** FTS4 MATCH expression: prefix match on each sanitised term, OR-ed together. */
    fun ftsExpression(terms: List<String>): String? {
        val safe = terms.map { it.replace(Regex("""[^\p{L}\p{N}]"""), "") }.filter { it.length >= 2 }
        if (safe.isEmpty()) return null
        return safe.joinToString(" OR ") { "$it*" }
    }
}
