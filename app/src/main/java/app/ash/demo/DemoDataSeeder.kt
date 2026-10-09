package app.ash.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import app.ash.BuildConfig
import app.ash.common.AppResult
import app.ash.common.Dates
import app.ash.common.TimeProvider
import app.ash.data.repository.SettingsRepository
import app.ash.data.repository.SourceRepositoryImpl
import app.ash.document.ImageTools
import app.ash.domain.model.Category
import app.ash.domain.model.Importance
import app.ash.domain.model.InsuranceKind
import app.ash.domain.model.InsurancePolicy
import app.ash.domain.model.ProfileRelation
import app.ash.domain.model.Record
import app.ash.domain.model.RecordStatus
import app.ash.domain.model.RecordType
import app.ash.domain.model.RecurrencePeriod
import app.ash.domain.model.Reminder
import app.ash.domain.model.Subscription
import app.ash.domain.model.Vehicle
import app.ash.domain.model.Warranty
import app.ash.domain.repository.DocumentRepository
import app.ash.domain.repository.LifeRepository
import app.ash.domain.repository.ProfileRepository
import app.ash.domain.repository.RecordRepository
import app.ash.domain.repository.ReminderRepository
import app.ash.logging.AshLogger
import app.ash.notification.NotificationIngestor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills ashOS with realistic sample data for testing (debug and staging builds only).
 *
 * Sample notifications go through the real pipeline (allow-list, parsers, confidence bands,
 * reminders), and sample documents are rendered as images so on-device OCR really runs.
 * Everything is ordinary local data: Settings → Backup and data → Delete all data removes it.
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val ingestor: NotificationIngestor,
    private val sources: SourceRepositoryImpl,
    private val records: RecordRepository,
    private val life: LifeRepository,
    private val profiles: ProfileRepository,
    private val reminders: ReminderRepository,
    private val documents: DocumentRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) {
    data class Summary(val notifications: Int, val created: Int, val review: Int, val documents: Int)

    private val today: LocalDate get() = time.today()
    private val dmy = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val dmyShort = DateTimeFormatter.ofPattern("dd-MM-yy")
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    private fun d(days: Long) = today.plusDays(days)
    private fun at(days: Long) = Dates.morningOf(d(days), time.zone())
    private fun dmy(days: Long) = dmy.format(d(days))
    private fun short(days: Long) = dmyShort.format(d(days))
    private fun dm(days: Long) = dayMonth.format(d(days))

    suspend fun seed(onProgress: (String) -> Unit = {}): Summary {
        check(BuildConfig.DEMO_DATA) { "Sample data is not available in release builds" }
        AshLogger.i(TAG, "Loading sample data")

        onProgress("Profiles")
        val dad = (profiles.add("Dad", ProfileRelation.PARENT) as? AppResult.Success)?.value
        val mom = (profiles.add("Mom", ProfileRelation.PARENT) as? AppResult.Success)?.value
        profiles.add("Home", ProfileRelation.HOME)

        // Vehicles first, so notifications about them link up by registration number.
        onProgress("Vehicles")
        life.saveVehicle(
            Vehicle(
                name = "Honda CB350RS", brand = "Honda", model = "CB350RS", regNumber = "MH12AB1234", fuelType = "Petrol",
                purchasedAt = at(-700), lastServiceAt = at(-160), nextServiceAt = at(18), warrantyExpiresAt = at(30),
                loanEmiPaise = 345_000, emiDayOfMonth = 5,
            ),
        )
        life.saveVehicle(
            Vehicle(name = "Hyundai Creta", brand = "Hyundai", model = "Creta", regNumber = "KA01MN4567", fuelType = "Diesel",
                purchasedAt = at(-1200), nextServiceAt = at(-3), profileId = dad),
        )

        onProgress("Notifications")
        val pkgSms = "com.google.android.apps.messaging"
        val pkgGpay = "com.google.android.apps.nbu.paisa.user"
        val pkgPhonePe = "com.phonepe.app"
        val pkgAmazon = "com.amazon.mShop.android.shopping"
        val pkgFlipkart = "com.flipkart.android"
        val pkgSwiggy = "in.swiggy.android"
        val pkgIrctc = "cris.org.in.prs.ima"
        val pkgIndigo = "in.goindigo.android"
        val pkgAcko = "com.acko.android"
        val pkgNetflix = "com.netflix.mediaclient"
        val pkgAirtel = "com.myairtelapp"
        val pkgUmang = "in.gov.umang.negd.g2c"
        val pkgParivahan = "com.nic.mparivahan"
        listOf(pkgSms, pkgGpay, pkgPhonePe, pkgAmazon, pkgFlipkart, pkgSwiggy, pkgIrctc, pkgIndigo, pkgAcko, pkgNetflix, pkgAirtel, pkgUmang, pkgParivahan)
            .forEach { sources.recordSeen(it); sources.setEnabled(it, true) }

        data class N(val pkg: String, val title: String, val text: String, val daysAgo: Long = 0, val minutesAgo: Long = 0)
        val notifications = listOf(
            // Money
            N(pkgSms, "HDFC Bank", "Rs.1,499.00 debited from A/c XX4321 on ${short(0)} to VPA swiggy@icici (UPI Ref No 628312345678). Not you? Call 18002586161", minutesAgo = 40),
            N(pkgSms, "SBI", "Dear Customer, your A/c X1234 has been credited with Rs 85,000.00 on ${short(-2)} by NEFT from ACME TECHNOLOGIES PVT LTD. Avl Bal Rs 1,42,345.50", daysAgo = 2),
            N(pkgSms, "ICICI Bank", "INR 649.00 spent on ICICI Bank Card XX9876 on ${short(-65)} at NETFLIX.COM. Avl Lmt: INR 1,20,000.00", daysAgo = 65),
            N(pkgSms, "ICICI Bank", "INR 649.00 spent on ICICI Bank Card XX9876 on ${short(-35)} at NETFLIX.COM. Avl Lmt: INR 1,19,351.00", daysAgo = 35),
            N(pkgSms, "ICICI Bank", "INR 649.00 spent on ICICI Bank Card XX9876 on ${short(-5)} at NETFLIX.COM. Avl Lmt: INR 1,18,702.00", daysAgo = 5),
            N(pkgSms, "Axis Bank", "Refund of Rs 799.00 credited to your Axis Bank A/c XX5555 from AMAZON on ${short(-1)}", daysAgo = 1),
            N(pkgGpay, "Payment successful", "You paid ₹250 to Raju Kirana", minutesAgo = 90),
            N(pkgPhonePe, "Money received", "Received ₹500 from Amit Sharma", daysAgo = 1),
            N(pkgSms, "HDFC Bank", "Rs.2,350.00 debited from A/c XX4321 on ${short(-3)} at INDIAN OIL PETROL PUMP. UPI Ref 628399912345", daysAgo = 3),
            // Bills
            N(pkgSms, "BESCOM", "BESCOM: Bill of Rs 2,140.00 for RR No 1234 is due on ${dmy(0)}. Pay via BESCOM Mithra.", daysAgo = 6),
            N(pkgAirtel, "Airtel", "Your Airtel postpaid bill of ₹799 is due on ${dm(4)}. Pay now.", daysAgo = 2),
            N(pkgSms, "HDFC Bank", "HDFC Bank Credit Card XX1234 statement: Total due Rs 15,432.10, Minimum due Rs 772. Due date ${dmy(9)}.", daysAgo = 1),
            N(pkgSms, "Jio", "Your Jio bill of Rs 599 is overdue. Pay immediately to avoid disconnection.", minutesAgo = 300),
            // Shopping and deliveries
            N(pkgAmazon, "Shipped", "Your order has been shipped and is arriving by ${dm(1)}. Order ID OD407123456789", minutesAgo = 600),
            N(pkgFlipkart, "Out for delivery", "Your Flipkart order is out for delivery today. Order ID OD998877665544", minutesAgo = 120),
            N(pkgSwiggy, "Swiggy", "Order delivered! Enjoy your meal.", minutesAgo = 30),
            // Travel
            N(pkgIrctc, "IRCTC", "PNR: 4512345678, Train: 12951, Doj: ${dmy(12)}, MMCT-NDLS, Coach B2 Berth 34 CNF", daysAgo = 4),
            N(pkgIndigo, "IndiGo", "Your flight 6E 2134 from Bengaluru to Delhi on ${dm(25)} departs at 06:15. PNR ABC12D. Web check-in opens 48 hours before.", daysAgo = 1),
            // Insurance
            N(pkgAcko, "ACKO", "Your bike insurance policy no. ACK123456789 for MH12AB1234 expires on ${dm(23)}. Renew to stay protected.", daysAgo = 1),
            N(pkgSms, "LIC", "Premium of Rs 12,500 for policy 512345678 is due on ${dmy(36)}. Pay before due date to avoid lapse.", daysAgo = 3),
            // Subscriptions
            N(pkgNetflix, "Netflix", "Your Netflix membership will renew on ${dm(25)} for ₹649.", daysAgo = 1),
            // Vehicle and government
            N(pkgParivahan, "mParivahan", "PUC certificate for vehicle KA01MN4567 expires on ${dmy(5)}.", daysAgo = 2),
            N(pkgSms, "VAHAN", "An e-challan of Rs 500 has been issued against vehicle DL3CAB1234 for overspeeding. Pay within 60 days.", daysAgo = 1),
            N(pkgUmang, "UMANG", "Your EPFO passbook has been updated. Last date to link Aadhaar is ${dm(80)} ${d(80).year}.", daysAgo = 7),
            // Things ashOS should be unsure about, or ignore
            N(pkgSms, "Bank", "Rs 1500 aapke khate XX1122 mein jama hua", minutesAgo = 15),
            N(pkgSms, "HDFC Bank", "482913 is your OTP for txn of Rs 2,000 at AMAZON. Do not share it with anyone.", minutesAgo = 10),
            N(pkgSms, "Bank", "Congratulations! Get a pre-approved personal loan up to Rs 5,00,000. Apply now", daysAgo = 1),
        )
        var created = 0
        var review = 0
        for ((i, n) in notifications.withIndex()) {
            val postedAt = time.nowMillis() - n.daysAgo * Dates.DAY_MS - n.minutesAgo * 60_000 - i
            when (ingestor.ingest(n.pkg, "demo-$i", n.title, n.text, postedAt)) {
                NotificationIngestor.Result.CREATED -> created++
                NotificationIngestor.Result.REVIEW -> review++
                else -> Unit
            }
        }
        // A delivery update to show merging into the same order.
        ingestor.ingest(pkgFlipkart, "demo-merge", "Delivered", "Your order OD998877665544 was delivered. Order ID OD998877665544", time.nowMillis() - 5 * 60_000)

        onProgress("Life")
        life.savePolicy(
            InsurancePolicy(insurer = "Star Health", policyNumber = "SH-2025-778812", kind = InsuranceKind.HEALTH,
                startAt = at(-315), expiresAt = at(50), premiumPaise = 1_850_000, profileId = dad),
        )
        life.saveWarranty(Warranty(product = "Samsung 55\" TV", purchasedAt = at(-325), expiresAt = at(40)))
        life.saveWarranty(Warranty(product = "iPhone 15", purchasedAt = at(-165), expiresAt = at(200), profileId = mom))
        life.saveWarranty(Warranty(product = "Old MacBook", purchasedAt = at(-1100), expiresAt = at(-20)))
        life.saveSubscription(Subscription(name = "Spotify", amountPaise = 11_900, period = RecurrencePeriod.MONTHLY, nextRenewalAt = at(3)))
        life.saveSubscription(Subscription(name = "Google One", amountPaise = 13_000, period = RecurrencePeriod.MONTHLY, nextRenewalAt = at(15)))
        life.saveSubscription(Subscription(name = "Amazon Prime", amountPaise = 149_900, period = RecurrencePeriod.YEARLY, nextRenewalAt = at(120)))
        life.detectSubscriptions()

        onProgress("Manual items")
        records.addManual(Record(type = RecordType.BILL, category = Category.HOME, title = "House rent", summary = "Rent for this month",
            amountPaise = 2_200_000, dueAt = at(3), importance = Importance.CRITICAL, status = RecordStatus.ACTION_REQUIRED))
        records.addManual(Record(type = RecordType.APPOINTMENT, category = Category.HEALTHCARE, title = "Dentist appointment",
            summary = "Dr. Mehta, 5 PM", dueAt = at(2), status = RecordStatus.ACTION_REQUIRED, profileId = mom))
        records.addManual(Record(type = RecordType.BILL, category = Category.EDUCATION, title = "School fees", summary = "Term 2",
            amountPaise = 4_500_000, dueAt = at(20), importance = Importance.CRITICAL, status = RecordStatus.ACTION_REQUIRED))
        records.addManual(Record(type = RecordType.TRANSACTION, category = Category.VEHICLE, title = "Honda service centre",
            summary = "Periodic service", amountPaise = 420_000, eventAt = at(-160),
            extras = app.ash.domain.model.RecordExtras(direction = app.ash.domain.model.TxnDirection.EXPENSE)))
        reminders.save(Reminder(title = "Renew passport photo", body = "Studio near office", fireAt = at(1)))

        onProgress("Documents (reading with OCR)")
        var docs = 0
        for ((title, lines) in sampleDocuments()) {
            val bytes = ImageTools.toJpeg(renderDocument(lines), 92)
            if (documents.importImageBytes(bytes, title) is AppResult.Success) docs++
        }

        settings.setDemoDataLoaded(true)
        val summary = Summary(notifications.size + 1, created, review, docs)
        AshLogger.i(TAG, "Sample data loaded: $summary")
        return summary
    }

    private fun sampleDocuments(): List<Pair<String, List<String>>> = listOf(
        "PAN card" to listOf("INCOME TAX DEPARTMENT", "GOVT. OF INDIA", "Permanent Account Number Card", "ABCDE1234F", "Name", "RAHUL SHARMA", "Date of Birth", "15/08/1990"),
        "Driving licence" to listOf("Union of India", "Driving Licence", "DL No: MH12 20110012345", "Name: RAHUL SHARMA", "Date of Issue: ${dmy(-1800)}", "Valid Till: ${dmy(1850)}", "Class: MCWG LMV"),
        "Bike insurance" to listOf("ACKO General Insurance", "Policy Schedule - Two Wheeler", "Policy No: ACK123456789", "Vehicle: MH12AB1234 Honda CB350RS",
            "Period of insurance ${dmy(-342)} to ${dmy(23)}", "Premium Rs 2,345", "Insured: Rahul Sharma"),
    )

    /** Draws a plain document so ML Kit OCR has real pixels to read. */
    private fun renderDocument(lines: List<String>): Bitmap {
        val bitmap = Bitmap.createBitmap(1400, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 52f; typeface = Typeface.DEFAULT_BOLD }
        val border = Paint().apply { color = Color.rgb(60, 60, 60); style = Paint.Style.STROKE; strokeWidth = 6f }
        canvas.drawRect(20f, 20f, 1380f, 880f, border)
        var y = 120f
        for ((i, line) in lines.withIndex()) {
            paint.textSize = if (i == 0) 60f else 46f
            canvas.drawText(line, 70f, y, paint)
            y += if (i == 0) 105f else 92f
        }
        return bitmap
    }

    private companion object {
        const val TAG = "Demo"
    }
}
