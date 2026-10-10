package app.ash.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ash.common.Dates
import app.ash.common.TimeProvider
import app.ash.data.local.dao.StatsDao
import app.ash.data.repository.SettingsRepository
import app.ash.domain.repository.RecordRepository
import app.ash.domain.repository.SourceRepository
import app.ash.domain.usecase.GetAttentionFeedUseCase
import app.ash.domain.usecase.HomeFeed
import app.ash.domain.usecase.LifeOrderScore
import app.ash.domain.usecase.Statement
import app.ash.domain.usecase.StatementCalculator
import app.ash.workspace.Workspace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

enum class Greeting { MORNING, AFTERNOON, EVENING }

/** Steps that take the real account from empty to useful. */
enum class SetupStep { NOTIFICATIONS, SOURCES, SMS_HISTORY, DOCUMENT, APP_LOCK }

data class HomeUiState(
    val loading: Boolean = true,
    val greeting: Greeting = Greeting.MORNING,
    val feed: HomeFeed = HomeFeed(),
    val order: LifeOrderScore.Result = LifeOrderScore.Result(100, emptyList()),
    val money: Statement? = null,
    /** Running balance per day of the current cycle, for the sparkline. */
    val balanceTrend: List<Float> = emptyList(),
    val setupLeft: List<SetupStep> = emptyList(),
    val showSetup: Boolean = false,
    val listenerEnabled: Boolean = true,
    val sourcesEnabled: Int = 0,
)

/** Today: the dashboard that answers "what should I care about right now?" at a glance. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    feed: GetAttentionFeedUseCase,
    records: RecordRepository,
    private val sources: SourceRepository,
    private val settings: SettingsRepository,
    stats: StatsDao,
    private val time: TimeProvider,
    private val lock: app.ash.security.AppLockManager,
    val workspace: Workspace,
) : ViewModel() {

    private val listener = MutableStateFlow(sources.isListenerEnabled())

    /** Amounts on Today stay hidden until the user unlocked with fingerprint/PIN recently (same rule as the Money tab). */
    val moneyRevealed = MutableStateFlow(lock.recentlyAuthenticated())

    private val money = records.observeTransactionsSince(time.nowMillis() - 400L * 24 * 60 * 60 * 1000)

    val state: StateFlow<HomeUiState> = combine(
        feed(),
        money,
        combine(listener, sources.observeEnabledCount(), stats.documents(), settings.settings) { l, s, d, st -> SetupInputs(l, s, d, st) },
    ) { f, txns, setup, ->
        val statement = StatementCalculator.compute(txns, 0, time.nowMillis(), time.zone())
        val steps = buildList {
            if (!setup.listener) add(SetupStep.NOTIFICATIONS)
            if (setup.sources == 0 && !setup.settings.readAllApps) add(SetupStep.SOURCES)
            if (setup.settings.smsImportedAt == 0L) add(SetupStep.SMS_HISTORY)
            if (setup.documents == 0) add(SetupStep.DOCUMENT)
            if (!setup.settings.appLockEnabled) add(SetupStep.APP_LOCK)
        }
        // The demo account is pre-filled and never shows setup or penalises missing setup.
        val realSetup = if (workspace == Workspace.REAL) steps else emptyList()
        HomeUiState(
            loading = false,
            greeting = greetingNow(),
            feed = f,
            order = LifeOrderScore.compute(f.openItems, f.reviewCount, realSetup.size),
            money = statement.takeIf { txns.isNotEmpty() },
            balanceTrend = trend(statement),
            setupLeft = realSetup,
            showSetup = realSetup.isNotEmpty() && !setup.settings.setupDismissed,
            listenerEnabled = setup.listener,
            sourcesEnabled = setup.sources,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private data class SetupInputs(val listener: Boolean, val sources: Int, val documents: Int, val settings: app.ash.data.repository.AppSettings)

    fun refreshPermissions() {
        listener.value = sources.isListenerEnabled()
        moneyRevealed.value = lock.recentlyAuthenticated()
    }

    fun dismissSetup() = viewModelScope.launch { settings.setSetupDismissed(true) }

    /** Balance at the end of each day from the cycle start to today. */
    private fun trend(s: Statement): List<Float> {
        if (s.lines.isEmpty()) return emptyList()
        val zone = time.zone()
        val start = Dates.toLocalDate(s.startAt, zone)
        val today = time.today()
        var balance = s.opening
        val byDay = s.lines.filter { it.balanceAfter != null }.groupBy { Dates.toLocalDate(it.txn.occurredAt, zone) }
        val points = mutableListOf<Float>()
        var day = start
        while (!day.isAfter(today) && points.size < 62) {
            byDay[day]?.lastOrNull()?.balanceAfter?.let { balance = it }
            points += balance / 100f
            day = day.plusDays(1)
        }
        return points
    }

    private fun greetingNow(): Greeting = when (LocalTime.now().hour) {
        in 4..11 -> Greeting.MORNING
        in 12..16 -> Greeting.AFTERNOON
        else -> Greeting.EVENING
    }
}
