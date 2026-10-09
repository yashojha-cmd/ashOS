package app.ash.data.parser.extract

import java.math.BigDecimal

/**
 * Finds rupee amounts in the many shapes Indian banks use: "₹1,499", "Rs.1499.00", "Rs:500",
 * "INR500", "INR 1,23,456.78", "1499/-", and currency-less "debited by 500.00".
 * Balances and limits ("Avl Bal", "Avbl Bal", "Available Limit") are flagged so they are never
 * mistaken for the transaction amount, and can be read separately.
 */
object AmountExtractor {
    data class Match(val paise: Long, val start: Int, val isBalance: Boolean)

    private const val NUMBER = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val prefixed = Regex("""(?i)(?:₹|\bRs\.?|\bINR|\bRS\.?)\s*[:.]?\s*$NUMBER""")
    private val suffixed = Regex("""(?i)\b$NUMBER\s*(?:rupees|/-)""")
    /** "debited by 500.00", "credited with 25000": only used when no currency marker is present. */
    private val verbAmount = Regex(
        """(?i)\b(?:debited|credited|spent|withdrawn|deposited|paid|sent|received|transferred)\s+(?:by|for|with|of|amount)?\s*([0-9][0-9,]*\.[0-9]{1,2}|[0-9][0-9,]{1,})\b(?!\s*(?:days?|hrs?|mins?|%|/))""",
    )
    private val balanceHint = Regex(
        """(?i)(avl\.?\s*bal(?:ance)?|avbl\.?\s*bal(?:ance)?|avail(?:able)?\.?\s*(?:bal(?:ance)?|limit|lmt|amt)|a/?c\.?\s*bal(?:ance)?|\bbal(?:ance)?|credit\s+limit|\blimit|clr\.?\s*bal|net\s+avail\w*|updated\s+bal\w*|total\s+bal\w*)\s*(?:is|:|-|of|rs|inr|now)?\s*[:.]?\s*$""",
    )

    fun all(text: String): List<Match> {
        val matches = mutableListOf<Match>()
        val seen = mutableSetOf<Int>()
        for (regex in listOf(prefixed, suffixed, verbAmount)) {
            for (m in regex.findAll(text)) {
                val group = m.groups[1] ?: continue
                if (!seen.add(group.range.first)) continue
                val paise = toPaise(group.value) ?: continue
                val prefix = text.substring(maxOf(0, m.range.first - 28), m.range.first)
                matches += Match(paise, group.range.first, balanceHint.containsMatchIn(prefix))
            }
        }
        return matches.sortedBy { it.start }
    }

    /** First amount that is not a balance or limit. */
    fun primary(text: String): Long? = all(text).firstOrNull { !it.isBalance }?.paise

    /** The account balance the message quotes, if any. Credit limits are not balances. */
    fun balance(text: String): Long? {
        val limit = Regex("""(?i)(limit|lmt)""")
        return all(text).firstOrNull { m ->
            m.isBalance && !limit.containsMatchIn(text.substring(maxOf(0, m.start - 28), m.start))
        }?.paise
    }

    internal fun toPaise(raw: String): Long? {
        val cleaned = raw.replace(",", "").trimEnd('.')
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0 || value > BigDecimal("10000000000")) return null
        return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }
}
