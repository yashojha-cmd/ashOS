package app.ash.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.ash.domain.model.DocType
import kotlinx.serialization.Serializable

/** Metadata only. The file itself is encrypted in filesDir/vault/<fileName>. */
@Serializable
@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = InsurancePolicyEntity::class, parentColumns = ["id"], childColumns = ["policyId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("expiresAt"), Index("docType"), Index("vehicleId"), Index("policyId"), Index("profileId"), Index(value = ["fileName"], unique = true)],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val docType: DocType,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val expiresAt: Long?,
    val issuedAt: Long?,
    val documentNumber: String?,
    val ocrText: String?,
    val notes: String?,
    val sensitive: Boolean,
    /** True until the user has checked the fields ASH read with OCR. */
    val needsReview: Boolean = false,
    val vehicleId: Long?,
    val policyId: Long?,
    val profileId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Serializable
@Entity(
    tableName = "document_tags",
    primaryKeys = ["documentId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class DocumentTagEntity(
    val documentId: Long,
    val tagId: Long,
)
