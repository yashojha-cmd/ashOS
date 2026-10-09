package app.ash.domain.usecase

import app.ash.common.TimeProvider
import app.ash.domain.model.Category
import app.ash.domain.model.Document
import app.ash.domain.model.Importance
import app.ash.domain.model.InsurancePolicy
import app.ash.domain.model.Record
import app.ash.domain.model.RecordStatus
import app.ash.domain.model.RecordType
import app.ash.domain.model.Subscription
import app.ash.domain.model.TransactionItem
import app.ash.domain.model.Vehicle
import app.ash.domain.model.Warranty
import app.ash.domain.repository.DocumentRepository
import app.ash.domain.repository.LifeRepository
import app.ash.domain.repository.RecordRepository
import app.ash.intelligence.PriorityEngine
import app.ash.intelligence.PriorityEngine.Bucket
import app.ash.intelligence.PriorityEngine.DeadlineKind
import app.ash.intelligence.SubscriptionDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

sealed interface AttentionTarget {
    data class RecordTarget(val id: Long) : AttentionTarget
    data class PolicyTarget(val id: Long) : AttentionTarget
    data class DocumentTarget(val id: Long) : AttentionTarget
    data class WarrantyTarget(val id: Long) : AttentionTarget
    data class VehicleTarget(val id: Long) : AttentionTarget
    data class SubscriptionTarget(val id: Long) : AttentionTarget
}

enum class AttentionKind { BILL, EXPIRY, PUC, SERVICE, RENEWAL, TRIP, DELIVERY, GENERIC }

data class AttentionItem(
    val key: String,
    val title: String,
    val category: Category,
    val kind: AttentionKind,
    val amountPaise: Long?,
    val deadlineAt: Long?,
    val deadlineKind: DeadlineKind,
    val result: PriorityEngine.Result,
    val target: AttentionTarget,
)

data class HomeFeed(
    val needsAttention: List<AttentionItem> = emptyList(),
    val comingUp: List<AttentionItem> = emptyList(),
    val recentlyHandled: List<AttentionItem> = emptyList(),
    val upcomingPayments: List<AttentionItem> = emptyList(),
    val recentTransactions: List<TransactionItem> = emptyList(),
    val reviewCount: Int = 0,
    val expiringDocuments: List<Document> = emptyList(),
    val monthlyRecurringPaise: Long = 0,
    val isEmpty: Boolean = true,
)

/**
 * Builds the Home "What needs your attention?" feed (§5, §15) from every local source,
 * scored by the explainable [PriorityEngine]. No AI involved.
 */
class GetAttentionFeedUseCase @Inject constructor(
    private val records: RecordRepository,
    private val life: LifeRepository,
    private val documents: DocumentRepository,
    private val time: TimeProvider,
) {
    operator fun invoke(): Flow<HomeFeed> = combine(
        records.observeForAttention(),
        combine(life.observePolicies(), life.observeVehicles(), life.observeWarranties(), life.observeSubscriptions()) { p, v, w, s -> Quad(p, v, w, s) },
        documents.observeDocuments(),
        records.observeTransactions(5),
    ) { recordList, entities, docs, txns ->
        build(recordList, entities.policies, entities.vehicles, entities.warranties, entities.subscriptions, docs, txns)
    }

    private data class Quad(val policies: List<InsurancePolicy>, val vehicles: List<Vehicle>, val warranties: List<Warranty>, val subscriptions: List<Subscription>)

    internal fun build(
        recordList: List<Record>,
        policies: List<InsurancePolicy>,
        vehicles: List<Vehicle>,
        warranties: List<Warranty>,
        subscriptions: List<Subscription>,
        docs: List<Document>,
        txns: List<TransactionItem>,
    ): HomeFeed {
        val now = time.nowMillis()
        val today = time.today()
        val zone = time.zone()
        fun score(category: Category, importance: Importance, deadline: Long?, kind: DeadlineKind, handled: Boolean = false, handledAt: Long? = null, action: Boolean = false) =
            PriorityEngine.evaluate(PriorityEngine.Input(category, importance, deadline, kind, handled, handledAt, action), now, today, zone)

        val items = mutableListOf<AttentionItem>()
        for (r in recordList) {
            if (r.status == RecordStatus.REVIEW) continue
            if (r.type == RecordType.TRANSACTION || r.type == RecordType.INSURANCE || r.type == RecordType.SUBSCRIPTION) continue
            val (deadline, kind) = when {
                r.dueAt != null -> r.dueAt to DeadlineKind.DUE
                r.expiresAt != null -> r.expiresAt to DeadlineKind.EXPIRES
                r.eventAt != null -> r.eventAt to DeadlineKind.EVENT
                else -> null to DeadlineKind.NONE
            }
            val handled = r.status == RecordStatus.HANDLED || r.status == RecordStatus.ARCHIVED
            val result = score(r.category, r.importance, deadline, kind, handled, if (r.status == RecordStatus.HANDLED) r.updatedAt else null, r.status == RecordStatus.ACTION_REQUIRED)
            val attentionKind = when (r.type) {
                RecordType.BILL -> AttentionKind.BILL
                RecordType.TRIP -> AttentionKind.TRIP
                RecordType.DELIVERY -> AttentionKind.DELIVERY
                RecordType.VEHICLE -> AttentionKind.EXPIRY
                else -> AttentionKind.GENERIC
            }
            items += AttentionItem("r${r.id}", r.title, r.category, attentionKind, r.amountPaise, deadline, kind, result, AttentionTarget.RecordTarget(r.id))
        }
        for (p in policies) {
            val result = score(Category.INSURANCE, Importance.CRITICAL, p.expiresAt, if (p.expiresAt != null) DeadlineKind.EXPIRES else DeadlineKind.NONE)
            items += AttentionItem("p${p.id}", p.insurer, Category.INSURANCE, AttentionKind.EXPIRY, p.premiumPaise, p.expiresAt, DeadlineKind.EXPIRES, result, AttentionTarget.PolicyTarget(p.id))
        }
        for (d in docs) {
            val expires = d.expiresAt ?: continue
            val result = score(Category.DOCUMENTS, Importance.HIGH, expires, DeadlineKind.EXPIRES)
            items += AttentionItem("d${d.id}", d.title, Category.DOCUMENTS, AttentionKind.EXPIRY, null, expires, DeadlineKind.EXPIRES, result, AttentionTarget.DocumentTarget(d.id))
        }
        for (w in warranties) {
            val result = score(Category.DOCUMENTS, Importance.MEDIUM, w.expiresAt, DeadlineKind.EXPIRES)
            items += AttentionItem("w${w.id}", w.product, Category.DOCUMENTS, AttentionKind.EXPIRY, null, w.expiresAt, DeadlineKind.EXPIRES, result, AttentionTarget.WarrantyTarget(w.id))
        }
        for (v in vehicles) {
            v.pucExpiresAt?.let {
                items += AttentionItem("vp${v.id}", v.name, Category.VEHICLE, AttentionKind.PUC, null, it, DeadlineKind.EXPIRES,
                    score(Category.VEHICLE, Importance.HIGH, it, DeadlineKind.EXPIRES), AttentionTarget.VehicleTarget(v.id))
            }
            v.nextServiceAt?.let {
                items += AttentionItem("vs${v.id}", v.name, Category.VEHICLE, AttentionKind.SERVICE, null, it, DeadlineKind.DUE,
                    score(Category.VEHICLE, Importance.MEDIUM, it, DeadlineKind.DUE), AttentionTarget.VehicleTarget(v.id))
            }
        }
        for (s in subscriptions.filter { it.active }) {
            val renewal = s.nextRenewalAt ?: continue
            items += AttentionItem("s${s.id}", s.name, Category.SUBSCRIPTIONS, AttentionKind.RENEWAL, s.amountPaise, renewal, DeadlineKind.DUE,
                score(Category.SUBSCRIPTIONS, Importance.MEDIUM, renewal, DeadlineKind.DUE), AttentionTarget.SubscriptionTarget(s.id))
        }

        val byScore = items.sortedWith(compareByDescending<AttentionItem> { it.result.score }.thenBy { it.deadlineAt ?: Long.MAX_VALUE })
        val thirtyDays = now + 30L * 24 * 60 * 60 * 1000
        return HomeFeed(
            needsAttention = byScore.filter { it.result.bucket == Bucket.NEEDS_ATTENTION }.take(5),
            comingUp = byScore.filter { it.result.bucket == Bucket.COMING_UP }.take(5),
            recentlyHandled = items.filter { it.result.bucket == Bucket.RECENTLY_HANDLED }.take(3),
            upcomingPayments = items.filter {
                it.amountPaise != null && it.deadlineKind == DeadlineKind.DUE && it.deadlineAt != null &&
                    it.deadlineAt in (now - 24L * 60 * 60 * 1000)..thirtyDays && it.result.bucket != Bucket.RECENTLY_HANDLED && it.result.bucket != Bucket.HIDDEN
            }.sortedBy { it.deadlineAt }.take(6),
            recentTransactions = txns,
            reviewCount = recordList.count { it.status == RecordStatus.REVIEW },
            expiringDocuments = docs.filter { it.expiresAt != null && it.expiresAt < now + 60L * 24 * 60 * 60 * 1000 }.sortedBy { it.expiresAt },
            monthlyRecurringPaise = SubscriptionDetector.monthlyTotal(subscriptions.filter { it.active }.map { it.amountPaise to it.period }),
            isEmpty = items.isEmpty() && txns.isEmpty() && recordList.isEmpty(),
        )
    }
}
