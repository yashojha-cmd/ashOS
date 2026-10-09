package app.ash.navigation

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ash.data.local.dao.StatsDao
import app.ash.data.repository.SettingsRepository
import app.ash.data.repository.ThemeMode
import app.ash.demo.DemoDataSeeder
import app.ash.domain.model.RecordStatus
import app.ash.domain.repository.RecordRepository
import app.ash.logging.AshLogger
import app.ash.security.AppLockManager
import app.ash.workspace.Workspace
import app.ash.workspace.WorkspaceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppState(
    val loaded: Boolean = false,
    val workspace: Workspace = Workspace.REAL,
    val signedIn: Boolean = false,
    val preparingDemo: Boolean = false,
    val demoStep: String? = null,
    val onboardingDone: Boolean = false,
    val locked: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val secureAll: Boolean = false,
    val reviewCount: Int = 0,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val lock: AppLockManager,
    records: RecordRepository,
    private val workspaces: WorkspaceManager,
    private val demo: DemoDataSeeder,
    private val stats: StatsDao,
) : ViewModel() {

    private val preparing = MutableStateFlow<String?>(null)

    val state: StateFlow<AppState> = combine(
        settingsRepo.settings,
        lock.locked,
        workspaces.signedIn,
        preparing,
        // The database is not touched until an account is chosen.
        workspaces.signedIn.flatMapLatest { signed -> if (signed) records.observeCount(RecordStatus.REVIEW) else flowOf(0) },
    ) { s, locked, signedIn, step, review ->
        AppState(
            loaded = true,
            workspace = workspaces.active,
            signedIn = signedIn,
            preparingDemo = step != null,
            demoStep = step,
            // The demo account never shows onboarding.
            onboardingDone = s.onboardingDone || workspaces.active == Workspace.DEMO,
            locked = s.appLockEnabled && locked,
            themeMode = s.themeMode,
            secureAll = s.secureAllScreens,
            reviewCount = review,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    init {
        viewModelScope.launch {
            // Fill the demo account the first time it is opened (or after "Reset demo data").
            workspaces.signedIn.first { it }
            if (workspaces.active == Workspace.DEMO && stats.records().first() == 0) seedDemo()
        }
    }

    private suspend fun seedDemo() {
        preparing.value = "…"
        runCatching { demo.seed { step -> preparing.value = step } }
            .onFailure { AshLogger.e("Demo", "Preparing demo data failed", it) }
        preparing.value = null
    }

    fun signIn(workspace: Workspace, activity: Activity) = workspaces.signIn(workspace, activity)

    fun unlocked() = lock.unlock()

    /** No screen lock on the phone: app lock cannot work, so it is switched off with an explanation. */
    fun disableLockBecauseUnavailable() {
        viewModelScope.launch {
            settingsRepo.setAppLock(false)
            lock.unlock()
        }
    }
}
