package app.ash.presentation.reminders

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.ash.designsystem.AshFormat
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.AshTextField
import app.ash.designsystem.DateField
import app.ash.designsystem.EmptyState
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.groupedItems
import app.ash.designsystem.MessageBanner
import app.ash.designsystem.PrimaryButton
import app.ash.designsystem.Tone
import app.ash.designsystem.relativeDay
import app.ash.domain.model.Reminder
import app.ash.domain.model.ReminderState
import app.ash.domain.repository.ReminderRepository
import app.ash.navigation.ReminderEditRoute
import app.ash.presentation.onboarding.hasPostNotifications
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(private val repo: ReminderRepository) : ViewModel() {
    val reminders = repo.observeUpcoming(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun done(r: Reminder) = viewModelScope.launch { repo.markDone(r.id) }
    fun snooze(r: Reminder) = viewModelScope.launch { repo.snooze(r.id, System.currentTimeMillis() + 24L * 60 * 60 * 1000) }
}

/** Local reminders (§13): no cloud push, works in airplane mode. */
@Composable
fun RemindersScreen(onBack: () -> Unit, navigate: (Any) -> Unit) {
    val vm: RemindersViewModel = hiltViewModel()
    val list by vm.reminders.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    AshScaffold(
        title = stringResource(R.string.reminders_title),
        onBack = onBack,
        actions = { IconButton(onClick = { navigate(ReminderEditRoute()) }) { Icon(Icons.Rounded.Add, stringResource(R.string.add_reminder)) } },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            if (!hasPostNotifications(context)) item { MessageBanner(stringResource(R.string.reminders_no_permission), Tone.SOON) }
            val items = list
            if (items != null && items.isEmpty()) item { EmptyState(Icons.Rounded.NotificationsNone, stringResource(R.string.empty_reminders_title), stringResource(R.string.empty_reminders_body)) }
            groupedItems(items.orEmpty(), key = { it.id }) { r, last ->
                val fired = stringResource(R.string.reminder_fired)
                AshRow(
                    title = r.title,
                    subtitle = listOfNotNull(r.body, "${relativeDay(r.fireAt)} · ${AshFormat.dateTime(r.fireAt)}",
                        fired.takeIf { r.state == ReminderState.FIRED }).joinToString("\n"),
                    showDivider = !last,
                    onClick = { navigate(ReminderEditRoute(r.id)) },
                    trailing = {
                        IconButton(onClick = { vm.snooze(r) }) { Icon(Icons.Rounded.Snooze, stringResource(R.string.action_snooze_day)) }
                        IconButton(onClick = { vm.done(r) }) { Icon(Icons.Rounded.Done, stringResource(R.string.action_done)) }
                    },
                )
            }
        }
    }
}

@HiltViewModel
class ReminderEditViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: ReminderRepository) : ViewModel() {
    private val route = savedState.toRoute<ReminderEditRoute>()
    private val id = route.id
    val form = MutableStateFlow<Reminder?>(null)

    init {
        viewModelScope.launch {
            form.value = (if (id != 0L) repo.get(id) else null)
                ?: Reminder(title = route.title, fireAt = System.currentTimeMillis() + 24L * 60 * 60 * 1000)
        }
    }

    fun update(t: (Reminder) -> Reminder) { form.value = form.value?.let(t) }
    fun save(done: () -> Unit) = viewModelScope.launch { form.value?.let { repo.save(it) }; done() }
    fun delete(done: () -> Unit) = viewModelScope.launch { repo.delete(id); done() }
    val isNew get() = id == 0L
}

@Composable
fun ReminderEditScreen(onBack: () -> Unit) {
    val vm: ReminderEditViewModel = hiltViewModel()
    val form by vm.form.collectAsStateWithLifecycle()
    val f = form ?: return
    var hour by remember(f.id) { mutableStateOf(Instant.ofEpochMilli(f.fireAt).atZone(ZoneId.systemDefault()).hour) }
    val canSave = f.title.isNotBlank() && f.fireAt > System.currentTimeMillis()
    AshScaffold(
        title = stringResource(if (vm.isNew) R.string.add_reminder else R.string.reminder_edit),
        onBack = onBack,
        actions = { TextButton(onClick = { vm.save(onBack) }, enabled = canSave) { Text(stringResource(R.string.action_save)) } },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            item {
                AshTextField(f.title, { v -> vm.update { it.copy(title = v) } }, stringResource(R.string.field_title))
                AshTextField(f.body.orEmpty(), { v -> vm.update { it.copy(body = v) } }, stringResource(R.string.field_notes), singleLine = false, minLines = 2)
                DateField(f.fireAt, { d ->
                    d?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        vm.update { it.copy(fireAt = date.atTime(LocalTime.of(hour, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
                    }
                }, stringResource(R.string.field_when))
                app.ash.designsystem.OptionField(
                    stringResource(R.string.field_time),
                    (6..22).toList(),
                    hour,
                    { "%02d:00".format(it) },
                    { h ->
                        hour = h
                        val date = Instant.ofEpochMilli(f.fireAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        vm.update { it.copy(fireAt = date.atTime(LocalTime.of(h, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
                    },
                )
                if (f.fireAt <= System.currentTimeMillis()) MessageBanner(stringResource(R.string.reminder_past), Tone.SOON)
                PrimaryButton(stringResource(R.string.action_save), onClick = { vm.save(onBack) }, enabled = canSave, modifier = Modifier.padding(16.dp))
                if (!vm.isNew) TextButton(onClick = { vm.delete(onBack) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}
