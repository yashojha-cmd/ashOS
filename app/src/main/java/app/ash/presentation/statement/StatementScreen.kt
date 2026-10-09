package app.ash.presentation.statement

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ash.R
import app.ash.common.Money
import app.ash.common.TimeProvider
import app.ash.designsystem.AshFormat
import app.ash.designsystem.AshIcons
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.AshTheme
import app.ash.designsystem.EmptyState
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.MessageBanner
import app.ash.designsystem.SecureScreen
import app.ash.designsystem.Tone
import app.ash.designsystem.formatDate
import app.ash.domain.model.TxnDirection
import app.ash.domain.repository.RecordRepository
import app.ash.domain.usecase.Statement
import app.ash.domain.usecase.StatementCalculator
import app.ash.domain.usecase.StatementLine
import app.ash.navigation.RecordRoute
import app.ash.presentation.common.SensitiveGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatementViewModel @Inject constructor(
    records: RecordRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val cycle = MutableStateFlow(0)

    /** About 13 months of history: enough for a year of statements plus the cycle in progress. */
    val statement = combine(records.observeTransactionsSince(time.nowMillis() - 400L * 24 * 60 * 60 * 1000), cycle) { txns, index ->
        StatementCalculator.compute(txns, index, time.nowMillis(), time.zone())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun older() { cycle.value = cycle.value + 1 }
    fun newer() { cycle.value = (cycle.value - 1).coerceAtLeast(0) }
}

/** Balance tab: salary-to-salary statement with a running balance (behind fingerprint/PIN). */
@Composable
fun StatementScreen(navigate: (Any) -> Unit) {
    SensitiveGate(required = true, title = stringResource(R.string.statement_title), onBack = {}) {
        StatementContent(navigate)
    }
}

@Composable
private fun StatementContent(navigate: (Any) -> Unit) {
    SecureScreen()
    val vm: StatementViewModel = hiltViewModel()
    val s by vm.statement.collectAsStateWithLifecycle()
    AshScaffold(title = stringResource(R.string.statement_title)) { padding ->
        val st = s ?: return@AshScaffold
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item { CycleSwitcher(st, vm::older, vm::newer) }
            item { BalanceCard(st) }
            if (st.salary == null) {
                item { MessageBanner(stringResource(R.string.statement_no_salary), Tone.INFO) }
            }
            st.bankReported?.let { t ->
                item {
                    GroupedSection {
                        app.ash.designsystem.AshRow(
                            title = stringResource(R.string.statement_bank_says, Money.format(t.balanceAfterPaise ?: 0)),
                            subtitle = listOfNotNull(t.accountRef, t.sourceLabel, AshFormat.dateTime(t.occurredAt)).joinToString(" · "),
                            icon = Icons.Rounded.AccountBalance,
                            showDivider = false,
                        )
                    }
                }
            }
            if (st.byCategory.isNotEmpty()) item { WhereItWent(st) }
            if (st.changes.isNotEmpty()) item { Changes(st) }
            if (st.topMerchants.isNotEmpty()) item { TopPayees(st) }
            if (st.lines.isEmpty()) {
                item { EmptyState(AshIcons.category(app.ash.domain.model.Category.FINANCE), stringResource(R.string.empty_txn_title), stringResource(R.string.statement_empty_body)) }
            } else {
                item {
                    Text(
                        stringResource(R.string.statement_lines).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 6.dp),
                    )
                }
                // Newest first, like a bank app; each line keeps the running balance after it.
                val newestFirst = st.lines.asReversed()
                items(newestFirst, key = { it.txn.recordId }) { line ->
                    StatementRow(line, isOpening = line.txn.recordId == st.salary?.recordId, last = line === newestFirst.last()) {
                        navigate(RecordRoute(line.txn.recordId))
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleSwitcher(st: Statement, older: () -> Unit, newer: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = older, enabled = st.salary != null && st.cycleIndex < st.cycleCount - 1) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.statement_older))
        }
        Text(
            text = when {
                st.salary == null -> stringResource(R.string.statement_this_month)
                st.endAt == null -> stringResource(R.string.statement_since, formatDate(st.startAt))
                else -> stringResource(R.string.statement_range, formatDate(st.startAt), formatDate(st.endAt - 1))
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = newer, enabled = st.cycleIndex > 0) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.statement_newer))
        }
    }
}

@Composable
private fun BalanceCard(st: Statement) {
    val status = AshTheme.status
    val usedFraction = if (st.opening + st.received > 0) (st.spent.toFloat() / (st.opening + st.received)).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(usedFraction, label = "spent")
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(20.dp).semantics(mergeDescendants = true) {}) {
            Text(stringResource(R.string.statement_remaining), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Money.format(st.remaining),
                style = MaterialTheme.typography.displaySmall,
                color = if (st.remaining < 0) status.urgent else MaterialTheme.colorScheme.onSurface,
            )
            if (st.salary != null) {
                Text(stringResource(R.string.statement_of_salary, Money.format(st.opening)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = if (usedFraction > 0.9f) status.urgent else if (usedFraction > 0.7f) status.soon else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Figure(stringResource(R.string.statement_spent), "−" + Money.format(st.spent), Modifier.weight(1f))
                Figure(stringResource(R.string.statement_received), "+" + Money.format(st.received), Modifier.weight(1f))
                if (st.daysLeft != null && st.perDay != null) {
                    Figure(
                        pluralString(R.plurals.statement_days_left, st.daysLeft),
                        stringResource(R.string.statement_per_day, Money.format(st.perDay)),
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WhereItWent(st: Statement) {
    val top = st.byCategory.take(5)
    val max = top.maxOf { it.second }.coerceAtLeast(1)
    GroupedSection(header = stringResource(R.string.statement_where)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            top.forEach { (category, amount) ->
                val label = stringResource(AshIcons.categoryLabel(category))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label ${Money.format(amount)}" }) {
                    Icon(AshIcons.category(category), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(Money.format(amount), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                            Box(Modifier.fillMaxWidth(amount.toFloat() / max).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementRow(line: StatementLine, isOpening: Boolean, last: Boolean, onClick: () -> Unit) {
    val t = line.txn
    val status = AshTheme.status
    val counted = line.balanceAfter != null
    val (sign, color) = when {
        isOpening -> "+" to status.ok
        t.direction == TxnDirection.EXPENSE -> "−" to MaterialTheme.colorScheme.onSurface
        t.direction == TxnDirection.INCOME || t.direction == TxnDirection.REFUND -> "+" to status.ok
        else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val title = when {
        isOpening -> stringResource(R.string.statement_salary_line, t.merchant ?: stringResource(R.string.statement_salary))
        else -> t.merchant ?: t.sourceLabel ?: app.ash.designsystem.directionLabel(t.direction)
    }
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AshIcons.category(t.category), null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isOpening) FontWeight.SemiBold else FontWeight.Normal)
                    Text(
                        listOfNotNull(AshFormat.dateTime(t.occurredAt), t.accountRef, stringResource(R.string.statement_not_counted).takeIf { !counted }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(sign + Money.format(t.amountPaise), style = MaterialTheme.typography.titleSmall, color = color)
                    line.balanceAfter?.let {
                        Text(stringResource(R.string.statement_bal, Money.format(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!last) HorizontalDivider(color = status.separator, modifier = Modifier.padding(start = 52.dp))
        }
    }
}

@Composable
private fun pluralString(id: Int, count: Int): String = androidx.compose.ui.res.pluralStringResource(id, count, count)

/** "Compared with last cycle": direction is spelled out in text, not shown by colour alone. */
@Composable
private fun Changes(st: Statement) {
    val status = AshTheme.status
    GroupedSection(header = stringResource(R.string.statement_compared)) {
        st.changes.forEachIndexed { i, c ->
            val up = c.current > c.previous
            val label = stringResource(AshIcons.categoryLabel(c.category))
            val detail = when {
                c.percent == null -> stringResource(R.string.statement_new_spend, Money.format(c.current))
                up -> stringResource(R.string.statement_up, c.percent!!, Money.format(c.previous), Money.format(c.current))
                else -> stringResource(R.string.statement_down, -c.percent!!, Money.format(c.previous), Money.format(c.current))
            }
            app.ash.designsystem.AshRow(
                title = label,
                subtitle = detail,
                icon = AshIcons.category(c.category),
                iconTint = if (up) status.urgent else status.ok,
                iconBackground = if (up) status.urgentContainer else status.okContainer,
                showDivider = i < st.changes.lastIndex,
            )
        }
    }
}

@Composable
private fun TopPayees(st: Statement) {
    GroupedSection(header = stringResource(R.string.statement_top_payees)) {
        st.topMerchants.forEachIndexed { i, m ->
            app.ash.designsystem.AshRow(
                title = m.merchant,
                subtitle = pluralString(R.plurals.statement_payments, m.count),
                showDivider = i < st.topMerchants.lastIndex,
                trailing = { Text(Money.format(m.total), style = MaterialTheme.typography.titleSmall) },
            )
        }
    }
}
