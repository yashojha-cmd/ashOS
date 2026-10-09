package app.ash.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ash.R
import app.ash.common.Money
import app.ash.designsystem.AshIcons
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.EmptyState
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.docTypeLabel
import app.ash.designsystem.expiresText
import app.ash.designsystem.periodLabel
import app.ash.designsystem.relativeDay
import app.ash.designsystem.statusLabel
import app.ash.logging.AshLogger
import app.ash.navigation.DocumentRoute
import app.ash.navigation.PolicyEditRoute
import app.ash.navigation.RecordRoute
import app.ash.navigation.SubscriptionEditRoute
import app.ash.navigation.VehicleRoute
import app.ash.navigation.WarrantyEditRoute
import app.ash.search.SearchRepository
import app.ash.search.SearchResults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val repo: SearchRepository) : ViewModel() {
    val query = MutableStateFlow("")

    val results = query.debounce(150).distinctUntilChanged().mapLatest { q ->
        if (q.isBlank()) null else runCatching { repo.search(q) }.onFailure { AshLogger.e("Search", "Search failed", it) }.getOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val suggestions = listOf("bike insurance", "documents expiring", "Amazon", "₹5000", "train ticket", "electricity", "September", "bills due")

/** Local search over everything (§14). Nothing leaves the device. */
@Composable
fun SearchScreen(navigate: (Any) -> Unit) {
    val vm: SearchViewModel = hiltViewModel()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AshScaffold(title = stringResource(R.string.search_title)) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(AshIcons.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Rounded.Clear, stringResource(R.string.cd_clear)) } },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(focus),
                )
            }
            val r = results
            if (query.isBlank()) {
                item {
                    Text(stringResource(R.string.search_try), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s -> AssistChip(onClick = { vm.query.value = s }, label = { Text(s) }) }
                    }
                }
            } else if (r != null) {
                if (r.isEmpty) item { EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.search_none_title), stringResource(R.string.search_none_body)) }
                else item {
                    Text(pluralStringResource(R.plurals.search_count, r.total, r.total), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
                }
                results(r, navigate)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.results(r: SearchResults, navigate: (Any) -> Unit) {
    if (r.records.isNotEmpty()) item {
        GroupedSection(header = stringResource(R.string.search_records)) {
            r.records.forEachIndexed { i, rec ->
                AshRow(
                    title = rec.title,
                    subtitle = listOfNotNull(rec.amountPaise?.let { Money.format(it) }, statusLabel(rec.status), relativeDay(rec.eventAt ?: rec.dueAt ?: rec.createdAt)).joinToString(" · "),
                    icon = AshIcons.category(rec.category), showDivider = i < r.records.lastIndex, onClick = { navigate(RecordRoute(rec.id)) },
                )
            }
        }
    }
    if (r.documents.isNotEmpty()) item {
        GroupedSection(header = stringResource(R.string.vault_title)) {
            r.documents.forEachIndexed { i, d ->
                AshRow(d.title, subtitle = listOfNotNull(docTypeLabel(d.docType), d.expiresAt?.let { expiresText(it) }).joinToString(" · "),
                    icon = AshIcons.category(app.ash.domain.model.Category.DOCUMENTS), showDivider = i < r.documents.lastIndex, onClick = { navigate(DocumentRoute(d.id)) })
            }
        }
    }
    if (r.policies.isNotEmpty()) item {
        GroupedSection(header = stringResource(R.string.policies_title)) {
            r.policies.forEachIndexed { i, p ->
                AshRow(p.insurer, subtitle = p.expiresAt?.let { expiresText(it) }, icon = Icons.Rounded.Shield, showDivider = i < r.policies.lastIndex, onClick = { navigate(PolicyEditRoute(p.id)) })
            }
        }
    }
    if (r.vehicles.isNotEmpty()) item {
        GroupedSection(header = stringResource(R.string.vehicles_title)) {
            r.vehicles.forEachIndexed { i, v ->
                AshRow(v.name, subtitle = v.regNumber, icon = Icons.Rounded.DirectionsCar, showDivider = i < r.vehicles.lastIndex, onClick = { navigate(VehicleRoute(v.id)) })
            }
        }
    }
    if (r.warranties.isNotEmpty()) item {
        GroupedSection(header = stringResource(R.string.warranties_title)) {
            r.warranties.forEachIndexed { i, w ->
                AshRow(w.product, subtitle = expiresText(w.expiresAt), icon = Icons.Rounded.VerifiedUser, showDivider = i < r.warranties.lastIndex, onClick = { navigate(WarrantyEditRoute(w.id)) })
            }
        }
    }
    if (r.subscriptions.isNotEmpty()) item {
        GroupedSection(header = stringResource(R.string.money_tab_subscriptions)) {
            r.subscriptions.forEachIndexed { i, s ->
                AshRow(s.name, subtitle = "${Money.format(s.amountPaise)} · ${periodLabel(s.period)}", icon = AshIcons.category(app.ash.domain.model.Category.SUBSCRIPTIONS),
                    showDivider = i < r.subscriptions.lastIndex, onClick = { navigate(SubscriptionEditRoute(s.id)) })
            }
        }
    }
}
