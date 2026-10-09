package app.ash.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val appLockEnabled: Boolean = false,
    val autoLockSeconds: Int = 60,
    val exactReminders: Boolean = false,
    val hideAmountsInNotifications: Boolean = true,
    val storeOriginalText: Boolean = true,
    val detailedLogsUntil: Long = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val secureAllScreens: Boolean = false,
    val lastMaintenanceAt: Long = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ash_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val appLock = booleanPreferencesKey("app_lock")
        val autoLock = intPreferencesKey("auto_lock_seconds")
        val exact = booleanPreferencesKey("exact_reminders")
        val hideAmounts = booleanPreferencesKey("hide_amounts")
        val storeText = booleanPreferencesKey("store_original_text")
        val detailedUntil = longPreferencesKey("detailed_logs_until")
        val theme = stringPreferencesKey("theme_mode")
        val secureAll = booleanPreferencesKey("secure_all_screens")
        val maintenance = longPreferencesKey("last_maintenance")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.onboarding] ?: false,
            appLockEnabled = p[Keys.appLock] ?: false,
            autoLockSeconds = p[Keys.autoLock] ?: 60,
            exactReminders = p[Keys.exact] ?: false,
            hideAmountsInNotifications = p[Keys.hideAmounts] ?: true,
            storeOriginalText = p[Keys.storeText] ?: true,
            detailedLogsUntil = p[Keys.detailedUntil] ?: 0,
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            secureAllScreens = p[Keys.secureAll] ?: false,
            lastMaintenanceAt = p[Keys.maintenance] ?: 0,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[Keys.onboarding] = done }
    suspend fun setAppLock(enabled: Boolean) = context.dataStore.edit { it[Keys.appLock] = enabled }
    suspend fun setAutoLockSeconds(seconds: Int) = context.dataStore.edit { it[Keys.autoLock] = seconds }
    suspend fun setExactReminders(enabled: Boolean) = context.dataStore.edit { it[Keys.exact] = enabled }
    suspend fun setHideAmounts(enabled: Boolean) = context.dataStore.edit { it[Keys.hideAmounts] = enabled }
    suspend fun setStoreOriginalText(enabled: Boolean) = context.dataStore.edit { it[Keys.storeText] = enabled }
    suspend fun setDetailedLogsUntil(until: Long) = context.dataStore.edit { it[Keys.detailedUntil] = until }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setSecureAllScreens(enabled: Boolean) = context.dataStore.edit { it[Keys.secureAll] = enabled }
    suspend fun setLastMaintenance(at: Long) = context.dataStore.edit { it[Keys.maintenance] = at }

    suspend fun clear() = context.dataStore.edit { it.clear() }
}
