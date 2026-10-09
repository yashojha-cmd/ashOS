package app.ash.backup

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import app.ash.BuildConfig
import app.ash.common.AppError
import app.ash.common.AppResult
import app.ash.common.IoDispatcher
import app.ash.common.TimeProvider
import app.ash.data.local.dao.BackupDao
import app.ash.data.local.database.AshDatabase
import app.ash.data.local.entities.AppSourceEntity
import app.ash.data.local.entities.AuditAction
import app.ash.data.local.entities.BillEntity
import app.ash.data.local.entities.CategoryEntity
import app.ash.data.local.entities.DeliveryEntity
import app.ash.data.local.entities.DocumentEntity
import app.ash.data.local.entities.DocumentTagEntity
import app.ash.data.local.entities.InsurancePolicyEntity
import app.ash.data.local.entities.MerchantEntity
import app.ash.data.local.entities.NotificationRecordEntity
import app.ash.data.local.entities.ProfileEntity
import app.ash.data.local.entities.RecordEntity
import app.ash.data.local.entities.ReminderEntity
import app.ash.data.local.entities.SubscriptionEntity
import app.ash.data.local.entities.TagEntity
import app.ash.data.local.entities.TransactionEntity
import app.ash.data.local.entities.TripEntity
import app.ash.data.local.entities.VehicleEntity
import app.ash.data.local.entities.WarrantyEntity
import app.ash.data.repository.DiagnosticsRepository
import app.ash.data.repository.SettingsRepository
import app.ash.document.VaultStorage
import app.ash.domain.repository.ReminderRepository
import app.ash.logging.AshLogger
import app.ash.logging.LogManager
import app.ash.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupPayload(
    val format: Int = 1,
    val createdAt: Long,
    val appVersion: String,
    val profiles: List<ProfileEntity>,
    val categories: List<CategoryEntity>,
    val sources: List<AppSourceEntity>,
    val notifications: List<NotificationRecordEntity>,
    val records: List<RecordEntity>,
    val merchants: List<MerchantEntity>,
    val transactions: List<TransactionEntity>,
    val bills: List<BillEntity>,
    val subscriptions: List<SubscriptionEntity>,
    val vehicles: List<VehicleEntity>,
    val policies: List<InsurancePolicyEntity>,
    val documents: List<DocumentEntity>,
    val tags: List<TagEntity>,
    val documentTags: List<DocumentTagEntity>,
    val warranties: List<WarrantyEntity>,
    val deliveries: List<DeliveryEntity>,
    val trips: List<TripEntity>,
    val reminders: List<ReminderEntity>,
)

/**
 * Export, import and delete-all (§32). A backup is a ZIP (data.json + documents) wrapped in
 * password-based streaming encryption. Nothing ever goes to a server; the user picks where the
 * file is saved through the system file picker.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AshDatabase,
    private val dao: BackupDao,
    private val vault: VaultStorage,
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val settings: SettingsRepository,
    private val logs: LogManager,
    private val diagnostics: DiagnosticsRepository,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(target: Uri, password: CharArray): AppResult<Int> = withContext(io) {
        try {
            val payload = snapshot()
            context.contentResolver.openOutputStream(target, "w")?.use { raw ->
                app.ash.security.BackupCrypto.encryptingStream(raw.buffered(), password).use { encrypted ->
                    ZipOutputStream(encrypted).use { zip ->
                        zip.putNextEntry(ZipEntry(DATA_ENTRY))
                        zip.write(json.encodeToString(BackupPayload.serializer(), payload).toByteArray())
                        zip.closeEntry()
                        for (doc in payload.documents) {
                            if (!vault.exists(doc.fileName)) continue
                            zip.putNextEntry(ZipEntry(DOCS_PREFIX + doc.fileName))
                            vault.openDecrypted(doc.fileName).use { it.copyTo(zip) }
                            zip.closeEntry()
                            val thumb = VaultStorage.thumbName(doc.fileName)
                            if (vault.exists(thumb)) {
                                zip.putNextEntry(ZipEntry(DOCS_PREFIX + thumb))
                                vault.openDecrypted(thumb).use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                }
            } ?: return@withContext AppResult.Failure(AppError.FILE_READ_FAILED)
            diagnostics.audit(AuditAction.EXPORT, "backup")
            AshLogger.i(TAG, "Exported ${payload.records.size} records, ${payload.documents.size} documents")
            AppResult.Success(payload.records.size + payload.documents.size)
        } catch (e: Exception) {
            AshLogger.e(TAG, "Export failed", e)
            AppResult.Failure(AppError.UNKNOWN, e)
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun import(source: Uri, password: CharArray): AppResult<Int> = withContext(io) {
        val importedFiles = mutableListOf<String>()
        try {
            var payload: BackupPayload? = null
            context.contentResolver.openInputStream(source)?.use { raw ->
                val decrypted = app.ash.security.BackupCrypto.decryptingStream(raw.buffered(), password)
                ZipInputStream(decrypted).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        when {
                            entry.name == DATA_ENTRY -> payload = json.decodeFromString(BackupPayload.serializer(), zip.readBytes().decodeToString())
                            entry.name.startsWith(DOCS_PREFIX) -> {
                                val name = entry.name.removePrefix(DOCS_PREFIX)
                                if (name.contains('/') || name.contains("..")) continue
                                vault.store(NonClosing(zip), name, Long.MAX_VALUE)
                                importedFiles += name
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: return@withContext AppResult.Failure(AppError.FILE_READ_FAILED)
            val data = payload ?: return@withContext AppResult.Failure(AppError.CORRUPT_BACKUP)

            val previousFiles = dao.documents().map { it.fileName }.toSet()
            db.withTransaction {
                wipeTables()
                dao.insertProfiles(data.profiles)
                dao.insertCategories(data.categories)
                dao.insertSources(data.sources)
                dao.insertMerchants(data.merchants)
                dao.insertVehicles(data.vehicles)
                dao.insertPolicies(data.policies)
                dao.insertDocuments(data.documents)
                dao.insertTags(data.tags)
                dao.insertDocumentTags(data.documentTags)
                dao.insertNotifications(data.notifications)
                dao.insertRecords(data.records)
                dao.insertTransactions(data.transactions)
                dao.insertBills(data.bills)
                dao.insertSubscriptions(data.subscriptions)
                dao.insertWarranties(data.warranties)
                dao.insertDeliveries(data.deliveries)
                dao.insertTrips(data.trips)
                dao.insertReminders(data.reminders)
            }
            val keep = data.documents.map { it.fileName }.toSet()
            (previousFiles - keep).forEach {
                vault.delete(it)
                vault.delete(VaultStorage.thumbName(it))
            }
            scheduler.cancelAll()
            reminders.rescheduleAll()
            diagnostics.audit(AuditAction.IMPORT, "backup")
            AshLogger.i(TAG, "Imported ${data.records.size} records, ${data.documents.size} documents")
            AppResult.Success(data.records.size + data.documents.size)
        } catch (e: app.ash.security.BackupCrypto.NotABackup) {
            importedFiles.forEach { vault.delete(it) }
            AppResult.Failure(AppError.CORRUPT_BACKUP, e)
        } catch (e: Exception) {
            importedFiles.forEach { vault.delete(it) }
            AshLogger.w(TAG, "Import failed (wrong password or damaged file)", e)
            AppResult.Failure(AppError.WRONG_PASSWORD, e)
        } finally {
            password.fill('\u0000')
        }
    }

    /** Permanently removes everything ASH stored on this device. */
    suspend fun deleteAll() = withContext(io) {
        scheduler.cancelAll()
        androidx.work.WorkManager.getInstance(context).cancelAllWork()
        NotificationManagerCompat.from(context).cancelAll()
        db.withTransaction { wipeTables() }
        AshDatabase.seed(db.openHelper.writableDatabase)
        vault.deleteAll()
        logs.clear()
        settings.clear()
        AshLogger.i(TAG, "All local data deleted by the user")
    }

    private suspend fun snapshot() = BackupPayload(
        createdAt = time.nowMillis(),
        appVersion = BuildConfig.VERSION_NAME,
        profiles = dao.profiles(), categories = dao.categories(), sources = dao.sources(),
        notifications = dao.notifications(), records = dao.records(), merchants = dao.merchants(),
        transactions = dao.transactions(), bills = dao.bills(), subscriptions = dao.subscriptions(),
        vehicles = dao.vehicles(), policies = dao.policies(), documents = dao.documents().filter { it.deletedAt == null },
        tags = dao.tags(), documentTags = dao.documentTags(), warranties = dao.warranties(),
        deliveries = dao.deliveries(), trips = dao.trips(), reminders = dao.reminders(),
    )

    private fun wipeTables() {
        val sql = db.openHelper.writableDatabase
        // Children first so foreign keys never block the wipe.
        listOf(
            "document_tags", "tags", "transactions", "bills", "deliveries", "trips", "warranties", "documents",
            "insurance_policies", "subscriptions", "merchants", "records", "notification_records", "vehicles",
            "app_sources", "reminders", "audit_records", "diagnostic_counters", "profiles", "categories",
        ).forEach { sql.execSQL("DELETE FROM $it") }
        sql.execSQL("INSERT INTO records_fts(records_fts) VALUES('rebuild')")
        sql.execSQL("INSERT INTO documents_fts(documents_fts) VALUES('rebuild')")
    }

    /** Lets VaultStorage close its stream without closing the surrounding ZIP. */
    private class NonClosing(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    private companion object {
        const val TAG = "Backup"
        const val DATA_ENTRY = "data.json"
        const val DOCS_PREFIX = "docs/"
    }
}
