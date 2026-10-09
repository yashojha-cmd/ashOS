package app.ash.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.ash.domain.model.RecurrencePeriod
import app.ash.domain.model.TxnDirection
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "merchants", indices = [Index(value = ["canonicalName"], unique = true)])
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val canonicalName: String,
    val aliases: String = "",
    val defaultCategoryKey: String? = null,
)

@Serializable
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MerchantEntity::class, parentColumns = ["id"], childColumns = ["merchantId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("occurredAt"), Index("merchantId"), Index("vehicleId")],
)
data class TransactionEntity(
    @PrimaryKey val recordId: Long,
    val amountPaise: Long,
    val direction: TxnDirection,
    val merchantId: Long?,
    val accountRef: String?,
    val occurredAt: Long,
    val vehicleId: Long? = null,
    val userCorrected: Boolean = false,
    /** Starts a new monthly statement (salary credit). Added in schema v2. */
    @androidx.room.ColumnInfo(defaultValue = "0") val isSalary: Boolean = false,
    /** Account balance the bank reported in the same SMS ("Avl Bal"), if any. Added in schema v2. */
    val balanceAfterPaise: Long? = null,
)

@Serializable
@Entity(
    tableName = "bills",
    foreignKeys = [ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dueAt")],
)
data class BillEntity(
    @PrimaryKey val recordId: Long,
    val biller: String,
    val amountPaise: Long?,
    val dueAt: Long,
    val paidAt: Long? = null,
    val recurrence: RecurrencePeriod? = null,
)

@Serializable
@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(entity = MerchantEntity::class, parentColumns = ["id"], childColumns = ["merchantId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("nextRenewalAt"), Index("merchantId"), Index("profileId"), Index(value = ["name"])],
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val merchantId: Long?,
    val amountPaise: Long,
    val period: RecurrencePeriod,
    val nextRenewalAt: Long?,
    val autoDetected: Boolean,
    val active: Boolean,
    val profileId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
