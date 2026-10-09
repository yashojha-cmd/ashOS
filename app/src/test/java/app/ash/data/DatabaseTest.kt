package app.ash.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ash.data.local.database.AshDatabase
import app.ash.data.local.entities.DocumentEntity
import app.ash.data.local.entities.RecordEntity
import app.ash.domain.model.Category
import app.ash.domain.model.DocType
import app.ash.domain.model.Importance
import app.ash.domain.model.RecordSource
import app.ash.domain.model.RecordStatus
import app.ash.domain.model.RecordType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Room schema, seeding, FTS and soft-delete behaviour. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AshDatabase::class.java)
        .allowMainThreadQueries()
        .addCallback(AshDatabase.SeedCallback)
        .build()

    @After fun close() = db.close()

    private fun record(title: String, summary: String, status: RecordStatus = RecordStatus.UNREAD, amount: Long? = null, created: Long = 1_000) = RecordEntity(
        type = RecordType.BILL, categoryKey = Category.BILLS.name, title = title, summary = summary, merchantName = null,
        amountPaise = amount, dueAt = null, eventAt = null, expiresAt = null, reference = null, importance = Importance.HIGH,
        status = status, confidence = 1f, source = RecordSource.MANUAL, sourcePackage = null, sourceLabel = null,
        notificationId = null, profileId = null, extrasJson = null, materialized = false, createdAt = created, updatedAt = created,
    )

    @Test fun seedsCategoriesAndDefaultProfile() = runBlocking<Unit> {
        assertThat(db.categoryDao().getAll().map { it.key }).containsExactlyElementsIn(Category.entries.map { it.name })
        val profiles = db.profileDao().getAll()
        assertThat(profiles).hasSize(1)
        assertThat(profiles[0].name).isEqualTo("Me")
    }

    @Test fun fullTextSearchFindsByPrefixAndFollowsUpdates() = runBlocking<Unit> {
        val id = db.recordDao().insert(record("Electricity bill", "BESCOM due on 15 Oct"))
        db.recordDao().insert(record("Netflix", "Membership renewal"))
        assertThat(db.recordDao().search("bescom*").map { it.id }).containsExactly(id)
        assertThat(db.recordDao().search("electric*").map { it.id }).containsExactly(id)
        db.recordDao().update(db.recordDao().getById(id)!!.copy(summary = "Tata Power due"))
        assertThat(db.recordDao().search("bescom*")).isEmpty()
        assertThat(db.recordDao().search("tata*").map { it.id }).containsExactly(id)
    }

    @Test fun softDeletedRecordsDisappearAndArePurged() = runBlocking<Unit> {
        val id = db.recordDao().insert(record("Old bill", "x"))
        db.recordDao().softDelete(id, now = 5_000)
        assertThat(db.recordDao().observeTotal().first()).isEqualTo(0)
        assertThat(db.recordDao().search("old*")).isEmpty()
        assertThat(db.recordDao().purgeDeleted(before = 10_000)).isEqualTo(1)
        assertThat(db.recordDao().getById(id)).isNull()
    }

    @Test fun amountAndStatusQueries() = runBlocking<Unit> {
        db.recordDao().insert(record("A", "a", amount = 500_000))
        db.recordDao().insert(record("B", "b", amount = 120_000, status = RecordStatus.REVIEW))
        assertThat(db.recordDao().byAmount(499_000, 501_000).map { it.title }).containsExactly("A")
        assertThat(db.recordDao().observeCount(RecordStatus.REVIEW).first()).isEqualTo(1)
    }

    @Test fun documentTagsAndSearch() = runBlocking<Unit> {
        val id = db.documentDao().insert(
            DocumentEntity(
                title = "PAN card", docType = DocType.PAN, fileName = "f.bin", mimeType = "image/jpeg", sizeBytes = 10, sha256 = "x",
                expiresAt = null, issuedAt = null, documentNumber = "ABCDE1234F", ocrText = "INCOME TAX DEPARTMENT", notes = null,
                sensitive = true, vehicleId = null, policyId = null, profileId = null, createdAt = 1, updatedAt = 1,
            ),
        )
        db.documentDao().replaceTags(id, listOf("ID", "tax", "id"))
        assertThat(db.documentDao().tagNames(id)).containsExactly("id", "tax")
        assertThat(db.documentDao().search("income*").map { it.id }).containsExactly(id)
        val withTags = db.documentDao().observeAll().first().single()
        assertThat(withTags.tags.map { it.name }).containsExactly("id", "tax")
    }
}
