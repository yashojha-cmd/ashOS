package app.ash.presentation.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ash.R
import app.ash.common.Dates
import app.ash.common.Money
import app.ash.common.TimeProvider
import app.ash.designsystem.AshIcons
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.EmptyState
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.SecureScreen
import app.ash.designsystem.directionLabel
import app.ash.designsystem.dueText
import app.ash.designsystem.periodLabel
import app.ash.designsystem.relativeDay
import app.ash.domain.model.TxnDirection
import app.ash.domain.repository.LifeRepository
import app.ash.domain.repository.MoneySummary
import app.ash.domain.repository.RecordRepository
import app.ash.intelligence.SubscriptionDetector
import app.ash.navigation.RecordEditRoute
import app.ash.navigation.RecordRoute
import app.ash.navigation.SubscriptionEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoneyViewModel @Inject constructor(
    private val records: RecordRepository,
    private val life: LifeRepository,
    time: TimeProvider,
) : ViewModel() {
    private val monthStart = time.today().withDayOfMonth(1)
    val transactions = records.observeTransactions(300).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val bills = records.observeBills(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val subscriptions = life.observeSubscriptions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val month = records.observeMonthSummary(Dates.startOfDay(monthStart, time.zone()), Dates.startOfDay(monthStart.plusMonths(1), time.zone()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneySummary(0, 0, 0))

    fun setPaid(recordId: Long, paid: Boolean) = viewModelScope.launch { records.setBillPaid(recordId, paid) }
    fun detect() = viewModelScope.launch { life.detectSubscriptions() }
}

/** Transactions, bills and subscriptions (§16, §17). Protected from screenshots. */
@Composable
fun MoneyScreen(onBack: () -> Unit, navigate: (Any) -> Unit) {
    SecureScreen()
    val vm: MoneyViewModel = hiltViewModel()
    val txns by vm.transactions.collectAsStateWithLifecycle()
    val bills by vm.bills.collectAsStateWithLifecycle()
    val subs by vm.subscriptions.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    AshScaffold(
        title = stringResource(R.string.money_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                when (tab) {
                    0 -> navigate(RecordEditRoute(type = "TRANSACTION"))
                    1 -> navigate(RecordEditRoute(type = "BILL"))
                    else -> navigate(SubscriptionEditRoute())
                }
            }) { Icon(Icons.Rounded.Add, stringResource(R.string.action_add)) }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryTile(stringResource(R.string.money_spent_month), Money.format(month.expensePaise), Modifier.weight(1f))
                    SummaryTile(stringResource(R.string.money_received_month), Money.format(month.incomePaise + month.refundPaise), Modifier.weight(1f))
                }
            }
            item {
                SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.money_tab_transactions)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.money_tab_bills)) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.money_tab_subscriptions)) })
                }
            }
            when (tab) {
                0 -> {
                    val list = txns
                    if (list != null && list.isEmpty()) item { EmptyState(Icons.Rounded.SwapVert, stringResource(R.string.empty_txn_title), stringResource(R.string.empty_txn_body)) }
                    if (!list.isNullOrEmpty()) item {
                        GroupedSection {
                            list.forEachIndexed { i, t ->
                                val sign = when (t.direction) { TxnDirection.EXPENSE -> "−"; TxnDirection.INCOME, TxnDirection.REFUND -> "+"; else -> "" }
                                AshRow(
                                    title = t.merchant ?: t.sourceLabel ?: directionLabel(t.direction),
                                    subtitle = buildString {
                                        append(directionLabel(t.direction)); append(" · "); append(relativeDay(t.occurredAt))
                                        t.accountRef?.let { append(" · "); append(it) }
                                        if (t.userCorrected) { append(" · "); append(stringResource(R.string.txn_corrected)) }
                                    },
                                    icon = AshIcons.category(t.category),
                                    showDivider = i < list.lastIndex,
                                    onClick = { navigate(RecordRoute(t.recordId)) },
                                    trailing = { Text(sign + Money.format(t.amountPaise), style = MaterialTheme.typography.titleSmall) },
                                )
                            }
                        }
                    }
                }
                1 -> {
                    val list = bills
                    if (list != null && list.isEmpty()) item { EmptyState(Icons.Rounded.ReceiptLong, stringResource(R.string.empty_bills_title), stringResource(R.string.empty_bills_body)) }
                    if (!list.isNullOrEmpty()) item {
                        GroupedSection {
                            list.forEachIndexed { i, b ->
                                AshRow(
                                    title = b.biller,
                                    subtitle = listOfNotNull(b.amountPaise?.let { Money.format(it) }, if (b.paidAt != null) stringResource(R.string.bill_paid) else dueText(b.dueAt)).joinToString(" · "),
                                    icon = AshIcons.category(app.ash.domain.model.Category.BILLS),
                                    showDivider = i < list.lastIndex,
                                    onClick = { navigate(RecordRoute(b.recordId)) },
                                    trailing = { Checkbox(checked = b.paidAt != null, onCheckedChange = { vm.setPaid(b.recordId, it) }) },
                                )
                            }
                        }
                    }
                }
                else -> {
                    val list = subs
                    item {
                        val total = SubscriptionDetector.monthlyTotal(list.orEmpty().filter { it.active }.map { it.amountPaise to it.period })
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text(stringResource(R.string.home_recurring, Money.format(total)), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.money_yearly, Money.format(total * 12)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = vm::detect) { Text(stringResource(R.string.money_detect)) }
                        }
                    }
                    if (list != null && list.isEmpty()) item { EmptyState(Icons.Rounded.Autorenew, stringResource(R.string.empty_subs_title), stringResource(R.string.empty_subs_body)) }
                    if (!list.isNullOrEmpty()) item {
                        GroupedSection {
                            list.forEachIndexed { i, s ->
                                AshRow(
                                    title = s.name,
                                    subtitle = listOfNotNull(
                                        periodLabel(s.period),
                                        s.nextRenewalAt?.let { stringResource(R.string.renews_on, relativeDay(it)) },
                                        if (s.autoDetected) stringResource(R.string.sub_detected) else null,
                                        if (!s.active) stringResource(R.string.sub_inactive) else null,
                                    ).joinToString(" · "),
                                    icon = AshIcons.category(app.ash.domain.model.Category.SUBSCRIPTIONS),
                                    showDivider = i < list.lastIndex,
                                    onClick = { navigate(SubscriptionEditRoute(s.id)) },
                                    trailing = { Text(Money.format(s.amountPaise), style = MaterialTheme.typography.titleSmall) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
