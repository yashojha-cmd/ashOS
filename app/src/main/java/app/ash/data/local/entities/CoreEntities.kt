package app.ash.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.ash.domain.model.Importance
import app.ash.domain.model.ProfileRelation
import app.ash.domain.model.RecordSource
import app.ash.domain.model.RecordStatus
import app.ash.domain.model.RecordType
import app.ash.domain.model.SourceKind
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "profiles", indices = [Index(value = ["name"], unique = true)])
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relation: ProfileRelation,
    val createdAt: Long,
)

/** Built-in categories are seeded on creation; the table lets future versions add more. */
@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val key: String,
    val builtIn: Boolean,
    val sortOrder: Int,
    val customLabel: String? = null,
)

@Serializable
@Entity(tableName = "app_sources")
data class AppSourceEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val kind: SourceKind,
    val enabled: Boolean,
    val firstSeenAt: Long?,
    val lastSeenAt: Long?,
    val notificationCount: Long = 0,
)

enum class NotificationOutcome { RECORD_CREATED, REVIEW, LOW_CONFIDENCE, DUPLICATE }

/** One processed notification. Text is kept only if the user allows it (Settings > Privacy). */
@Serializable
@Entity(
    tableName = "notification_records",
    foreignKeys = [
        ForeignKey(entity = AppSourceEntity::class, parentColumns = ["packageName"], childColumns = ["packageName"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("postedAt"),
        Index("packageName"),
        Index(value = ["packageName", "contentHash"]),
    ],
)
data class NotificationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val notificationKey: String?,
    val postedAt: Long,
    val title: String?,
    val text: String?,
    val contentHash: Int,
    val parserId: String?,
    val confidence: Float?,
    val outcome: NotificationOutcome,
    val recordId: Long?,
    val createdAt: Long,
)

@Serializable
@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["key"], childColumns = ["categoryKey"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["status", "dueAt"]),
        Index("categoryKey"),
        Index("profileId"),
        Index("createdAt"),
        Index("type"),
        Index("deletedAt"),
    ],
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RecordType,
    val categoryKey: String,
    val title: String,
    val summary: String,
    val merchantName: String?,
    val amountPaise: Long?,
    val dueAt: Long?,
    val eventAt: Long?,
    val expiresAt: Long?,
    val reference: String?,
    val importance: Importance,
    val status: RecordStatus,
    val confidence: Float,
    val source: RecordSource,
    val sourcePackage: String?,
    val sourceLabel: String?,
    val notificationId: Long?,
    val profileId: Long?,
    val extrasJson: String?,
    val materialized: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
