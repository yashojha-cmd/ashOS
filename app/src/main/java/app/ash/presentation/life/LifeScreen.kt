package app.ash.presentation.life

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ash.R
import app.ash.common.Money
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.GroupedSection
import app.ash.domain.repository.LifeRepository
import app.ash.domain.repository.ProfileRepository
import app.ash.domain.repository.ReminderRepository
import app.ash.intelligence.SubscriptionDetector
import app.ash.navigation.MoneyRoute
import app.ash.navigation.PoliciesRoute
import app.ash.navigation.ProfilesRoute
import app.ash.navigation.RemindersRoute
import app.ash.navigation.VehiclesRoute
import app.ash.navigation.WarrantiesRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LifeSummary(
    val reminders: Int = 0,
    val vehicles: Int = 0,
    val policies: Int = 0,
    val warranties: Int = 0,
    val profiles: Int = 0,
    val monthlyRecurring: Long = 0,
)

@HiltViewModel
class LifeViewModel @Inject constructor(life: LifeRepository, reminders: ReminderRepository, profiles: ProfileRepository) : ViewModel() {
    val summary = combine(
        reminders.observePendingCount(),
        life.observeVehicles(),
        life.observePolicies(),
        life.observeWarranties(),
        combine(profiles.observeProfiles(), life.observeSubscriptions()) { p, s -> p to s },
    ) { r, v, p, w, ps ->
        LifeSummary(r, v.size, p.size, w.size, ps.first.size,
            SubscriptionDetector.monthlyTotal(ps.second.filter { it.active }.map { it.amountPaise to it.period }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LifeSummary())
}

/** The Life graph hub (§10): reminders, money, vehicles, insurance, warranties, people. */
@Composable
fun LifeScreen(navigate: (Any) -> Unit) {
    val vm: LifeViewModel = hiltViewModel()
    val s by vm.summary.collectAsStateWithLifecycle()
    AshScaffold(title = stringResource(R.string.life_title)) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            item {
                GroupedSection {
                    AshRow(stringResource(R.string.reminders_title), subtitle = pluralStringResource(R.plurals.life_reminders, s.reminders, s.reminders),
                        icon = Icons.Rounded.Notifications, showChevron = true, onClick = { navigate(RemindersRoute) })
                    AshRow(stringResource(R.string.money_title),
                        subtitle = if (s.monthlyRecurring > 0) stringResource(R.string.home_recurring, Money.format(s.monthlyRecurring)) else stringResource(R.string.life_money_desc),
                        icon = Icons.Rounded.AccountBalanceWallet, showChevron = true, showDivider = false, onClick = { navigate(MoneyRoute) })
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.life_things)) {
                    AshRow(stringResource(R.string.vehicles_title), subtitle = pluralStringResource(R.plurals.life_count, s.vehicles, s.vehicles),
                        icon = Icons.Rounded.DirectionsCar, showChevron = true, onClick = { navigate(VehiclesRoute) })
                    AshRow(stringResource(R.string.policies_title), subtitle = pluralStringResource(R.plurals.life_count, s.policies, s.policies),
                        icon = Icons.Rounded.Shield, showChevron = true, onClick = { navigate(PoliciesRoute) })
                    AshRow(stringResource(R.string.warranties_title), subtitle = pluralStringResource(R.plurals.life_count, s.warranties, s.warranties),
                        icon = Icons.Rounded.VerifiedUser, showChevron = true, showDivider = false, onClick = { navigate(WarrantiesRoute) })
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.life_people), footer = stringResource(R.string.profiles_footer)) {
                    AshRow(stringResource(R.string.profiles_title), subtitle = pluralStringResource(R.plurals.life_count, s.profiles, s.profiles),
                        icon = Icons.Rounded.Group, showChevron = true, showDivider = false, onClick = { navigate(ProfilesRoute) })
                }
            }
        }
    }
}
