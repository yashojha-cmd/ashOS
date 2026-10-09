package app.ash.data.repository

import app.ash.R
import app.ash.common.TimeProvider
import app.ash.data.local.dao.DocumentDao
import app.ash.data.local.dao.PolicyDao
import app.ash.data.local.dao.SubscriptionDao
import app.ash.data.local.dao.TransactionDao
import app.ash.data.local.dao.VehicleDao
import app.ash.data.local.dao.WarrantyDao
import app.ash.data.local.entities.AuditAction
import app.ash.data.local.entities.InsurancePolicyEntity
import app.ash.data.local.entities.SubscriptionEntity
import app.ash.data.local.entities.VehicleEntity
import app.ash.data.local.entities.WarrantyEntity
import app.ash.domain.model.InsurancePolicy
import app.ash.domain.model.ReminderTarget
import app.ash.domain.model.Subscription
import app.ash.domain.model.TimelineEvent
import app.ash.domain.model.TransactionItem
import app.ash.domain.model.Vehicle
import app.ash.domain.model.Warranty
import app.ash.domain.repository.LifeRepository
import app.ash.intelligence.SubscriptionDetector
import app.ash.logging.AshLogger
import app.ash.reminder.ReminderPlanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LifeRepositoryImpl @Inject constructor(
    private val vehicles: VehicleDao,
    private val policies: PolicyDao,
    private val warranties: WarrantyDao,
    private val subscriptions: SubscriptionDao,
    private val transactions: TransactionDao,
    private val documents: DocumentDao,
    private val reminders: ReminderRepositoryImpl,
    private val text: ReminderText,
    private val diagnostics: DiagnosticsRepository,
    private val time: TimeProvider,
) : LifeRepository {

    // ---- Vehicles ----

    override fun observeVehicles(): Flow<List<Vehicle>> = vehicles.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeVehicle(id: Long): Flow<Vehicle?> = vehicles.observeById(id).map { it?.toDomain() }

    override suspend fun getVehicle(id: Long): Vehicle? = vehicles.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()

    suspend fun findVehicleByReg(reg: String): Vehicle? = vehicles.findByReg(normaliseReg(reg))?.toDomain()

    override suspend fun saveVehicle(vehicle: Vehicle): Long {
        val now = time.nowMillis()
        val reg = vehicle.regNumber?.let(::normaliseReg)?.ifBlank { null }
        val entity = VehicleEntity(
            id = vehicle.id, name = vehicle.name.trim(), brand = vehicle.brand, model = vehicle.model, regNumber = reg,
            fuelType = vehicle.fuelType, purchasedAt = vehicle.purchasedAt, pucExpiresAt = vehicle.pucExpiresAt,
            lastServiceAt = vehicle.lastServiceAt, nextServiceAt = vehicle.nextServiceAt,
            warrantyExpiresAt = vehicle.warrantyExpiresAt, loanEmiPaise = vehicle.loanEmiPaise,
            emiDayOfMonth = vehicle.emiDayOfMonth, profileId = vehicle.profileId, notes = vehicle.notes,
            createdAt = now, updatedAt = now,
        )
        val id = if (vehicle.id == 0L) {
            vehicles.insert(entity).also { diagnostics.audit(AuditAction.CREATE, "vehicle", it) }
        } else {
            val existing = vehicles.getById(vehicle.id)
            vehicles.update(entity.copy(createdAt = existing?.createdAt ?: now))
            diagnostics.audit(AuditAction.EDIT, "vehicle", vehicle.id)
            vehicle.id
        }
        reminders.planAuto(ReminderTarget.VEHICLE_PUC, id, ReminderPlanner.Kind.EXPIRY, vehicle.pucExpiresAt,
            text.string(R.string.reminder_puc_title, vehicle.name)) { text.expires(it) }
        reminders.planAuto(ReminderTarget.VEHICLE_SERVICE, id, ReminderPlanner.Kind.SERVICE, vehicle.nextServiceAt,
            text.string(R.string.reminder_service_title, vehicle.name)) { text.due(it, null) }
        return id
    }

    override suspend fun deleteVehicle(id: Long) {
        vehicles.softDelete(id, time.nowMillis())
        reminders.cancelForTarget(ReminderTarget.VEHICLE_PUC, id)
        reminders.cancelForTarget(ReminderTarget.VEHICLE_SERVICE, id)
        diagnostics.audit(AuditAction.DELETE, "vehicle", id)
    }

    override fun observeVehicleExpenses(vehicleId: Long): Flow<List<TransactionItem>> =
        transactions.observeForVehicle(vehicleId).map { rows -> rows.map { it.toDomain() } }

    override fun observeTimeline(vehicleId: Long): Flow<List<TimelineEvent>> = combine(
        vehicles.observeById(vehicleId),
        policies.observeForVehicle(vehicleId),
        documents.observeForVehicle(vehicleId),
        warranties.observeAll(),
        transactions.observeForVehicle(vehicleId),
    ) { vehicle, vehiclePolicies, docs, allWarranties, expenses ->
        if (vehicle == null) return@combine emptyList()
        buildList {
            vehicle.purchasedAt?.let { add(TimelineEvent(it, vehicle.name, TimelineEvent.Kind.PURCHASE)) }
            vehicle.lastServiceAt?.let { add(TimelineEvent(it, vehicle.name, TimelineEvent.Kind.SERVICE_DONE)) }
            vehicle.nextServiceAt?.let { add(TimelineEvent(it, vehicle.name, TimelineEvent.Kind.SERVICE_DUE)) }
            vehicle.pucExpiresAt?.let { add(TimelineEvent(it, vehicle.name, TimelineEvent.Kind.PUC_EXPIRY)) }
            vehicle.warrantyExpiresAt?.let { add(TimelineEvent(it, vehicle.name, TimelineEvent.Kind.WARRANTY_EXPIRY)) }
            vehiclePolicies.forEach { p ->
                p.startAt?.let { add(TimelineEvent(it, p.insurer, TimelineEvent.Kind.INSURANCE_START)) }
                p.expiresAt?.let { add(TimelineEvent(it, p.insurer, TimelineEvent.Kind.INSURANCE_EXPIRY)) }
            }
            allWarranties.filter { it.vehicleId == vehicleId }.forEach { add(TimelineEvent(it.expiresAt, it.product, TimelineEvent.Kind.WARRANTY_EXPIRY)) }
            docs.forEach { add(TimelineEvent(it.document.createdAt, it.document.title, TimelineEvent.Kind.DOCUMENT)) }
            expenses.take(20).forEach { add(TimelineEvent(it.occurredAt, it.merchant ?: "", TimelineEvent.Kind.EXPENSE)) }
        }.sortedBy { it.at }
    }

    // ---- Insurance ----

    override fun observePolicies(): Flow<List<InsurancePolicy>> = policies.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observePoliciesForVehicle(vehicleId: Long): Flow<List<InsurancePolicy>> =
        policies.observeForVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    override suspend fun getPolicy(id: Long): InsurancePolicy? = policies.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()

    suspend fun findPolicyByNumber(number: String): InsurancePolicy? = policies.findByNumber(number)?.toDomain()

    override suspend fun savePolicy(policy: InsurancePolicy): Long {
        val now = time.nowMillis()
        val entity = InsurancePolicyEntity(
            id = policy.id, insurer = policy.insurer.trim(), policyNumber = policy.policyNumber?.trim()?.ifBlank { null },
            kind = policy.kind, startAt = policy.startAt, expiresAt = policy.expiresAt, premiumPaise = policy.premiumPaise,
            vehicleId = policy.vehicleId, profileId = policy.profileId, notes = policy.notes, createdAt = now, updatedAt = now,
        )
        val id = if (policy.id == 0L) {
            policies.insert(entity).also { diagnostics.audit(AuditAction.CREATE, "policy", it) }
        } else {
            val existing = policies.getById(policy.id)
            policies.update(entity.copy(createdAt = existing?.createdAt ?: now))
            diagnostics.audit(AuditAction.EDIT, "policy", policy.id)
            policy.id
        }
        reminders.planAuto(ReminderTarget.POLICY, id, ReminderPlanner.Kind.EXPIRY, policy.expiresAt,
            text.string(R.string.reminder_policy_title, policy.insurer)) { text.expires(it) }
        return id
    }

    override suspend fun deletePolicy(id: Long) {
        policies.softDelete(id, time.nowMillis())
        reminders.cancelForTarget(ReminderTarget.POLICY, id)
        diagnostics.audit(AuditAction.DELETE, "policy", id)
    }

    // ---- Warranties ----

    override fun observeWarranties(): Flow<List<Warranty>> = warranties.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getWarranty(id: Long): Warranty? = warranties.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()

    override suspend fun saveWarranty(warranty: Warranty): Long {
        val now = time.nowMillis()
        val entity = WarrantyEntity(
            id = warranty.id, product = warranty.product.trim(), purchasedAt = warranty.purchasedAt, expiresAt = warranty.expiresAt,
            documentId = warranty.documentId, vehicleId = warranty.vehicleId, profileId = warranty.profileId,
            createdAt = now, updatedAt = now,
        )
        val id = if (warranty.id == 0L) warranties.insert(entity) else warranty.id.also {
            warranties.update(entity.copy(createdAt = warranties.getById(it)?.createdAt ?: now))
        }
        reminders.planAuto(ReminderTarget.WARRANTY, id, ReminderPlanner.Kind.EXPIRY, warranty.expiresAt,
            text.string(R.string.reminder_warranty_title, warranty.product)) { text.expires(it) }
        return id
    }

    override suspend fun deleteWarranty(id: Long) {
        warranties.softDelete(id, time.nowMillis())
        reminders.cancelForTarget(ReminderTarget.WARRANTY, id)
    }

    // ---- Subscriptions ----

    override fun observeSubscriptions(): Flow<List<Subscription>> = subscriptions.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getSubscription(id: Long): Subscription? = subscriptions.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()

    suspend fun findSubscriptionByName(name: String): Subscription? = subscriptions.findByName(name)?.toDomain()

    override suspend fun saveSubscription(subscription: Subscription): Long {
        val now = time.nowMillis()
        val entity = SubscriptionEntity(
            id = subscription.id, name = subscription.name.trim(), merchantId = null, amountPaise = subscription.amountPaise,
            period = subscription.period, nextRenewalAt = subscription.nextRenewalAt, autoDetected = subscription.autoDetected,
            active = subscription.active, profileId = subscription.profileId, createdAt = now, updatedAt = now,
        )
        val id = if (subscription.id == 0L) subscriptions.insert(entity) else subscription.id.also {
            subscriptions.update(entity.copy(createdAt = subscriptions.getById(it)?.createdAt ?: now))
        }
        reminders.planAuto(
            ReminderTarget.SUBSCRIPTION, id, ReminderPlanner.Kind.RENEWAL,
            subscription.nextRenewalAt.takeIf { subscription.active },
            text.string(R.string.reminder_subscription_title, subscription.name),
        ) { text.renews(it, subscription.amountPaise) }
        return id
    }

    override suspend fun deleteSubscription(id: Long) {
        subscriptions.softDelete(id, time.nowMillis())
        reminders.cancelForTarget(ReminderTarget.SUBSCRIPTION, id)
    }

    override suspend fun detectSubscriptions(): Int {
        val now = time.nowMillis()
        val history = transactions.expensesSince(now - 400L * 24 * 60 * 60 * 1000)
            .map { SubscriptionDetector.Payment(it.merchant, it.amountPaise, it.occurredAt) }
        var added = 0
        for (found in SubscriptionDetector.detect(history)) {
            val existing = subscriptions.findByName(found.merchant)
            if (existing == null) {
                saveSubscription(
                    Subscription(
                        name = found.merchant, amountPaise = found.amountPaise, period = found.period,
                        nextRenewalAt = found.nextAt, autoDetected = true,
                    ),
                )
                added++
            } else if (existing.autoDetected && (existing.nextRenewalAt ?: 0) < found.nextAt) {
                saveSubscription(existing.toDomain().copy(nextRenewalAt = found.nextAt, amountPaise = found.amountPaise))
            }
        }
        if (added > 0) AshLogger.i(TAG, "Detected $added new recurring payments")
        return added
    }

    override suspend fun rollRenewals(): Int {
        val today = time.now().toLocalDate()
        var moved = 0
        for (entity in subscriptions.observeAll().first()) {
            val next = entity.nextRenewalAt ?: continue
            if (!entity.active) continue
            var date = app.ash.common.Dates.toLocalDate(next, time.zone())
            if (!date.isBefore(today)) continue
            while (date.isBefore(today)) {
                date = when (entity.period) {
                    app.ash.domain.model.RecurrencePeriod.WEEKLY -> date.plusWeeks(1)
                    app.ash.domain.model.RecurrencePeriod.MONTHLY -> date.plusMonths(1)
                    app.ash.domain.model.RecurrencePeriod.QUARTERLY -> date.plusMonths(3)
                    app.ash.domain.model.RecurrencePeriod.YEARLY -> date.plusYears(1)
                }
            }
            saveSubscription(entity.toDomain().copy(nextRenewalAt = app.ash.common.Dates.morningOf(date, time.zone())))
            moved++
        }
        return moved
    }

    private fun normaliseReg(reg: String) = reg.uppercase().replace(" ", "").replace("-", "")

    private companion object {
        const val TAG = "Life"
    }
}
