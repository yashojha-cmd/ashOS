package app.ash.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.ash.security.DatabaseKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

/**
 * Defers the Keystore unwrap of the SQLCipher passphrase until the database is first opened,
 * which Room always does off the main thread.
 */
private class LazySqlCipherFactory(private val passphrase: () -> ByteArray) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        object : SupportSQLiteOpenHelper {
            private val delegate by lazy {
                System.loadLibrary("sqlcipher")
                SupportOpenHelperFactory(passphrase()).create(configuration)
            }
            private var walEnabled: Boolean? = null

            override val databaseName: String? get() = configuration.name
            override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
                walEnabled = enabled
                delegate.setWriteAheadLoggingEnabled(enabled)
            }
            override val writableDatabase: SupportSQLiteDatabase get() = delegate.writableDatabase
            override val readableDatabase: SupportSQLiteDatabase get() = delegate.readableDatabase
            override fun close() = delegate.close()
        }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, keys: DatabaseKeyProvider): AshDatabase =
        Room.databaseBuilder(context, AshDatabase::class.java, AshDatabase.NAME)
            .openHelperFactory(LazySqlCipherFactory { keys.passphrase() })
            .addCallback(AshDatabase.SeedCallback)
            .build()

    @Provides fun recordDao(db: AshDatabase) = db.recordDao()
    @Provides fun notificationDao(db: AshDatabase) = db.notificationDao()
    @Provides fun appSourceDao(db: AshDatabase) = db.appSourceDao()
    @Provides fun transactionDao(db: AshDatabase) = db.transactionDao()
    @Provides fun billDao(db: AshDatabase) = db.billDao()
    @Provides fun subscriptionDao(db: AshDatabase) = db.subscriptionDao()
    @Provides fun merchantDao(db: AshDatabase) = db.merchantDao()
    @Provides fun documentDao(db: AshDatabase) = db.documentDao()
    @Provides fun vehicleDao(db: AshDatabase) = db.vehicleDao()
    @Provides fun policyDao(db: AshDatabase) = db.policyDao()
    @Provides fun warrantyDao(db: AshDatabase) = db.warrantyDao()
    @Provides fun deliveryDao(db: AshDatabase) = db.deliveryDao()
    @Provides fun tripDao(db: AshDatabase) = db.tripDao()
    @Provides fun reminderDao(db: AshDatabase) = db.reminderDao()
    @Provides fun profileDao(db: AshDatabase) = db.profileDao()
    @Provides fun categoryDao(db: AshDatabase) = db.categoryDao()
    @Provides fun auditDao(db: AshDatabase) = db.auditDao()
    @Provides fun diagnosticsDao(db: AshDatabase) = db.diagnosticsDao()
    @Provides fun statsDao(db: AshDatabase) = db.statsDao()
    @Provides fun backupDao(db: AshDatabase) = db.backupDao()
}
