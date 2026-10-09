package app.ash.domain.usecase

import app.ash.common.Dates
import app.ash.domain.model.Category
import app.ash.domain.model.TransactionItem
import app.ash.domain.model.TxnDirection
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class MerchantSpend(val merchant: String, val total: Long, val count: Int)

data class CategoryChange(val category: Category, val current: Long, val previous: Long) {
    /** Percentage change; null when there was nothing to compare against. */
    val percent: Int? get() = if (previous == 0L) null else (((current - previous) * 100) / previous).toInt()
}

/** One line of a statement, with the running balance after it. */
data class StatementLine(
    val txn: TransactionItem,
    /** Null for lines that do not move the balance (self-transfers, unknown direction). */
    val balanceAfter: Long?,
)

data class Statement(
    /** 0 is the current cycle; higher numbers go back in time. */
    val cycleIndex: Int,
    val cycleCount: Int,
    /** The salary credit that opened this cycle; null when no salary has been seen yet. */
    val salary: TransactionItem?,
    val startAt: Long,
    /** Start of the next cycle (the next salary), or null for the current cycle. */
    val endAt: Long?,
    val opening: Long,
    val spent: Long,
    val received: Long,
    val remaining: Long,
    /** Oldest first. */
    val lines: List<StatementLine>,
    /** The latest line where the bank itself quoted the balance ("Avl Bal"). */
    val bankReported: TransactionItem?,
    val byCategory: List<Pair<Category, Long>>,
    /** Where the money went by payee, biggest first (payee, total, number of payments). */
    val topMerchants: List<MerchantSpend> = emptyList(),
    /** Biggest category moves against the previous cycle, if there is one. */
    val changes: List<CategoryChange> = emptyList(),
    val expectedNextSalaryAt: Long?,
    val daysLeft: Int?,
    val perDay: Long?,
)

/**
 * Builds salary-to-salary statements (§ Balance tab).
 *
 *  - Each salary credit opens a cycle; the salary amount is the opening balance.
 *  - Expenses subtract; other credits and refunds add. Self-transfers and unknown directions
 *    are listed but do not move the balance.
 *  - The cycle runs until the next salary credit. Before any salary is known, the cycle is the
 *    current calendar month with an opening balance of zero.
 */
object StatementCalculator {

    fun cycleStarts(txns: List<TransactionItem>): List<TransactionItem> = txns.filter { it.isSalary }.sortedBy { it.occurredAt }

    fun compute(txns: List<TransactionItem>, cycleIndex: Int, nowMillis: Long, zone: ZoneId): Statement {
        val sorted = txns.sortedWith(compareBy<TransactionItem> { it.occurredAt }.thenBy { it.recordId })
        val salaries = cycleStarts(sorted)
        val today = Dates.toLocalDate(nowMillis, zone)

        if (salaries.isEmpty()) {
            val start = Dates.startOfDay(today.withDayOfMonth(1), zone)
            val previousStart = Dates.startOfDay(today.withDayOfMonth(1).minusMonths(1), zone)
            val inCycle = sorted.filter { it.occurredAt >= start }
            return build(inCycle, null, start, null, 0, 1, 0, null, nowMillis, zone)
                .withInsights(inCycle, sorted.filter { it.occurredAt in previousStart until start })
        }

        val index = cycleIndex.coerceIn(0, salaries.lastIndex)
        val salary = salaries[salaries.lastIndex - index]
        val next = salaries.getOrNull(salaries.lastIndex - index + 1)
        val previous = salaries.getOrNull(salaries.lastIndex - index - 1)
        val inCycle = sorted.filter { it.occurredAt >= salary.occurredAt && (next == null || it.occurredAt < next.occurredAt) }
        val previousCycle = previous?.let { p -> sorted.filter { it.occurredAt >= p.occurredAt && it.occurredAt < salary.occurredAt } }.orEmpty()
        val expectedNext = if (next == null) expectedNextSalary(salaries, zone) else null
        return build(inCycle, salary, salary.occurredAt, next?.occurredAt, salary.amountPaise, salaries.size, index, expectedNext, nowMillis, zone)
            .withInsights(inCycle, previousCycle)
    }

    /** Top payees and the biggest category moves against the previous cycle. */
    private fun Statement.withInsights(current: List<TransactionItem>, previous: List<TransactionItem>): Statement {
        val spending = current.filter { it.direction == TxnDirection.EXPENSE }
        val merchants = spending.filter { !it.merchant.isNullOrBlank() }
            .groupBy { it.merchant!!.trim() }
            .map { (name, items) -> MerchantSpend(name, items.sumOf { it.amountPaise }, items.size) }
            .sortedByDescending { it.total }
            .take(5)
        val before = previous.filter { it.direction == TxnDirection.EXPENSE }.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amountPaise } }
        val now = spending.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amountPaise } }
        val changes = if (previous.isEmpty()) emptyList() else (before.keys + now.keys)
            .map { CategoryChange(it, now[it] ?: 0, before[it] ?: 0) }
            .filter { it.current != it.previous }
            .sortedByDescending { kotlin.math.abs(it.current - it.previous) }
            .take(3)
        return copy(topMerchants = merchants, changes = changes)
    }

    private fun build(
        inCycle: List<TransactionItem>,
        salary: TransactionItem?,
        start: Long,
        end: Long?,
        opening: Long,
        cycleCount: Int,
        index: Int,
        expectedNext: Long?,
        nowMillis: Long,
        zone: ZoneId,
    ): Statement {
        var balance = opening
        var spent = 0L
        var received = 0L
        val lines = inCycle.map { t ->
            if (t.recordId == salary?.recordId) return@map StatementLine(t, opening)
            when (t.direction) {
                TxnDirection.EXPENSE -> { balance -= t.amountPaise; spent += t.amountPaise; StatementLine(t, balance) }
                TxnDirection.INCOME, TxnDirection.REFUND -> { balance += t.amountPaise; received += t.amountPaise; StatementLine(t, balance) }
                TxnDirection.TRANSFER, TxnDirection.UNKNOWN -> StatementLine(t, null)
            }
        }
        val byCategory = inCycle.filter { it.direction == TxnDirection.EXPENSE }
            .groupBy { it.category }
            .map { (category, items) -> category to items.sumOf { it.amountPaise } }
            .sortedByDescending { it.second }
        val today = Dates.toLocalDate(nowMillis, zone)
        val daysLeft = expectedNext?.let { ChronoUnit.DAYS.between(today, Dates.toLocalDate(it, zone)).toInt().coerceAtLeast(1) }
        return Statement(
            cycleIndex = index,
            cycleCount = cycleCount,
            salary = salary,
            startAt = start,
            endAt = end,
            opening = opening,
            spent = spent,
            received = received,
            remaining = balance,
            lines = lines,
            bankReported = inCycle.lastOrNull { it.balanceAfterPaise != null },
            byCategory = byCategory,
            expectedNextSalaryAt = expectedNext,
            daysLeft = daysLeft,
            perDay = daysLeft?.let { maxOf(balance, 0) / it },
        )
    }

    /** Median gap between past salaries; one calendar month after the last if there is only one. */
    internal fun expectedNextSalary(salaries: List<TransactionItem>, zone: ZoneId): Long {
        val last = salaries.last()
        if (salaries.size < 2) {
            val day: LocalDate = Dates.toLocalDate(last.occurredAt, zone).plusMonths(1)
            return Dates.morningOf(day, zone)
        }
        val gaps = salaries.zipWithNext { a, b -> b.occurredAt - a.occurredAt }.sorted()
        return last.occurredAt + gaps[gaps.size / 2]
    }
}
