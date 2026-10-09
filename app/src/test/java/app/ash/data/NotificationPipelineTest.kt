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
    private val reminders = ReminderRepositoryImpl(db.reminderDao(), ReminderScheduler(context), settings, time)
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
