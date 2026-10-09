package app.ash.domain.repository

import android.net.Uri
import app.ash.common.AppResult
import app.ash.domain.model.AppSource
import app.ash.domain.model.BillItem
import app.ash.domain.model.Category
import app.ash.domain.model.Document
import app.ash.domain.model.InsurancePolicy
import app.ash.domain.model.Profile
import app.ash.domain.model.ProfileRelation
import app.ash.domain.model.Record
import app.ash.domain.model.RecordStatus
import app.ash.domain.model.Reminder
import app.ash.domain.model.Subscription
import app.ash.domain.model.TimelineEvent
import app.ash.domain.model.TransactionItem
import app.ash.domain.model.TxnDirection
import app.ash.domain.model.Vehicle
import app.ash.domain.model.Warranty
import kotlinx.coroutines.flow.Flow

/**
 * Inbox views. Routine payments are not inbox items (they live in the Balance tab); only
 * transactions still waiting for review appear here.
 */
enum class InboxFilter(val statuses: List<RecordStatus>) {
    /** Default: things that still need the user. */
    NEEDS_YOU(listOf(RecordStatus.REVIEW, RecordStatus.ACTION_REQUIRED, RecordStatus.UNREAD)),
    REVIEW(listOf(RecordStatus.REVIEW)),
    HANDLED(listOf(RecordStatus.HANDLED)),
    ARCHIVED(listOf(RecordStatus.ARCHIVED)),
    ALL(listOf(RecordStatus.UNREAD, RecordStatus.REVIEW, RecordStatus.ACTION_REQUIRED, RecordStatus.HANDLED)),
}

data class MoneySummary(val incomePaise: Long, val expensePaise: Long, val refundPaise: Long)

interface RecordRepository {
    fun observeForAttention(): Flow<List<Record>>
    fun observeInbox(filter: InboxFilter, category: Category?): Flow<List<Record>>
    fun observeCount(status: RecordStatus): Flow<Int>
    fun observe(id: Long): Flow<Record?>
    suspend fun get(id: Long): Record?
    suspend fun addManual(record: Record): Long
    suspend fun update(record: Record)
    /** User confirmed a REVIEW item (optionally edited): typed rows and reminders are created. */
    suspend fun confirm(record: Record)
    suspend fun setStatus(id: Long, status: RecordStatus)
    suspend fun delete(id: Long)

    fun observeTransactions(limit: Int = 200): Flow<List<TransactionItem>>
    fun observeMonthSummary(from: Long, to: Long): Flow<MoneySummary>
    suspend fun correctTransaction(recordId: Long, amountPaise: Long, direction: TxnDirection, merchant: String?, category: Category)
    /** All transactions from [from] onwards, oldest first (statements). */
    fun observeTransactionsSince(from: Long): Flow<List<TransactionItem>>
    /** User marks or unmarks a credit as salary; the payer is remembered for next month. */
    suspend fun setSalary(recordId: Long, isSalary: Boolean)
    /** Null when the record has no transaction row. */
    fun observeIsSalary(recordId: Long): Flow<Boolean?>
    fun observeBills(limit: Int = 200): Flow<List<BillItem>>
    suspend fun setBillPaid(recordId: Long, paid: Boolean)
}

interface LifeRepository {
    fun observeVehicles(): Flow<List<Vehicle>>
    fun observeVehicle(id: Long): Flow<Vehicle?>
    suspend fun getVehicle(id: Long): Vehicle?
    suspend fun saveVehicle(vehicle: Vehicle): Long
    suspend fun deleteVehicle(id: Long)
    fun observeTimeline(vehicleId: Long): Flow<List<TimelineEvent>>
    fun observeVehicleExpenses(vehicleId: Long): Flow<List<TransactionItem>>

    fun observePolicies(): Flow<List<InsurancePolicy>>
    fun observePoliciesForVehicle(vehicleId: Long): Flow<List<InsurancePolicy>>
    suspend fun getPolicy(id: Long): InsurancePolicy?
    suspend fun savePolicy(policy: InsurancePolicy): Long
    suspend fun deletePolicy(id: Long)

    fun observeWarranties(): Flow<List<Warranty>>
    suspend fun getWarranty(id: Long): Warranty?
    suspend fun saveWarranty(warranty: Warranty): Long
    suspend fun deleteWarranty(id: Long)

    fun observeSubscriptions(): Flow<List<Subscription>>
    suspend fun getSubscription(id: Long): Subscription?
    suspend fun saveSubscription(subscription: Subscription): Long
    suspend fun deleteSubscription(id: Long)
    /** Scans transaction history for recurring payments; returns how many new subscriptions were found. */
    suspend fun detectSubscriptions(): Int
    /** Moves passed renewal dates of active subscriptions forward by one period; returns how many moved. */
    suspend fun rollRenewals(): Int
}

interface ProfileRepository {
    fun observeProfiles(): Flow<List<Profile>>
    suspend fun add(name: String, relation: ProfileRelation): AppResult<Long>
    suspend fun rename(profile: Profile, name: String)
    suspend fun delete(id: Long)
}

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    fun observeDocument(id: Long): Flow<Document?>
    fun observeForVehicle(vehicleId: Long): Flow<List<Document>>
    suspend fun importFromUri(uri: Uri): AppResult<Long>
    suspend fun importImageBytes(bytes: ByteArray, title: String): AppResult<Long>
    suspend fun update(document: Document)
    suspend fun delete(id: Long)
    /** Decrypted bytes for in-memory display. Never written to disk. */
    suspend fun readDecrypted(id: Long): AppResult<ByteArray>
    suspend fun readThumbnail(id: Long): ByteArray?
    /** Decrypts to a short-lived private file for PdfRenderer; caller deletes it when done. */
    suspend fun decryptToTempFile(id: Long): AppResult<java.io.File>
    /** Re-runs OCR and stores the text. */
    suspend fun runOcr(id: Long): AppResult<String>
}

interface ReminderRepository {
    fun observeUpcoming(limit: Int = 100): Flow<List<Reminder>>
    fun observePendingCount(): Flow<Int>
    suspend fun get(id: Long): Reminder?
    suspend fun save(reminder: Reminder): Long
    suspend fun delete(id: Long)
    suspend fun markDone(id: Long)
    suspend fun snooze(id: Long, untilMillis: Long)
    suspend fun rescheduleAll()
}

interface SourceRepository {
    fun observeSources(): Flow<List<AppSource>>
    fun observeEnabledCount(): Flow<Int>
    suspend fun setEnabled(packageName: String, enabled: Boolean)
    /** Adds installed apps ASH recognises, disabled, so the user can opt in. */
    suspend fun discoverInstalledKnownApps(): Int
    fun isListenerEnabled(): Boolean
}
