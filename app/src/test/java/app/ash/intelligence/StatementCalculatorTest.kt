package app.ash.intelligence

import app.ash.common.Dates
import app.ash.domain.model.Category
import app.ash.domain.model.TransactionItem
import app.ash.domain.model.TxnDirection
import app.ash.domain.usecase.StatementCalculator
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatementCalculatorTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 10)
    private val now = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    private var nextId = 1L

    private fun at(date: LocalDate, hour: Int = 10) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun txn(
        amount: Long,
        direction: TxnDirection,
        date: LocalDate,
        salary: Boolean = false,
        category: Category = Category.SHOPPING,
        balance: Long? = null,
        merchant: String? = null,
    ) = TransactionItem(
        recordId = nextId++, amountPaise = amount, direction = direction, merchant = merchant, accountRef = "XX1234",
        occurredAt = at(date), category = category, confidence = 1f, userCorrected = false, vehicleId = null,
        sourceLabel = "SMS", isSalary = salary, balanceAfterPaise = balance,
    )

    private val sep1 = LocalDate.of(2026, 9, 1)
    private val oct1 = LocalDate.of(2026, 10, 1)

    private val history = listOf(
        txn(8_500_000, TxnDirection.INCOME, sep1, salary = true, category = Category.FINANCE, merchant = "ACME"),
        txn(1_250_000, TxnDirection.EXPENSE, sep1.plusDays(3), category = Category.HOME),
        txn(320_000, TxnDirection.EXPENSE, sep1.plusDays(15)),
        txn(8_500_000, TxnDirection.INCOME, oct1, salary = true, category = Category.FINANCE, merchant = "ACME"),
        txn(149_900, TxnDirection.EXPENSE, oct1.plusDays(2)),
        txn(79_900, TxnDirection.REFUND, oct1.plusDays(3)),
        txn(50_000, TxnDirection.INCOME, oct1.plusDays(4), category = Category.FINANCE),
        txn(2_000_000, TxnDirection.TRANSFER, oct1.plusDays(5), category = Category.FINANCE),
        txn(64_900, TxnDirection.EXPENSE, oct1.plusDays(6), category = Category.SUBSCRIPTIONS, balance = 13_000_000),
    )

    @Test fun currentCycleStartsAtLatestSalary() {
        val s = StatementCalculator.compute(history, 0, now, zone)
        assertThat(s.salary!!.occurredAt).isEqualTo(at(oct1))
        assertThat(s.opening).isEqualTo(8_500_000)
        assertThat(s.endAt).isNull()
        assertThat(s.cycleCount).isEqualTo(2)
    }

    @Test fun runningBalanceAndTotals() {
        val s = StatementCalculator.compute(history, 0, now, zone)
        // 85,000 − 1,499 + 799 (refund) + 500 (credit) − 649; the self-transfer is not counted.
        assertThat(s.spent).isEqualTo(149_900 + 64_900)
        assertThat(s.received).isEqualTo(79_900 + 50_000)
        assertThat(s.remaining).isEqualTo(8_500_000 - 149_900 + 79_900 + 50_000 - 64_900)
        assertThat(s.lines.map { it.balanceAfter }).containsExactly(
            8_500_000L, 8_350_100L, 8_430_000L, 8_480_000L, null, 8_415_100L,
        ).inOrder()
    }

    @Test fun resetsOnNewSalaryAndPreviousCycleIsBounded() {
        val previous = StatementCalculator.compute(history, 1, now, zone)
        assertThat(previous.salary!!.occurredAt).isEqualTo(at(sep1))
        assertThat(previous.endAt).isEqualTo(at(oct1))
        assertThat(previous.remaining).isEqualTo(8_500_000 - 1_250_000 - 320_000)
        assertThat(previous.lines).hasSize(3)
        assertThat(previous.expectedNextSalaryAt).isNull()
    }

    @Test fun cycleIndexIsClamped() {
        assertThat(StatementCalculator.compute(history, 99, now, zone).cycleIndex).isEqualTo(1)
        assertThat(StatementCalculator.compute(history, -5, now, zone).cycleIndex).isEqualTo(0)
    }

    @Test fun expectedNextSalaryAndDailyBudget() {
        val s = StatementCalculator.compute(history, 0, now, zone)
        // Gap Sep 1 → Oct 1 is 30 days, so the next salary is expected on Oct 31: 21 days from Oct 10.
        assertThat(Dates.toLocalDate(s.expectedNextSalaryAt!!, zone)).isEqualTo(LocalDate.of(2026, 10, 31))
        assertThat(s.daysLeft).isEqualTo(21)
        assertThat(s.perDay).isEqualTo(s.remaining / 21)
    }

    @Test fun categoriesAndBankReportedBalance() {
        val s = StatementCalculator.compute(history, 0, now, zone)
        assertThat(s.byCategory.first().first).isEqualTo(Category.SHOPPING)
        assertThat(s.bankReported!!.balanceAfterPaise).isEqualTo(13_000_000)
    }

    @Test fun noSalaryFallsBackToThisMonth() {
        val noSalary = listOf(
            txn(100_000, TxnDirection.EXPENSE, LocalDate.of(2026, 9, 20)),
            txn(50_000, TxnDirection.EXPENSE, oct1.plusDays(1)),
        )
        val s = StatementCalculator.compute(noSalary, 0, now, zone)
        assertThat(s.salary).isNull()
        assertThat(s.opening).isEqualTo(0)
        assertThat(s.lines).hasSize(1)
        assertThat(s.remaining).isEqualTo(-50_000)
        assertThat(s.daysLeft).isNull()
    }

    @Test fun singleSalaryExpectsOneMonthLater() {
        val one = listOf(txn(8_500_000, TxnDirection.INCOME, LocalDate.of(2026, 10, 5), salary = true))
        val s = StatementCalculator.compute(one, 0, now, zone)
        assertThat(Dates.toLocalDate(s.expectedNextSalaryAt!!, zone)).isEqualTo(LocalDate.of(2026, 11, 5))
    }

    @Test fun insightsTopPayeesAndChangesAgainstLastCycle() {
        val s = StatementCalculator.compute(history, 0, now, zone)
        // Shopping: last cycle 3,200, this cycle 1,499 → down; Home: 12,500 → 0 → down; Subscriptions new.
        val byCategory = s.changes.associateBy { it.category }
        assertThat(byCategory.getValue(Category.HOME).current).isEqualTo(0)
        assertThat(byCategory.getValue(Category.HOME).percent).isEqualTo(-100)
        assertThat(s.changes.first().category).isEqualTo(Category.HOME) // biggest absolute move first
        assertThat(StatementCalculator.compute(history, 1, now, zone).changes).isEmpty() // nothing older to compare
    }

    @Test fun categoryChangePercent() {
        assertThat(app.ash.domain.usecase.CategoryChange(Category.SHOPPING, 150, 100).percent).isEqualTo(50)
        assertThat(app.ash.domain.usecase.CategoryChange(Category.SHOPPING, 150, 0).percent).isNull()
    }
}
