package app.ash.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.ash.domain.model.DeliveryState
import app.ash.domain.model.InsuranceKind
import app.ash.domain.model.TripMode
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "vehicles",
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["regNumber"], unique = true), Index("profileId")],
)
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String?,
    val model: String?,
    val regNumber: String?,
    val fuelType: String?,
    val purchasedAt: Long?,
    val pucExpiresAt: Long?,
    val lastServiceAt: Long?,
    val nextServiceAt: Long?,
    val warrantyExpiresAt: Long?,
    val loanEmiPaise: Long?,
    val emiDayOfMonth: Int?,
    val profileId: Long?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "insurance_policies",
    foreignKeys = [
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("expiresAt"), Index("vehicleId"), Index("profileId"), Index("policyNumber")],
)
data class InsurancePolicyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val insurer: String,
    val policyNumber: String?,
    val kind: InsuranceKind,
    val startAt: Long?,
    val expiresAt: Long?,
    val premiumPaise: Long?,
    val vehicleId: Long?,
    val profileId: Long?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "warranties",
    foreignKeys = [
        ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("expiresAt"), Index("documentId"), Index("vehicleId"), Index("profileId")],
)
data class WarrantyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val product: String,
    val purchasedAt: Long?,
    val expiresAt: Long,
    val documentId: Long?,
    val vehicleId: Long?,
    val profileId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "deliveries",
    foreignKeys = [ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("expectedAt"), Index("orderRef")],
)
data class DeliveryEntity(
    @PrimaryKey val recordId: Long,
    val merchantName: String?,
    val orderRef: String?,
    val state: DeliveryState,
    val expectedAt: Long?,
    val deliveredAt: Long?,
)

@Serializable
@Entity(
    tableName = "trips",
    foreignKeys = [ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("departAt"), Index("pnr")],
)
data class TripEntity(
    @PrimaryKey val recordId: Long,
    val mode: TripMode,
    val origin: String?,
    val destination: String?,
    val departAt: Long?,
    val pnr: String?,
)
