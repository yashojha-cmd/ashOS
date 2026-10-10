package app.ash.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ash.R
import app.ash.backup.BackupRepository
import app.ash.common.AppResult
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.MessageBanner
import app.ash.designsystem.SecureScreen
import app.ash.designsystem.Tone
import app.ash.presentation.vault.appErrorText
import app.ash.security.AuthResult
import app.ash.security.BiometricGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(private val repo: BackupRepository) : ViewModel() {
    val busy = MutableStateFlow(false)

    fun export(uri: Uri, password: CharArray, done: (AppResult<Int>) -> Unit) = viewModelScope.launch {
        busy.value = true
        done(repo.export(uri, password))
        busy.value = false
    }

    fun import(uri: Uri, password: CharArray, done: (AppResult<Int>) -> Unit) = viewModelScope.launch {
        busy.value = true
        done(repo.import(uri, password))
        busy.value = false
    }

    fun deleteAll(done: () -> Unit) = viewModelScope.launch {
        busy.value = true
        repo.deleteAll()
        busy.value = false
        done()
    }
}

private enum class PasswordMode { EXPORT, IMPORT }

/** Encrypted export, import and permanent deletion (§20, §32). No account, no cloud. */
@Composable
fun BackupScreen(onBack: () -> Unit, onDeletedAll: () -> Unit) {
    SecureScreen()
    val vm: BackupViewModel = hiltViewModel()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val activity = androidx.activity.compose.LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var mode by remember { mutableStateOf<PasswordMode?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val exported = stringResource(R.string.backup_exported)
    val imported = stringResource(R.string.backup_imported)
    val promptTitle = stringResource(R.string.backup_delete_prompt)
    val errors = app.ash.common.AppError.entries.associateWith { appErrorText(it) }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { pendingUri = uri; mode = PasswordMode.EXPORT }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingUri = uri; mode = PasswordMode.IMPORT }
    }

    AshScaffold(title = stringResource(R.string.backup_title), onBack = onBack, snackbarHost = snackbar) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            if (busy) item { MessageBanner(stringResource(R.string.backup_working), Tone.INFO) }
            item {
                GroupedSection(header = stringResource(R.string.backup_export_header), footer = stringResource(R.string.backup_export_footer)) {
                    AshRow(stringResource(R.string.backup_export), icon = Icons.Rounded.Upload, showChevron = true, showDivider = false,
                        onClick = { if (!busy) createLauncher.launch("ash-backup-${LocalDate.now()}.ashbak") })
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.backup_import_header), footer = stringResource(R.string.backup_import_footer)) {
                    AshRow(stringResource(R.string.backup_import), icon = Icons.Rounded.Download, showChevron = true, showDivider = false,
                        onClick = { if (!busy) openLauncher.launch(arrayOf("*/*")) })
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.backup_delete_header), footer = stringResource(R.string.backup_delete_footer)) {
                    AshRow(stringResource(R.string.backup_delete_all), icon = Icons.Rounded.DeleteForever, titleColor = MaterialTheme.colorScheme.error,
                        iconTint = MaterialTheme.colorScheme.error, iconBackground = MaterialTheme.colorScheme.errorContainer, showDivider = false,
                        onClick = { if (!busy) confirmDelete = true })
                }
            }
        }
    }

    mode?.let { m ->
        var password by remember { mutableStateOf("") }
        var repeat by remember { mutableStateOf("") }
        val valid = if (m == PasswordMode.EXPORT) password.length >= 8 && password == repeat else password.isNotEmpty()
        AlertDialog(
            onDismissRequest = { mode = null },
            title = { Text(stringResource(if (m == PasswordMode.EXPORT) R.string.backup_password_set else R.string.backup_password_enter)) },
            text = {
                Column {
                    if (m == PasswordMode.EXPORT) Text(stringResource(R.string.backup_password_warning), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.backup_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.padding(top = 8.dp))
                    if (m == PasswordMode.EXPORT) {
                        OutlinedTextField(repeat, { repeat = it }, label = { Text(stringResource(R.string.backup_password_repeat)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = repeat.isNotEmpty() && repeat != password, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    val uri = pendingUri ?: return@TextButton
                    val chars = password.toCharArray()
                    mode = null
                    val onResult: (AppResult<Int>) -> Unit = { result ->
                        scope.launch {
                            snackbar.showSnackbar(
                                when (result) {
                                    is AppResult.Success -> if (m == PasswordMode.EXPORT) exported else imported
                                    is AppResult.Failure -> errors.getValue(result.error)
                                },
                            )
                        }
                    }
                    if (m == PasswordMode.EXPORT) vm.export(uri, chars, onResult) else vm.import(uri, chars, onResult)
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = { TextButton(onClick = { mode = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (confirmDelete) {
        var typed by remember { mutableStateOf("") }
        val word = stringResource(R.string.backup_delete_word)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.backup_delete_all)) },
            text = {
                Column {
                    Text(stringResource(R.string.backup_delete_body, word))
                    OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim().equals(word, ignoreCase = true), onClick = {
                    confirmDelete = false
                    scope.launch {
                        val auth = BiometricGate.authenticate(activity, promptTitle, null)
                        if (auth == AuthResult.Success || auth == AuthResult.Unavailable) vm.deleteAll(onDeletedAll)
                    }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
