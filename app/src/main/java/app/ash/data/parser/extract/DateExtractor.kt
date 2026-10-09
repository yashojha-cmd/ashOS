package app.ash.data.parser.extract

import app.ash.data.parser.ParseContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Deterministic date extraction for Indian notification formats:
 * 21/10/2026, 21-10-26, 2026-10-21, 21 Oct, 21st October 2026, Oct 21, 21-OCT-26,
 * today / tomorrow / in 3 days / by Friday, and the Hinglish "aaj".
 * Missing years are inferred relative to today (future-biased for deadlines, past-biased for transactions).
 */
object DateExtractor {
    data class Found(val date: LocalDate, val start: Int, val end: Int)

    private const val MONTHS = "jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec"

    private val iso = Regex("""\b(20\d{2})-(\d{1,2})-(\d{1,2})\b""")
    private val numeric = Regex("""\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{4}|\d{2})\b""")
    private val dayMonth = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?[\s-]*($MONTHS)[a-z]*\.?(?:[\s,-]*(\d{4}|\d{2})(?!\d|:))?""")
    private val monthDay = Regex("""(?i)\b($MONTHS)[a-z]*\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s*(\d{4}))?""")
    private val dayAfterTomorrow = Regex("""(?i)\bday after tomorrow\b""")
    private val tomorrow = Regex("""(?i)\b(tomorrow|tmrw|tmr)\b""")
    private val today = Regex("""(?i)\b(today|tonight|aaj)\b""")
    private val inDays = Regex("""(?i)\b(?:in|within|next)\s+(\d{1,3})\s+days?\b""")
    private val weekday = Regex("""(?i)\b(?:on|by|this|before)\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""")

    fun all(text: String, ctx: ParseContext, preferFuture: Boolean = true): List<Found> {
        val found = mutableListOf<Found>()
        fun add(date: LocalDate?, range: IntRange) {
            if (date != null) found += Found(date, range.first, range.last + 1)
        }
        iso.findAll(text).forEach { m ->
            add(safe(m.int(1), m.int(2), m.int(3)), m.range)
        }
        numeric.findAll(text).forEach { m ->
            add(safe(year(m.groupValues[3]), m.int(2), m.int(1)), m.range)
        }
        dayMonth.findAll(text).forEach { m ->
            val month = month(m.groupValues[2])
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.let(::year)
            add(resolve(m.int(1), month, y, ctx, preferFuture), m.range)
        }
        monthDay.findAll(text).forEach { m ->
            val month = month(m.groupValues[1])
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            add(resolve(m.int(2), month, y, ctx, preferFuture), m.range)
        }
        dayAfterTomorrow.findAll(text).forEach { add(ctx.today.plusDays(2), it.range) }
        tomorrow.findAll(text).forEach { add(ctx.today.plusDays(1), it.range) }
        today.findAll(text).forEach { add(ctx.today, it.range) }
        inDays.findAll(text).forEach { m -> add(ctx.today.plusDays(m.int(1).toLong()), m.range) }
        weekday.findAll(text).forEach { m ->
            val dow = DayOfWeek.valueOf(m.groupValues[1].uppercase())
            add(ctx.today.with(TemporalAdjusters.nextOrSame(dow)), m.range)
        }
        // Earlier patterns are more specific; drop anything overlapping an already accepted span.
        val accepted = mutableListOf<Found>()
        for (f in found) {
            if (accepted.none { it.start < f.end && f.start < it.end }) accepted += f
        }
        return accepted.sortedBy { it.start }
    }

    fun first(text: String, ctx: ParseContext, preferFuture: Boolean = true): LocalDate? =
        all(text, ctx, preferFuture).firstOrNull()?.date

    /** The date that follows one of [keywords] most closely (within 45 characters). */
    fun near(text: String, ctx: ParseContext, keywords: Regex, preferFuture: Boolean = true): LocalDate? {
        val dates = all(text, ctx, preferFuture)
        if (dates.isEmpty()) return null
        var best: Found? = null
        var bestDistance = Int.MAX_VALUE
        for (k in keywords.findAll(text)) {
            for (d in dates) {
                val distance = d.start - k.range.last
                if (distance in 0..45 && distance < bestDistance) {
                    best = d
                    bestDistance = distance
                }
            }
        }
        return best?.date
    }

    private fun resolve(day: Int, month: Int, year: Int?, ctx: ParseContext, preferFuture: Boolean): LocalDate? {
        if (year != null) return safe(year, month, day)
        val candidate = safe(ctx.today.year, month, day) ?: return null
        return when {
            preferFuture && candidate.isBefore(ctx.today.minusDays(7)) -> candidate.plusYears(1)
            !preferFuture && candidate.isAfter(ctx.today.plusDays(7)) -> candidate.minusYears(1)
            else -> candidate
        }
    }

    private fun safe(y: Int, m: Int, d: Int): LocalDate? =
        if (m !in 1..12 || d !in 1..31 || y !in 2000..2100) null else runCatching { LocalDate.of(y, m, d) }.getOrNull()

    private fun year(raw: String): Int = raw.toInt().let { if (raw.length == 2) 2000 + it else it }

    private fun month(raw: String): Int = when (raw.lowercase().take(3)) {
        "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
        "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; else -> 12
    }

    private fun MatchResult.int(i: Int) = groupValues[i].toInt()
}
