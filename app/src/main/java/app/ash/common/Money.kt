package app.ash.common

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money is always stored as Long paise to avoid floating-point errors.
 * Formatting uses Indian digit grouping (1,23,456.78) regardless of device locale.
 */
object Money {
    fun rupeesToPaise(rupees: BigDecimal): Long = rupees.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()

    fun format(paise: Long, showPaise: Boolean = paise % 100 != 0L): String {
        val negative = paise < 0
        val abs = kotlin.math.abs(paise)
        val rupees = abs / 100
        val fraction = abs % 100
        val grouped = groupIndian(rupees.toString())
        val body = if (showPaise) "$grouped.${fraction.toString().padStart(2, '0')}" else grouped
        return (if (negative) "-₹" else "₹") + body
    }

    /** Short Indian form for charts: ₹850, ₹12.4K, ₹3.2L, ₹1.1Cr. */
    fun formatCompact(paise: Long): String {
        val r = kotlin.math.abs(paise) / 100.0
        val sign = if (paise < 0) "-" else ""
        fun one(v: Double) = if (v >= 100) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v).removeSuffix(".0")
        return sign + when {
            r >= 1_00_00_000 -> "₹" + one(r / 1_00_00_000) + "Cr"
            r >= 1_00_000 -> "₹" + one(r / 1_00_000) + "L"
            r >= 1_000 -> "₹" + one(r / 1_000) + "K"
            else -> "₹" + r.toLong()
        }
    }

    /** Groups the last three digits, then every two: 1234567 -> 12,34,567. */
    fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val sb = StringBuilder()
        val firstLen = rest.length % 2
        if (firstLen > 0) sb.append(rest, 0, firstLen)
        var i = firstLen
        while (i < rest.length) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(rest, i, i + 2)
            i += 2
        }
        return "$sb,$last3"
    }

    /** Parses user input like "2140", "2,140.50" into paise; null if invalid. */
    fun parseInput(input: String): Long? {
        val cleaned = input.replace(",", "").replace("₹", "").trim()
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }?.let(::rupeesToPaise)
    }

    fun toInput(paise: Long?): String {
        if (paise == null) return ""
        return if (paise % 100 == 0L) (paise / 100).toString() else BigDecimal(paise).movePointLeft(2).toPlainString()
    }
}
