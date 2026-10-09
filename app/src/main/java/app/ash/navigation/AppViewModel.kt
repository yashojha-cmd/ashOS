package app.ash.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ash.data.repository.SettingsRepository
import app.ash.data.repository.ThemeMode
import app.ash.domain.model.RecordStatus
import app.ash.domain.repository.RecordRepository
import app.ash.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppState(
    val loaded: Boolean = false,
    val onboardingDone: Boolean = false,
    val locked: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val secureAll: Boolean = false,
    val reviewCount: Int = 0,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    private val lock: AppLockManager,
    records: RecordRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<AppState> = combine(
        settings.settings,
        lock.locked,
        records.observeCount(RecordStatus.REVIEW),
    ) { s, locked, review ->
        AppState(
            loaded = true,
            onboardingDone = s.onboardingDone,
            locked = s.appLockEnabled && locked,
            themeMode = s.themeMode,
            secureAll = s.secureAllScreens,
            reviewCount = review,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    fun unlocked() = lock.unlock()

    /** No screen lock on the phone: app lock cannot work, so it is switched off with an explanation. */
    fun disableLockBecauseUnavailable() {
        viewModelScope.launch {
            settingsRepo.setAppLock(false)
            lock.unlock()
        }
    }
}
