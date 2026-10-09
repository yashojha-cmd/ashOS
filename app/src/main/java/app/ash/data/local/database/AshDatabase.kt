package app.ash.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.ash.data.local.dao.AppSourceDao
import app.ash.data.local.dao.AuditDao
import app.ash.data.local.dao.BackupDao
import app.ash.data.local.dao.BillDao
import app.ash.data.local.dao.CategoryDao
import app.ash.data.local.dao.DeliveryDao
import app.ash.data.local.dao.DiagnosticsDao
import app.ash.data.local.dao.DocumentDao
import app.ash.data.local.dao.MerchantDao
import app.ash.data.local.dao.NotificationDao
import app.ash.data.local.dao.PolicyDao
import app.ash.data.local.dao.ProfileDao
import app.ash.data.local.dao.RecordDao
import app.ash.data.local.dao.ReminderDao
import app.ash.data.local.dao.StatsDao
import app.ash.data.local.dao.SubscriptionDao
import app.ash.data.local.dao.TransactionDao
import app.ash.data.local.dao.TripDao
import app.ash.data.local.dao.VehicleDao
import app.ash.data.local.dao.WarrantyDao
import app.ash.data.local.entities.AppSourceEntity
import app.ash.data.local.entities.AuditRecordEntity
import app.ash.data.local.entities.BillEntity
import app.ash.data.local.entities.CategoryEntity
import app.ash.data.local.entities.DeliveryEntity
import app.ash.data.local.entities.DiagnosticCounterEntity
import app.ash.data.local.entities.DocumentEntity
import app.ash.data.local.entities.DocumentFtsEntity
import app.ash.data.local.entities.DocumentTagEntity
import app.ash.data.local.entities.InsurancePolicyEntity
import app.ash.data.local.entities.MerchantEntity
import app.ash.data.local.entities.NotificationRecordEntity
import app.ash.data.local.entities.ProfileEntity
import app.ash.data.local.entities.RecordEntity
import app.ash.data.local.entities.RecordFtsEntity
import app.ash.data.local.entities.ReminderEntity
import app.ash.data.local.entities.SubscriptionEntity
import app.ash.data.local.entities.TagEntity
import app.ash.data.local.entities.TransactionEntity
import app.ash.data.local.entities.TripEntity
import app.ash.data.local.entities.VehicleEntity
import app.ash.data.local.entities.WarrantyEntity
import app.ash.domain.model.Category

@Database(
    entities = [
        ProfileEntity::class, CategoryEntity::class, AppSourceEntity::class, NotificationRecordEntity::class,
        RecordEntity::class, MerchantEntity::class, TransactionEntity::class, BillEntity::class,
        SubscriptionEntity::class, VehicleEntity::class, InsurancePolicyEntity::class, WarrantyEntity::class,
        DeliveryEntity::class, TripEntity::class, DocumentEntity::class, TagEntity::class, DocumentTagEntity::class,
        ReminderEntity::class, AuditRecordEntity::class, DiagnosticCounterEntity::class,
        RecordFtsEntity::class, DocumentFtsEntity::class,
    ],
    version = 2,
    autoMigrations = [androidx.room.AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
abstract class AshDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun notificationDao(): NotificationDao
    abstract fun appSourceDao(): AppSourceDao
    abstract fun transactionDao(): TransactionDao
    abstract fun billDao(): BillDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun merchantDao(): MerchantDao
    abstract fun documentDao(): DocumentDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun policyDao(): PolicyDao
    abstract fun warrantyDao(): WarrantyDao
    abstract fun deliveryDao(): DeliveryDao
    abstract fun tripDao(): TripDao
    abstract fun reminderDao(): ReminderDao
    abstract fun profileDao(): ProfileDao
    abstract fun categoryDao(): CategoryDao
    abstract fun auditDao(): AuditDao
    abstract fun diagnosticsDao(): DiagnosticsDao
    abstract fun statsDao(): StatsDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "ash.db"

        /** Seeds built-in categories and the default "Me" profile on first creation. */
        val SeedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                seed(db)
            }
        }

        fun seed(db: SupportSQLiteDatabase, selfName: String = "Me") {
            Category.entries.forEachIndexed { index, category ->
                db.execSQL(
                    "INSERT OR IGNORE INTO categories(`key`, builtIn, sortOrder, customLabel) VALUES (?, 1, ?, NULL)",
                    arrayOf<Any>(category.name, index),
                )
            }
            db.execSQL(
                "INSERT OR IGNORE INTO profiles(name, relation, createdAt) VALUES (?, 'SELF', ?)",
                arrayOf<Any>(selfName, System.currentTimeMillis()),
            )
        }
    }
}
