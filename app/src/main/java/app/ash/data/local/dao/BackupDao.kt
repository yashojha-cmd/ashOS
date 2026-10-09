package app.ash.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.ash.data.local.entities.AppSourceEntity
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

/** Bulk read/write used only by encrypted export and import. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM profiles") suspend fun profiles(): List<ProfileEntity>
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM app_sources") suspend fun sources(): List<AppSourceEntity>
    @Query("SELECT * FROM notification_records") suspend fun notifications(): List<NotificationRecordEntity>
    @Query("SELECT * FROM records") suspend fun records(): List<RecordEntity>
    @Query("SELECT * FROM merchants") suspend fun merchants(): List<MerchantEntity>
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM bills") suspend fun bills(): List<BillEntity>
    @Query("SELECT * FROM subscriptions") suspend fun subscriptions(): List<SubscriptionEntity>
    @Query("SELECT * FROM vehicles") suspend fun vehicles(): List<VehicleEntity>
    @Query("SELECT * FROM insurance_policies") suspend fun policies(): List<InsurancePolicyEntity>
    @Query("SELECT * FROM documents") suspend fun documents(): List<DocumentEntity>
    @Query("SELECT * FROM tags") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM document_tags") suspend fun documentTags(): List<DocumentTagEntity>
    @Query("SELECT * FROM warranties") suspend fun warranties(): List<WarrantyEntity>
    @Query("SELECT * FROM deliveries") suspend fun deliveries(): List<DeliveryEntity>
    @Query("SELECT * FROM trips") suspend fun trips(): List<TripEntity>
    @Query("SELECT * FROM reminders") suspend fun reminders(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProfiles(items: List<ProfileEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(items: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSources(items: List<AppSourceEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNotifications(items: List<NotificationRecordEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRecords(items: List<RecordEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMerchants(items: List<MerchantEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactions(items: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBills(items: List<BillEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSubscriptions(items: List<SubscriptionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVehicles(items: List<VehicleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPolicies(items: List<InsurancePolicyEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDocuments(items: List<DocumentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTags(items: List<TagEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDocumentTags(items: List<DocumentTagEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWarranties(items: List<WarrantyEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDeliveries(items: List<DeliveryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTrips(items: List<TripEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReminders(items: List<ReminderEntity>)
}
