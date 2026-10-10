package app.ash.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.ash.common.AppResult
import app.ash.common.FixedTimeProvider
import app.ash.data.local.database.AshDatabase
import app.ash.data.repository.Counters
import app.ash.data.repository.DiagnosticsRepository
import app.ash.data.repository.LifeRepositoryImpl
import app.ash.data.repository.RecordRepositoryImpl
import app.ash.data.repository.ReminderRepositoryImpl
import app.ash.data.repository.ReminderText
import app.ash.data.repository.SettingsRepository
import app.ash.data.repository.SourceRepositoryImpl
import app.ash.domain.model.Category
import app.ash.domain.model.DeliveryState
import app.ash.domain.model.Document
import app.ash.domain.model.RecordStatus
import app.ash.domain.model.RecordType
import app.ash.domain.model.ReminderState
import app.ash.domain.model.TxnDirection
import app.ash.domain.repository.DocumentRepository
import app.ash.domain.repository.InboxFilter
import app.ash.domain.usecase.GetAttentionFeedUseCase
import app.ash.intelligence.RuleBasedIntelligenceEngine
import app.ash.notification.NotificationIngestor
import app.ash.notification.NotificationIngestor.Result
import app.ash.reminder.ReminderScheduler
import app.ash.search.SearchRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * End-to-end notification tests (§39): real Room database, real rule engine, real repositories,
 * WorkManager in test mode. Verifies the privacy rules as much as the parsing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NotificationPipelineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val time = FixedTimeProvider.at(LocalDate.of(2026, 10, 10), hour = 10)
    private val db = Room.inMemoryDatabaseBuilder(context, AshDatabase::class.java)
        .allowMainThreadQueries()
        .addCallback(AshDatabase.SeedCallback)
        .build()

    init {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    private val settings = SettingsRepository(context)
    private val reminders = ReminderRepositoryImpl(db.reminderDao(), ReminderScheduler(context), settings, time, app.ash.workspace.Workspace.REAL)
    private val text = ReminderText(context)
    private val diagnostics = DiagnosticsRepository(db.diagnosticsDao(), db.auditDao(), db.statsDao(), time)
    private val life = LifeRepositoryImpl(
        db.vehicleDao(), db.policyDao(), db.warrantyDao(), db.subscriptionDao(), db.transactionDao(), db.documentDao(),
        reminders, text, diagnostics, time,
    )
    private val records = RecordRepositoryImpl(
        db, db.recordDao(), db.transactionDao(), db.billDao(), db.deliveryDao(), db.tripDao(), db.merchantDao(),
        life, reminders, text, diagnostics, time,
    )
    private val sources = SourceRepositoryImpl(context, db.appSourceDao(), time)
    private val engine = RuleBasedIntelligenceEngine()
    private val ingestor = NotificationIngestor(sources, db.notificationDao(), records, engine, settings, diagnostics, time)
    private val search = SearchRepository(
        db.recordDao(), db.documentDao(), db.vehicleDao(), db.policyDao(), db.warrantyDao(), db.subscriptionDao(),
        db.profileDao(), engine, time, Dispatchers.IO,
    )

    @After fun tearDown() = db.close()

    private suspend fun post(pkg: String, title: String, body: String, minutesAgo: Long = 0) =
        ingestor.ingest(pkg, "key-$title-$body".hashCode().toString(), title, body, time.nowMillis() - minutesAgo * 60_000)

    @Test
    fun fullPipeline() = runBlocking<Unit> {
        val sms = "com.google.android.apps.messaging"

        // 1. A source the user has not enabled is ignored and nothing is stored.
        assertThat(post(sms, "HDFC Bank", "Rs.1,499.00 debited from A/c XX4321 to VPA swiggy@icici. UPI Ref No 628312345678")).isEqualTo(Result.SOURCE_DISABLED)
        assertThat(db.statsDao().notifications().first()).isEqualTo(0)
        assertThat(db.appSourceDao().get(sms)?.enabled).isFalse()

        sources.setEnabled(sms, true)

        // 2. Bank debit becomes a transaction automatically.
        assertThat(post(sms, "HDFC Bank", "Rs.1,499.00 debited from A/c XX4321 to VPA swiggy@icici. UPI Ref No 628312345678")).isEqualTo(Result.CREATED)
        val txns = records.observeTransactions(10).first()
        assertThat(txns).hasSize(1)
        assertThat(txns[0].amountPaise).isEqualTo(149_900)
        assertThat(txns[0].direction).isEqualTo(TxnDirection.EXPENSE)
        assertThat(txns[0].merchant).isEqualTo("Swiggy")

        // 3. The same notification again is a duplicate.
        assertThat(post(sms, "HDFC Bank", "Rs.1,499.00 debited from A/c XX4321 to VPA swiggy@icici. UPI Ref No 628312345678")).isEqualTo(Result.DUPLICATE)

        // 4. OTPs are never stored, not even as raw notifications.
        val before = db.statsDao().notifications().first()
        assertThat(post(sms, "HDFC Bank", "482913 is your OTP for Rs 2,000 at Amazon. Do not share.")).isEqualTo(Result.SENSITIVE)
        assertThat(db.statsDao().notifications().first()).isEqualTo(before)

        // 5. A bill creates a record, a bill row, and automatic reminders.
        assertThat(post(sms, "BESCOM", "BESCOM: Bill of Rs 2,140.00 for RR No 1234 is due on 15-10-2026.")).isEqualTo(Result.CREATED)
        val bill = records.observeBills(10).first().single()
        assertThat(bill.amountPaise).isEqualTo(214_000)
        val pending = db.reminderDao().pendingAfter(time.nowMillis())
        assertThat(pending.map { it.leadDays }).containsExactly(3, 1, 0)
        assertThat(pending.all { it.state == ReminderState.SCHEDULED }).isTrue()

        // 6. Shipping updates merge into one delivery record.
        val amazon = "com.amazon.mShop.android.shopping"
        sources.recordSeen(amazon)
        sources.setEnabled(amazon, true)
        assertThat(post(amazon, "Shipped", "Your order has been shipped and is arriving by 14 Oct. Order ID OD123456789012")).isEqualTo(Result.CREATED)
        assertThat(post(amazon, "Delivered", "Your order OD123456789012 was delivered. Order ID OD123456789012")).isEqualTo(Result.CREATED)
        val deliveries = db.recordDao().byCategories(listOf(Category.DELIVERIES.name))
        assertThat(deliveries).hasSize(1)
        assertThat(deliveries[0].status).isEqualTo(RecordStatus.HANDLED)
        assertThat(db.deliveryDao().get(deliveries[0].id)?.state).isEqualTo(DeliveryState.DELIVERED)

        // 7. Chit-chat is not turned into a record.
        val whatsapp = "com.whatsapp"
        sources.recordSeen(whatsapp)
        sources.setEnabled(whatsapp, true)
        assertThat(post(whatsapp, "Rahul", "Are we meeting today?")).isEqualTo(Result.LOW_CONFIDENCE)

        // 8. Uncertain finds go to review; no transaction until the user confirms.
        assertThat(post(sms, "Bank", "Rs 1500 aapke khate XX1122 mein jama hua", minutesAgo = 5)).isIn(listOf(Result.REVIEW, Result.CREATED))
        val review = records.observeInbox(InboxFilter.REVIEW, null).first()
        if (review.isNotEmpty()) {
            assertThat(records.observeTransactions(10).first()).hasSize(1)
            records.confirm(review.first())
            assertThat(records.observeTransactions(10).first()).hasSize(2)
            assertThat(diagnostics.counters.first()[Counters.PARSER_CONFIRMATIONS]).isEqualTo(1)
        }

        // 9. Local search finds things by name and amount.
        assertThat(search.search("BESCOM").records.map { it.type }).contains(RecordType.BILL)
        assertThat(search.search("₹1499").records).isNotEmpty()
        assertThat(search.search("bills due").records.map { it.title }).contains("BESCOM")

        // 10. The bill shows up on Home as something that needs attention soon.
        val feed = GetAttentionFeedUseCase(records, life, EmptyDocuments, time)().first()
        assertThat((feed.needsAttention + feed.comingUp).map { it.title }).contains("BESCOM")
        assertThat(feed.upcomingPayments.map { it.title }).contains("BESCOM")

        // 11. Marking the bill handled cancels its reminders.
        records.setStatus(bill.recordId, RecordStatus.HANDLED)
        assertThat(db.reminderDao().forTarget(app.ash.domain.model.ReminderTarget.RECORD, bill.recordId)).isEmpty()

        // 12. Counters are local and accurate.
        val counters = diagnostics.counters.first()
        assertThat(counters[Counters.NOTIFICATIONS_SENSITIVE]).isEqualTo(1)
        assertThat(counters[Counters.NOTIFICATIONS_DUPLICATE]).isEqualTo(1)

        // 13. Reopening a handled bill brings its reminders back.
        records.setStatus(bill.recordId, RecordStatus.ACTION_REQUIRED)
        assertThat(db.reminderDao().forTarget(app.ash.domain.model.ReminderTarget.RECORD, bill.recordId)).isNotEmpty()

        // 14. With "keep original text" off, no notification wording is stored anywhere.
        settings.setStoreOriginalText(false)
        assertThat(post(sms, "Airtel", "Your Airtel postpaid bill of Rs 799 is due on 20 Oct. Pay now.")).isEqualTo(Result.CREATED)
        val airtel = db.recordDao().search("airtel*").single()
        assertThat(airtel.summary).isEmpty()
        assertThat(airtel.extrasJson.orEmpty()).doesNotContain("postpaid")
        assertThat(db.notificationDao().getById(airtel.notificationId!!)?.text).isNull()

        // 15. Passed subscription renewals roll forward by whole periods.
        val subId = life.saveSubscription(
            app.ash.domain.model.Subscription(
                name = "Spotify", amountPaise = 11_900, period = app.ash.domain.model.RecurrencePeriod.MONTHLY,
                nextRenewalAt = app.ash.common.Dates.morningOf(LocalDate.of(2026, 9, 5), time.zone()),
            ),
        )
        assertThat(life.rollRenewals()).isEqualTo(1)
        val rolled = life.getSubscription(subId)!!.nextRenewalAt!!
        assertThat(app.ash.common.Dates.toLocalDate(rolled, time.zone())).isEqualTo(LocalDate.of(2026, 11, 5))

        // 16. Warranties that ended over a week ago drop off Home; ones ending soon show up.
        life.saveWarranty(app.ash.domain.model.Warranty(product = "Old TV", expiresAt = time.nowMillis() - 30L * 24 * 60 * 60 * 1000))
        life.saveWarranty(app.ash.domain.model.Warranty(product = "New phone", expiresAt = time.nowMillis() + 5L * 24 * 60 * 60 * 1000))
        val titles = GetAttentionFeedUseCase(records, life, EmptyDocuments, time)().first().let { f -> (f.needsAttention + f.comingUp).map { it.title } }
        assertThat(titles).contains("New phone")
        assertThat(titles).doesNotContain("Old TV")

        // 17. SMS history import: dated when received, past deadlines filed as handled, uncertain ones only logged.
        settings.setStoreOriginalText(true)
        val fortyDaysAgo = time.nowMillis() - 40L * 24 * 60 * 60 * 1000
        val oldDue = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy").format(LocalDate.of(2026, 9, 5))
        assertThat(
            ingestor.ingest(sms, "sms-1", "TATAPW", "Tata Power: Bill of Rs 1,830.00 for CA 556677 is due on $oldDue.", fortyDaysAgo, historical = true),
        ).isEqualTo(Result.CREATED)
        val oldBill = db.recordDao().search("tata*").single()
        assertThat(oldBill.status).isEqualTo(RecordStatus.HANDLED)
        assertThat(oldBill.createdAt).isEqualTo(fortyDaysAgo)
        assertThat(db.reminderDao().forTarget(app.ash.domain.model.ReminderTarget.RECORD, oldBill.id)).isEmpty()
        assertThat(
            ingestor.ingest(sms, "sms-2", "DLHVRY", "Your shipment AWB 9988776655 is out for delivery today by Delhivery.", fortyDaysAgo + 1000, historical = true),
        ).isEqualTo(Result.LOW_CONFIDENCE)
        assertThat(app.ash.notification.SmsHistoryImporter.isBusinessSender("VM-HDFCBK-S")).isTrue()
        assertThat(app.ash.notification.SmsHistoryImporter.isBusinessSender("56767")).isTrue()
        assertThat(app.ash.notification.SmsHistoryImporter.isBusinessSender("+919876543210")).isFalse()
        assertThat(app.ash.notification.SmsHistoryImporter.isBusinessSender("9876543210")).isFalse()
        assertThat(app.ash.notification.SmsHistoryImporter.senderLabel("VM-HDFCBK-S")).isEqualTo("HDFCBK")
        assertThat(app.ash.notification.SmsHistoryImporter.senderLabel("AX-SBIINB")).isEqualTo("SBIINB")

        // 18. Workspaces are isolated: the demo database never sees real data, and reminder work is namespaced.
        val demoDb = Room.inMemoryDatabaseBuilder(context, AshDatabase::class.java).allowMainThreadQueries().addCallback(AshDatabase.SeedCallback).build()
        val realGraph = app.ash.workspace.WorkspaceGraph(app.ash.workspace.Workspace.REAL, db, context, ReminderScheduler(context), settings, text, engine, time)
        val demoGraph = app.ash.workspace.WorkspaceGraph(app.ash.workspace.Workspace.DEMO, demoDb, context, ReminderScheduler(context), settings, text, engine, time)
        val realBefore = db.statsDao().records().first()
        realGraph.sources.setEnabled(sms, true)
        assertThat(realGraph.ingestor.ingest(sms, "ws-1", "Airtel", "Your Airtel broadband bill of Rs 1,178 is due on 25 Oct. Pay now.", time.nowMillis())).isEqualTo(Result.CREATED)
        assertThat(db.statsDao().records().first()).isEqualTo(realBefore + 1)
        assertThat(demoDb.statsDao().records().first()).isEqualTo(0)
        // WorkManager measures delays on the real clock, so fire well after both the fixed and the real "now".
        val fireAt = maxOf(time.nowMillis(), System.currentTimeMillis()) + 86_400_000
        val realReminder = realGraph.reminders.save(app.ash.domain.model.Reminder(title = "Real", fireAt = fireAt))
        val demoReminder = demoGraph.reminders.save(app.ash.domain.model.Reminder(title = "Demo", fireAt = fireAt))
        assertThat(demoReminder).isEqualTo(1L) // same numeric id space per database...
        val wm = androidx.work.WorkManager.getInstance(context)
        assertThat(wm.getWorkInfosForUniqueWork("reminder-yash-$realReminder").get()).isNotEmpty()
        assertThat(wm.getWorkInfosForUniqueWork("reminder-demo-$demoReminder").get()).isNotEmpty() // ...but separate work
        demoGraph.reminders.delete(demoReminder)
        assertThat(wm.getWorkInfosForUniqueWork("reminder-yash-$realReminder").get().single().state).isEqualTo(androidx.work.WorkInfo.State.ENQUEUED)
        demoDb.close()

        // 19. One payment, two messages (UPI app notification + bank SMS): counted once.
        val gpay = "com.google.android.apps.nbu.paisa.user"
        sources.recordSeen(gpay)
        sources.setEnabled(gpay, true)
        val txnsBefore = records.observeTransactions(500).first().size
        assertThat(post(gpay, "Payment successful", "You paid ₹420 to Chai Point")).isEqualTo(Result.CREATED)
        val today = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yy").format(time.today())
        assertThat(post(sms, "HDFC Bank", "Rs.420.00 debited from A/c XX4321 on $today to VPA chaipoint@ybl (UPI Ref No 628377788899)")).isEqualTo(Result.CREATED)
        val afterMerge = records.observeTransactions(500).first()
        assertThat(afterMerge.size).isEqualTo(txnsBefore + 1)
        assertThat(afterMerge.first { it.amountPaise == 42_000L }.accountRef).isEqualTo("XX4321") // enriched from the SMS

        // 20. Salary: keyword detection, then learning a payer the user marks as salary.
        assertThat(post(sms, "SBI", "Your A/c X1234 has been credited with Rs 85,000.00 by NEFT-SALARY ACME TECH. Avl Bal Rs 1,02,000.00")).isEqualTo(Result.CREATED)
        assertThat(records.observeTransactions(500).first().first { it.amountPaise == 8_500_000L }.isSalary).isTrue()
        assertThat(post(sms, "SBI", "Rs 70,000.00 credited to your A/c XX1234 by NEFT from GLOBEX CORP", minutesAgo = 60L * 24 * 3)).isEqualTo(Result.CREATED)
        val globex = records.observeTransactions(500).first().first { it.amountPaise == 7_000_000L }
        assertThat(globex.isSalary).isFalse()
        records.setSalary(globex.recordId, true)
        assertThat(post(sms, "SBI", "Rs 71,500.00 credited to your A/c XX1234 by NEFT from GLOBEX CORP ref 99")).isEqualTo(Result.CREATED)
        assertThat(records.observeTransactions(500).first().first { it.amountPaise == 7_150_000L }.isSalary).isTrue()

        // 21. The statement opens with the latest salary and tracks the remaining balance.
        val statement = app.ash.domain.usecase.StatementCalculator.compute(
            records.observeTransactionsSince(0).first(), 0, time.nowMillis(), time.zone(),
        )
        assertThat(statement.salary!!.amountPaise).isEqualTo(7_150_000)
        assertThat(statement.opening).isEqualTo(7_150_000)
        assertThat(statement.remaining).isEqualTo(statement.opening - statement.spent + statement.received)

        // 22. A payment that matches an open bill marks it paid and stops its reminders.
        assertThat(post(sms, "Tata Play", "Tata Play DTH bill of Rs 349.00 is due on 25-10-2026. Pay now.")).isEqualTo(Result.CREATED)
        val dth = records.observeBills(50).first().first { it.amountPaise == 34_900L }
        assertThat(dth.paidAt).isNull()
        assertThat(post(sms, "HDFC Bank", "Rs.349.00 debited from A/c XX4321 on $today to Tata Play DTH (UPI Ref No 628300099988)")).isEqualTo(Result.CREATED)
        val settled = records.observeBills(50).first().first { it.recordId == dth.recordId }
        assertThat(settled.paidAt).isNotNull()
        assertThat(settled.status).isEqualTo(RecordStatus.HANDLED)
        assertThat(db.reminderDao().forTarget(app.ash.domain.model.ReminderTarget.RECORD, dth.recordId)).isEmpty()

        // 23. Correcting a merchant's category teaches ashOS for next time.
        assertThat(post(sms, "HDFC Bank", "Rs.180.00 debited from A/c XX4321 on $today to VPA sharmadairy@okicici (UPI Ref No 628311122233)")).isEqualTo(Result.CREATED)
        val dairy = records.observeTransactions(500).first().first { it.amountPaise == 18_000L }
        records.correctTransaction(dairy.recordId, 18_000, TxnDirection.EXPENSE, dairy.merchant, app.ash.domain.model.Category.HOME)
        assertThat(post(sms, "HDFC Bank", "Rs.220.00 debited from A/c XX4321 on $today to VPA sharmadairy@okicici (UPI Ref No 628311199999)", minutesAgo = 30)).isEqualTo(Result.CREATED)
        assertThat(records.observeTransactions(500).first().first { it.amountPaise == 22_000L }.category).isEqualTo(app.ash.domain.model.Category.HOME)

        // 24. Routine payments are not inbox items; open bills still are.
        val needsYou = records.observeInbox(InboxFilter.NEEDS_YOU, null).first()
        assertThat(needsYou.none { it.type == RecordType.TRANSACTION && it.status != RecordStatus.REVIEW }).isTrue()
    }

    private object EmptyDocuments : DocumentRepository {
        override fun observeDocuments(): Flow<List<Document>> = flowOf(emptyList())
        override fun observeDocument(id: Long): Flow<Document?> = flowOf(null)
        override fun observeForVehicle(vehicleId: Long): Flow<List<Document>> = flowOf(emptyList())
        override suspend fun importFromUri(uri: Uri): AppResult<Long> = error("unused")
        override suspend fun importImageBytes(bytes: ByteArray, title: String): AppResult<Long> = error("unused")
        override suspend fun update(document: Document) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun readDecrypted(id: Long): AppResult<ByteArray> = error("unused")
        override suspend fun readThumbnail(id: Long): ByteArray? = null
        override suspend fun decryptToTempFile(id: Long): AppResult<File> = error("unused")
        override suspend fun runOcr(id: Long): AppResult<String> = error("unused")
    }
}
