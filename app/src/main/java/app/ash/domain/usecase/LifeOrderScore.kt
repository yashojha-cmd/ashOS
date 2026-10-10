package app.ash.domain.usecase

import app.ash.intelligence.PriorityEngine.Bucket
import app.ash.intelligence.PriorityEngine.DeadlineKind

/**
 * "Life in order": one number from 0 to 100 that goes up as the user deals with things.
 * Transparent on purpose: every point lost is attributed to a reason the user can act on.
 *
 *   start at 100, then subtract (each reason is capped so one category can't swamp the score):
 *   overdue payments      12 each (max 48)
 *   expired documents     10 each (max 30)
 *   due within 3 days      4 each (max 16)
 *   expiring within 7 days 4 each (max 12)
 *   waiting for review     2 each (max 10)
 *   setup steps left       3 each (max 12)
 */
object LifeOrderScore {
    enum class Reason(val perItem: Int, val cap: Int) {
        OVERDUE(12, 48),
        EXPIRED(10, 30),
        DUE_SOON(4, 16),
        EXPIRING_SOON(4, 12),
        TO_REVIEW(2, 10),
        SETUP(3, 12),
    }

    data class Part(val reason: Reason, val count: Int, val points: Int)

    data class Result(val score: Int, val parts: List<Part>) {
        val level: Level get() = when {
            score >= 85 -> Level.CALM
            score >= 60 -> Level.ATTENTION
            else -> Level.BUSY
        }
        /** The single most useful thing to do next. */
        val topReason: Reason? get() = parts.maxByOrNull { it.points }?.reason
    }

    enum class Level { CALM, ATTENTION, BUSY }

    fun compute(openItems: List<AttentionItem>, reviewCount: Int, setupStepsLeft: Int): Result {
        val counts = mutableMapOf<Reason, Int>()
        fun bump(r: Reason) { counts[r] = (counts[r] ?: 0) + 1 }
        for (item in openItems) {
            if (item.result.bucket == Bucket.RECENTLY_HANDLED || item.result.bucket == Bucket.HIDDEN) continue
            val days = item.result.daysLeft ?: continue
            when (item.deadlineKind) {
                DeadlineKind.DUE -> when {
                    days < 0 -> bump(Reason.OVERDUE)
                    days <= 3 -> bump(Reason.DUE_SOON)
                }
                DeadlineKind.EXPIRES -> when {
                    days < 0 -> bump(Reason.EXPIRED)
                    days <= 7 -> bump(Reason.EXPIRING_SOON)
                }
                DeadlineKind.EVENT, DeadlineKind.NONE -> Unit
            }
        }
        if (reviewCount > 0) counts[Reason.TO_REVIEW] = reviewCount
        if (setupStepsLeft > 0) counts[Reason.SETUP] = setupStepsLeft
        val parts = counts.map { (reason, count) -> Part(reason, count, minOf(count * reason.perItem, reason.cap)) }
            .sortedByDescending { it.points }
        return Result((100 - parts.sumOf { it.points }).coerceIn(0, 100), parts)
    }
}
