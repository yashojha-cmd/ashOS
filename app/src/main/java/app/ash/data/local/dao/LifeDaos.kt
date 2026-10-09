package app.ash.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import app.ash.data.local.entities.DeliveryEntity
import app.ash.data.local.entities.InsurancePolicyEntity
import app.ash.data.local.entities.TripEntity
import app.ash.data.local.entities.VehicleEntity
import app.ash.data.local.entities.WarrantyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Insert
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Query("SELECT * FROM vehicles WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE deletedAt IS NULL ORDER BY name")
    suspend fun getAll(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: Long): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getById(id: Long): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE deletedAt IS NULL AND REPLACE(REPLACE(UPPER(regNumber),' ',''),'-','') = :normalisedReg LIMIT 1")
    suspend fun findByReg(normalisedReg: String): VehicleEntity?

    @Query("UPDATE vehicles SET deletedAt = :now, updatedAt = :now, regNumber = NULL WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("DELETE FROM vehicles WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long): Int
}

@Dao
interface PolicyDao {
    @Insert
    suspend fun insert(policy: InsurancePolicyEntity): Long

    @Update
    suspend fun update(policy: InsurancePolicyEntity)

    @Query("SELECT * FROM insurance_policies WHERE deletedAt IS NULL ORDER BY expiresAt IS NULL, expiresAt")
    fun observeAll(): Flow<List<InsurancePolicyEntity>>

    @Query("SELECT * FROM insurance_policies WHERE deletedAt IS NULL ORDER BY expiresAt IS NULL, expiresAt")
    suspend fun getAll(): List<InsurancePolicyEntity>

    @Query("SELECT * FROM insurance_policies WHERE deletedAt IS NULL AND vehicleId = :vehicleId ORDER BY expiresAt DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<InsurancePolicyEntity>>

    @Query("SELECT * FROM insurance_policies WHERE id = :id")
    suspend fun getById(id: Long): InsurancePolicyEntity?

    @Query("SELECT * FROM insurance_policies WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: Long): Flow<InsurancePolicyEntity?>

    @Query("SELECT * FROM insurance_policies WHERE deletedAt IS NULL AND policyNumber = :number LIMIT 1")
    suspend fun findByNumber(number: String): InsurancePolicyEntity?

    @Query("UPDATE insurance_policies SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("DELETE FROM insurance_policies WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long): Int
}

@Dao
interface WarrantyDao {
    @Insert
    suspend fun insert(warranty: WarrantyEntity): Long

    @Update
    suspend fun update(warranty: WarrantyEntity)

    @Query("SELECT * FROM warranties WHERE deletedAt IS NULL ORDER BY expiresAt")
    fun observeAll(): Flow<List<WarrantyEntity>>

    @Query("SELECT * FROM warranties WHERE deletedAt IS NULL ORDER BY expiresAt")
    suspend fun getAll(): List<WarrantyEntity>

    @Query("SELECT * FROM warranties WHERE id = :id")
    suspend fun getById(id: Long): WarrantyEntity?

    @Query("UPDATE warranties SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("DELETE FROM warranties WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long): Int
}

@Dao
interface DeliveryDao {
    @Upsert
    suspend fun upsert(delivery: DeliveryEntity)

    @Query("SELECT * FROM deliveries WHERE recordId = :recordId")
    suspend fun get(recordId: Long): DeliveryEntity?

    @Query("SELECT d.* FROM deliveries d JOIN records r ON r.id = d.recordId WHERE r.deletedAt IS NULL AND d.orderRef = :orderRef LIMIT 1")
    suspend fun findByOrderRef(orderRef: String): DeliveryEntity?
}

@Dao
interface TripDao {
    @Upsert
    suspend fun upsert(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE recordId = :recordId")
    suspend fun get(recordId: Long): TripEntity?

    @Query("SELECT t.* FROM trips t JOIN records r ON r.id = t.recordId WHERE r.deletedAt IS NULL AND t.pnr = :pnr LIMIT 1")
    suspend fun findByPnr(pnr: String): TripEntity?
}
