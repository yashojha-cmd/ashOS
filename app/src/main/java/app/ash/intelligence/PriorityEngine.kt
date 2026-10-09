package app.ash.intelligence

import app.ash.common.Dates
import app.ash.domain.model.Category
import app.ash.domain.model.Importance
import java.time.LocalDate
import java.time.ZoneId

/**
 * Transparent, deterministic prioritisation (§15).
 *
 *   score = urgency + proximity + importance + overdue + action
 *
 *   urgency    URGENT 40 · HIGH 25 · MEDIUM 10 · LOW 0
 *   proximity  today/overdue 30 · ≤3d 20 · ≤7d 12 · ≤30d 5
 *   importance max(category weight, record importance) 0–20
 *   overdue    +10 when the deadline has passed and it is not handled
 *   action     +15 when the source said action is required
 *
 *   ≥ 60 → Needs attention · 25–59 → Coming up · otherwise informational
 */
object PriorityEngine {

    enum class Urgency(val points: Int) { URGENT(40), HIGH(25), MEDIUM(10), LOW(0) }

    enum class Bucket { NEEDS_ATTENTION, COMING_UP, RECENTLY_HANDLED, INFORMATIONAL, HIDDEN }

    enum class DeadlineKind { DUE, EXPIRES, EVENT, NONE }

    data class Input(
        val category: Category,
        val importance: Importance,
        val deadlineAt: Long?,
        val deadlineKind: DeadlineKind,
        val handled: Boolean,
        val handledAt: Long? = null,
        val actionRequired: Boolean = false,
    )

    enum class Part { URGENCY, PROXIMITY, IMPORTANCE, OVERDUE, ACTION }

    data class ScorePart(val part: Part, val points: Int)

    data class Result(
        val score: Int,
        val urgency: Urgency,
        val bucket: Bucket,
        val daysLeft: Long?,
        val parts: List<ScorePart>,
    )

    private const val RECENT_HANDLED_MS = 48L * 60 * 60 * 1000

    fun categoryWeight(category: Category): Int = when (category) {
        Category.BILLS, Category.INSURANCE -> 20
        Category.GOVERNMENT, Category.DOCUMENTS, Category.HEALTHCARE -> 15
        Category.VEHICLE, Category.TRAVEL -> 12
        Category.FINANCE, Category.SUBSCRIPTIONS, Category.REMINDERS -> 10
        Category.EDUCATION, Category.WORK, Category.HOME -> 8
        Category.DELIVERIES, Category.SHOPPING -> 4
        Category.OTHER -> 2
    }

    fun evaluate(input: Input, nowMillis: Long, today: LocalDate, zone: ZoneId): Result {
        if (input.handled) {
            val recent = input.handledAt != null && nowMillis - input.handledAt <= RECENT_HANDLED_MS
            return Result(0, Urgency.LOW, if (recent) Bucket.RECENTLY_HANDLED else Bucket.HIDDEN, null, emptyList())
        }
        val days = input.deadlineAt?.let { Dates.daysUntil(it, today, zone) }
        val urgency = urgencyFor(days, input.deadlineKind)
        val proximity = when {
            days == null -> 0
            days <= 0 -> 30
            days <= 3 -> 20
            days <= 7 -> 12
            days <= 30 -> 5
            else -> 0
        }
        val importance = maxOf(categoryWeight(input.category), input.importance.weight)
        val overdue = if (days != null && days < 0 && input.deadlineKind != DeadlineKind.EVENT) 10 else 0
        val action = if (input.actionRequired) 15 else 0

        val parts = listOf(
            ScorePart(Part.URGENCY, urgency.points),
            ScorePart(Part.PROXIMITY, proximity),
            ScorePart(Part.IMPORTANCE, importance),
            ScorePart(Part.OVERDUE, overdue),
            ScorePart(Part.ACTION, action),
        ).filter { it.points > 0 }
        val score = parts.sumOf { it.points }.coerceAtMost(100)

        // Past events (a delivery yesterday, last week's trip) are history, not attention.
        val bucket = when {
            input.deadlineKind == DeadlineKind.EVENT && days != null && days < 0 -> Bucket.HIDDEN
            score >= 60 -> Bucket.NEEDS_ATTENTION
            score >= 25 -> Bucket.COMING_UP
            else -> Bucket.INFORMATIONAL
        }
        return Result(score, urgency, bucket, days, parts)
    }

    private fun urgencyFor(days: Long?, kind: DeadlineKind): Urgency {
        if (days == null) return Urgency.LOW
        return when (kind) {
            DeadlineKind.DUE -> when {
                days <= 0 -> Urgency.URGENT
                days <= 3 -> Urgency.HIGH
                days <= 30 -> Urgency.MEDIUM
                else -> Urgency.LOW
            }
            DeadlineKind.EXPIRES -> when {
                days <= 0 -> Urgency.URGENT
                days <= 7 -> Urgency.HIGH
                days <= 30 -> Urgency.MEDIUM
                else -> Urgency.LOW
            }
            DeadlineKind.EVENT -> when {
                days < 0 -> Urgency.LOW
                days <= 1 -> Urgency.MEDIUM
                else -> Urgency.LOW
            }
            DeadlineKind.NONE -> Urgency.LOW
        }
    }
}
