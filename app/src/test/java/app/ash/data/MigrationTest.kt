package app.ash.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ash.data.local.database.AshDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Schema v1 → v2 (salary and bank-balance columns on transactions). Builds a real v1 database
 * from the exported v1 schema (exactly what an older install has on disk), then opens it with
 * the current app: Room must run the migration, validate the v2 schema, and keep every row.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun v1DatabaseUpgradesWithoutLosingTransactions() = runBlocking<Unit> {
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }
        createV1(file)

        val db = Room.databaseBuilder(context, AshDatabase::class.java, file.absolutePath).allowMainThreadQueries().build()
        val txns = db.backupDao().transactions()
        assertThat(txns).hasSize(1)
        assertThat(txns[0].amountPaise).isEqualTo(8_500_000)
        assertThat(txns[0].isSalary).isFalse()
        assertThat(txns[0].balanceAfterPaise).isNull()
        assertThat(db.backupDao().profiles().single().name).isEqualTo("Yash")
        // New columns are writable after the upgrade.
        db.transactionDao().setSalary(1, true)
        assertThat(db.backupDao().transactions().single().isSalary).isTrue()
        db.close()
    }

    private fun createV1(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/app.ash.data.local.database.AshDatabase/1.json").readText())
            .jsonObject["database"]!!.jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content
        for (entity in schema["entities"]!!.jsonArray.map { it.jsonObject }) {
            val table = entity.str("tableName")
            sql.execSQL(entity.str("createSql").replace("\${TABLE_NAME}", table))
            entity["indices"]?.jsonArray?.forEach { sql.execSQL(it.jsonObject.str("createSql").replace("\${TABLE_NAME}", table)) }
            entity["contentSyncTriggers"]?.jsonArray?.forEach { sql.execSQL(it.jsonPrimitive.content) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
        sql.execSQL("INSERT INTO categories(`key`, builtIn, sortOrder) VALUES ('FINANCE', 1, 0)")
        sql.execSQL("INSERT INTO profiles(name, relation, createdAt) VALUES ('Yash', 'SELF', 1)")
        sql.execSQL(
            """INSERT INTO records(id, type, categoryKey, title, summary, importance, status, confidence, source, materialized, createdAt, updatedAt)
               VALUES (1, 'TRANSACTION', 'FINANCE', 'ACME', 'salary', 'LOW', 'HANDLED', 1.0, 'NOTIFICATION', 1, 1000, 1000)""",
        )
        sql.execSQL("INSERT INTO transactions(recordId, amountPaise, direction, accountRef, occurredAt, userCorrected) VALUES (1, 8500000, 'INCOME', 'XX1234', 1000, 0)")
        sql.version = 1
        sql.close()
    }
}
