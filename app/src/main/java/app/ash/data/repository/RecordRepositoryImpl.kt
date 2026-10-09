package app.ash.data.repository

import androidx.room.withTransaction
import app.ash.R
import app.ash.common.TimeProvider
import app.ash.data.local.dao.BillDao
import app.ash.data.local.dao.DeliveryDao
import app.ash.data.local.dao.MerchantDao
import app.ash.data.local.dao.RecordDao
import app.ash.data.local.dao.TransactionDao
import app.ash.data.local.dao.TripDao
import app.ash.data.local.database.AshDatabase
import app.ash.data.local.entities.AuditAction
import app.ash.data.local.entities.BillEntity
import app.ash.data.local.entities.DeliveryEntity
import app.ash.data.local.entities.MerchantEntity
import app.ash.data.local.entities.RecordEntity
import app.ash.data.local.entities.TransactionEntity
import app.ash.data.local.entities.TripEntity
import app.ash.data.parser.ConfidencePolicy
import app.ash.data.parser.ParsedSignal
import app.ash.data.parser.dictionary.MerchantDictionary
import app.ash.domain.model.BillItem
import app.ash.domain.model.Category
import app.ash.domain.model.DeliveryState
import app.ash.domain.model.InsurancePolicy
import app.ash.domain.model.Record
import app.ash.domain.model.RecordExtras
import app.ash.domain.model.RecordSource
import app.ash.domain.model.RecordStatus
import app.ash.domain.model.RecordType
import app.ash.domain.model.RecurrencePeriod
import app.ash.domain.model.ReminderTarget
import app.ash.domain.model.Subscription
import app.ash.domain.model.TransactionItem
import app.ash.domain.model.TripMode
import app.ash.domain.model.TxnDirection
import app.ash.domain.repository.InboxFilter
import app.ash.domain.repository.MoneySummary
import app.ash.domain.repository.RecordRepository
import app.ash.logging.AshLogger
import app.ash.reminder.ReminderPlanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordRepositoryImpl @Inject constructor(
    private val db: AshDatabase,
    private val records: RecordDao,
    private val transactions: TransactionDao,
    private val bills: BillDao,
    private val deliveries: DeliveryDao,
    private val trips: TripDao,
    private val merchants: MerchantDao,
    private val life: LifeRepositoryImpl,
    private val reminders: ReminderRepositoryImpl,
    private val reminderText: ReminderText,
    private val diagnostics: DiagnosticsRepository,
    private val time: TimeProvider,
) : RecordRepository {

    override fun observeForAttention(): Flow<List<Record>> =
        records.observeForAttention(time.nowMillis() - 48L * 60 * 60 * 1000).map { list -> list.map { it.toDomain() } }

    override fun observeInbox(filter: InboxFilter, category: Category?): Flow<List<Record>> {
        val source = if (category == null) records.observeByStatus(filter.statuses, 500)
        else records.observeByStatusAndCategory(filter.statuses, category.name, 500)
        return source.map { list -> list.map { it.toDomain() } }
    }

    override fun observeCount(status: RecordStatus): Flow<Int> = records.observeCount(status)

    override fun observe(id: Long): Flow<Record?> = records.observeById(id).map { it?.toDomain() }

    override suspend fun get(id: Long): Record? = records.getById(id)?.toDomain()

    /** Called by notification ingestion. Returns the record id and the status it received. */
    suspend fun createFromSignal(
        signal: ParsedSignal,
        notificationId: Long?,
        sourcePackage: String,
        sourceLabel: String,
        originalText: String?,
        postedAt: Long = time.nowMillis(),
        historical: Boolean = false,
    ): Pair<Long, RecordStatus> {
        val now = time.nowMillis()
        val auto = signal.confidence >= ConfidencePolicy.AUTO
        val deadline = signal.dueAt ?: signal.expiresAt ?: signal.eventAt
        val status = when {
            !auto -> RecordStatus.REVIEW
            signal.alreadyHandled -> RecordStatus.HANDLED
            // Imported history: anything whose date has passed is already in the past for the user.
            historical && signal.type != RecordType.INSURANCE && signal.type != RecordType.SUBSCRIPTION &&
                (deadline == null || deadline < now - 24L * 60 * 60 * 1000) -> RecordStatus.HANDLED
            signal.actionRequired -> RecordStatus.ACTION_REQUIRED
            else -> RecordStatus.UNREAD
        }
        val extras = RecordExtras(
            direction = signal.direction, accountRef = signal.accountRef, deliveryState = signal.deliveryState,
            tripMode = signal.tripMode, tripFrom = signal.tripFrom, tripTo = signal.tripTo, recurrence = signal.recurrence,
            insuranceKind = signal.insuranceKind, vehicleReg = signal.vehicleReg, parserId = signal.parserId,
            originalText = originalText, salary = signal.salary.takeIf { it }, balancePaise = signal.balancePaise,
        )

        mergeIntoExisting(signal, status, now)?.let { return it to status }
        if (auto) mergeCrossSourceTransaction(signal, sourcePackage, postedAt, now)?.let { return it to status }

        val record = Record(
            type = signal.type, category = learnedCategory(signal), title = signal.title,
            // With "Keep original notification text" off, no notification wording is kept at all.
            summary = if (originalText == null) "" else signal.summary,
            merchant = signal.merchant, amountPaise = signal.amountPaise, dueAt = signal.dueAt, eventAt = signal.eventAt,
            expiresAt = signal.expiresAt, reference = signal.reference, importance = signal.importance, status = status,
            confidence = signal.confidence, source = RecordSource.NOTIFICATION, sourcePackage = sourcePackage,
            sourceLabel = sourceLabel, notificationId = notificationId, extras = extras,
            // Dated when it actually arrived, so imported SMS sort into the right month.
            createdAt = postedAt.coerceAtMost(now),
        )
        // History keeps its original timestamp, so it never shows up as "recently handled" on Home.
        val id = records.insert(record.toEntity(if (historical) record.createdAt else now))
        if (auto) materialize(record.copy(id = id))
        // Only a confident "bill paid" may close other bills; uncertain ones wait for the user.
        if (auto && signal.type == RecordType.BILL && signal.alreadyHandled) closeOpenBills(signal.title, now)
        return id to status
    }

    /** Shipping updates and repeat booking notifications update the original record instead of duplicating it. */
    private suspend fun mergeIntoExisting(signal: ParsedSignal, status: RecordStatus, now: Long): Long? {
        val ref = signal.reference ?: return null
        when (signal.type) {
            RecordType.DELIVERY -> {
                val existing = deliveries.findByOrderRef(ref) ?: return null
                val record = records.getById(existing.recordId) ?: return null
                val state = signal.deliveryState ?: existing.state
                deliveries.upsert(
                    existing.copy(
                        state = state,
                        expectedAt = signal.eventAt ?: existing.expectedAt,
                        deliveredAt = if (state == DeliveryState.DELIVERED) now else existing.deliveredAt,
                    ),
                )
                records.update(
                    record.copy(
                        summary = signal.summary,
                        eventAt = signal.eventAt ?: record.eventAt,
                        status = if (state == DeliveryState.DELIVERED || state == DeliveryState.CANCELLED) RecordStatus.HANDLED else record.status,
                        updatedAt = now,
                    ),
                )
                return record.id
            }
            RecordType.TRIP -> {
                val existing = trips.findByPnr(ref) ?: return null
                val record = records.getById(existing.recordId) ?: return null
                records.update(
                    record.copy(
                        summary = signal.summary,
                        eventAt = signal.eventAt ?: record.eventAt,
                        status = if (signal.alreadyHandled) RecordStatus.HANDLED else record.status,
                        updatedAt = now,
                    ),
                )
                return record.id
            }
            else -> return null
        }
    }

    private suspend fun closeOpenBills(title: String, now: Long) {
        for (open in records.openByTypeAndTitle(RecordType.BILL, title, now - 45L * 24 * 60 * 60 * 1000)) {
            records.updateStatus(open.id, RecordStatus.HANDLED, now)
            bills.markPaid(open.id, now)
            reminders.cancelForTarget(ReminderTarget.RECORD, open.id)
        }
    }

    override suspend fun addManual(record: Record): Long {
        val now = time.nowMillis()
        val status = if (record.type == RecordType.TRANSACTION) RecordStatus.HANDLED else record.status
        val toSave = record.copy(source = RecordSource.MANUAL, confidence = 1f, status = status)
        val id = records.insert(toSave.toEntity(now))
        materialize(toSave.copy(id = id))
        diagnostics.audit(AuditAction.CREATE, "record", id)
        return id
    }

    override suspend fun update(record: Record) {
        val now = time.nowMillis()
        records.update(record.toEntity(now))
        if (record.materialized && record.status != RecordStatus.REVIEW) materialize(record)
        diagnostics.audit(AuditAction.EDIT, "record", record.id)
    }

    override suspend fun confirm(record: Record) {
        val now = time.nowMillis()
        val deadline = record.dueAt ?: record.expiresAt
        val status = when {
            record.type == RecordType.TRANSACTION -> RecordStatus.HANDLED
            record.extras.deliveryState == DeliveryState.DELIVERED -> RecordStatus.HANDLED
            deadline != null && deadline >= now - 24L * 60 * 60 * 1000 -> RecordStatus.ACTION_REQUIRED
            else -> RecordStatus.UNREAD
        }
        val confirmed = record.copy(status = status, confidence = 1f)
        records.update(confirmed.toEntity(now))
        materialize(confirmed)
        diagnostics.increment(Counters.PARSER_CONFIRMATIONS)
        diagnostics.audit(AuditAction.CONFIRM, "record", record.id)
        AshLogger.i(TAG, "Record ${record.id} confirmed (${record.type}, parser=${record.extras.parserId})")
    }

    override suspend fun setStatus(id: Long, status: RecordStatus) {
        val now = time.nowMillis()
        records.updateStatus(id, status, now)
        if (status == RecordStatus.HANDLED || status == RecordStatus.ARCHIVED) {
            reminders.cancelForTarget(ReminderTarget.RECORD, id)
            if (bills.get(id) != null) bills.markPaid(id, now)
        } else if (status == RecordStatus.UNREAD || status == RecordStatus.ACTION_REQUIRED) {
            // Reopening brings the reminders back.
            if (bills.get(id)?.paidAt != null) bills.markPaid(id, null)
            records.getById(id)?.toDomain()?.let { planRecordReminders(it) }
        }
        if (status == RecordStatus.ARCHIVED) diagnostics.audit(AuditAction.DISMISS, "record", id)
    }

    override suspend fun delete(id: Long) {
        records.softDelete(id, time.nowMillis())
        reminders.cancelForTarget(ReminderTarget.RECORD, id)
        diagnostics.audit(AuditAction.DELETE, "record", id)
    }

    /** Creates or refreshes typed rows (transaction, bill, trip, ...) and automatic reminders for a confirmed record. */
    private suspend fun materialize(record: Record) {
        val now = time.nowMillis()
        val extras = record.extras
        db.withTransaction {
            when (record.type) {
                RecordType.TRANSACTION -> record.amountPaise?.let { amount ->
                    upsertTransaction(record, amount, extras.direction ?: TxnDirection.UNKNOWN, now)
                }
                RecordType.BILL -> record.dueAt?.let { due ->
                    val existing = bills.get(record.id)
                    bills.upsert(
                        BillEntity(
                            recordId = record.id, biller = record.title, amountPaise = record.amountPaise, dueAt = due,
                            paidAt = existing?.paidAt ?: if (record.status == RecordStatus.HANDLED) now else null,
                            recurrence = extras.recurrence ?: existing?.recurrence,
                        ),
                    )
                }
                RecordType.DELIVERY -> deliveries.upsert(
                    DeliveryEntity(
                        recordId = record.id, merchantName = record.merchant ?: record.title, orderRef = record.reference,
                        state = extras.deliveryState ?: DeliveryState.ORDERED, expectedAt = record.eventAt,
                        deliveredAt = if (extras.deliveryState == DeliveryState.DELIVERED) now else null,
                    ),
                )
                RecordType.TRIP -> trips.upsert(
                    TripEntity(
                        recordId = record.id, mode = extras.tripMode ?: TripMode.OTHER, origin = extras.tripFrom,
                        destination = extras.tripTo, departAt = record.eventAt, pnr = record.reference,
                    ),
                )
                else -> Unit
            }
            records.getById(record.id)?.let { if (!it.materialized) records.update(it.copy(materialized = true, updatedAt = now)) }
        }

        // Entities that live outside the record (policies, subscriptions, vehicles) and reminders.
        when (record.type) {
            RecordType.INSURANCE -> linkPolicy(record)
            RecordType.TRANSACTION -> if (extras.direction == TxnDirection.EXPENSE) record.amountPaise?.let { linkBillPayment(record, it, now) }
            RecordType.SUBSCRIPTION -> linkSubscription(record, now)
            RecordType.VEHICLE -> linkVehicle(record)
            else -> Unit
        }
        planRecordReminders(record)
    }

    private suspend fun upsertTransaction(record: Record, amount: Long, direction: TxnDirection, now: Long) {
        val existing = transactions.get(record.id)
        transactions.upsert(
            TransactionEntity(
                recordId = record.id,
                amountPaise = amount,
                direction = direction,
                merchantId = record.merchant?.let { merchantId(it, record.category) },
                accountRef = record.extras.accountRef,
                occurredAt = record.eventAt ?: record.createdAt.takeIf { it > 0 } ?: now,
                vehicleId = existing?.vehicleId,
                userCorrected = existing?.userCorrected ?: false,
                // A user's own "Mark as salary" choice always wins over detection.
                isSalary = if (existing?.userCorrected == true) existing.isSalary else existing?.isSalary == true || isSalary(record, amount, direction),
                balanceAfterPaise = record.extras.balancePaise ?: existing?.balanceAfterPaise,
            ),
        )
    }

    /** Salary keyword in the SMS, or a credit from a payer the user already marked as salary (at least half the last amount). */
    private suspend fun isSalary(record: Record, amount: Long, direction: TxnDirection): Boolean {
        if (direction != TxnDirection.INCOME) return false
        if (record.extras.salary == true) return true
        val payer = record.merchant ?: return false
        val last = transactions.lastSalaryAmountFrom(payer) ?: return false
        return amount * 2 >= last
    }

    /**
     * One payment often produces two messages: the bank SMS and the UPI app's notification.
     * If a transaction with the same amount and direction arrived from another app within 15
     * minutes, enrich that one instead of creating a second (so balances are not counted twice).
     */
    private suspend fun mergeCrossSourceTransaction(signal: ParsedSignal, sourcePackage: String, postedAt: Long, now: Long): Long? {
        if (signal.type != RecordType.TRANSACTION) return null
        val amount = signal.amountPaise ?: return null
        val direction = signal.direction ?: return null
        val window = 15L * 60 * 1000
        val existingId = transactions.findCrossSourceDuplicate(amount, direction, postedAt - window, postedAt + window, sourcePackage) ?: return null
        val record = records.getById(existingId) ?: return null
        val txn = transactions.get(existingId) ?: return null
        val betterMerchant = record.merchantName ?: signal.merchant
        records.update(record.copy(merchantName = betterMerchant, title = if (record.merchantName == null && signal.merchant != null) signal.merchant else record.title, updatedAt = now))
        transactions.upsert(
            txn.copy(
                accountRef = txn.accountRef ?: signal.accountRef,
                merchantId = txn.merchantId ?: betterMerchant?.let { merchantId(it, Category.fromKey(record.categoryKey)) },
                isSalary = txn.isSalary || signal.salary,
                balanceAfterPaise = txn.balanceAfterPaise ?: signal.balancePaise,
            ),
        )
        diagnostics.increment(Counters.DUPLICATES_MERGED)
        AshLogger.i(TAG, "Merged duplicate ${direction.name} from $sourcePackage into record $existingId")
        return existingId
    }

    /** Payments to a merchant the user has categorised before keep that category. */
    private suspend fun learnedCategory(signal: ParsedSignal): Category {
        if (signal.type != RecordType.TRANSACTION) return signal.category
        val name = signal.merchant ?: return signal.category
        val canonical = MerchantDictionary.canonical(name)?.name ?: name
        return merchants.findByName(canonical)?.defaultCategoryKey?.let { Category.fromKey(it) } ?: signal.category
    }

    /**
     * A payment that matches an open bill (same amount within ₹1, due between 30 days before and
     * 15 days after the payment) marks that bill paid and stops its reminders. When several bills
     * match, the one whose biller appears in the payment is preferred; ambiguous matches are left alone.
     */
    private suspend fun linkBillPayment(payment: Record, amount: Long, now: Long) {
        val paidAt = payment.eventAt ?: payment.createdAt.takeIf { it > 0 } ?: now
        val day = 24L * 60 * 60 * 1000
        val candidates = bills.openBillsMatching(amount - 100, amount + 100, paidAt - 30 * day, paidAt + 15 * day, paidAt)
        if (candidates.isEmpty()) return
        val haystack = "${payment.merchant.orEmpty()} ${payment.title} ${payment.extras.originalText.orEmpty()}".lowercase()
        val bill = candidates.firstOrNull { haystack.contains(it.biller.lowercase()) }
            ?: candidates.singleOrNull()
            ?: return
        bills.markPaid(bill.recordId, paidAt)
        records.updateStatus(bill.recordId, RecordStatus.HANDLED, now)
        reminders.cancelForTarget(ReminderTarget.RECORD, bill.recordId)
        diagnostics.increment(Counters.BILLS_AUTO_PAID)
        AshLogger.i(TAG, "Payment ${payment.id} settled bill ${bill.recordId} (${bill.biller})")
    }

    override fun observeTransactionsSince(from: Long): Flow<List<TransactionItem>> =
        transactions.observeSince(from).map { rows -> rows.map { it.toDomain() } }

    override fun observeIsSalary(recordId: Long): Flow<Boolean?> = transactions.observeIsSalary(recordId)

    override suspend fun setSalary(recordId: Long, isSalary: Boolean) {
        val existing = transactions.get(recordId) ?: return
        // Remember it as a user decision so detection never overrides it.
        transactions.upsert(existing.copy(isSalary = isSalary, userCorrected = true))
        diagnostics.audit(AuditAction.EDIT, "salary", recordId)
    }

    private suspend fun linkPolicy(record: Record) {
        val existing = record.reference?.let { life.findPolicyByNumber(it) }
        val vehicleId = record.extras.vehicleReg?.let { life.findVehicleByReg(it)?.id }
        val policy = (existing ?: InsurancePolicy(insurer = record.title)).copy(
            insurer = existing?.insurer ?: record.title,
            policyNumber = record.reference ?: existing?.policyNumber,
            kind = record.extras.insuranceKind ?: existing?.kind ?: app.ash.domain.model.InsuranceKind.OTHER,
            expiresAt = record.expiresAt ?: existing?.expiresAt,
            premiumPaise = record.amountPaise ?: existing?.premiumPaise,
            vehicleId = existing?.vehicleId ?: vehicleId,
            profileId = existing?.profileId ?: record.profileId,
        )
        life.savePolicy(policy)
    }

    private suspend fun linkSubscription(record: Record, now: Long) {
        val name = record.merchant ?: record.title
        val period = record.extras.recurrence ?: RecurrencePeriod.MONTHLY
        val existing = life.findSubscriptionByName(name)
        val next = record.dueAt ?: if (record.extras.direction == TxnDirection.EXPENSE) now + period.approxDays * 24L * 60 * 60 * 1000 else existing?.nextRenewalAt
        life.saveSubscription(
            (existing ?: Subscription(name = name, amountPaise = record.amountPaise ?: 0, period = period)).copy(
                amountPaise = record.amountPaise ?: existing?.amountPaise ?: 0,
                period = period,
                nextRenewalAt = next,
                active = true,
                profileId = existing?.profileId ?: record.profileId,
            ),
        )
        if (record.extras.direction == TxnDirection.EXPENSE && record.amountPaise != null) {
            upsertTransaction(record, record.amountPaise, TxnDirection.EXPENSE, now)
        }
    }

    private suspend fun linkVehicle(record: Record) {
        val reg = record.extras.vehicleReg ?: return
        val vehicle = life.findVehicleByReg(reg) ?: return
        val text = (record.summary + " " + (record.extras.originalText ?: "")).lowercase()
        val updated = when {
            ("puc" in text || "pollution" in text) && record.dueAt != null -> vehicle.copy(pucExpiresAt = record.dueAt)
            "service" in text && record.dueAt != null -> vehicle.copy(nextServiceAt = record.dueAt)
            else -> return
        }
        life.saveVehicle(updated)
    }

    private suspend fun planRecordReminders(record: Record) {
        if (record.status == RecordStatus.HANDLED || record.status == RecordStatus.ARCHIVED || record.status == RecordStatus.REVIEW) {
            reminders.cancelForTarget(ReminderTarget.RECORD, record.id)
            return
        }
        when (record.type) {
            RecordType.BILL, RecordType.GOVERNMENT, RecordType.APPOINTMENT, RecordType.GENERIC -> reminders.planAuto(
                ReminderTarget.RECORD, record.id, ReminderPlanner.Kind.BILL_DUE, record.dueAt, record.title,
            ) { lead -> reminderText.due(lead, record.amountPaise) }
            RecordType.VEHICLE -> reminders.planAuto(
                ReminderTarget.RECORD, record.id, ReminderPlanner.Kind.EXPIRY, record.dueAt, record.title,
            ) { lead -> reminderText.expires(lead) }
            RecordType.TRIP -> reminders.planAuto(
                ReminderTarget.RECORD, record.id, ReminderPlanner.Kind.EVENT, record.eventAt,
                reminderText.string(R.string.reminder_trip_title, record.title),
            ) { lead -> reminderText.event(lead) }
            else -> Unit
        }
    }

    private suspend fun merchantId(name: String, category: Category): Long {
        val canonical = MerchantDictionary.canonical(name)?.name ?: name
        merchants.findByName(canonical)?.let { return it.id }
        val inserted = merchants.insert(MerchantEntity(canonicalName = canonical, defaultCategoryKey = category.name))
        return if (inserted > 0) inserted else merchants.findByName(canonical)!!.id
    }

    override fun observeTransactions(limit: Int): Flow<List<TransactionItem>> =
        transactions.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeMonthSummary(from: Long, to: Long): Flow<MoneySummary> =
        transactions.observeTotals(from, to).map { totals ->
            fun sum(d: TxnDirection) = totals.firstOrNull { it.direction == d }?.total ?: 0
            MoneySummary(sum(TxnDirection.INCOME), sum(TxnDirection.EXPENSE), sum(TxnDirection.REFUND))
        }

    override suspend fun correctTransaction(recordId: Long, amountPaise: Long, direction: TxnDirection, merchant: String?, category: Category) {
        val now = time.nowMillis()
        val record = records.getById(recordId) ?: return
        val existing = transactions.get(recordId)
        records.update(record.copy(amountPaise = amountPaise, merchantName = merchant, categoryKey = category.name, title = merchant ?: record.title, confidence = 1f, updatedAt = now))
        // Learn: future payments to this merchant get the category the user chose.
        merchant?.let { name -> merchants.setDefaultCategory(merchantId(name, category), category.name) }
        transactions.upsert(
            TransactionEntity(
                recordId = recordId, amountPaise = amountPaise, direction = direction,
                merchantId = merchant?.let { merchantId(it, category) }, accountRef = existing?.accountRef,
                occurredAt = existing?.occurredAt ?: record.eventAt ?: record.createdAt, vehicleId = existing?.vehicleId,
                userCorrected = true,
                isSalary = existing?.isSalary ?: false,
                balanceAfterPaise = existing?.balanceAfterPaise,
            ),
        )
        diagnostics.audit(AuditAction.EDIT, "transaction", recordId)
    }

    override fun observeBills(limit: Int): Flow<List<BillItem>> = bills.observeAll(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun setBillPaid(recordId: Long, paid: Boolean) {
        val now = time.nowMillis()
        bills.markPaid(recordId, if (paid) now else null)
        records.updateStatus(recordId, if (paid) RecordStatus.HANDLED else RecordStatus.ACTION_REQUIRED, now)
        if (paid) reminders.cancelForTarget(ReminderTarget.RECORD, recordId)
        else records.getById(recordId)?.toDomain()?.let { planRecordReminders(it) }
    }

    /** Used by import/maintenance for raw entity access. */
    internal suspend fun entity(id: Long): RecordEntity? = records.getById(id)

    private companion object {
        const val TAG = "Records"
    }
}
