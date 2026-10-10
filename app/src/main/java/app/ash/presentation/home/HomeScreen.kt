package app.ash.presentation.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MarkunreadMailbox
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ash.R
import app.ash.common.Dates
import app.ash.common.Money
import app.ash.designsystem.AshIcons
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.AshTheme
import app.ash.designsystem.AttentionCard
import app.ash.designsystem.EmptyState
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.MessageBanner
import app.ash.designsystem.RingGauge
import app.ash.designsystem.SectionHeader
import app.ash.designsystem.Sparkline
import app.ash.designsystem.Tone
import app.ash.designsystem.directionLabel
import app.ash.designsystem.pressScale
import app.ash.designsystem.relativeDay
import app.ash.domain.model.RecordType
import app.ash.domain.model.TxnDirection
import app.ash.domain.usecase.AttentionItem
import app.ash.domain.usecase.LifeOrderScore
import app.ash.navigation.BalanceRoute
import app.ash.navigation.InboxRoute
import app.ash.navigation.PermissionsRoute
import app.ash.navigation.RecordEditRoute
import app.ash.navigation.RecordRoute
import app.ash.navigation.ReminderEditRoute
import app.ash.navigation.SearchRoute
import app.ash.navigation.SettingsRoute
import app.ash.navigation.SourcesRoute
import app.ash.navigation.VaultRoute
import app.ash.presentation.common.attentionDetail
import app.ash.presentation.common.bucketLabel
import app.ash.presentation.common.route
import app.ash.presentation.common.scorePartLabel
import app.ash.presentation.common.tone
import app.ash.workspace.Workspace
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * Today: one glanceable dashboard. Life-in-order score, money left this month, what is due
 * today, the next 7 days, and anything waiting for confirmation, then recent activity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navigate: (Any) -> Unit) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val revealed by vm.moneyRevealed.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshPermissions()
        onPauseOrDispose { }
    }
    var explainItem by remember { mutableStateOf<AttentionItem?>(null) }
    var explainOrder by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    val feed = state.feed

    val timeGreeting = stringResource(
        when (state.greeting) {
            Greeting.MORNING -> R.string.greeting_morning
            Greeting.AFTERNOON -> R.string.greeting_afternoon
            Greeting.EVENING -> R.string.greeting_evening
        },
    )
    val greeting = if (vm.workspace == Workspace.REAL) stringResource(R.string.greeting_with_name, timeGreeting, vm.workspace.selfProfileName) else timeGreeting
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val todayLabel = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(LocalDate.now())

    AshScaffold(
        title = greeting,
        actions = {
            IconButton(onClick = { navigate(SearchRoute) }) { Icon(Icons.Rounded.Search, stringResource(R.string.search_title)) }
            IconButton(onClick = { navigate(InboxRoute) }) { Icon(AshIcons.Inbox, stringResource(R.string.inbox_open)) }
            IconButton(onClick = { navigate(SettingsRoute) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings_title)) }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { addMenu = true }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.action_add))
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_bill)) }, onClick = { addMenu = false; navigate(RecordEditRoute(type = RecordType.BILL.name)) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_transaction)) }, onClick = { addMenu = false; navigate(RecordEditRoute(type = RecordType.TRANSACTION.name)) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_appointment)) }, onClick = { addMenu = false; navigate(RecordEditRoute(type = RecordType.APPOINTMENT.name)) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_reminder)) }, onClick = { addMenu = false; navigate(ReminderEditRoute()) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.vault_add)) }, onClick = { addMenu = false; navigate(VaultRoute) })
                }
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 104.dp)) {
            item {
                Text(todayLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            if (vm.workspace == Workspace.DEMO) item { MessageBanner(stringResource(R.string.account_demo_banner), Tone.INFO) }

            item { LifeOrderCard(state.order, onClick = { explainOrder = true }) }

            item {
                val money = state.money
                if (money != null) MoneyCard(state, revealed, onClick = { navigate(BalanceRoute) })
                else if (vm.workspace == Workspace.REAL) MoneyPlaceholder(onClick = { navigate(SettingsRoute) })
            }

            if (state.showSetup) item { SetupCard(state.setupLeft, navigate, onDismiss = vm::dismissSetup) }

            if (feed.reviewCount > 0) {
                item {
                    MessageBanner(
                        pluralStringResource(R.plurals.home_review_count, feed.reviewCount, feed.reviewCount),
                        Tone.SOON,
                        actionLabel = stringResource(R.string.action_review),
                        onAction = { navigate(InboxRoute) },
                    )
                }
            }

            if (feed.today.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.today_section)) }
                items(feed.today, key = { "t" + it.key }) { item ->
                    AttentionCard(
                        title = item.title,
                        detail = attentionDetail(item),
                        label = bucketLabel(item.result.bucket),
                        tone = item.result.bucket.tone(),
                        icon = AshIcons.category(item.category),
                        onClick = { navigate(item.target.route()) },
                        onLongClick = { explainItem = item },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).animateItem(),
                    )
                }
            }

            item { WeekStrip(feed.week, navigate) { explainItem = it } }

            if (!state.loading && feed.isEmpty && !state.showSetup) {
                item { EmptyState(Icons.Rounded.TaskAlt, stringResource(R.string.empty_attention_title), stringResource(R.string.empty_home_body)) }
            }

            if (feed.recentTransactions.isNotEmpty()) {
                item {
                    GroupedSection(header = stringResource(R.string.home_recent_transactions)) {
                        feed.recentTransactions.take(4).forEachIndexed { i, t ->
                            val sign = when (t.direction) { TxnDirection.EXPENSE -> "−"; TxnDirection.INCOME, TxnDirection.REFUND -> "+"; else -> "" }
                            AshRow(
                                title = t.merchant ?: t.sourceLabel ?: directionLabel(t.direction),
                                subtitle = relativeDay(t.occurredAt),
                                icon = AshIcons.category(t.category),
                                showDivider = i < minOf(3, feed.recentTransactions.lastIndex),
                                onClick = { navigate(RecordRoute(t.recordId)) },
                                trailing = {
                                    Text(
                                        sign + Money.format(t.amountPaise),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (sign == "+") AshTheme.status.ok else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (explainOrder) {
        ExplainDialog(onDismiss = { explainOrder = false }) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.order_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.order_explain), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (state.order.parts.isEmpty()) {
                    Text(stringResource(R.string.order_all_clear), style = MaterialTheme.typography.bodyLarge)
                }
                state.order.parts.forEach { part ->
                    AshRow(
                        title = orderReasonText(part.reason, part.count),
                        trailing = { Text("−${part.points}", style = MaterialTheme.typography.titleMedium, color = AshTheme.status.urgent) },
                    )
                }
                AshRow(stringResource(R.string.why_total), showDivider = false, trailing = { Text("${state.order.score}/100", style = MaterialTheme.typography.titleMedium) })
            }
        }
    }

    explainItem?.let { item ->
        ExplainDialog(onDismiss = { explainItem = null }) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.why_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(item.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                item.result.parts.forEach { part ->
                    AshRow(title = scorePartLabel(part.part), trailing = { Text("+${part.points}", style = MaterialTheme.typography.titleMedium) })
                }
                AshRow(
                    title = stringResource(R.string.why_total),
                    showDivider = false,
                    trailing = { Text("${item.result.score} · ${bucketLabel(item.result.bucket)}", style = MaterialTheme.typography.titleMedium) },
                )
                Text(stringResource(R.string.why_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun orderReasonText(reason: LifeOrderScore.Reason, count: Int): String = pluralStringResource(
    when (reason) {
        LifeOrderScore.Reason.OVERDUE -> R.plurals.order_overdue
        LifeOrderScore.Reason.EXPIRED -> R.plurals.order_expired
        LifeOrderScore.Reason.DUE_SOON -> R.plurals.order_due_soon
        LifeOrderScore.Reason.EXPIRING_SOON -> R.plurals.order_expiring
        LifeOrderScore.Reason.TO_REVIEW -> R.plurals.order_review
        LifeOrderScore.Reason.SETUP -> R.plurals.order_setup
    },
    count,
    count,
)

@Composable
private fun HeroCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).pressScale(interaction),
    ) { Box(Modifier.padding(18.dp)) { content() } }
}

@Composable
private fun LifeOrderCard(order: LifeOrderScore.Result, onClick: () -> Unit) {
    val status = AshTheme.status
    val (color, levelText) = when (order.level) {
        LifeOrderScore.Level.CALM -> status.ok to stringResource(R.string.order_calm)
        LifeOrderScore.Level.ATTENTION -> status.soon to stringResource(R.string.order_attention)
        LifeOrderScore.Level.BUSY -> status.urgent to stringResource(R.string.order_busy)
    }
    val top = order.parts.firstOrNull()
    val description = stringResource(R.string.order_cd, order.score, levelText)
    HeroCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(order.score / 100f, color, description, size = 96.dp, stroke = 10.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${order.score}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("/100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.order_title).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(levelText, style = MaterialTheme.typography.titleLarge)
                Text(
                    top?.let { orderReasonText(it.reason, it.count) } ?: stringResource(R.string.order_all_clear),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoneyCard(state: HomeUiState, revealed: Boolean, onClick: () -> Unit) {
    val s = state.money ?: return
    val status = AshTheme.status
    val base = (s.opening + s.received).coerceAtLeast(1)
    val leftFraction = (s.remaining.toFloat() / base).coerceIn(0f, 1f)
    val color = when {
        s.remaining < 0 -> status.urgent
        leftFraction < 0.15f -> status.urgent
        leftFraction < 0.35f -> status.soon
        else -> MaterialTheme.colorScheme.primary
    }
    HeroCard(onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (s.salary != null) R.string.money_left_cycle else R.string.money_this_month).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (revealed) Money.format(s.remaining) else "₹ • • • •", style = MaterialTheme.typography.displaySmall, color = if (s.remaining < 0) status.urgent else MaterialTheme.colorScheme.onSurface)
                    val sub = when {
                        !revealed -> stringResource(R.string.money_hidden)
                        s.salary != null && s.perDay != null && s.daysLeft != null ->
                            stringResource(R.string.money_per_day_line, Money.format(s.perDay), pluralStringResource(R.plurals.statement_days_left, s.daysLeft, s.daysLeft))
                        s.salary != null -> stringResource(R.string.statement_of_salary, Money.format(s.opening))
                        else -> stringResource(R.string.money_spent_line, Money.format(s.spent))
                    }
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (s.salary != null) {
                    RingGauge(leftFraction, color, stringResource(R.string.money_ring_cd, (leftFraction * 100).toInt()), size = 64.dp, stroke = 8.dp) {
                        Text("${(leftFraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (state.balanceTrend.size >= 2) {
                Spacer(Modifier.height(12.dp))
                Sparkline(state.balanceTrend, color, stringResource(R.string.money_trend_cd), Modifier.fillMaxWidth().height(48.dp))
            }
        }
    }
}

@Composable
private fun MoneyPlaceholder(onClick: () -> Unit) {
    HeroCard(onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.money_left_cycle).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.money_placeholder_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.money_placeholder_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Guided setup until the real account is connected (notifications, apps, SMS history, a document, lock). */
@Composable
private fun SetupCard(left: List<SetupStep>, navigate: (Any) -> Unit, onDismiss: () -> Unit) {
    val total = SetupStep.entries.size
    val done = total - left.size
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.setup_progress, done, total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_hide)) }
            }
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            SetupStep.entries.forEach { step ->
                val isDone = step !in left
                val (icon, title, route) = when (step) {
                    SetupStep.NOTIFICATIONS -> Triple<ImageVector, Int, Any>(Icons.Rounded.MarkunreadMailbox, R.string.setup_notifications, PermissionsRoute)
                    SetupStep.SOURCES -> Triple<ImageVector, Int, Any>(Icons.Rounded.Apps, R.string.setup_sources, SourcesRoute())
                    SetupStep.SMS_HISTORY -> Triple<ImageVector, Int, Any>(Icons.Rounded.Sms, R.string.setup_sms, SettingsRoute)
                    SetupStep.DOCUMENT -> Triple<ImageVector, Int, Any>(Icons.Rounded.Description, R.string.setup_document, VaultRoute)
                    SetupStep.APP_LOCK -> Triple<ImageVector, Int, Any>(Icons.Rounded.Fingerprint, R.string.setup_lock, SettingsRoute)
                }
                SetupRow(icon, stringResource(title), isDone) { if (!isDone) navigate(route) }
            }
        }
    }
}

@Composable
private fun SetupRow(icon: ImageVector, title: String, done: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = !done, color = Color.Transparent, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = stringResource(if (done) R.string.setup_done else R.string.setup_todo),
                tint = if (done) AshTheme.status.ok else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (done) 0.55f else 1f),
            )
        }
    }
}

/** Next 7 days at a glance: a row of days with markers, and the selected day's items below. */
@Composable
private fun WeekStrip(week: List<AttentionItem>, navigate: (Any) -> Unit, onExplain: (AttentionItem) -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val days = (0L until 7L).map { today.plusDays(it) }
    val byDay = week.groupBy { Dates.toLocalDate(it.deadlineAt!!, zone) }
    val firstBusy = days.indexOfFirst { byDay[it].orEmpty().isNotEmpty() }.takeIf { it >= 0 } ?: 0
    var selected by rememberSaveable { mutableStateOf(firstBusy) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val status = AshTheme.status

    SectionHeader(stringResource(R.string.week_section))
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, day ->
            val items = byDay[day].orEmpty()
            val isSelected = i == selected
            val worst = items.maxOfOrNull { it.result.score } ?: 0
            val dot = when {
                items.isEmpty() -> Color.Transparent
                worst >= 60 -> status.urgent
                worst >= 25 -> status.soon
                else -> status.info
            }
            val dayName = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            val cd = pluralStringResource(R.plurals.week_day_cd, items.size, dayName, day.dayOfMonth, items.size)
            Surface(
                onClick = { selected = i },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) MaterialTheme.colorScheme.inverseSurface else Color.Transparent,
                modifier = Modifier.width(46.dp).semantics(mergeDescendants = true) { contentDescription = cd },
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val fg = if (isSelected) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface
                    Text(if (i == 0) stringResource(R.string.today_short) else dayName, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f), maxLines = 1)
                    Text("${day.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = fg)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
                }
            }
        }
    }
    val dayItems = byDay[days[selected]].orEmpty()
    if (dayItems.isEmpty()) {
        Text(
            stringResource(R.string.week_free_day),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
    } else {
        Column(Modifier.padding(top = 6.dp)) {
            dayItems.forEach { item ->
                AttentionCard(
                    title = item.title,
                    detail = attentionDetail(item),
                    label = bucketLabel(item.result.bucket),
                    tone = item.result.bucket.tone(),
                    icon = AshIcons.category(item.category),
                    onClick = { navigate(item.target.route()) },
                    onLongClick = { onExplain(item) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }
        }
    }
}


/**
 * Explanations open in a dialog, not a ModalBottomSheet: Material3 1.4.0's sheet can place itself
 * before its anchors exist ("offset was read before being initialized"), which crashed the app on the Pixel 7.
 */
@Composable
private fun ExplainDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column {
                content()
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 8.dp)) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }
}
