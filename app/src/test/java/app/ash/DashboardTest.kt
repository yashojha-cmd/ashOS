package app.ash

import app.ash.common.Money
import app.ash.domain.model.Category
import app.ash.domain.model.InsuranceKind
import app.ash.domain.model.Record
import app.ash.domain.model.RecordExtras
import app.ash.domain.model.RecordType
import app.ash.domain.model.TripMode
import app.ash.domain.model.TxnDirection
import app.ash.domain.usecase.AttentionItem
import app.ash.domain.usecase.AttentionKind
import app.ash.domain.usecase.AttentionTarget
import app.ash.domain.usecase.FactKind
import app.ash.domain.usecase.Headline
import app.ash.domain.usecase.LifeOrderScore
import app.ash.domain.usecase.RecordSummarizer
import app.ash.intelligence.PriorityEngine
import app.ash.intelligence.PriorityEngine.Bucket
import app.ash.intelligence.PriorityEngine.DeadlineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The redesigned Today dashboard: record summaries, the Life-in-order score and compact money. */
class DashboardTest {

    private fun record(type: RecordType, title: String, summary: String = "raw message text", extras: RecordExtras = RecordExtras(), block: Record.() -> Record = { this }) =
        Record(type = type, category = Category.OTHER, title = title, summary = summary, extras = extras).block()

    @Test fun insuranceShowsMeaningNotTheMessage() {
        val r = record(RecordType.INSURANCE, "ACKO", "ACKO: Your car policy AB123 expires on 12 Oct. Renew now", RecordExtras(insuranceKind = InsuranceKind.VEHICLE)) {
            copy(sourceLabel = "ACKO", reference = "AB123", expiresAt = 1_000L)
        }
        val s = RecordSummarizer.summarize(r)
        assertEquals("ACKO", s.subject)
        assertEquals(Headline.Expires(InsuranceKind.VEHICLE, 1_000L), s.headline)
        // Policy number becomes a fact; the source label is dropped because it repeats the subject.
        assertEquals(listOf(FactKind.POLICY), s.facts.map { it.kind })
    }

    @Test fun billWithDueDateIsDue() {
        val r = record(RecordType.BILL, "BESCOM") { copy(amountPaise = 214_000, dueAt = 5_000L, sourceLabel = "Messages") }
        val s = RecordSummarizer.summarize(r)
        assertEquals(Headline.Due(214_000, 5_000L), s.headline)
        assertEquals(listOf(FactKind.SOURCE), s.facts.map { it.kind })
    }

    @Test fun salaryCreditIsLabelledSalary() {
        val r = record(RecordType.TRANSACTION, "Credit", extras = RecordExtras(direction = TxnDirection.INCOME, salary = true, accountRef = "XX1234")) {
            copy(merchant = "ACME CORP", amountPaise = 8_500_000)
        }
        val s = RecordSummarizer.summarize(r)
        assertEquals("ACME CORP", s.subject)
        assertEquals(Headline.Received(8_500_000, salary = true), s.headline)
        assertTrue(s.facts.any { it.kind == FactKind.ACCOUNT && it.value == "XX1234" })
    }

    @Test fun tripCarriesRouteAndPnr() {
        val r = record(RecordType.TRIP, "IRCTC", extras = RecordExtras(tripMode = TripMode.TRAIN, tripFrom = "MMCT", tripTo = "NDLS")) {
            copy(reference = "4521367890", eventAt = 9_000L)
        }
        val s = RecordSummarizer.summarize(r)
        assertEquals(Headline.Trip(TripMode.TRAIN, "MMCT", "NDLS", 9_000L), s.headline)
        assertEquals(FactKind.PNR, s.facts.first().kind)
    }

    @Test fun unstructuredFallsBackToFirstCleanSentence() {
        val r = record(RecordType.GENERIC, "Notice", "HDFC: Your KYC is pending. Update by visiting branch. T&C apply")
        assertEquals(Headline.Note("Your KYC is pending."), RecordSummarizer.summarize(r).headline)
    }

    private fun item(kind: DeadlineKind, days: Long, bucket: Bucket = Bucket.NEEDS_ATTENTION) = AttentionItem(
        key = "k$kind$days", title = "x", category = Category.BILLS, kind = AttentionKind.BILL, amountPaise = null,
        deadlineAt = 0L, deadlineKind = kind,
        result = PriorityEngine.Result(50, PriorityEngine.Urgency.HIGH, bucket, days, emptyList()),
        target = AttentionTarget.RecordTarget(1),
    )

    @Test fun lifeOrderIsPerfectWhenNothingNeedsYou() {
        val r = LifeOrderScore.compute(listOf(item(DeadlineKind.DUE, 20, Bucket.COMING_UP)), reviewCount = 0, setupStepsLeft = 0)
        assertEquals(100, r.score)
        assertEquals(LifeOrderScore.Level.CALM, r.level)
        assertTrue(r.parts.isEmpty())
    }

    @Test fun lifeOrderExplainsEveryPointLost() {
        val items = listOf(
            item(DeadlineKind.DUE, -2), item(DeadlineKind.DUE, -1), // 2 overdue: 24
            item(DeadlineKind.EXPIRES, -5),                          // 1 expired: 10
            item(DeadlineKind.DUE, 1),                               // due soon: 4
            item(DeadlineKind.EXPIRES, 6),                           // expiring: 4
            item(DeadlineKind.EVENT, 0),                             // events never cost points
            item(DeadlineKind.DUE, -9, Bucket.RECENTLY_HANDLED),     // handled never costs points
        )
        val r = LifeOrderScore.compute(items, reviewCount = 3, setupStepsLeft = 2) // review 6, setup 6
        assertEquals(100 - 24 - 10 - 4 - 4 - 6 - 6, r.score)
        assertEquals(100 - r.score, r.parts.sumOf { it.points })
        assertEquals(LifeOrderScore.Reason.OVERDUE, r.topReason)
        assertEquals(LifeOrderScore.Level.BUSY, r.level)
    }

    @Test fun lifeOrderCapsEachReason() {
        val r = LifeOrderScore.compute(List(10) { item(DeadlineKind.DUE, -1) }, 0, 0)
        assertEquals(100 - 48, r.score)
    }

    @Test fun compactMoneyUsesIndianUnits() {
        assertEquals("₹850", Money.formatCompact(85_000))
        assertEquals("₹12.4K", Money.formatCompact(1_240_000))
        assertEquals("₹3.2L", Money.formatCompact(32_000_000))
        assertEquals("₹1.1Cr", Money.formatCompact(1_100_000_000))
        assertEquals("-₹2K", Money.formatCompact(-200_000))
    }
}
