package app.ash.presentation.life

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.ash.designsystem.AshFormat
import app.ash.designsystem.AshIcons
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.AshTextField
import app.ash.designsystem.CenteredLoading
import app.ash.designsystem.EmptyState
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.OptionField
import app.ash.designsystem.StatusPill
import app.ash.designsystem.Tone
import app.ash.designsystem.expiresText
import app.ash.designsystem.formatDate
import app.ash.designsystem.insuranceKindLabel
import app.ash.designsystem.relationLabel
import app.ash.domain.model.Category
import app.ash.domain.model.Profile
import app.ash.domain.model.ProfileRelation
import app.ash.domain.model.TimelineEvent
import app.ash.domain.repository.DocumentRepository
import app.ash.domain.repository.LifeRepository
import app.ash.domain.repository.ProfileRepository
import app.ash.navigation.DocumentRoute
import app.ash.navigation.PolicyEditRoute
import app.ash.navigation.VehicleEditRoute
import app.ash.navigation.VehicleRoute
import app.ash.navigation.WarrantyEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun expiryTone(millis: Long?): Tone {
    if (millis == null) return Tone.INFO
    val days = AshFormat.daysFromToday(millis)
    return when {
        days <= 7 -> Tone.URGENT
        days <= 30 -> Tone.SOON
        else -> Tone.OK
    }
}

// ---------- Vehicles ----------

@HiltViewModel
class VehiclesViewModel @Inject constructor(life: LifeRepository) : ViewModel() {
    val vehicles = life.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun VehiclesScreen(onBack: () -> Unit, navigate: (Any) -> Unit) {
    val vm: VehiclesViewModel = hiltViewModel()
    val list by vm.vehicles.collectAsStateWithLifecycle()
    AshScaffold(
        title = stringResource(R.string.vehicles_title),
        onBack = onBack,
        actions = { IconButton(onClick = { navigate(VehicleEditRoute()) }) { Icon(Icons.Rounded.Add, stringResource(R.string.vehicle_add)) } },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            val items = list
            if (items != null && items.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.DirectionsCar, stringResource(R.string.empty_vehicles_title), stringResource(R.string.empty_vehicles_body),
                        actionLabel = stringResource(R.string.vehicle_add), onAction = { navigate(VehicleEditRoute()) })
                }
            }
            if (!items.isNullOrEmpty()) item {
                GroupedSection {
                    items.forEachIndexed { i, v ->
                        AshRow(
                            title = v.name,
                            subtitle = listOfNotNull(v.regNumber, listOfNotNull(v.brand, v.model).joinToString(" ").ifBlank { null }).joinToString(" · ").ifBlank { null },
                            icon = Icons.Rounded.DirectionsCar,
                            showDivider = i < items.lastIndex,
                            showChevron = true,
                            onClick = { navigate(VehicleRoute(v.id)) },
                        )
                    }
                }
            }
        }
    }
}

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(savedState: SavedStateHandle, life: LifeRepository, documents: DocumentRepository) : ViewModel() {
    val id = savedState.toRoute<VehicleRoute>().id
    val vehicle = life.observeVehicle(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val timeline = life.observeTimeline(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val policies = life.observePoliciesForVehicle(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val documents = documents.observeForVehicle(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val expenses = life.observeVehicleExpenses(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Vehicle module (§18): status, linked insurance and documents, and the life timeline. */
@Composable
fun VehicleDetailScreen(onBack: () -> Unit, navigate: (Any) -> Unit) {
    val vm: VehicleDetailViewModel = hiltViewModel()
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val timeline by vm.timeline.collectAsStateWithLifecycle()
    val policies by vm.policies.collectAsStateWithLifecycle()
    val docs by vm.documents.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val v = vehicle
    AshScaffold(
        title = v?.name ?: "",
        onBack = onBack,
        actions = { IconButton(onClick = { navigate(VehicleEditRoute(vm.id)) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) } },
    ) { padding ->
        if (v == null) { CenteredLoading(); return@AshScaffold }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            item {
                GroupedSection(header = stringResource(R.string.vehicle_status)) {
                    v.regNumber?.let { AshRow(stringResource(R.string.field_reg), trailing = { Text(it) }) }
                    AshRow(stringResource(R.string.field_puc_expiry), trailing = {
                        v.pucExpiresAt?.let { StatusPill(expiresText(it), expiryTone(it)) } ?: Text(stringResource(R.string.not_set))
                    })
                    AshRow(stringResource(R.string.field_next_service), trailing = {
                        v.nextServiceAt?.let { StatusPill(formatDate(it), expiryTone(it)) } ?: Text(stringResource(R.string.not_set))
                    })
                    v.loanEmiPaise?.let { AshRow(stringResource(R.string.field_emi), trailing = { Text(Money.format(it)) }) }
                    AshRow(stringResource(R.string.vehicle_fuel_spend), showDivider = false, trailing = { Text(Money.format(expenses.sumOf { it.amountPaise })) })
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.policies_title)) {
                    policies.forEach { p ->
                        AshRow(p.insurer, subtitle = p.expiresAt?.let { expiresText(it) }, icon = Icons.Rounded.Shield, showChevron = true, onClick = { navigate(PolicyEditRoute(p.id)) })
                    }
                    AshRow(stringResource(R.string.policy_add), icon = Icons.Rounded.Add, showDivider = false, titleColor = MaterialTheme.colorScheme.primary,
                        onClick = { navigate(PolicyEditRoute(vehicleId = vm.id)) })
                }
            }
            if (docs.isNotEmpty()) item {
                GroupedSection(header = stringResource(R.string.vault_title)) {
                    docs.forEachIndexed { i, d ->
                        AshRow(d.title, subtitle = d.expiresAt?.let { expiresText(it) }, icon = AshIcons.category(Category.DOCUMENTS),
                            showDivider = i < docs.lastIndex, showChevron = true, onClick = { navigate(DocumentRoute(d.id)) })
                    }
                }
            }
            if (timeline.isNotEmpty()) {
                item { Text(stringResource(R.string.vehicle_timeline), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)) }
                itemsIndexed(timeline) { i, e -> TimelineRow(e, last = i == timeline.lastIndex) }
            }
        }
    }
}

@Composable
private fun TimelineRow(event: TimelineEvent, last: Boolean) {
    val future = event.at > System.currentTimeMillis()
    Row(Modifier.padding(horizontal = 24.dp).height(64.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(16.dp).fillMaxHeight()) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (future) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(timelineLabel(event.kind), style = MaterialTheme.typography.titleSmall)
            Text("${formatDate(event.at)} · ${event.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun timelineLabel(kind: TimelineEvent.Kind): String = stringResource(
    when (kind) {
        TimelineEvent.Kind.PURCHASE -> R.string.tl_purchase
        TimelineEvent.Kind.INSURANCE_START -> R.string.tl_insurance_start
        TimelineEvent.Kind.INSURANCE_EXPIRY -> R.string.tl_insurance_expiry
        TimelineEvent.Kind.SERVICE_DONE -> R.string.tl_service_done
        TimelineEvent.Kind.SERVICE_DUE -> R.string.tl_service_due
        TimelineEvent.Kind.PUC_EXPIRY -> R.string.tl_puc
        TimelineEvent.Kind.WARRANTY_EXPIRY -> R.string.tl_warranty
        TimelineEvent.Kind.DOCUMENT -> R.string.tl_document
        TimelineEvent.Kind.EXPENSE -> R.string.tl_expense
    },
)

// ---------- Policies & warranties ----------

@HiltViewModel
class PoliciesViewModel @Inject constructor(life: LifeRepository) : ViewModel() {
    val policies = life.observePolicies().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val warranties = life.observeWarranties().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun PoliciesScreen(onBack: () -> Unit, navigate: (Any) -> Unit) {
    val vm: PoliciesViewModel = hiltViewModel()
    val list by vm.policies.collectAsStateWithLifecycle()
    AshScaffold(
        title = stringResource(R.string.policies_title),
        onBack = onBack,
        actions = { IconButton(onClick = { navigate(PolicyEditRoute()) }) { Icon(Icons.Rounded.Add, stringResource(R.string.policy_add)) } },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            val items = list
            if (items != null && items.isEmpty()) item {
                EmptyState(Icons.Rounded.Shield, stringResource(R.string.empty_policies_title), stringResource(R.string.empty_policies_body),
                    actionLabel = stringResource(R.string.policy_add), onAction = { navigate(PolicyEditRoute()) })
            }
            if (!items.isNullOrEmpty()) item {
                GroupedSection {
                    items.forEachIndexed { i, p ->
                        AshRow(
                            title = p.insurer,
                            subtitle = listOfNotNull(insuranceKindLabel(p.kind), p.policyNumber, p.premiumPaise?.let { Money.format(it) }).joinToString(" · "),
                            icon = Icons.Rounded.Shield,
                            showDivider = i < items.lastIndex,
                            onClick = { navigate(PolicyEditRoute(p.id)) },
                            trailing = { p.expiresAt?.let { StatusPill(expiresText(it), expiryTone(it)) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WarrantiesScreen(onBack: () -> Unit, navigate: (Any) -> Unit) {
    val vm: PoliciesViewModel = hiltViewModel()
    val list by vm.warranties.collectAsStateWithLifecycle()
    AshScaffold(
        title = stringResource(R.string.warranties_title),
        onBack = onBack,
        actions = { IconButton(onClick = { navigate(WarrantyEditRoute()) }) { Icon(Icons.Rounded.Add, stringResource(R.string.warranty_add)) } },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            val items = list
            if (items != null && items.isEmpty()) item {
                EmptyState(Icons.Rounded.VerifiedUser, stringResource(R.string.empty_warranties_title), stringResource(R.string.empty_warranties_body),
                    actionLabel = stringResource(R.string.warranty_add), onAction = { navigate(WarrantyEditRoute()) })
            }
            if (!items.isNullOrEmpty()) item {
                GroupedSection {
                    items.forEachIndexed { i, w ->
                        AshRow(w.product, icon = Icons.Rounded.VerifiedUser, showDivider = i < items.lastIndex, onClick = { navigate(WarrantyEditRoute(w.id)) },
                            trailing = { StatusPill(expiresText(w.expiresAt), expiryTone(w.expiresAt)) })
                    }
                }
            }
        }
    }
}

// ---------- Profiles ----------

@HiltViewModel
class ProfilesViewModel @Inject constructor(private val repo: ProfileRepository) : ViewModel() {
    val profiles = repo.observeProfiles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun add(name: String, relation: ProfileRelation) = viewModelScope.launch { repo.add(name, relation) }
    fun rename(p: Profile, name: String) = viewModelScope.launch { repo.rename(p, name) }
    fun delete(p: Profile) = viewModelScope.launch { repo.delete(p.id) }
}

/** Local profiles (§19): Me, Dad, Mom, Home. No sharing or sync. */
@Composable
fun ProfilesScreen(onBack: () -> Unit) {
    val vm: ProfilesViewModel = hiltViewModel()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    AshScaffold(
        title = stringResource(R.string.profiles_title),
        onBack = onBack,
        actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.profile_add)) } },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            item {
                GroupedSection(footer = stringResource(R.string.profiles_footer)) {
                    profiles.forEachIndexed { i, p ->
                        AshRow(p.name, subtitle = relationLabel(p.relation), icon = Icons.Rounded.Person, showDivider = i < profiles.lastIndex,
                            onClick = { editing = p })
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        val current = editing
        var name by remember(current) { mutableStateOf(current?.name.orEmpty()) }
        var relation by remember(current) { mutableStateOf(current?.relation ?: ProfileRelation.PARENT) }
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(stringResource(if (current == null) R.string.profile_add else R.string.profile_edit)) },
            text = {
                Column {
                    AshTextField(name, { name = it }, stringResource(R.string.field_name))
                    if (current == null) {
                        OptionField(stringResource(R.string.field_relation), ProfileRelation.entries.filter { it != ProfileRelation.SELF }, relation, { relationLabel(it) }, { relation = it })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    if (current == null) vm.add(name, relation) else vm.rename(current, name)
                    adding = false; editing = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                Row {
                    if (current != null && current.relation != ProfileRelation.SELF) {
                        TextButton(onClick = { vm.delete(current); editing = null }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { adding = false; editing = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }
}
