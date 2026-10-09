package app.ash.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import app.ash.data.local.entities.DocumentEntity
import app.ash.data.local.entities.DocumentTagEntity
import app.ash.data.local.entities.TagEntity
import kotlinx.coroutines.flow.Flow

data class DocumentWithTags(
    @Embedded val document: DocumentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(DocumentTagEntity::class, parentColumn = "documentId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
)

@Dao
interface DocumentDao {
    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity)

    @Transaction
    @Query("SELECT * FROM documents WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DocumentWithTags>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: Long): Flow<DocumentWithTags?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE deletedAt IS NULL")
    suspend fun getAllActive(): List<DocumentEntity>

    @Transaction
    @Query("SELECT * FROM documents WHERE deletedAt IS NULL AND vehicleId = :vehicleId ORDER BY createdAt DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<DocumentWithTags>>

    @Query("SELECT * FROM documents WHERE deletedAt IS NULL AND expiresAt IS NOT NULL AND expiresAt < :before ORDER BY expiresAt")
    fun observeExpiringBefore(before: Long): Flow<List<DocumentEntity>>

    @Query(
        """SELECT documents.* FROM documents JOIN documents_fts ON documents.id = documents_fts.rowid
           WHERE documents_fts MATCH :match AND documents.deletedAt IS NULL LIMIT 100""",
    )
    suspend fun search(match: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE deletedAt IS NULL AND expiresAt IS NOT NULL AND expiresAt < :before ORDER BY expiresAt")
    suspend fun expiringBefore(before: Long): List<DocumentEntity>

    @Query("UPDATE documents SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun deletedBefore(before: Long): List<DocumentEntity>

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findTag(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkTag(link: DocumentTagEntity)

    @Query("DELETE FROM document_tags WHERE documentId = :documentId")
    suspend fun clearTags(documentId: Long)

    @Query("SELECT t.name FROM tags t JOIN document_tags dt ON dt.tagId = t.id WHERE dt.documentId = :documentId")
    suspend fun tagNames(documentId: Long): List<String>

    @Transaction
    suspend fun replaceTags(documentId: Long, names: List<String>) {
        clearTags(documentId)
        for (name in names.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()) {
            val id = insertTag(TagEntity(name = name)).takeIf { it > 0 } ?: findTag(name)?.id ?: continue
            linkTag(DocumentTagEntity(documentId, id))
        }
    }
}
