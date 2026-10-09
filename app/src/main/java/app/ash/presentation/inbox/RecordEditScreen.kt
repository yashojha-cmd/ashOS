package app.ash.presentation.inbox

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.AshTextField
import app.ash.designsystem.DateField
import app.ash.designsystem.MoneyField
import app.ash.designsystem.OptionField
import app.ash.designsystem.PrimaryButton
import app.ash.designsystem.directionLabel
import app.ash.designsystem.typeLabel
import app.ash.domain.model.Category
import app.ash.domain.model.Importance
import app.ash.domain.model.Record
import app.ash.domain.model.RecordStatus
import app.ash.domain.model.RecordType
import app.ash.domain.model.TxnDirection
import app.ash.domain.repository.RecordRepository
import app.ash.navigation.RecordEditRoute
import app.ash.presentation.common.ProfilePicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordForm(
    val loaded: Boolean = false,
    val original: Record? = null,
    val type: RecordType = RecordType.BILL,
    val title: String = "",
    val category: Category = Category.BILLS,
    val amount: String = "",
    val direction: TxnDirection = TxnDirection.EXPENSE,
    val dueAt: Long? = null,
    val eventAt: Long? = null,
    val notes: String = "",
    val profileId: Long? = null,
) {
    val valid: Boolean get() = title.isNotBlank() && (amount.isBlank() || Money.parseInput(amount) != null) &&
        (type != RecordType.TRANSACTION || Money.parseInput(amount) != null)
}

@HiltViewModel
class RecordEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val records: RecordRepository,
) : ViewModel() {
    private val route = savedState.toRoute<RecordEditRoute>()
    val form = MutableStateFlow(RecordForm())

    init {
        viewModelScope.launch {
            val existing = if (route.id != 0L) records.get(route.id) else null
            form.value = if (existing != null) {
                RecordForm(
                    loaded = true, original = existing, type = existing.type, title = existing.title, category = existing.category,
                    amount = Money.toInput(existing.amountPaise), direction = existing.extras.direction ?: TxnDirection.EXPENSE,
                    dueAt = existing.dueAt ?: existing.expiresAt, eventAt = existing.eventAt, notes = existing.summary, profileId = existing.profileId,
                )
            } else {
                val type = runCatching { RecordType.valueOf(route.type) }.getOrDefault(RecordType.BILL)
                RecordForm(loaded = true, type = type, category = defaultCategory(type), eventAt = if (type == RecordType.TRANSACTION) System.currentTimeMillis() else null)
            }
        }
    }

    fun update(transform: (RecordForm) -> RecordForm) = form.update(transform)

    fun save(onDone: () -> Unit) {
        val f = form.value
        if (!f.valid) return
        viewModelScope.launch {
            val base = f.original ?: Record(type = f.type, category = f.category, title = f.title, summary = "")
            val usesExpiry = base.expiresAt != null && base.dueAt == null
            val edited = base.copy(
                type = f.type, title = f.title.trim(), category = f.category, merchant = if (f.type == RecordType.TRANSACTION) f.title.trim() else base.merchant,
                amountPaise = Money.parseInput(f.amount), summary = f.notes.trim(),
                dueAt = if (usesExpiry) null else f.dueAt, expiresAt = if (usesExpiry) f.dueAt else base.expiresAt,
                eventAt = f.eventAt, profileId = f.profileId,
                importance = if (f.type == RecordType.BILL) Importance.CRITICAL else base.importance,
                status = if (f.original == null && f.type != RecordType.TRANSACTION && f.dueAt != null) RecordStatus.ACTION_REQUIRED else base.status,
                extras = base.extras.copy(direction = if (f.type == RecordType.TRANSACTION) f.direction else base.extras.direction),
            )
            when {
                f.original == null -> records.addManual(edited)
                f.original.status == RecordStatus.REVIEW -> records.confirm(edited)
                else -> {
                    records.update(edited)
                    if (f.type == RecordType.TRANSACTION) {
                        Money.parseInput(f.amount)?.let { records.correctTransaction(edited.id, it, f.direction, edited.merchant, f.category) }
                    }
                }
            }
            onDone()
        }
    }

    private fun defaultCategory(type: RecordType) = when (type) {
        RecordType.BILL -> Category.BILLS
        RecordType.TRANSACTION -> Category.FINANCE
        RecordType.APPOINTMENT -> Category.HEALTHCARE
        else -> Category.OTHER
    }
}

/** Manual entry and correction of records. Every extracted value can be fixed here (§16). */
@Composable
fun RecordEditScreen(onBack: () -> Unit) {
    val vm: RecordEditViewModel = hiltViewModel()
    val f by vm.form.collectAsStateWithLifecycle()
    val isNew = f.original == null
    AshScaffold(
        title = stringResource(if (isNew) R.string.record_new_title else R.string.record_edit_title),
        onBack = onBack,
        actions = { TextButton(onClick = { vm.save(onBack) }, enabled = f.valid) { Text(stringResource(R.string.action_save)) } },
    ) { padding ->
        if (!f.loaded) return@AshScaffold
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                if (isNew) {
                    OptionField(
                        label = stringResource(R.string.field_type),
                        options = listOf(RecordType.BILL, RecordType.TRANSACTION, RecordType.APPOINTMENT, RecordType.GENERIC),
                        selected = f.type,
                        optionLabel = { typeLabel(it) },
                        onSelect = { t -> vm.update { it.copy(type = t) } },
                    )
                }
                AshTextField(
                    f.title, { v -> vm.update { it.copy(title = v) } },
                    stringResource(if (f.type == RecordType.TRANSACTION) R.string.field_merchant else R.string.field_title),
                    isError = f.title.isBlank(),
                )
                OptionField(
                    label = stringResource(R.string.field_category),
                    options = Category.entries,
                    selected = f.category,
                    optionLabel = { stringResource(AshIcons.categoryLabel(it)) },
                    onSelect = { c -> vm.update { it.copy(category = c) } },
                )
                MoneyField(f.amount, { v -> vm.update { it.copy(amount = v) } }, stringResource(R.string.field_amount))
                if (f.type == RecordType.TRANSACTION) {
                    OptionField(
                        label = stringResource(R.string.field_direction),
                        options = TxnDirection.entries,
                        selected = f.direction,
                        optionLabel = { directionLabel(it) },
                        onSelect = { d -> vm.update { it.copy(direction = d) } },
                    )
                    DateField(f.eventAt, { v -> vm.update { it.copy(eventAt = v) } }, stringResource(R.string.field_date), deadline = false)
                } else {
                    DateField(f.dueAt, { v -> vm.update { it.copy(dueAt = v) } }, stringResource(if (f.type == RecordType.APPOINTMENT) R.string.field_when else R.string.field_due))
                }
                AshTextField(f.notes, { v -> vm.update { it.copy(notes = v) } }, stringResource(R.string.field_notes), singleLine = false, minLines = 3)
                ProfilePicker(f.profileId) { id -> vm.update { it.copy(profileId = id) } }
                PrimaryButton(stringResource(R.string.action_save), onClick = { vm.save(onBack) }, enabled = f.valid, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
