package app.ash.data.parser.extract

import java.math.BigDecimal

/** Finds rupee amounts: "₹1,499", "Rs.1499.00", "INR 1,499", "1499/-". Balances and limits are flagged. */
object AmountExtractor {
    data class Match(val paise: Long, val start: Int, val isBalance: Boolean)

    private val prefixed = Regex("""(?i)(?:₹|\bRs\.?|\bINR|\bRS\.?)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val suffixed = Regex("""(?i)\b([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:rupees|/-)""")
    private val balanceHint = Regex(
        """(?i)(avl\.?\s*bal(?:ance)?|avail(?:able)?\.?\s*(?:bal(?:ance)?|limit|lmt)|\bbal(?:ance)?|credit\s+limit|\blimit|clr\.?\s*bal)\s*(?:is|:|-|of|rs)?\s*$""",
    )

    fun all(text: String): List<Match> {
        val matches = mutableListOf<Match>()
        val seen = mutableSetOf<Int>()
        for (regex in listOf(prefixed, suffixed)) {
            for (m in regex.findAll(text)) {
                val group = m.groups[1] ?: continue
                if (!seen.add(group.range.first)) continue
                val paise = toPaise(group.value) ?: continue
                val prefix = text.substring(maxOf(0, m.range.first - 28), m.range.first)
                matches += Match(paise, m.range.first, balanceHint.containsMatchIn(prefix))
            }
        }
        return matches.sortedBy { it.start }
    }

    /** First amount that is not a balance or limit. */
    fun primary(text: String): Long? = all(text).firstOrNull { !it.isBalance }?.paise

    internal fun toPaise(raw: String): Long? {
        val cleaned = raw.replace(",", "").trimEnd('.')
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0 || value > BigDecimal("10000000000")) return null
        return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }
}
