package app.ash.intelligence

import app.ash.domain.model.RecurrencePeriod
import kotlin.math.abs

/**
 * Finds recurring payments in local transaction history (§17):
 * at least [minOccurrences] expenses to the same merchant, amounts within 10% of each other,
 * spaced roughly a week, month, quarter or year apart.
 */
object SubscriptionDetector {
    data class Payment(val merchant: String, val amountPaise: Long, val occurredAt: Long)

    data class Detected(
        val merchant: String,
        val amountPaise: Long,
        val period: RecurrencePeriod,
        val lastAt: Long,
        val nextAt: Long,
        val occurrences: Int,
    )

    private const val DAY = 24L * 60 * 60 * 1000

    private val windows = listOf(
        RecurrencePeriod.WEEKLY to 5..9,
        RecurrencePeriod.MONTHLY to 26..35,
        RecurrencePeriod.QUARTERLY to 85..97,
        RecurrencePeriod.YEARLY to 350..380,
    )

    fun detect(payments: List<Payment>, minOccurrences: Int = 2): List<Detected> =
        payments.groupBy { it.merchant.trim().lowercase() }
            .values
            .mapNotNull { group -> detectFor(group.sortedBy { it.occurredAt }, minOccurrences) }
            .sortedByDescending { it.amountPaise }

    private fun detectFor(sorted: List<Payment>, minOccurrences: Int): Detected? {
        if (sorted.size < minOccurrences) return null
        // Work backwards from the latest payment and keep the chain of similar amounts.
        val latest = sorted.last()
        val similar = sorted.filter { abs(it.amountPaise - latest.amountPaise) <= latest.amountPaise / 10 }
        if (similar.size < minOccurrences) return null
        val gaps = similar.zipWithNext { a, b -> ((b.occurredAt - a.occurredAt) / DAY).toInt() }
        if (gaps.isEmpty()) return null
        for ((period, range) in windows) {
            val matching = gaps.count { it in range }
            if (matching >= (gaps.size + 1) / 2 && gaps.last() in range) {
                return Detected(
                    merchant = latest.merchant,
                    amountPaise = latest.amountPaise,
                    period = period,
                    lastAt = latest.occurredAt,
                    nextAt = latest.occurredAt + period.approxDays * DAY,
                    occurrences = similar.size,
                )
            }
        }
        return null
    }

    /** Monthly-equivalent spend across subscriptions, in paise. */
    fun monthlyTotal(items: List<Pair<Long, RecurrencePeriod>>): Long =
        items.sumOf { (amount, period) -> amount * period.perMonth }.toLong()
}
