package app.ash.data.local.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import app.ash.domain.model.ReminderState
import app.ash.domain.model.ReminderTarget
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "reminders",
    indices = [Index("fireAt"), Index(value = ["target", "targetId"]), Index("state")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String?,
    val fireAt: Long,
    val target: ReminderTarget,
    val targetId: Long?,
    val leadDays: Int?,
    val state: ReminderState,
    val auto: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class AuditAction { CREATE, EDIT, CONFIRM, DISMISS, DELETE, EXPORT, IMPORT, UNLOCK_FAILED, DELETE_ALL }

/** What happened, never what it contained. */
@Serializable
@Entity(tableName = "audit_records", indices = [Index("at")])
data class AuditRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val action: AuditAction,
    val entity: String,
    val entityId: Long?,
)

/** Local-only diagnostics counters (§22). */
@Entity(tableName = "diagnostic_counters")
data class DiagnosticCounterEntity(
    @PrimaryKey val key: String,
    val value: Long,
)

@Fts4(contentEntity = RecordEntity::class)
@Entity(tableName = "records_fts")
data class RecordFtsEntity(
    val title: String,
    val summary: String,
    val merchantName: String?,
    val reference: String?,
)

@Fts4(contentEntity = DocumentEntity::class)
@Entity(tableName = "documents_fts")
data class DocumentFtsEntity(
    val title: String,
    val ocrText: String?,
    val notes: String?,
    val documentNumber: String?,
)
