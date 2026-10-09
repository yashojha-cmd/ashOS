package app.ash.parser

import app.ash.data.parser.ConfidencePolicy
import app.ash.data.parser.NotificationParserEngine
import app.ash.data.parser.ParseContext
import app.ash.data.parser.ParseOutcome
import app.ash.data.parser.RawNotification
import app.ash.domain.model.Category
import app.ash.domain.model.DeliveryState
import app.ash.domain.model.InsuranceKind
import app.ash.domain.model.RecordType
import app.ash.domain.model.RecurrencePeriod
import app.ash.domain.model.SourceKind
import app.ash.domain.model.TripMode
import app.ash.domain.model.TxnDirection
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Golden corpus of realistic Indian notifications (§39). Adding a new format means adding one case.
 * Formats change without notice, so these tests describe intent (type, amount, dates), not exact text.
 */
class ParserGoldenTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 10) // Saturday
    private val ctx = ParseContext(today, zone, today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
    private val engine = NotificationParserEngine()

    private enum class Band { AUTO, REVIEW_OR_BETTER, LOW }

    private data class Case(
        val name: String,
        val kind: SourceKind,
        val title: String,
        val text: String,
        val type: RecordType? = null,
        val band: Band = Band.AUTO,
        val amountPaise: Long? = null,
        val direction: TxnDirection? = null,
        val category: Category? = null,
        val merchant: String? = null,
        val dueOn: LocalDate? = null,
        val expiresOn: LocalDate? = null,
        val eventOn: LocalDate? = null,
        val reference: String? = null,
        val delivery: DeliveryState? = null,
        val tripMode: TripMode? = null,
        val insuranceKind: InsuranceKind? = null,
        val recurrence: RecurrencePeriod? = null,
        val handled: Boolean? = null,
        val action: Boolean? = null,
        val sensitive: Boolean = false,
        val salary: Boolean? = null,
        val balance: Long? = null,
        val label: String = "Source",
    )

    private val cases = listOf(
        // ---- Bank SMS ----
        Case("HDFC UPI debit", SourceKind.SMS, "HDFC Bank",
            "Rs.1,499.00 debited from A/c XX4321 on 09-10-26 to VPA swiggy@icici (UPI Ref No 628312345678). Not you? Call 18002586161",
            type = RecordType.TRANSACTION, amountPaise = 149_900, direction = TxnDirection.EXPENSE, merchant = "Swiggy", category = Category.SHOPPING),
        Case("SBI NEFT credit with balance", SourceKind.SMS, "SBI",
            "Dear Customer, your A/c X1234 has been credited with Rs 25,000.00 on 01Oct26 by NEFT from ACME PVT LTD. Avl Bal Rs 1,02,345.50",
            type = RecordType.TRANSACTION, amountPaise = 2_500_000, direction = TxnDirection.INCOME, eventOn = LocalDate.of(2026, 10, 1)),
        Case("ICICI card spend at Netflix", SourceKind.SMS, "ICICI Bank",
            "INR 649.00 spent on ICICI Bank Card XX9876 on 05-Oct-26 at NETFLIX.COM. Avl Lmt: INR 1,20,000.00",
            type = RecordType.TRANSACTION, amountPaise = 64_900, direction = TxnDirection.EXPENSE, merchant = "Netflix", category = Category.SUBSCRIPTIONS),
        Case("Axis refund", SourceKind.SMS, "Axis Bank",
            "Refund of Rs 799.00 credited to your Axis Bank A/c XX5555 from AMAZON",
            type = RecordType.TRANSACTION, amountPaise = 79_900, direction = TxnDirection.REFUND),
        Case("Hinglish debit", SourceKind.SMS, "Bank",
            "Aapke khate XX1122 se Rs 300 kat gaya. UPI Ref 123456789012",
            type = RecordType.TRANSACTION, amountPaise = 30_000, direction = TxnDirection.EXPENSE),
        Case("Hinglish credit", SourceKind.SMS, "Bank",
            "Rs 1500 aapke khate XX1122 mein jama hua",
            type = RecordType.TRANSACTION, amountPaise = 150_000, direction = TxnDirection.INCOME, band = Band.REVIEW_OR_BETTER),
        Case("OTP is never parsed", SourceKind.SMS, "HDFC Bank",
            "123456 is your OTP for txn of Rs 2,000 at AMAZON. Do not share it with anyone.", sensitive = true),
        Case("Loan promo is not a transaction", SourceKind.SMS, "Bank",
            "Congratulations! Get a pre-approved personal loan up to Rs 5,00,000. Apply now", band = Band.LOW),

        // ---- UPI apps ----
        Case("GPay paid", SourceKind.UPI, "Payment successful", "You paid ₹250 to Raju Kirana",
            type = RecordType.TRANSACTION, amountPaise = 25_000, direction = TxnDirection.EXPENSE, merchant = "Raju Kirana", label = "Google Pay"),
        Case("PhonePe received", SourceKind.UPI, "Money received", "Received ₹500 from Amit Sharma",
            type = RecordType.TRANSACTION, amountPaise = 50_000, direction = TxnDirection.INCOME, merchant = "Amit Sharma", label = "PhonePe"),
        Case("Paytm failed payment", SourceKind.UPI, "Payment failed", "Payment of ₹300 to XYZ Store failed. Money will be refunded if debited.",
            band = Band.LOW, label = "Paytm"),

        // ---- Shopping & delivery ----
        Case("Amazon order placed", SourceKind.SHOPPING, "Order confirmed",
            "Your order #407-1234567-8901234 for boAt Airdopes has been placed.",
            type = RecordType.DELIVERY, delivery = DeliveryState.ORDERED, category = Category.SHOPPING, label = "Amazon"),
        Case("Amazon delivered", SourceKind.SHOPPING, "Delivered",
            "Your package with boAt Airdopes was delivered. Order #407-1234567-8901234",
            type = RecordType.DELIVERY, delivery = DeliveryState.DELIVERED, handled = true, label = "Amazon"),
        Case("Flipkart out for delivery", SourceKind.SHOPPING, "Out for delivery",
            "Your Flipkart order is out for delivery today.",
            type = RecordType.DELIVERY, delivery = DeliveryState.OUT_FOR_DELIVERY, eventOn = LocalDate.of(2026, 10, 10), label = "Flipkart"),
        Case("Flipkart arriving", SourceKind.SHOPPING, "Shipped",
            "Your order has been shipped and is arriving by 14 Oct. Order ID OD123456789012",
            type = RecordType.DELIVERY, delivery = DeliveryState.SHIPPED, eventOn = LocalDate.of(2026, 10, 14), reference = "OD123456789012", label = "Flipkart"),
        Case("Swiggy on the way", SourceKind.FOOD, "Swiggy", "Your order is on the way. Arriving in 20 mins",
            type = RecordType.DELIVERY, delivery = DeliveryState.OUT_FOR_DELIVERY, label = "Swiggy"),
        Case("Zomato delivered", SourceKind.FOOD, "Zomato", "Order delivered! Enjoy your meal.",
            type = RecordType.DELIVERY, delivery = DeliveryState.DELIVERED, label = "Zomato"),
        Case("Courier SMS", SourceKind.SMS, "DLHVRY", "Your shipment AWB 1234567890 is out for delivery today by Delhivery.",
            type = RecordType.DELIVERY, delivery = DeliveryState.OUT_FOR_DELIVERY, band = Band.REVIEW_OR_BETTER),

        // ---- Travel ----
        Case("IRCTC PNR", SourceKind.TRAVEL, "IRCTC",
            "PNR: 4512345678, Train: 12951, Doj: 25-10-2026, MMCT-NDLS, Coach B2 Berth 34 CNF",
            type = RecordType.TRIP, tripMode = TripMode.TRAIN, eventOn = LocalDate.of(2026, 10, 25), reference = "4512345678", label = "IRCTC Rail Connect"),
        Case("IndiGo flight", SourceKind.TRAVEL, "IndiGo",
            "Your flight 6E 2134 from Bengaluru to Delhi on 02 Nov departs at 06:15. PNR ABC12D. Web check-in is open.",
            type = RecordType.TRIP, tripMode = TripMode.FLIGHT, eventOn = LocalDate.of(2026, 11, 2), reference = "ABC12D", label = "IndiGo"),
        Case("redBus ticket", SourceKind.OTHER, "redBus",
            "Your bus ticket is confirmed. Bus departs on 14 Oct from Pune to Goa. PNR TS1234567",
            type = RecordType.TRIP, tripMode = TripMode.BUS, eventOn = LocalDate.of(2026, 10, 14), band = Band.REVIEW_OR_BETTER),

        // ---- Insurance ----
        Case("ACKO bike insurance", SourceKind.INSURANCE, "ACKO",
            "Your bike insurance policy no. ACK123456789 for MH12AB1234 expires on 21 October. Renew to stay protected.",
            type = RecordType.INSURANCE, expiresOn = LocalDate.of(2026, 10, 21), reference = "ACK123456789", insuranceKind = InsuranceKind.VEHICLE, label = "ACKO"),
        Case("LIC premium due", SourceKind.SMS, "LIC",
            "Premium of Rs 12,500 for policy 512345678 is due on 15/11/2026. Pay before due date to avoid lapse.",
            type = RecordType.INSURANCE, expiresOn = LocalDate.of(2026, 11, 15), amountPaise = 1_250_000),
        Case("Health insurance renewal", SourceKind.SMS, "Star Health",
            "Your Star Health policy will expire on 05-12-2026. Renew your health insurance before expiry.",
            type = RecordType.INSURANCE, expiresOn = LocalDate.of(2026, 12, 5), insuranceKind = InsuranceKind.HEALTH),

        // ---- Bills ----
        Case("BESCOM electricity bill", SourceKind.SMS, "BESCOM",
            "BESCOM: Bill of Rs 2,140.00 for RR No 1234 is due on 12-10-2026. Pay via BESCOM Mithra.",
            type = RecordType.BILL, amountPaise = 214_000, dueOn = LocalDate.of(2026, 10, 12), merchant = "BESCOM", category = Category.BILLS),
        Case("Airtel postpaid", SourceKind.TELECOM, "Airtel",
            "Your Airtel postpaid bill of ₹799 is due on 15 Oct. Pay now.",
            type = RecordType.BILL, amountPaise = 79_900, dueOn = LocalDate.of(2026, 10, 15), label = "Airtel Thanks"),
        Case("Credit card statement", SourceKind.SMS, "HDFC Bank",
            "HDFC Bank Credit Card XX1234 statement: Total due Rs 15,432.10, Minimum due Rs 772. Due date 20-10-2026.",
            type = RecordType.BILL, amountPaise = 1_543_210, dueOn = LocalDate.of(2026, 10, 20), category = Category.FINANCE),
        Case("Bill paid confirmation", SourceKind.SMS, "BESCOM",
            "Thank you for the payment of Rs 2,140 towards your BESCOM bill.",
            type = RecordType.BILL, amountPaise = 214_000, handled = true, band = Band.REVIEW_OR_BETTER),
        Case("Overdue telecom bill", SourceKind.SMS, "Jio",
            "Your Jio bill of Rs 599 is overdue. Pay immediately to avoid disconnection.",
            type = RecordType.BILL, amountPaise = 59_900, action = true, band = Band.REVIEW_OR_BETTER),

        // ---- Subscriptions ----
        Case("Netflix renewal", SourceKind.OTT, "Netflix",
            "Your Netflix membership will renew on 18 Oct for ₹649.",
            type = RecordType.SUBSCRIPTION, amountPaise = 64_900, dueOn = LocalDate.of(2026, 10, 18), recurrence = RecurrencePeriod.MONTHLY, label = "Netflix"),
        Case("YouTube annual plan", SourceKind.OTHER, "Google",
            "Your YouTube Premium annual plan renewed. ₹1,290 charged.",
            type = RecordType.SUBSCRIPTION, amountPaise = 129_000, recurrence = RecurrencePeriod.YEARLY, band = Band.REVIEW_OR_BETTER),
        Case("Spotify mandate debit", SourceKind.SMS, "Bank",
            "Your mandate for Spotify of Rs 119 has been executed successfully. A/c XX1111 debited.",
            amountPaise = 11_900, category = Category.SUBSCRIPTIONS),

        // ---- Vehicle & government ----
        Case("PUC expiry", SourceKind.GOVERNMENT, "mParivahan",
            "PUC certificate for vehicle KA01MN4567 expires on 30-10-2026.",
            type = RecordType.VEHICLE, dueOn = LocalDate.of(2026, 10, 30), reference = "KA01MN4567", label = "mParivahan"),
        Case("e-Challan", SourceKind.SMS, "VAHAN",
            "An e-challan of Rs 500 has been issued against vehicle DL3CAB1234 for overspeeding. Pay within 60 days.",
            type = RecordType.VEHICLE, amountPaise = 50_000, action = true),
        Case("EPFO with deadline", SourceKind.GOVERNMENT, "UMANG",
            "Your EPFO passbook has been updated. Last date to link Aadhaar is 31 Dec 2026.",
            type = RecordType.GOVERNMENT, dueOn = LocalDate.of(2026, 12, 31), band = Band.REVIEW_OR_BETTER, label = "UMANG"),
        Case("Aadhaar update SMS", SourceKind.SMS, "UIDAI",
            "Your Aadhaar update request has been completed. Download e-Aadhaar from DigiLocker.",
            type = RecordType.GOVERNMENT, band = Band.REVIEW_OR_BETTER),


        // ---- Salary and adaptive formats ----
        Case("ICICI NEFT salary with Avl Bal", SourceKind.SMS, "ICICI Bank",
            "Your A/c XX1234 is credited with INR 85,000.00 on 01-Oct-26. Info: NEFT-SALARY OCT26-ACME TECH. Avl Bal: INR 1,02,345.00",
            type = RecordType.TRANSACTION, amountPaise = 8_500_000, direction = TxnDirection.INCOME, salary = true, balance = 10_234_500),
        Case("HDFC 'SAL CR' with Avbl Bal", SourceKind.SMS, "HDFC Bank",
            "Rs 62,000.00 credited to A/c XX9988 on 30-09-26 SAL CR ACME. Avbl Bal Rs 70,512.40",
            type = RecordType.TRANSACTION, amountPaise = 6_200_000, direction = TxnDirection.INCOME, salary = true, balance = 7_051_240),
        Case("Rs: colon format, trf to", SourceKind.SMS, "Bank",
            "A/c *4321 Debited for Rs:500.00 on 09-10-26 trf to RAMESH STORES Refno 628312345678",
            type = RecordType.TRANSACTION, amountPaise = 50_000, direction = TxnDirection.EXPENSE, salary = false),
        Case("SBI 'debited by' without currency", SourceKind.SMS, "SBI",
            "Your a/c no. XXXXX1234 is debited by 2500.00 on 08Oct26 and credited to a/c no. XXXXX5678 (UPI Ref no 628344455566)",
            type = RecordType.TRANSACTION, amountPaise = 250_000, direction = TxnDirection.EXPENSE),
        Case("ATM withdrawal", SourceKind.SMS, "HDFC Bank",
            "Rs.5,000.00 withdrawn at ATM from A/c XX4321 on 07-10-26. Avl Bal Rs 45,000.00",
            type = RecordType.TRANSACTION, amountPaise = 500_000, direction = TxnDirection.EXPENSE, merchant = "ATM cash", balance = 4_500_000),
        Case("INR without space", SourceKind.SMS, "Axis Bank",
            "INR500.00 debited from A/c XX1111 at AMAZON PAY on 06-10-26",
            type = RecordType.TRANSACTION, amountPaise = 50_000, direction = TxnDirection.EXPENSE, merchant = "Amazon"),
        // ---- Noise ----
        Case("Chat message", SourceKind.OTHER, "Rahul", "Hey, are we meeting today?", band = Band.LOW),
    )

    @Test
    fun goldenCorpus() {
        val failures = mutableListOf<String>()
        for (c in cases) {
            val outcome = engine.parse(RawNotification("pkg", c.title, c.text, ctx.nowMillis, c.kind, c.label), ctx)
            try {
                check(c, outcome)
            } catch (e: AssertionError) {
                failures += "${c.name}: ${e.message?.lines()?.take(3)?.joinToString(" ")} | outcome=$outcome"
            }
        }
        assertWithMessage("Failed cases:\n" + failures.joinToString("\n")).that(failures).isEmpty()
    }

    @Test
    fun corpusAccuracyAtLeastNinetyPercent() {
        // Guard for future format drift: the corpus must stay ≥ 90% correct on type.
        val typed = cases.filter { it.type != null }
        val correct = typed.count { c ->
            val o = engine.parse(RawNotification("pkg", c.title, c.text, ctx.nowMillis, c.kind, c.label), ctx)
            (o as? ParseOutcome.Parsed)?.signal?.type == c.type
        }
        assertThat(correct.toDouble() / typed.size).isAtLeast(0.9)
    }

    private fun check(c: Case, outcome: ParseOutcome) {
        if (c.sensitive) {
            assertThat(outcome).isEqualTo(ParseOutcome.Sensitive)
            return
        }
        if (c.band == Band.LOW) {
            val s = (outcome as? ParseOutcome.Parsed)?.signal
            assertWithMessage("expected low confidence").that(s == null || s.confidence < ConfidencePolicy.REVIEW).isTrue()
            return
        }
        assertThat(outcome).isInstanceOf(ParseOutcome.Parsed::class.java)
        val s = (outcome as ParseOutcome.Parsed).signal
        when (c.band) {
            Band.AUTO -> assertWithMessage("confidence ${s.confidence} (${s.parserId})").that(s.confidence).isAtLeast(ConfidencePolicy.AUTO)
            Band.REVIEW_OR_BETTER -> assertWithMessage("confidence ${s.confidence} (${s.parserId})").that(s.confidence).isAtLeast(ConfidencePolicy.REVIEW)
            Band.LOW -> Unit
        }
        c.type?.let { assertWithMessage("type (${s.parserId})").that(s.type).isEqualTo(it) }
        c.amountPaise?.let { assertWithMessage("amount").that(s.amountPaise).isEqualTo(it) }
        c.direction?.let { assertWithMessage("direction").that(s.direction).isEqualTo(it) }
        c.category?.let { assertWithMessage("category").that(s.category).isEqualTo(it) }
        c.merchant?.let { assertWithMessage("merchant").that(s.merchant).isEqualTo(it) }
        c.dueOn?.let { assertWithMessage("due").that(s.dueAt?.toDate()).isEqualTo(it) }
        c.expiresOn?.let { assertWithMessage("expiry").that(s.expiresAt?.toDate()).isEqualTo(it) }
        c.eventOn?.let { assertWithMessage("event").that(s.eventAt?.toDate()).isEqualTo(it) }
        c.reference?.let { assertWithMessage("reference").that(s.reference).isEqualTo(it) }
        c.delivery?.let { assertWithMessage("delivery").that(s.deliveryState).isEqualTo(it) }
        c.tripMode?.let { assertWithMessage("trip mode").that(s.tripMode).isEqualTo(it) }
        c.insuranceKind?.let { assertWithMessage("insurance kind").that(s.insuranceKind).isEqualTo(it) }
        c.recurrence?.let { assertWithMessage("recurrence").that(s.recurrence).isEqualTo(it) }
        c.handled?.let { assertWithMessage("handled").that(s.alreadyHandled).isEqualTo(it) }
        c.action?.let { assertWithMessage("action").that(s.actionRequired).isEqualTo(it) }
        c.salary?.let { assertWithMessage("salary").that(s.salary).isEqualTo(it) }
        c.balance?.let { assertWithMessage("balance").that(s.balancePaise).isEqualTo(it) }
    }

    private fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
