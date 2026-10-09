package app.ash.presentation.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ash.R
import app.ash.common.Money
import app.ash.designsystem.AshIcons
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.AttentionCard
import app.ash.designsystem.EmptyState
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.MessageBanner
import app.ash.designsystem.SectionHeader
import app.ash.designsystem.Tone
import app.ash.designsystem.directionLabel
import app.ash.designsystem.dueText
import app.ash.designsystem.expiresText
import app.ash.designsystem.relativeDay
import app.ash.domain.model.RecordType
import app.ash.domain.model.TxnDirection
import app.ash.domain.usecase.AttentionItem
import app.ash.navigation.DocumentRoute
import app.ash.navigation.InboxRoute
import app.ash.navigation.MoneyRoute
import app.ash.navigation.PermissionsRoute
import app.ash.navigation.RecordEditRoute
import app.ash.navigation.RecordRoute
import app.ash.navigation.ReminderEditRoute
import app.ash.navigation.RemindersRoute
import app.ash.navigation.SettingsRoute
import app.ash.navigation.SourcesRoute
import app.ash.presentation.common.attentionDetail
import app.ash.presentation.common.bucketLabel
import app.ash.presentation.common.route
import app.ash.presentation.common.scorePartLabel
import app.ash.presentation.common.tone

/** The command centre: answers "What should I care about right now?" at a glance (§5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navigate: (Any) -> Unit) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshPermissions()
        onPauseOrDispose { }
    }
    var explain by remember { mutableStateOf<AttentionItem?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    val feed = state.feed

    val greeting = stringResource(
        when (state.greeting) {
            Greeting.MORNING -> R.string.greeting_morning
            Greeting.AFTERNOON -> R.string.greeting_afternoon
            Greeting.EVENING -> R.string.greeting_evening
        },
    )

    AshScaffold(
        title = greeting,
        actions = {
            IconButton(onClick = { navigate(RemindersRoute) }) { Icon(AshIcons.category(app.ash.domain.model.Category.REMINDERS), stringResource(R.string.reminders_title)) }
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
                }
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp), modifier = Modifier.animateContentSize()) {
            item {
                Text(
                    stringResource(R.string.home_question),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (!state.listenerEnabled) {
                item {
                    MessageBanner(stringResource(R.string.error_listener_disabled), Tone.SOON, actionLabel = stringResource(R.string.action_turn_on), onAction = { navigate(PermissionsRoute) })
                }
            } else if (state.sourcesEnabled == 0) {
                item {
                    MessageBanner(stringResource(R.string.home_no_sources), Tone.INFO, actionLabel = stringResource(R.string.action_choose), onAction = { navigate(SourcesRoute()) })
                }
            }
            if (feed.reviewCount > 0) {
                item {
                    MessageBanner(
                        pluralString(R.plurals.home_review_count, feed.reviewCount),
                        Tone.INFO,
                        actionLabel = stringResource(R.string.action_review),
                        onAction = { navigate(InboxRoute) },
                    )
                }
            }

            if (!state.loading && feed.needsAttention.isEmpty() && feed.comingUp.isEmpty() && feed.recentlyHandled.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.TaskAlt,
                        stringResource(R.string.empty_attention_title),
                        stringResource(if (feed.isEmpty) R.string.empty_home_body else R.string.empty_attention_body),
                    )
                }
            }
            attentionSection(R.string.bucket_needs_attention, feed.needsAttention, navigate) { explain = it }
            attentionSection(R.string.bucket_coming_up, feed.comingUp, navigate) { explain = it }
            attentionSection(R.string.bucket_handled, feed.recentlyHandled, navigate) { explain = it }

            if (feed.upcomingPayments.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.home_upcoming_payments)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(feed.upcomingPayments, key = { it.key }) { item ->
                            Surface(
                                onClick = { navigate(item.target.route()) },
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.width(168.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(6.dp))
                                    Text(item.amountPaise?.let { Money.format(it) } ?: "", style = MaterialTheme.typography.titleLarge)
                                    Text(item.deadlineAt?.let { dueText(it) } ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            if (feed.monthlyRecurringPaise > 0) {
                item {
                    GroupedSection {
                        AshRow(
                            title = stringResource(R.string.home_recurring, Money.format(feed.monthlyRecurringPaise)),
                            icon = Icons.Rounded.Autorenew,
                            showDivider = false,
                            showChevron = true,
                            onClick = { navigate(MoneyRoute) },
                        )
                    }
                }
            }

            if (feed.expiringDocuments.isNotEmpty()) {
                item {
                    GroupedSection(header = stringResource(R.string.home_detected_documents)) {
                        feed.expiringDocuments.take(3).forEachIndexed { i, doc ->
                            AshRow(
                                title = doc.title,
                                subtitle = doc.expiresAt?.let { expiresText(it) },
                                icon = AshIcons.category(app.ash.domain.model.Category.DOCUMENTS),
                                showDivider = i < minOf(2, feed.expiringDocuments.lastIndex),
                                showChevron = true,
                                onClick = { navigate(DocumentRoute(doc.id)) },
                            )
                        }
                    }
                }
            }

            if (feed.recentTransactions.isNotEmpty()) {
                item {
                    GroupedSection(header = stringResource(R.string.home_recent_transactions)) {
                        feed.recentTransactions.forEachIndexed { i, t ->
                            val sign = if (t.direction == TxnDirection.EXPENSE) "−" else if (t.direction == TxnDirection.INCOME || t.direction == TxnDirection.REFUND) "+" else ""
                            AshRow(
                                title = t.merchant ?: t.sourceLabel ?: directionLabel(t.direction),
                                subtitle = "${directionLabel(t.direction)} · ${relativeDay(t.occurredAt)}",
                                icon = AshIcons.category(t.category),
                                showDivider = i < feed.recentTransactions.lastIndex,
                                onClick = { navigate(RecordRoute(t.recordId)) },
                                trailing = { Text(sign + Money.format(t.amountPaise), style = MaterialTheme.typography.titleSmall) },
                            )
                        }
                    }
                }
            }
        }
    }

    explain?.let { item ->
        ModalBottomSheet(onDismissRequest = { explain = null }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
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

private fun androidx.compose.foundation.lazy.LazyListScope.attentionSection(
    title: Int,
    items: List<AttentionItem>,
    navigate: (Any) -> Unit,
    onExplain: (AttentionItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "h$title") { SectionHeader(stringResource(title)) }
    items(items, key = { it.key }) { item ->
        AttentionCard(
            title = item.title,
            detail = attentionDetail(item),
            label = bucketLabel(item.result.bucket),
            tone = item.result.bucket.tone(),
            icon = AshIcons.category(item.category),
            onClick = { navigate(item.target.route()) },
            onLongClick = { onExplain(item) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).animateItem(),
        )
    }
}

@Composable
fun pluralString(id: Int, count: Int): String = androidx.compose.ui.res.pluralStringResource(id, count, count)
