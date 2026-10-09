package app.ash.presentation.life

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.ash.R
import app.ash.common.Money
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.AshTextField
import app.ash.designsystem.ConfirmDialog
import app.ash.designsystem.DateField
import app.ash.designsystem.DestructiveButton
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.MoneyField
import app.ash.designsystem.OptionField
import app.ash.designsystem.PrimaryButton
import app.ash.designsystem.SwitchRow
import app.ash.designsystem.insuranceKindLabel
import app.ash.designsystem.periodLabel
import app.ash.domain.model.InsuranceKind
import app.ash.domain.model.InsurancePolicy
import app.ash.domain.model.RecurrencePeriod
import app.ash.domain.model.Subscription
import app.ash.domain.model.Vehicle
import app.ash.domain.model.Warranty
import app.ash.domain.repository.LifeRepository
import app.ash.navigation.PolicyEditRoute
import app.ash.navigation.SubscriptionEditRoute
import app.ash.navigation.VehicleEditRoute
import app.ash.navigation.WarrantyEditRoute
import app.ash.presentation.common.ProfilePicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------- Shared editor shell ----------

@Composable
private fun EditorShell(
    title: String,
    canSave: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    deleteTitle: String,
    content: @Composable () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    AshScaffold(
        title = title,
        onBack = onBack,
        actions = { TextButton(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.action_save)) } },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                content()
                PrimaryButton(stringResource(R.string.action_save), onClick = onSave, enabled = canSave, modifier = Modifier.padding(16.dp))
                if (onDelete != null) {
                    DestructiveButton(stringResource(R.string.action_delete), onClick = { confirm = true }, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
    if (confirm && onDelete != null) {
        ConfirmDialog(deleteTitle, stringResource(R.string.delete_generic_body), stringResource(R.string.action_delete),
            onConfirm = { confirm = false; onDelete() }, onDismiss = { confirm = false }, destructive = true)
    }
}

// ---------- Vehicle ----------

@HiltViewModel
class VehicleEditViewModel @Inject constructor(savedState: SavedStateHandle, private val life: LifeRepository) : ViewModel() {
    private val id = savedState.toRoute<VehicleEditRoute>().id
    val form = MutableStateFlow<Vehicle?>(null)
    val emi = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val v = if (id != 0L) life.getVehicle(id) else null
            form.value = v ?: Vehicle(name = "")
            emi.value = Money.toInput(v?.loanEmiPaise)
        }
    }

    fun update(t: (Vehicle) -> Vehicle) = form.update { it?.let(t) }
    fun save(done: () -> Unit) = viewModelScope.launch {
        form.value?.let { life.saveVehicle(it.copy(loanEmiPaise = Money.parseInput(emi.value))) }
        done()
    }
    fun delete(done: () -> Unit) = viewModelScope.launch { life.deleteVehicle(id); done() }
    val isNew get() = id == 0L
}

@Composable
fun VehicleEditScreen(onBack: () -> Unit) {
    val vm: VehicleEditViewModel = hiltViewModel()
    val v by vm.form.collectAsStateWithLifecycle()
    val emi by vm.emi.collectAsStateWithLifecycle()
    val f = v ?: return
    EditorShell(
        title = stringResource(if (vm.isNew) R.string.vehicle_add else R.string.vehicle_edit),
        canSave = f.name.isNotBlank(),
        onBack = onBack,
        onSave = { vm.save(onBack) },
        onDelete = if (vm.isNew) null else ({ vm.delete(onBack) }),
        deleteTitle = stringResource(R.string.vehicle_delete),
    ) {
        AshTextField(f.name, { s -> vm.update { it.copy(name = s) } }, stringResource(R.string.field_vehicle_name), supportingText = stringResource(R.string.field_vehicle_name_hint))
        AshTextField(f.brand.orEmpty(), { s -> vm.update { it.copy(brand = s) } }, stringResource(R.string.field_brand))
        AshTextField(f.model.orEmpty(), { s -> vm.update { it.copy(model = s) } }, stringResource(R.string.field_model))
        AshTextField(f.regNumber.orEmpty(), { s -> vm.update { it.copy(regNumber = s.uppercase()) } }, stringResource(R.string.field_reg), capitalization = KeyboardCapitalization.Characters)
        AshTextField(f.fuelType.orEmpty(), { s -> vm.update { it.copy(fuelType = s) } }, stringResource(R.string.field_fuel))
        DateField(f.purchasedAt, { d -> vm.update { it.copy(purchasedAt = d) } }, stringResource(R.string.field_purchased), deadline = false)
        DateField(f.pucExpiresAt, { d -> vm.update { it.copy(pucExpiresAt = d) } }, stringResource(R.string.field_puc_expiry))
        DateField(f.lastServiceAt, { d -> vm.update { it.copy(lastServiceAt = d) } }, stringResource(R.string.field_last_service), deadline = false)
        DateField(f.nextServiceAt, { d -> vm.update { it.copy(nextServiceAt = d) } }, stringResource(R.string.field_next_service))
        DateField(f.warrantyExpiresAt, { d -> vm.update { it.copy(warrantyExpiresAt = d) } }, stringResource(R.string.field_warranty_expiry))
        MoneyField(emi, { vm.emi.value = it }, stringResource(R.string.field_emi))
        AshTextField(f.notes.orEmpty(), { s -> vm.update { it.copy(notes = s) } }, stringResource(R.string.field_notes), singleLine = false, minLines = 2)
        ProfilePicker(f.profileId) { id -> vm.update { it.copy(profileId = id) } }
    }
}

// ---------- Insurance policy ----------

@HiltViewModel
class PolicyEditViewModel @Inject constructor(savedState: SavedStateHandle, private val life: LifeRepository) : ViewModel() {
    private val route = savedState.toRoute<PolicyEditRoute>()
    val form = MutableStateFlow<InsurancePolicy?>(null)
    val premium = MutableStateFlow("")
    val vehicles = life.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val p = if (route.id != 0L) life.getPolicy(route.id) else null
            form.value = p ?: InsurancePolicy(
                insurer = "",
                kind = if (route.vehicleId != 0L) InsuranceKind.VEHICLE else InsuranceKind.OTHER,
                vehicleId = route.vehicleId.takeIf { it != 0L },
            )
            premium.value = Money.toInput(p?.premiumPaise)
        }
    }

    fun update(t: (InsurancePolicy) -> InsurancePolicy) = form.update { it?.let(t) }
    fun save(done: () -> Unit) = viewModelScope.launch {
        form.value?.let { life.savePolicy(it.copy(premiumPaise = Money.parseInput(premium.value))) }
        done()
    }
    fun delete(done: () -> Unit) = viewModelScope.launch { life.deletePolicy(route.id); done() }
    val isNew get() = route.id == 0L
}

@Composable
fun PolicyEditScreen(onBack: () -> Unit) {
    val vm: PolicyEditViewModel = hiltViewModel()
    val p by vm.form.collectAsStateWithLifecycle()
    val premium by vm.premium.collectAsStateWithLifecycle()
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val f = p ?: return
    EditorShell(
        title = stringResource(if (vm.isNew) R.string.policy_add else R.string.policy_edit),
        canSave = f.insurer.isNotBlank(),
        onBack = onBack,
        onSave = { vm.save(onBack) },
        onDelete = if (vm.isNew) null else ({ vm.delete(onBack) }),
        deleteTitle = stringResource(R.string.policy_delete),
    ) {
        AshTextField(f.insurer, { s -> vm.update { it.copy(insurer = s) } }, stringResource(R.string.field_insurer))
        OptionField(stringResource(R.string.field_policy_kind), InsuranceKind.entries, f.kind, { insuranceKindLabel(it) }, { k -> vm.update { it.copy(kind = k) } })
        AshTextField(f.policyNumber.orEmpty(), { s -> vm.update { it.copy(policyNumber = s) } }, stringResource(R.string.field_policy_number), capitalization = KeyboardCapitalization.Characters)
        DateField(f.startAt, { d -> vm.update { it.copy(startAt = d) } }, stringResource(R.string.field_start), deadline = false)
        DateField(f.expiresAt, { d -> vm.update { it.copy(expiresAt = d) } }, stringResource(R.string.field_expires))
        MoneyField(premium, { vm.premium.value = it }, stringResource(R.string.field_premium))
        if (vehicles.isNotEmpty()) {
            val none = stringResource(R.string.profile_none)
            OptionField(stringResource(R.string.field_vehicle), listOf<Vehicle?>(null) + vehicles, vehicles.firstOrNull { it.id == f.vehicleId }, { it?.name ?: none }, { v -> vm.update { it.copy(vehicleId = v?.id) } })
        }
        AshTextField(f.notes.orEmpty(), { s -> vm.update { it.copy(notes = s) } }, stringResource(R.string.field_notes), singleLine = false, minLines = 2)
        ProfilePicker(f.profileId) { id -> vm.update { it.copy(profileId = id) } }
    }
}

// ---------- Warranty ----------

@HiltViewModel
class WarrantyEditViewModel @Inject constructor(savedState: SavedStateHandle, private val life: LifeRepository) : ViewModel() {
    private val id = savedState.toRoute<WarrantyEditRoute>().id
    val form = MutableStateFlow<Warranty?>(null)
    val vehicles = life.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            form.value = (if (id != 0L) life.getWarranty(id) else null) ?: Warranty(product = "", expiresAt = 0)
        }
    }

    fun update(t: (Warranty) -> Warranty) = form.update { it?.let(t) }
    fun save(done: () -> Unit) = viewModelScope.launch { form.value?.let { life.saveWarranty(it) }; done() }
    fun delete(done: () -> Unit) = viewModelScope.launch { life.deleteWarranty(id); done() }
    val isNew get() = id == 0L
}

@Composable
fun WarrantyEditScreen(onBack: () -> Unit) {
    val vm: WarrantyEditViewModel = hiltViewModel()
    val w by vm.form.collectAsStateWithLifecycle()
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val f = w ?: return
    EditorShell(
        title = stringResource(if (vm.isNew) R.string.warranty_add else R.string.warranty_edit),
        canSave = f.product.isNotBlank() && f.expiresAt > 0,
        onBack = onBack,
        onSave = { vm.save(onBack) },
        onDelete = if (vm.isNew) null else ({ vm.delete(onBack) }),
        deleteTitle = stringResource(R.string.warranty_delete),
    ) {
        AshTextField(f.product, { s -> vm.update { it.copy(product = s) } }, stringResource(R.string.field_product))
        DateField(f.purchasedAt, { d -> vm.update { it.copy(purchasedAt = d) } }, stringResource(R.string.field_purchased), deadline = false)
        DateField(f.expiresAt.takeIf { it > 0 }, { d -> vm.update { it.copy(expiresAt = d ?: 0) } }, stringResource(R.string.field_warranty_expiry))
        if (vehicles.isNotEmpty()) {
            val none = stringResource(R.string.profile_none)
            OptionField(stringResource(R.string.field_vehicle), listOf<Vehicle?>(null) + vehicles, vehicles.firstOrNull { it.id == f.vehicleId }, { it?.name ?: none }, { v -> vm.update { it.copy(vehicleId = v?.id) } })
        }
        ProfilePicker(f.profileId) { id -> vm.update { it.copy(profileId = id) } }
    }
}

// ---------- Subscription ----------

@HiltViewModel
class SubscriptionEditViewModel @Inject constructor(savedState: SavedStateHandle, private val life: LifeRepository) : ViewModel() {
    private val id = savedState.toRoute<SubscriptionEditRoute>().id
    val form = MutableStateFlow<Subscription?>(null)
    val amount = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val s = if (id != 0L) life.getSubscription(id) else null
            form.value = s ?: Subscription(name = "", amountPaise = 0, period = RecurrencePeriod.MONTHLY)
            amount.value = Money.toInput(s?.amountPaise)
        }
    }

    fun update(t: (Subscription) -> Subscription) = form.update { it?.let(t) }
    fun save(done: () -> Unit) = viewModelScope.launch {
        val paise = Money.parseInput(amount.value) ?: return@launch
        form.value?.let { life.saveSubscription(it.copy(amountPaise = paise)) }
        done()
    }
    fun delete(done: () -> Unit) = viewModelScope.launch { life.deleteSubscription(id); done() }
    val isNew get() = id == 0L
}

@Composable
fun SubscriptionEditScreen(onBack: () -> Unit) {
    val vm: SubscriptionEditViewModel = hiltViewModel()
    val s by vm.form.collectAsStateWithLifecycle()
    val amount by vm.amount.collectAsStateWithLifecycle()
    val f = s ?: return
    EditorShell(
        title = stringResource(if (vm.isNew) R.string.sub_add else R.string.sub_edit),
        canSave = f.name.isNotBlank() && Money.parseInput(amount) != null,
        onBack = onBack,
        onSave = { vm.save(onBack) },
        onDelete = if (vm.isNew) null else ({ vm.delete(onBack) }),
        deleteTitle = stringResource(R.string.sub_delete),
    ) {
        AshTextField(f.name, { v -> vm.update { it.copy(name = v) } }, stringResource(R.string.field_name))
        MoneyField(amount, { vm.amount.value = it }, stringResource(R.string.field_amount))
        OptionField(stringResource(R.string.field_period), RecurrencePeriod.entries, f.period, { periodLabel(it) }, { p -> vm.update { it.copy(period = p) } })
        DateField(f.nextRenewalAt, { d -> vm.update { it.copy(nextRenewalAt = d) } }, stringResource(R.string.field_next_renewal))
        GroupedSection {
            SwitchRow(stringResource(R.string.field_active), f.active, { a -> vm.update { it.copy(active = a) } }, showDivider = false)
        }
        ProfilePicker(f.profileId) { id -> vm.update { it.copy(profileId = id) } }
    }
}
