package app.ash.presentation.settings

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ash.R
import app.ash.data.repository.Counters
import app.ash.data.repository.DiagnosticsRepository
import app.ash.data.repository.SettingsRepository
import app.ash.designsystem.AshFormat
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.ConfirmDialog
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.MessageBanner
import app.ash.designsystem.SwitchRow
import app.ash.designsystem.Tone
import app.ash.logging.AshLogger
import app.ash.logging.LogManager
import app.ash.navigation.LogsRoute
import app.ash.notification.AshNotificationListener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    diagnostics: DiagnosticsRepository,
    private val settings: SettingsRepository,
    private val logs: LogManager,
) : ViewModel() {
    val counters = diagnostics.counters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val audit = diagnostics.recentAudit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settingsState = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val logSize = MutableStateFlow(0L)
    val lines = MutableStateFlow<List<String>>(emptyList())

    fun refreshLogs() = viewModelScope.launch(Dispatchers.IO) {
        logSize.value = logs.totalSizeBytes()
        lines.value = logs.readRecent(800).reversed()
    }

    fun setDetailed(enabled: Boolean) = viewModelScope.launch {
        val until = if (enabled) System.currentTimeMillis() + 24L * 60 * 60 * 1000 else 0
        settings.setDetailedLogsUntil(until)
        AshLogger.detailedParserLogging = enabled
        AshLogger.i("Diagnostics", "Detailed parser logs ${if (enabled) "on for 24h" else "off"}")
    }

    suspend fun exportIntent(): Intent = withContext(Dispatchers.IO) { logs.buildExportIntent() }

    fun clearLogs() = viewModelScope.launch(Dispatchers.IO) {
        logs.clear()
        AshLogger.i("Diagnostics", "Logs cleared by user")
        refreshLogs()
    }
}

/** Local diagnostics instead of analytics (§22). Nothing here is ever uploaded. */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, navigate: (Any) -> Unit) {
    val vm: DiagnosticsViewModel = hiltViewModel()
    val counters by vm.counters.collectAsStateWithLifecycle()
    val audit by vm.audit.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val size by vm.logSize.collectAsStateWithLifecycle()
    val connected by AshNotificationListener.isConnected.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshLogs() }
    val detailedOn = (settings?.detailedLogsUntil ?: 0) > System.currentTimeMillis()

    AshScaffold(title = stringResource(R.string.diagnostics_title), onBack = onBack) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                GroupedSection(header = stringResource(R.string.diag_counters), footer = stringResource(R.string.diag_footer)) {
                    listOf(
                        R.string.diag_processed to Counters.NOTIFICATIONS_PROCESSED,
                        R.string.diag_created to Counters.RECORDS_CREATED,
                        R.string.diag_review to Counters.RECORDS_REVIEW,
                        R.string.diag_confirmations to Counters.PARSER_CONFIRMATIONS,
                        R.string.diag_sensitive to Counters.NOTIFICATIONS_SENSITIVE,
                        R.string.diag_duplicates to Counters.NOTIFICATIONS_DUPLICATE,
                        R.string.diag_ocr to Counters.OCR_DOCUMENTS,
                        R.string.diag_reminders to Counters.REMINDERS_FIRED,
                        R.string.diag_errors to Counters.ERRORS,
                    ).forEach { (label, key) ->
                        AshRow(stringResource(label), trailing = { Text((counters[key] ?: 0).toString(), style = MaterialTheme.typography.titleSmall) })
                    }
                    AshRow(stringResource(R.string.diag_listener), showDivider = false, trailing = {
                        Text(stringResource(if (connected) R.string.diag_connected else R.string.diag_not_connected), style = MaterialTheme.typography.titleSmall)
                    })
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.diag_logs), footer = stringResource(R.string.diag_logs_footer)) {
                    AshRow(stringResource(R.string.diag_view_logs), subtitle = stringResource(R.string.diag_log_size, size / 1024), icon = Icons.Rounded.Description,
                        showChevron = true, onClick = { navigate(LogsRoute) })
                    SwitchRow(stringResource(R.string.diag_detailed), detailedOn, vm::setDetailed, subtitle = stringResource(R.string.diag_detailed_desc), showDivider = false)
                }
            }
            if (detailedOn) item { MessageBanner(stringResource(R.string.diag_detailed_banner), Tone.SOON) }
            if (audit.isNotEmpty()) item {
                GroupedSection(header = stringResource(R.string.diag_audit), footer = stringResource(R.string.diag_audit_footer)) {
                    audit.take(30).forEachIndexed { i, a ->
                        AshRow("${a.action.name.lowercase().replace('_', ' ')} · ${a.entity}", subtitle = AshFormat.dateTime(a.at), showDivider = i < minOf(29, audit.lastIndex))
                    }
                }
            }
        }
    }
}

/** Log viewer with export (share sheet, user-initiated) and clear. */
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val vm: DiagnosticsViewModel = hiltViewModel()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshLogs() }

    AshScaffold(
        title = stringResource(R.string.logs_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { confirmExport = true }) { Icon(Icons.Rounded.Share, stringResource(R.string.logs_export)) }
            IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.logs_clear)) }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp, start = 12.dp, end = 12.dp)) {
            if (lines.isEmpty()) item { Text(stringResource(R.string.logs_empty), modifier = Modifier.padding(8.dp)) }
            items(lines) { line ->
                Text(
                    line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = when {
                        " | E | " in line -> MaterialTheme.colorScheme.error
                        " | W | " in line -> app.ash.designsystem.AshTheme.status.soon
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
                )
            }
        }
    }
    if (confirmExport) {
        ConfirmDialog(
            stringResource(R.string.logs_export), stringResource(R.string.logs_export_body), stringResource(R.string.action_share),
            onConfirm = {
                confirmExport = false
                scope.launch { context.startActivity(Intent.createChooser(vm.exportIntent(), null)) }
            },
            onDismiss = { confirmExport = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            stringResource(R.string.logs_clear), stringResource(R.string.logs_clear_body), stringResource(R.string.action_delete), destructive = true,
            onConfirm = { confirmClear = false; vm.clearLogs() }, onDismiss = { confirmClear = false },
        )
    }
}
