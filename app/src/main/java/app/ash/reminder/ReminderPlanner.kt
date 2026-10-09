package app.ash.reminder

import app.ash.common.Dates
import java.time.ZoneId

/** Decides when to remind about a deadline. Pure and deterministic. */
object ReminderPlanner {

    enum class Kind(val leadDays: List<Int>) {
        BILL_DUE(listOf(3, 1, 0)),
        EXPIRY(listOf(30, 7, 1, 0)),
        LONG_EXPIRY(listOf(180, 30, 7)),
        RENEWAL(listOf(2, 0)),
        SERVICE(listOf(7, 0)),
        EVENT(listOf(1, 0)),
    }

    data class Planned(val fireAt: Long, val leadDays: Int)

    private const val MIN_DELAY_MS = 5 * 60 * 1000L

    /**
     * Fire times at 9 AM local on each lead day. Past times are skipped; if every lead day has
     * passed but the deadline has not, a single reminder is planned shortly from now.
     */
    fun plan(deadlineAt: Long, kind: Kind, nowMillis: Long, zone: ZoneId): List<Planned> {
        if (deadlineAt <= nowMillis - Dates.DAY_MS) return emptyList()
        val deadlineDate = Dates.toLocalDate(deadlineAt, zone)
        val planned = kind.leadDays.map { lead ->
            Planned(Dates.morningOf(deadlineDate.minusDays(lead.toLong()), zone), lead)
        }.filter { it.fireAt > nowMillis }
        if (planned.isNotEmpty()) return planned
        val endOfDeadlineDay = Dates.startOfDay(deadlineDate.plusDays(1), zone)
        return if (nowMillis < endOfDeadlineDay) listOf(Planned(nowMillis + MIN_DELAY_MS, 0)) else emptyList()
    }
}
