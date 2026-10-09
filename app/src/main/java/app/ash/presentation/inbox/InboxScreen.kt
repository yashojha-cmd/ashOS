package app.ash.presentation.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ash.R
import app.ash.common.Money
import app.ash.designsystem.AshIcons
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.AshTheme
import app.ash.designsystem.EmptyState
import app.ash.designsystem.StatusPill
import app.ash.designsystem.Tone
import app.ash.designsystem.relativeDay
import app.ash.designsystem.statusLabel
import app.ash.designsystem.typeLabel
import app.ash.domain.model.Category
import app.ash.domain.model.Record
import app.ash.domain.model.RecordStatus
import app.ash.domain.repository.InboxFilter
import app.ash.domain.repository.RecordRepository
import app.ash.navigation.RecordRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUiState(
    val filter: InboxFilter = InboxFilter.NEEDS_YOU,
    val category: Category? = null,
    val items: List<Record> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxViewModel @Inject constructor(private val records: RecordRepository) : ViewModel() {
    private val filter = MutableStateFlow(InboxFilter.NEEDS_YOU)
    private val category = MutableStateFlow<Category?>(null)

    val state = combine(filter, category) { f, c -> f to c }
        .flatMapLatest { (f, c) -> records.observeInbox(f, c).map { InboxUiState(f, c, it, false) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    fun setFilter(f: InboxFilter) { filter.value = f }
    fun setCategory(c: Category?) { category.value = c }

    fun handle(record: Record) = viewModelScope.launch { records.setStatus(record.id, RecordStatus.HANDLED) }
    fun archive(record: Record) = viewModelScope.launch { records.setStatus(record.id, RecordStatus.ARCHIVED) }
    fun confirm(record: Record) = viewModelScope.launch { records.confirm(record) }
}

@Composable
fun statusTone(status: RecordStatus): Tone = when (status) {
    RecordStatus.ACTION_REQUIRED -> Tone.URGENT
    RecordStatus.REVIEW -> Tone.SOON
    RecordStatus.HANDLED -> Tone.OK
    else -> Tone.INFO
}

/** Unified local inbox (§6) with status filters and swipe to handle or archive. */
@Composable
fun InboxScreen(navigate: (Any) -> Unit) {
    val vm: InboxViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    AshScaffold(title = stringResource(R.string.inbox_title)) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InboxFilter.entries.forEach { f ->
                        FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(filterLabel(f)) })
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.category == null, onClick = { vm.setCategory(null) }, label = { Text(stringResource(R.string.filter_all_categories)) })
                    Category.entries.forEach { c ->
                        FilterChip(
                            selected = state.category == c,
                            onClick = { vm.setCategory(if (state.category == c) null else c) },
                            label = { Text(stringResource(AshIcons.categoryLabel(c))) },
                            leadingIcon = { Icon(AshIcons.category(c), null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            if (!state.loading && state.items.isEmpty()) {
                item { EmptyState(Icons.Rounded.Inbox, stringResource(R.string.empty_inbox_title), stringResource(R.string.empty_inbox_body)) }
            }
            // Grouped by when it arrived, so the list reads like a timeline instead of a dump.
            dayGroups(state.items).forEach { (group, records) ->
                stickyHeader(key = "g-$group") {
                    Text(
                        stringResource(group.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(records, key = { it.id }) { record ->
                    InboxRow(
                        record = record,
                        onClick = { navigate(RecordRoute(record.id)) },
                        onHandle = { vm.handle(record) },
                        onArchive = { vm.archive(record) },
                        onConfirm = { vm.confirm(record) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun filterLabel(f: InboxFilter): String = stringResource(
    when (f) {
        InboxFilter.ALL -> R.string.filter_all
        InboxFilter.REVIEW -> R.string.filter_review
        InboxFilter.NEEDS_YOU -> R.string.filter_needs_you
        InboxFilter.HANDLED -> R.string.filter_handled
        InboxFilter.ARCHIVED -> R.string.filter_archived
    },
)

@Composable
private fun InboxRow(
    record: Record,
    onClick: () -> Unit,
    onHandle: () -> Unit,
    onArchive: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipe = rememberSwipeToDismissBoxState()
    val status = AshTheme.status
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        enableDismissFromStartToEnd = record.status != RecordStatus.HANDLED,
        enableDismissFromEndToStart = record.status != RecordStatus.ARCHIVED,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onHandle()
                SwipeToDismissBoxValue.EndToStart -> onArchive()
                else -> Unit
            }
        },
        backgroundContent = {
            val toEnd = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(if (toEnd) status.okContainer else MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 20.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (toEnd) Icons.Rounded.Done else Icons.Rounded.Archive, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (toEnd) R.string.action_mark_handled else R.string.action_archive))
                }
            }
        },
    ) {
        Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AshIcons.category(record.category), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(record.sourceLabel ?: typeLabel(record.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(relativeDay(record.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    record.amountPaise?.let { Text(Money.format(it), style = MaterialTheme.typography.titleMedium) }
                }
                Text(record.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(statusLabel(record.status), statusTone(record.status))
                    Text("${stringResource(AshIcons.categoryLabel(record.category))} · ${typeLabel(record.type)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (record.status == RecordStatus.REVIEW) {
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.review_prompt), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        OutlinedButton(onClick = onConfirm) { Text(stringResource(R.string.action_confirm)) }
                        TextButton(onClick = onClick) { Text(stringResource(R.string.action_edit)) }
                        TextButton(onClick = onArchive) { Text(stringResource(R.string.action_not_important)) }
                    }
                }
            }
        }
    }
}

internal enum class DayGroup(val label: Int) {
    TODAY(R.string.group_today), YESTERDAY(R.string.group_yesterday), THIS_WEEK(R.string.group_this_week), EARLIER(R.string.group_earlier)
}

/** Splits newest-first records into Today / Yesterday / This week / Earlier, keeping order. */
internal fun dayGroups(records: List<Record>, today: java.time.LocalDate = java.time.LocalDate.now(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<Pair<DayGroup, List<Record>>> =
    records.groupBy { r ->
        val days = java.time.temporal.ChronoUnit.DAYS.between(app.ash.common.Dates.toLocalDate(r.createdAt, zone), today)
        when {
            days <= 0 -> DayGroup.TODAY
            days == 1L -> DayGroup.YESTERDAY
            days < 7 -> DayGroup.THIS_WEEK
            else -> DayGroup.EARLIER
        }
    }.toList().sortedBy { it.first.ordinal }
