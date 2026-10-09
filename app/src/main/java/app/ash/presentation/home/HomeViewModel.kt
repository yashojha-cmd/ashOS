package app.ash.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ash.domain.repository.SourceRepository
import app.ash.domain.usecase.GetAttentionFeedUseCase
import app.ash.domain.usecase.HomeFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime
import javax.inject.Inject

enum class Greeting { MORNING, AFTERNOON, EVENING }

data class HomeUiState(
    val loading: Boolean = true,
    val greeting: Greeting = Greeting.MORNING,
    val feed: HomeFeed = HomeFeed(),
    val listenerEnabled: Boolean = true,
    val sourcesEnabled: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    feed: GetAttentionFeedUseCase,
    private val sources: SourceRepository,
) : ViewModel() {

    private val listener = MutableStateFlow(sources.isListenerEnabled())

    val state: StateFlow<HomeUiState> = combine(feed(), listener.asStateFlow(), sources.observeEnabledCount()) { f, l, count ->
        HomeUiState(loading = false, greeting = greetingNow(), feed = f, listenerEnabled = l, sourcesEnabled = count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refreshPermissions() {
        listener.value = sources.isListenerEnabled()
    }

    private fun greetingNow(): Greeting = when (LocalTime.now().hour) {
        in 4..11 -> Greeting.MORNING
        in 12..16 -> Greeting.AFTERNOON
        else -> Greeting.EVENING
    }
}
