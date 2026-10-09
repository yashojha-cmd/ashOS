package app.ash.intelligence

import app.ash.common.Dates
import app.ash.data.parser.ParseContext
import app.ash.domain.model.Category
import app.ash.domain.model.DocType
import app.ash.domain.model.Importance
import app.ash.domain.model.InsuranceKind
import app.ash.domain.model.RecurrencePeriod
import app.ash.intelligence.PriorityEngine.Bucket
import app.ash.intelligence.PriorityEngine.DeadlineKind
import app.ash.reminder.ReminderPlanner
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class IntelligenceTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 10)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val ctx = ParseContext(today, zone, now)
    private fun day(offset: Long) = Dates.morningOf(today.plusDays(offset), zone)

    private fun eval(category: Category, deadline: Long?, kind: DeadlineKind, handled: Boolean = false, handledAt: Long? = null, action: Boolean = false, importance: Importance = Importance.MEDIUM) =
        PriorityEngine.evaluate(PriorityEngine.Input(category, importance, deadline, kind, handled, handledAt, action), now, today, zone)

    // ---- Priority engine ----

    @Test fun billDueTodayNeedsAttention() {
        val r = eval(Category.BILLS, day(0), DeadlineKind.DUE)
        assertThat(r.urgency).isEqualTo(PriorityEngine.Urgency.URGENT)
        assertThat(r.score).isEqualTo(40 + 30 + 20)
        assertThat(r.bucket).isEqualTo(Bucket.NEEDS_ATTENTION)
    }

    @Test fun insuranceIn23DaysIsComingUp() {
        val r = eval(Category.INSURANCE, day(23), DeadlineKind.EXPIRES)
        assertThat(r.urgency).isEqualTo(PriorityEngine.Urgency.MEDIUM)
        assertThat(r.bucket).isEqualTo(Bucket.COMING_UP)
        assertThat(r.daysLeft).isEqualTo(23)
    }

    @Test fun overdueBillGetsPenalty() {
        val r = eval(Category.BILLS, day(-2), DeadlineKind.DUE)
        assertThat(r.parts.map { it.part }).contains(PriorityEngine.Part.OVERDUE)
        assertThat(r.score).isEqualTo(100)
    }

    @Test fun handledRecentlyIsGreenThenHidden() {
        assertThat(eval(Category.BILLS, day(0), DeadlineKind.DUE, handled = true, handledAt = now - 1000).bucket).isEqualTo(Bucket.RECENTLY_HANDLED)
        assertThat(eval(Category.BILLS, day(0), DeadlineKind.DUE, handled = true, handledAt = now - 3 * Dates.DAY_MS).bucket).isEqualTo(Bucket.HIDDEN)
    }

    @Test fun pastEventsAreHidden() {
        assertThat(eval(Category.TRAVEL, day(-1), DeadlineKind.EVENT).bucket).isEqualTo(Bucket.HIDDEN)
    }

    @Test fun informationalWithoutDeadline() {
        assertThat(eval(Category.SHOPPING, null, DeadlineKind.NONE).bucket).isEqualTo(Bucket.INFORMATIONAL)
    }

    @Test fun scoreIsTheSumOfItsExplainedParts() {
        val r = eval(Category.GOVERNMENT, day(5), DeadlineKind.DUE, action = true)
        assertThat(r.score).isEqualTo(r.parts.sumOf { it.points })
    }

    // ---- Search query parser ----

    @Test fun bikeInsurance() {
        val q = SearchQueryParser.parse("bike insurance", ctx)
        assertThat(q.categories).containsAtLeast(Category.VEHICLE, Category.INSURANCE)
        assertThat(q.insuranceKind).isEqualTo(InsuranceKind.VEHICLE)
        assertThat(q.terms).containsExactly("bike", "insurance")
    }

    @Test fun amountQuery() {
        assertThat(SearchQueryParser.parse("₹5000", ctx).amountPaise).isEqualTo(500_000)
        assertThat(SearchQueryParser.parse("rs 1,499", ctx).amountPaise).isEqualTo(149_900)
        assertThat(SearchQueryParser.parse("2026", ctx).amountPaise).isNull()
    }

    @Test fun monthQuery() {
        val q = SearchQueryParser.parse("September", ctx)
        assertThat(q.fromMillis).isEqualTo(Dates.startOfDay(LocalDate.of(2026, 9, 1), zone))
        assertThat(q.toMillis).isEqualTo(Dates.startOfDay(LocalDate.of(2026, 10, 1), zone))
        // A future month in this year means last year's.
        assertThat(SearchQueryParser.parse("december", ctx).fromMillis).isEqualTo(Dates.startOfDay(LocalDate.of(2025, 12, 1), zone))
    }

    @Test fun expiringDocuments() {
        val q = SearchQueryParser.parse("documents expiring", ctx)
        assertThat(q.expiring).isTrue()
        assertThat(q.categories).contains(Category.DOCUMENTS)
        assertThat(q.terms).isEmpty()
    }

    @Test fun ftsExpressionIsSanitised() {
        assertThat(SearchQueryParser.ftsExpression(listOf("amazon", "a\"b*"))).isEqualTo("amazon* OR ab*")
        assertThat(SearchQueryParser.ftsExpression(listOf("x"))).isNull()
    }

    // ---- Subscription detection ----

    @Test fun detectsMonthlyNetflix() {
        val pays = listOf(
            SubscriptionDetector.Payment("Netflix", 64_900, day(-65)),
            SubscriptionDetector.Payment("Netflix", 64_900, day(-35)),
            SubscriptionDetector.Payment("Netflix", 64_900, day(-5)),
            SubscriptionDetector.Payment("Swiggy", 45_000, day(-3)),
            SubscriptionDetector.Payment("Swiggy", 12_000, day(-1)),
        )
        val found = SubscriptionDetector.detect(pays)
        assertThat(found).hasSize(1)
        assertThat(found[0].merchant).isEqualTo("Netflix")
        assertThat(found[0].period).isEqualTo(RecurrencePeriod.MONTHLY)
        assertThat(found[0].nextAt).isEqualTo(day(-5) + 30 * Dates.DAY_MS)
    }

    @Test fun detectsYearly() {
        val pays = listOf(
            SubscriptionDetector.Payment("Prime Video", 149_900, day(-370)),
            SubscriptionDetector.Payment("Prime Video", 149_900, day(-5)),
        )
        assertThat(SubscriptionDetector.detect(pays).single().period).isEqualTo(RecurrencePeriod.YEARLY)
    }

    @Test fun monthlyTotal() {
        val total = SubscriptionDetector.monthlyTotal(listOf(64_900L to RecurrencePeriod.MONTHLY, 120_000L to RecurrencePeriod.YEARLY))
        assertThat(total).isEqualTo(64_900 + 10_000)
    }

    // ---- OCR field extraction ----

    @Test fun panCard() {
        val text = "INCOME TAX DEPARTMENT\nGOVT. OF INDIA\nPermanent Account Number Card\nABCDE1234F\nRAHUL SHARMA\n01/01/1990"
        val f = DocumentFieldExtractor.extract(text, ctx)
        assertThat(f.docType).isEqualTo(DocType.PAN)
        assertThat(f.documentNumber).isEqualTo("ABCDE1234F")
    }

    @Test fun aadhaarKeepsOnlyLastFourDigits() {
        val f = DocumentFieldExtractor.extract("Government of India\nआधार\n1234 5678 9012\nMera Aadhaar, Meri Pehchaan", ctx)
        assertThat(f.docType).isEqualTo(DocType.AADHAAR)
        assertThat(f.documentNumber).isEqualTo("XXXX XXXX 9012")
    }

    @Test fun insurancePolicyWithExpiry() {
        val text = "ACKO General Insurance\nPolicy Schedule\nPolicy No: ACK123456789\nVehicle: MH12AB1234\nPeriod of insurance 22/10/2025 to 21/10/2026\nPremium Rs 2,345"
        val f = DocumentFieldExtractor.extract(text, ctx)
        assertThat(f.docType).isEqualTo(DocType.INSURANCE)
        assertThat(f.documentNumber).isEqualTo("ACK123456789")
        assertThat(f.expiresOn).isEqualTo(LocalDate.of(2026, 10, 21))
        assertThat(f.vehicleReg).isEqualTo("MH12AB1234")
    }

    @Test fun drivingLicenceValidTill() {
        val f = DocumentFieldExtractor.extract("Driving Licence\nDL No: MH12 20110012345\nValid Till: 14-03-2031", ctx)
        assertThat(f.docType).isEqualTo(DocType.DRIVING_LICENCE)
        assertThat(f.expiresOn).isEqualTo(LocalDate.of(2031, 3, 14))
    }

    @Test fun emptyTextGivesNothing() {
        assertThat(DocumentFieldExtractor.extract("", ctx).confidence).isEqualTo(0f)
    }

    // ---- Reminder planner ----

    @Test fun expiryLeadDays() {
        val planned = ReminderPlanner.plan(day(40), ReminderPlanner.Kind.EXPIRY, now, zone)
        assertThat(planned.map { it.leadDays }).containsExactly(30, 7, 1, 0).inOrder()
        assertThat(planned.first().fireAt).isEqualTo(day(10))
    }

    @Test fun pastLeadDaysSkipped() {
        assertThat(ReminderPlanner.plan(day(2), ReminderPlanner.Kind.BILL_DUE, now, zone).map { it.leadDays }).containsExactly(1, 0)
    }

    @Test fun dueTodayAfterNineGetsOneSoonReminder() {
        val planned = ReminderPlanner.plan(day(0), ReminderPlanner.Kind.BILL_DUE, now, zone)
        assertThat(planned).hasSize(1)
        assertThat(planned[0].fireAt).isGreaterThan(now)
    }

    @Test fun longPastDeadlineGetsNothing() {
        assertThat(ReminderPlanner.plan(day(-3), ReminderPlanner.Kind.EXPIRY, now, zone)).isEmpty()
    }
}
