package app.ash.presentation.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.ash.R
import app.ash.common.Money
import app.ash.designsystem.AshIcons
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.CenteredLoading
import app.ash.designsystem.ConfirmDialog
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.MessageBanner
import app.ash.designsystem.PrimaryButton
import app.ash.designsystem.StatusPill
import app.ash.designsystem.Tone
import app.ash.designsystem.directionLabel
import app.ash.designsystem.dueText
import app.ash.designsystem.expiresText
import app.ash.designsystem.formatDate
import app.ash.designsystem.statusLabel
import app.ash.designsystem.typeLabel
import app.ash.domain.model.Record
import app.ash.domain.model.RecordStatus
import app.ash.domain.model.RecordType
import app.ash.domain.repository.RecordRepository
import app.ash.navigation.RecordEditRoute
import app.ash.navigation.RecordRoute
import app.ash.navigation.ReminderEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordDetailState(val loading: Boolean = true, val record: Record? = null)

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val records: RecordRepository,
) : ViewModel() {
    private val id = savedState.toRoute<RecordRoute>().id

    val state = records.observe(id).map { RecordDetailState(false, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordDetailState())

    val isSalary = records.observeIsSalary(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setSalary(value: Boolean) = viewModelScope.launch { records.setSalary(id, value) }

    fun confirm() = viewModelScope.launch { state.value.record?.let { records.confirm(it) } }
    fun setStatus(status: RecordStatus) = viewModelScope.launch { records.setStatus(id, status) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { records.delete(id); onDone() }
}

@Composable
fun RecordDetailScreen(onBack: () -> Unit, navigate: (Any) -> Unit) {
    val vm: RecordDetailViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val salaryFlag by vm.isSalary.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var confirmDelete by remember { mutableStateOf(false) }
    val record = state.record

    AshScaffold(
        title = record?.title ?: "",
        onBack = onBack,
        actions = {
            if (record != null) {
                IconButton(onClick = { navigate(RecordEditRoute(record.id, record.type.name)) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
            }
        },
    ) { padding ->
        if (state.loading) { CenteredLoading(); return@AshScaffold }
        if (record == null) {
            MessageBanner(stringResource(R.string.error_not_found), Tone.INFO, Modifier.padding(padding))
            return@AshScaffold
        }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(statusLabel(record.status), statusTone(record.status))
                        StatusPill(typeLabel(record.type), Tone.INFO)
                    }
                    record.amountPaise?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(Money.format(it), style = MaterialTheme.typography.displaySmall)
                    }
                }
            }
            if (record.status == RecordStatus.REVIEW) {
                item {
                    MessageBanner(stringResource(R.string.review_prompt_confidence, (record.confidence * 100).toInt()), Tone.SOON)
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(stringResource(R.string.action_confirm), onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            vm.confirm()
                        }, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { vm.setStatus(RecordStatus.ARCHIVED); onBack() }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text(stringResource(R.string.action_not_important))
                        }
                    }
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.detail_details)) {
                    AshRow(stringResource(R.string.field_category), trailing = { Text(stringResource(AshIcons.categoryLabel(record.category))) }, icon = AshIcons.category(record.category))
                    record.merchant?.let { AshRow(stringResource(R.string.field_merchant), trailing = { Text(it) }) }
                    record.extras.direction?.let { AshRow(stringResource(R.string.field_direction), trailing = { Text(directionLabel(it)) }) }
                    record.extras.accountRef?.let { AshRow(stringResource(R.string.field_account), trailing = { Text(it) }) }
                    record.dueAt?.let { AshRow(stringResource(R.string.field_due), subtitle = dueText(it), trailing = { Text(formatDate(it)) }) }
                    record.expiresAt?.let { AshRow(stringResource(R.string.field_expires), subtitle = expiresText(it), trailing = { Text(formatDate(it)) }) }
                    record.eventAt?.let { AshRow(stringResource(if (record.type == RecordType.TRANSACTION) R.string.field_date else R.string.field_event_date), trailing = { Text(formatDate(it)) }) }
                    record.reference?.let { AshRow(stringResource(R.string.field_reference), trailing = { Text(it) }) }
                    record.extras.tripFrom?.let { from -> AshRow(stringResource(R.string.field_route), trailing = { Text("$from → ${record.extras.tripTo.orEmpty()}") }) }
                    AshRow(stringResource(R.string.field_source), trailing = { Text(record.sourceLabel ?: stringResource(R.string.source_manual)) })
                    AshRow(stringResource(R.string.field_added), trailing = { Text(formatDate(record.createdAt)) }, showDivider = false)
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.detail_summary), footer = stringResource(R.string.detail_parser_footer, record.extras.parserId ?: "-", (record.confidence * 100).toInt())) {
                    Text(
                        record.extras.originalText ?: record.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (record.status != RecordStatus.HANDLED && record.status != RecordStatus.REVIEW) {
                        PrimaryButton(stringResource(R.string.action_mark_handled), onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            vm.setStatus(RecordStatus.HANDLED)
                        })
                    }
                    if (record.status == RecordStatus.HANDLED || record.status == RecordStatus.ARCHIVED) {
                        OutlinedButton(onClick = { vm.setStatus(RecordStatus.UNREAD) }, modifier = Modifier.height(52.dp).fillMaxWidth()) {
                            Text(stringResource(R.string.action_reopen))
                        }
                    }
                    if (record.extras.direction == app.ash.domain.model.TxnDirection.INCOME && salaryFlag != null) {
                        OutlinedButton(onClick = { vm.setSalary(salaryFlag != true) }, modifier = Modifier.height(52.dp).fillMaxWidth()) {
                            Text(stringResource(if (salaryFlag == true) R.string.action_unmark_salary else R.string.action_mark_salary))
                        }
                    }
                    OutlinedButton(onClick = { navigate(ReminderEditRoute(title = record.title)) }, modifier = Modifier.height(52.dp).fillMaxWidth()) {
                        Text(stringResource(R.string.add_reminder))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_record_title),
            message = stringResource(R.string.delete_record_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}


