package app.ash.presentation.onboarding

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ash.R
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.EmptyState
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.PrimaryButton
import app.ash.designsystem.SwitchRow
import app.ash.domain.model.AppSource
import app.ash.domain.repository.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(private val repo: SourceRepository) : ViewModel() {
    val sources = repo.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repo.discoverInstalledKnownApps() }
    }

    fun toggle(source: AppSource, enabled: Boolean) = viewModelScope.launch { repo.setEnabled(source.packageName, enabled) }

    fun enableSuggested() = viewModelScope.launch {
        sources.value.filter { it.suggested && !it.enabled }.forEach { repo.setEnabled(it.packageName, true) }
    }
}

/** Opt-in allow-list of apps ASH may read (R11). Nothing is pre-ticked. */
@Composable
fun SourcesScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val vm: SourcesViewModel = hiltViewModel()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val suggested = sources.filter { it.suggested }
    val others = sources.filterNot { it.suggested }
    AshScaffold(
        title = stringResource(R.string.sources_title),
        onBack = onBack,
        actions = { if (suggested.any { !it.enabled }) TextButton(onClick = vm::enableSuggested) { Text(stringResource(R.string.sources_select_suggested)) } },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Text(
                    stringResource(R.string.sources_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (sources.isEmpty()) {
                item { EmptyState(Icons.Rounded.Apps, stringResource(R.string.sources_empty_title), stringResource(R.string.sources_empty_body)) }
            }
            if (suggested.isNotEmpty()) {
                item {
                    GroupedSection(header = stringResource(R.string.sources_suggested)) {
                        suggested.forEachIndexed { i, s ->
                            SwitchRow(
                                title = s.label,
                                subtitle = sourceSubtitle(s),
                                checked = s.enabled,
                                onCheckedChange = { vm.toggle(s, it) },
                                showDivider = i < suggested.lastIndex,
                            )
                        }
                    }
                }
            }
            if (others.isNotEmpty()) {
                item {
                    GroupedSection(header = stringResource(R.string.sources_other), footer = stringResource(R.string.sources_other_footer)) {
                        others.forEachIndexed { i, s ->
                            SwitchRow(title = s.label, subtitle = sourceSubtitle(s), checked = s.enabled, onCheckedChange = { vm.toggle(s, it) }, showDivider = i < others.lastIndex)
                        }
                    }
                }
            }
            item {
                PrimaryButton(stringResource(R.string.action_done), onDone, Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun sourceSubtitle(s: AppSource): String =
    if (s.notificationCount > 0) stringResource(R.string.sources_seen, s.notificationCount) else stringResource(R.string.sources_not_seen)
