package app.ash.domain.model

import kotlinx.serialization.Serializable

data class Profile(
    val id: Long,
    val name: String,
    val relation: ProfileRelation,
)

/** A unit of information ASH extracted or the user added. Typed details live in [extras] and typed tables. */
data class Record(
    val id: Long = 0,
    val type: RecordType,
    val category: Category,
    val title: String,
    val summary: String,
    val merchant: String? = null,
    val amountPaise: Long? = null,
    val dueAt: Long? = null,
    val eventAt: Long? = null,
    val expiresAt: Long? = null,
    val reference: String? = null,
    val importance: Importance = Importance.MEDIUM,
    val status: RecordStatus = RecordStatus.UNREAD,
    val confidence: Float = 1f,
    val source: RecordSource = RecordSource.MANUAL,
    val sourcePackage: String? = null,
    val sourceLabel: String? = null,
    val notificationId: Long? = null,
    val profileId: Long? = null,
    val extras: RecordExtras = RecordExtras(),
    val materialized: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Type-specific fields captured at parse time; turned into typed rows when the record is confirmed. */
@Serializable
data class RecordExtras(
    val direction: TxnDirection? = null,
    val accountRef: String? = null,
    val deliveryState: DeliveryState? = null,
    val tripMode: TripMode? = null,
    val tripFrom: String? = null,
    val tripTo: String? = null,
    val recurrence: RecurrencePeriod? = null,
    val insuranceKind: InsuranceKind? = null,
    val vehicleReg: String? = null,
    val parserId: String? = null,
    val originalText: String? = null,
    /** Parser saw a salary keyword ("NEFT-SALARY", "Sal Cr"). */
    val salary: Boolean? = null,
    /** "Avl Bal" reported in the same message. */
    val balancePaise: Long? = null,
)

data class TransactionItem(
    val recordId: Long,
    val amountPaise: Long,
    val direction: TxnDirection,
    val merchant: String?,
    val accountRef: String?,
    val occurredAt: Long,
    val category: Category,
    val confidence: Float,
    val userCorrected: Boolean,
    val vehicleId: Long?,
    val sourceLabel: String?,
    val isSalary: Boolean = false,
    val balanceAfterPaise: Long? = null,
)

data class BillItem(
    val recordId: Long,
    val biller: String,
    val amountPaise: Long?,
    val dueAt: Long,
    val paidAt: Long?,
    val recurrence: RecurrencePeriod?,
    val status: RecordStatus,
)

data class Subscription(
    val id: Long = 0,
    val name: String,
    val amountPaise: Long,
    val period: RecurrencePeriod,
    val nextRenewalAt: Long? = null,
    val autoDetected: Boolean = false,
    val active: Boolean = true,
    val profileId: Long? = null,
)

data class Document(
    val id: Long = 0,
    val title: String,
    val docType: DocType,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val expiresAt: Long? = null,
    val issuedAt: Long? = null,
    val documentNumber: String? = null,
    val ocrText: String? = null,
    val notes: String? = null,
    val sensitive: Boolean = true,
    val needsReview: Boolean = false,
    val vehicleId: Long? = null,
    val policyId: Long? = null,
    val profileId: Long? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0,
)

data class Vehicle(
    val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val model: String? = null,
    val regNumber: String? = null,
    val fuelType: String? = null,
    val purchasedAt: Long? = null,
    val pucExpiresAt: Long? = null,
    val lastServiceAt: Long? = null,
    val nextServiceAt: Long? = null,
    val warrantyExpiresAt: Long? = null,
    val loanEmiPaise: Long? = null,
    val emiDayOfMonth: Int? = null,
    val profileId: Long? = null,
    val notes: String? = null,
)

data class InsurancePolicy(
    val id: Long = 0,
    val insurer: String,
    val policyNumber: String? = null,
    val kind: InsuranceKind = InsuranceKind.OTHER,
    val startAt: Long? = null,
    val expiresAt: Long? = null,
    val premiumPaise: Long? = null,
    val vehicleId: Long? = null,
    val profileId: Long? = null,
    val notes: String? = null,
)

data class Warranty(
    val id: Long = 0,
    val product: String,
    val purchasedAt: Long? = null,
    val expiresAt: Long,
    val documentId: Long? = null,
    val vehicleId: Long? = null,
    val profileId: Long? = null,
)

data class Reminder(
    val id: Long = 0,
    val title: String,
    val body: String? = null,
    val fireAt: Long,
    val target: ReminderTarget = ReminderTarget.NONE,
    val targetId: Long? = null,
    val leadDays: Int? = null,
    val state: ReminderState = ReminderState.SCHEDULED,
    val auto: Boolean = false,
)

data class AppSource(
    val packageName: String,
    val label: String,
    val kind: SourceKind,
    val enabled: Boolean,
    val suggested: Boolean,
    val notificationCount: Long,
    val lastSeenAt: Long?,
)

/** One item on a vehicle's timeline. */
data class TimelineEvent(
    val at: Long,
    val label: String,
    val kind: Kind,
) {
    enum class Kind { PURCHASE, INSURANCE_START, INSURANCE_EXPIRY, SERVICE_DONE, SERVICE_DUE, PUC_EXPIRY, WARRANTY_EXPIRY, DOCUMENT, EXPENSE }
}
