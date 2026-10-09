package app.ash.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import app.ash.R
import app.ash.common.AppError
import app.ash.common.AppResult
import app.ash.common.Dates
import app.ash.common.IoDispatcher
import app.ash.common.TimeProvider
import app.ash.data.local.dao.DocumentDao
import app.ash.data.local.entities.AuditAction
import app.ash.data.local.entities.DocumentEntity
import app.ash.data.parser.ParseContext
import app.ash.document.ImageTools
import app.ash.document.OcrEngine
import app.ash.document.VaultStorage
import app.ash.domain.model.DocType
import app.ash.domain.model.Document
import app.ash.domain.model.ReminderTarget
import app.ash.domain.repository.DocumentRepository
import app.ash.intelligence.IntelligenceEngine
import app.ash.logging.AshLogger
import app.ash.reminder.ReminderPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DocumentDao,
    private val vault: VaultStorage,
    private val ocr: OcrEngine,
    private val engine: IntelligenceEngine,
    private val reminders: ReminderRepositoryImpl,
    private val reminderText: ReminderText,
    private val diagnostics: DiagnosticsRepository,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeDocument(id: Long): Flow<Document?> = dao.observeById(id).map { it?.toDomain() }

    override fun observeForVehicle(vehicleId: Long): Flow<List<Document>> = dao.observeForVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    override suspend fun importFromUri(uri: Uri): AppResult<Long> = withContext(io) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        if (!mime.startsWith("image/") && mime != "application/pdf") return@withContext AppResult.Failure(AppError.UNSUPPORTED_FILE)
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: context.getString(R.string.vault_untitled)
        val stored = try {
            resolver.openInputStream(uri)?.use { vault.store(it) } ?: return@withContext AppResult.Failure(AppError.FILE_READ_FAILED)
        } catch (e: VaultStorage.TooLarge) {
            return@withContext AppResult.Failure(AppError.FILE_TOO_LARGE, e)
        } catch (e: Exception) {
            AshLogger.e(TAG, "Import failed", e)
            diagnostics.increment(Counters.ERRORS)
            return@withContext AppResult.Failure(AppError.FILE_READ_FAILED, e)
        }
        AppResult.Success(finishImport(stored, mime, name.substringBeforeLast('.')))
    }

    override suspend fun importImageBytes(bytes: ByteArray, title: String): AppResult<Long> = withContext(io) {
        try {
            val stored = vault.storeBytes(bytes, vault.newFileName())
            AppResult.Success(finishImport(stored, "image/jpeg", title))
        } catch (e: Exception) {
            AshLogger.e(TAG, "Camera import failed", e)
            AppResult.Failure(AppError.ENCRYPTION_FAILED, e)
        }
    }

    private suspend fun finishImport(stored: VaultStorage.Stored, mime: String, fallbackTitle: String): Long {
        val now = time.nowMillis()
        val page = renderFirstPage(stored.fileName, mime, 1800)
        page?.let { bitmap ->
            runCatching { vault.storeBytes(ImageTools.toJpeg(ImageTools.scaleToMax(bitmap, 360), 75), VaultStorage.thumbName(stored.fileName)) }
                .onFailure { AshLogger.w(TAG, "Thumbnail failed", it) }
        }
        val text = page?.let { recognise(it) }.orEmpty()
        val fields = engine.extractDocumentFields(text, ParseContext(time.today(), time.zone(), now))
        val entity = DocumentEntity(
            title = fields.title ?: fallbackTitle,
            docType = fields.docType ?: DocType.OTHER,
            fileName = stored.fileName,
            mimeType = mime,
            sizeBytes = stored.sizeBytes,
            sha256 = stored.sha256,
            expiresAt = fields.expiresOn?.let { Dates.morningOf(it, time.zone()) },
            issuedAt = fields.issuedOn?.let { Dates.startOfDay(it, time.zone()) },
            documentNumber = fields.documentNumber,
            ocrText = text.ifBlank { null },
            notes = null,
            sensitive = true,
            needsReview = text.isNotBlank(),
            vehicleId = null,
            policyId = null,
            profileId = null,
            createdAt = now,
            updatedAt = now,
        )
        val id = dao.insert(entity)
        if (text.isNotBlank()) diagnostics.increment(Counters.OCR_DOCUMENTS)
        diagnostics.audit(AuditAction.CREATE, "document", id)
        planReminder(entity.copy(id = id))
        AshLogger.i(TAG, "Imported document $id type=${entity.docType} ocrChars=${text.length} confidence=${fields.confidence}")
        return id
    }

    private suspend fun renderFirstPage(fileName: String, mime: String, maxSide: Int): Bitmap? = runCatching {
        if (mime == "application/pdf") {
            val temp = vault.decryptToTemp(fileName, "pdf")
            try {
                ImageTools.renderPdfPage(temp, 0, maxSide)
            } finally {
                temp.delete()
            }
        } else {
            ImageTools.decodeSampled(vault.readBytes(fileName), maxSide)
        }
    }.onFailure { AshLogger.w(TAG, "Could not render document for OCR", it) }.getOrNull()

    private suspend fun recognise(bitmap: Bitmap): String = try {
        ocr.recognize(bitmap)
    } catch (e: Exception) {
        AshLogger.w(TAG, "OCR failed", e)
        diagnostics.increment(Counters.ERRORS)
        ""
    }

    override suspend fun runOcr(id: Long): AppResult<String> = withContext(io) {
        val entity = dao.getById(id) ?: return@withContext AppResult.Failure(AppError.FILE_READ_FAILED)
        val page = renderFirstPage(entity.fileName, entity.mimeType, 2200) ?: return@withContext AppResult.Failure(AppError.OCR_FAILED)
        val text = recognise(page)
        if (text.isBlank()) return@withContext AppResult.Failure(AppError.OCR_LOW_CONFIDENCE)
        dao.update(entity.copy(ocrText = text, needsReview = true, updatedAt = time.nowMillis()))
        diagnostics.increment(Counters.OCR_DOCUMENTS)
        AppResult.Success(text)
    }

    override suspend fun update(document: Document) {
        val existing = dao.getById(document.id) ?: return
        val updated = existing.copy(
            title = document.title.trim().ifBlank { existing.title },
            docType = document.docType,
            expiresAt = document.expiresAt,
            issuedAt = document.issuedAt,
            documentNumber = document.documentNumber?.trim()?.ifBlank { null },
            notes = document.notes?.trim()?.ifBlank { null },
            sensitive = document.sensitive,
            needsReview = false,
            vehicleId = document.vehicleId,
            policyId = document.policyId,
            profileId = document.profileId,
            updatedAt = time.nowMillis(),
        )
        dao.update(updated)
        dao.replaceTags(document.id, document.tags)
        planReminder(updated)
        diagnostics.audit(AuditAction.EDIT, "document", document.id)
    }

    override suspend fun delete(id: Long) {
        val entity = dao.getById(id) ?: return
        vault.delete(entity.fileName)
        vault.delete(VaultStorage.thumbName(entity.fileName))
        dao.hardDelete(id)
        reminders.cancelForTarget(ReminderTarget.DOCUMENT, id)
        diagnostics.audit(AuditAction.DELETE, "document", id)
    }

    override suspend fun readDecrypted(id: Long): AppResult<ByteArray> = withContext(io) {
        val entity = dao.getById(id) ?: return@withContext AppResult.Failure(AppError.FILE_READ_FAILED)
        try {
            AppResult.Success(vault.readBytes(entity.fileName))
        } catch (e: Exception) {
            AshLogger.e(TAG, "Decrypt failed for document $id", e)
            AppResult.Failure(AppError.ENCRYPTION_FAILED, e)
        }
    }

    override suspend fun readThumbnail(id: Long): ByteArray? = withContext(io) {
        val entity = dao.getById(id) ?: return@withContext null
        val thumb = VaultStorage.thumbName(entity.fileName)
        if (!vault.exists(thumb)) null else runCatching { vault.readBytes(thumb) }.getOrNull()
    }

    override suspend fun decryptToTempFile(id: Long): AppResult<File> = withContext(io) {
        val entity = dao.getById(id) ?: return@withContext AppResult.Failure(AppError.FILE_READ_FAILED)
        try {
            AppResult.Success(vault.decryptToTemp(entity.fileName, if (entity.mimeType == "application/pdf") "pdf" else "img"))
        } catch (e: Exception) {
            AppResult.Failure(AppError.ENCRYPTION_FAILED, e)
        }
    }

    private suspend fun planReminder(doc: DocumentEntity) {
        val kind = if (doc.docType == DocType.PASSPORT) ReminderPlanner.Kind.LONG_EXPIRY else ReminderPlanner.Kind.EXPIRY
        reminders.planAuto(
            ReminderTarget.DOCUMENT, doc.id, kind, doc.expiresAt,
            reminderText.string(R.string.reminder_document_title, doc.title),
        ) { reminderText.expires(it) }
    }

    private companion object {
        const val TAG = "Vault"
    }
}
