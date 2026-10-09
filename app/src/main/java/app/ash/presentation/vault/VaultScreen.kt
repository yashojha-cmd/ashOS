package app.ash.presentation.vault

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ash.R
import app.ash.common.AppError
import app.ash.common.AppResult
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.EmptyState
import app.ash.designsystem.MessageBanner
import app.ash.designsystem.PrimaryButton
import app.ash.designsystem.SecureScreen
import app.ash.designsystem.StatusPill
import app.ash.designsystem.Tone
import app.ash.designsystem.docTypeLabel
import app.ash.designsystem.expiresText
import app.ash.document.ImageTools
import app.ash.domain.model.Document
import app.ash.domain.repository.DocumentRepository
import app.ash.navigation.CameraRoute
import app.ash.navigation.DocumentRoute
import app.ash.security.AppLockManager
import app.ash.security.AuthResult
import app.ash.security.BiometricGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val lock: AppLockManager,
) : ViewModel() {
    val documents = repo.observeDocuments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val unlocked = MutableStateFlow(lock.recentlyAuthenticated())
    val importing = MutableStateFlow(false)

    fun markUnlocked() {
        lock.markAuthenticated()
        unlocked.value = true
    }

    fun import(uri: Uri, onResult: (AppResult<Long>) -> Unit) = viewModelScope.launch {
        importing.value = true
        val result = repo.importFromUri(uri)
        importing.value = false
        onResult(result)
    }

    /** Decrypted thumbnails, kept in memory only (max ~16 MB) so scrolling never re-decrypts. */
    private val thumbs = object : android.util.LruCache<Long, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }

    suspend fun thumbnail(id: Long): Bitmap? {
        thumbs.get(id)?.let { return it }
        val bitmap = repo.readThumbnail(id)?.let { bytes -> withContext(Dispatchers.Default) { ImageTools.decodeSampled(bytes, 480) } }
        bitmap?.let { thumbs.put(id, it) }
        return bitmap
    }

    override fun onCleared() = thumbs.evictAll()
}

@Composable
fun appErrorText(error: AppError): String = stringResource(
    when (error) {
        AppError.NOTIFICATION_ACCESS_DISABLED -> R.string.error_listener_disabled
        AppError.OCR_LOW_CONFIDENCE, AppError.OCR_FAILED -> R.string.error_ocr
        AppError.FILE_TOO_LARGE -> R.string.error_file_too_large
        AppError.UNSUPPORTED_FILE -> R.string.error_unsupported_file
        AppError.FILE_READ_FAILED -> R.string.error_file_read
        AppError.ENCRYPTION_FAILED -> R.string.error_encryption
        AppError.WRONG_PASSWORD -> R.string.error_wrong_password
        AppError.CORRUPT_BACKUP -> R.string.error_corrupt_backup
        AppError.STORAGE_FULL -> R.string.error_storage
        AppError.UNKNOWN -> R.string.error_unknown
    },
)

/** Local, encrypted document vault (§11), gated by fingerprint or device PIN. */
@Composable
fun VaultScreen(navigate: (Any) -> Unit) {
    SecureScreen()
    val vm: VaultViewModel = hiltViewModel()
    val docs by vm.documents.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var noLock by remember { mutableStateOf(false) }
    val promptTitle = stringResource(R.string.vault_prompt_title)
    val errorTexts = AppError.entries.associateWith { appErrorText(it) }

    fun unlock() = scope.launch {
        when (BiometricGate.authenticate(activity, promptTitle, null)) {
            AuthResult.Success -> vm.markUnlocked()
            AuthResult.Unavailable -> { noLock = true; vm.markUnlocked() }
            else -> Unit
        }
    }
    LaunchedEffect(Unit) { if (!unlocked) unlock() }

    val onImported: (AppResult<Long>) -> Unit = { result ->
        when (result) {
            is AppResult.Success -> navigate(DocumentRoute(result.value))
            is AppResult.Failure -> scope.launch { snackbar.showSnackbar(errorTexts.getValue(result.error)) }
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.import(it, onImported) } }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.import(it, onImported) } }

    AshScaffold(
        title = stringResource(R.string.vault_title),
        snackbarHost = snackbar,
        floatingActionButton = {
            if (unlocked) Box {
                FloatingActionButton(onClick = { menu = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.vault_add)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.vault_scan)) }, leadingIcon = { Icon(Icons.Rounded.CameraAlt, null) }, onClick = { menu = false; navigate(CameraRoute) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.vault_photo)) }, leadingIcon = { Icon(Icons.Rounded.Image, null) }, onClick = {
                        menu = false
                        photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.vault_file)) }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) }, onClick = {
                        menu = false
                        fileLauncher.launch(arrayOf("application/pdf", "image/*"))
                    })
                }
            }
        },
    ) { padding ->
        if (!unlocked) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                EmptyState(Icons.Rounded.Lock, stringResource(R.string.vault_locked_title), stringResource(R.string.vault_locked_body))
                PrimaryButton(stringResource(R.string.action_unlock), onClick = { unlock() }, modifier = Modifier.padding(horizontal = 32.dp))
            }
            return@AshScaffold
        }
        val filtered = docs.orEmpty().filter { d ->
            query.isBlank() || d.title.contains(query, true) || d.tags.any { it.contains(query, true) } || d.docType.name.contains(query, true)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    if (noLock) MessageBanner(stringResource(R.string.vault_no_lock), Tone.SOON)
                    if (importing) MessageBanner(stringResource(R.string.vault_reading), Tone.INFO)
                    if (!docs.isNullOrEmpty()) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.vault_filter)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                }
            }
            if (docs != null && docs!!.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Rounded.Description, stringResource(R.string.empty_vault_title), stringResource(R.string.empty_vault_body))
                }
            }
            items(filtered, key = { it.id }) { doc ->
                DocumentTile(doc, thumbnail = { vm.thumbnail(doc.id) }, onClick = { navigate(DocumentRoute(doc.id)) })
            }
        }
    }
}

@Composable
private fun DocumentTile(doc: Document, thumbnail: suspend () -> Bitmap?, onClick: () -> Unit) {
    val bitmap by produceState<Bitmap?>(null, doc.id) { value = thumbnail() }
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.3f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val b = bitmap
                if (b != null) {
                    Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(docTypeLabel(doc.docType), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                doc.expiresAt?.let {
                    Spacer(Modifier.height(4.dp))
                    val days = app.ash.designsystem.AshFormat.daysFromToday(it)
                    StatusPill(expiresText(it), if (days <= 7) Tone.URGENT else if (days <= 60) Tone.SOON else Tone.OK)
                }
                if (doc.needsReview) {
                    Spacer(Modifier.height(4.dp))
                    StatusPill(stringResource(R.string.status_review), Tone.SOON)
                }
            }
        }
    }
}

@Composable
fun LoadingOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
