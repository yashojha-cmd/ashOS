package app.ash.presentation.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ash.R
import app.ash.data.repository.DiagnosticsRepository
import app.ash.data.repository.StoredCounts
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.AshTheme
import app.ash.designsystem.GroupedSection
import app.ash.navigation.BackupRoute
import app.ash.presentation.onboarding.notificationAccessIntent
import app.ash.security.KeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivacyViewModel @Inject constructor(diagnostics: DiagnosticsRepository, keys: KeyManager) : ViewModel() {
    val counts = diagnostics.storedCounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoredCounts())
    val strongBox = MutableStateFlow<Boolean?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) { strongBox.value = keys.isStrongBoxBacked() }
    }

    /** Checked against the installed app, not hard-coded: the manifest really has no INTERNET permission. */
    fun hasInternetPermission(context: Context): Boolean =
        context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.contains(android.Manifest.permission.INTERNET) == true
}

/** Privacy Center (§21): what is stored, and proof that nothing is sent out. */
@Composable
fun PrivacyCenterScreen(onBack: () -> Unit, navigate: (Any) -> Unit) {
    val vm: PrivacyViewModel = hiltViewModel()
    val c by vm.counts.collectAsStateWithLifecycle()
    val strongBox by vm.strongBox.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val internet = vm.hasInternetPermission(context)
    val status = AshTheme.status

    AshScaffold(title = stringResource(R.string.privacy_title), onBack = onBack) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                Surface(shape = MaterialTheme.shapes.large, color = status.okContainer, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CloudOff, null, tint = status.ok)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.privacy_promise), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.privacy_promise_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.privacy_stored_header)) {
                    CountRow(R.string.privacy_notifications, c.notifications)
                    CountRow(R.string.privacy_records, c.records)
                    CountRow(R.string.privacy_documents, c.documents)
                    CountRow(R.string.privacy_transactions, c.transactions)
                    CountRow(R.string.privacy_reminders, c.reminders)
                    CountRow(R.string.privacy_vehicles, c.vehicles)
                    CountRow(R.string.privacy_policies, c.policies)
                    CountRow(R.string.privacy_subscriptions, c.subscriptions, last = true)
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.privacy_sent_header), footer = stringResource(if (internet) R.string.privacy_internet_present else R.string.privacy_sent_footer)) {
                    AshRow(stringResource(R.string.privacy_sent_label), showDivider = false, trailing = {
                        Text(stringResource(if (internet) R.string.privacy_sent_check else R.string.privacy_nothing), style = MaterialTheme.typography.titleMedium, color = if (internet) status.urgent else status.ok)
                    })
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.privacy_protection_header)) {
                    AshRow(stringResource(R.string.privacy_db_encrypted), subtitle = stringResource(R.string.privacy_db_encrypted_desc))
                    AshRow(stringResource(R.string.privacy_files_encrypted), subtitle = stringResource(R.string.privacy_files_encrypted_desc))
                    AshRow(stringResource(R.string.privacy_keys), subtitle = stringResource(
                        when (strongBox) {
                            true -> R.string.privacy_keys_strongbox
                            false -> R.string.privacy_keys_tee
                            null -> R.string.privacy_keys_checking
                        },
                    ))
                    AshRow(stringResource(R.string.privacy_backup_off), subtitle = stringResource(R.string.privacy_backup_off_desc), showDivider = false)
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.privacy_control_header), footer = stringResource(R.string.privacy_limits)) {
                    AshRow(stringResource(R.string.privacy_revoke_notifications), showChevron = true, onClick = { context.startActivity(notificationAccessIntent(context)) })
                    AshRow(stringResource(R.string.privacy_export_delete), showChevron = true, showDivider = false, onClick = { navigate(BackupRoute) })
                }
            }
        }
    }
}

@Composable
private fun CountRow(label: Int, count: Int, last: Boolean = false) {
    AshRow(stringResource(label), showDivider = !last, trailing = { Text(count.toString(), style = MaterialTheme.typography.titleSmall) })
}
