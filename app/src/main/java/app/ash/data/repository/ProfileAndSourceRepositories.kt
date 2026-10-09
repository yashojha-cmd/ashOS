package app.ash.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.database.sqlite.SQLiteConstraintException
import app.ash.common.AppError
import app.ash.common.AppResult
import app.ash.common.TimeProvider
import app.ash.data.local.dao.AppSourceDao
import app.ash.data.local.dao.ProfileDao
import app.ash.data.local.entities.AppSourceEntity
import app.ash.data.local.entities.ProfileEntity
import app.ash.data.parser.dictionary.KnownApps
import app.ash.domain.model.AppSource
import app.ash.domain.model.Profile
import app.ash.domain.model.ProfileRelation
import app.ash.domain.model.SourceKind
import app.ash.domain.repository.ProfileRepository
import app.ash.domain.repository.SourceRepository
import app.ash.notification.AshNotificationListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val dao: ProfileDao,
    private val time: TimeProvider,
) : ProfileRepository {
    override fun observeProfiles(): Flow<List<Profile>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun add(name: String, relation: ProfileRelation): AppResult<Long> = try {
        AppResult.Success(dao.insert(ProfileEntity(name = name.trim(), relation = relation, createdAt = time.nowMillis())))
    } catch (e: SQLiteConstraintException) {
        AppResult.Failure(AppError.UNKNOWN, e)
    } catch (e: Exception) {
        // SQLCipher throws its own constraint exception type.
        AppResult.Failure(AppError.UNKNOWN, e)
    }

    override suspend fun rename(profile: Profile, name: String) {
        dao.update(ProfileEntity(profile.id, name.trim(), profile.relation, time.nowMillis()))
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

@Singleton
class SourceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AppSourceDao,
    private val time: TimeProvider,
) : SourceRepository {
    override fun observeSources(): Flow<List<AppSource>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeEnabledCount(): Flow<Int> = dao.observeEnabledCount()

    override suspend fun setEnabled(packageName: String, enabled: Boolean) = dao.setEnabled(packageName, enabled)

    override suspend fun discoverInstalledKnownApps(): Int {
        val pm = context.packageManager
        var added = 0
        for (app in KnownApps.all) {
            val installed = try {
                pm.getApplicationInfo(app.packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            if (installed && dao.get(app.packageName) == null) {
                dao.upsert(AppSourceEntity(app.packageName, app.label, app.kind, enabled = false, firstSeenAt = null, lastSeenAt = null))
                added++
            }
        }
        return added
    }

    override fun isListenerEnabled(): Boolean = isListenerEnabled(context)

    /** Resolves a readable name for a package that posted a notification. */
    fun labelFor(packageName: String): Pair<String, SourceKind> {
        KnownApps.find(packageName)?.let { return it.label to it.kind }
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        return label to SourceKind.OTHER
    }

    suspend fun recordSeen(packageName: String): AppSourceEntity {
        val now = time.nowMillis()
        val existing = dao.get(packageName)
        if (existing == null) {
            val (label, kind) = labelFor(packageName)
            // Unknown apps start disabled: ASH only reads sources the user turned on.
            val entity = AppSourceEntity(packageName, label, kind, enabled = false, firstSeenAt = now, lastSeenAt = now, notificationCount = 1)
            dao.upsert(entity)
            return entity
        }
        dao.markSeen(packageName, now)
        return existing.copy(firstSeenAt = existing.firstSeenAt ?: now)
    }

    companion object {
        fun isListenerEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, AshNotificationListener::class.java)
            return flat.split(":").mapNotNull { ComponentName.unflattenFromString(it) }.any { it == me }
        }
    }
}
