package app.ash.presentation.vault

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.ash.R
import app.ash.common.AppResult
import app.ash.common.TimeProvider
import app.ash.data.parser.ParseContext
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.AshTextField
import app.ash.designsystem.CenteredLoading
import app.ash.designsystem.ConfirmDialog
import app.ash.designsystem.DateField
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.MessageBanner
import app.ash.designsystem.OptionField
import app.ash.designsystem.PrimaryButton
import app.ash.designsystem.SecureScreen
import app.ash.designsystem.SwitchRow
import app.ash.designsystem.Tone
import app.ash.designsystem.docTypeLabel
import app.ash.document.ImageTools
import app.ash.domain.model.DocType
import app.ash.domain.model.Document
import app.ash.domain.model.Vehicle
import app.ash.domain.repository.DocumentRepository
import app.ash.domain.repository.LifeRepository
import app.ash.intelligence.DocumentFields
import app.ash.intelligence.IntelligenceEngine
import app.ash.navigation.DocumentRoute
import app.ash.presentation.common.ProfilePicker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DocForm(
    val title: String = "",
    val type: DocType = DocType.OTHER,
    val number: String = "",
    val issuedAt: Long? = null,
    val expiresAt: Long? = null,
    val notes: String = "",
    val tags: String = "",
    val sensitive: Boolean = true,
    val vehicleId: Long? = null,
    val profileId: Long? = null,
)

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repo: DocumentRepository,
    life: LifeRepository,
    private val engine: IntelligenceEngine,
    private val time: TimeProvider,
) : ViewModel() {
    private val id = savedState.toRoute<DocumentRoute>().id
    val document = repo.observeDocument(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val vehicles = life.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val form = MutableStateFlow<DocForm?>(null)
    val pages = MutableStateFlow<List<Bitmap>>(emptyList())
    val busy = MutableStateFlow(false)
    private var tempPdf: File? = null

    fun load(doc: Document) {
        if (form.value != null) return
        form.value = DocForm(doc.title, doc.docType, doc.documentNumber.orEmpty(), doc.issuedAt, doc.expiresAt, doc.notes.orEmpty(), doc.tags.joinToString(", "), doc.sensitive, doc.vehicleId, doc.profileId)
        viewModelScope.launch { pages.value = render(doc) }
    }

    /** Fields OCR suggested, shown so the user can see what ASH read. */
    fun suggestions(doc: Document): DocumentFields? = doc.ocrText?.let {
        engine.extractDocumentFields(it, ParseContext(time.today(), time.zone(), time.nowMillis()))
    }

    private suspend fun render(doc: Document): List<Bitmap> = withContext(Dispatchers.IO) {
        if (doc.mimeType == "application/pdf") {
            when (val r = repo.decryptToTempFile(doc.id)) {
                is AppResult.Success -> {
                    tempPdf = r.value
                    val count = runCatching { ImageTools.pdfPageCount(r.value) }.getOrDefault(0).coerceAtMost(20)
                    (0 until count).mapNotNull { runCatching { ImageTools.renderPdfPage(r.value, it, 1200) }.getOrNull() }
                        .also { r.value.delete(); tempPdf = null }
                }
                is AppResult.Failure -> emptyList()
            }
        } else {
            when (val r = repo.readDecrypted(doc.id)) {
                is AppResult.Success -> listOfNotNull(ImageTools.decodeSampled(r.value, 2000))
                is AppResult.Failure -> emptyList()
            }
        }
    }

    fun update(transform: (DocForm) -> DocForm) = form.update { it?.let(transform) }

    fun save(onSaved: () -> Unit) {
        val doc = document.value ?: return
        val f = form.value ?: return
        viewModelScope.launch {
            repo.update(
                doc.copy(
                    title = f.title, docType = f.type, documentNumber = f.number, issuedAt = f.issuedAt, expiresAt = f.expiresAt,
                    notes = f.notes, tags = f.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }, sensitive = f.sensitive,
                    vehicleId = f.vehicleId, profileId = f.profileId,
                ),
            )
            onSaved()
        }
    }

    fun rerunOcr(onResult: (AppResult<String>) -> Unit) = viewModelScope.launch {
        busy.value = true
        val result = repo.runOcr(id)
        busy.value = false
        if (result is AppResult.Success) {
            document.value?.let { doc ->
                suggestions(doc.copy(ocrText = result.value))?.let { s ->
                    update { f -> f.copy(type = s.docType ?: f.type, number = s.documentNumber ?: f.number, expiresAt = s.expiresOn?.let { app.ash.common.Dates.morningOf(it, time.zone()) } ?: f.expiresAt) }
                }
            }
        }
        onResult(result)
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.delete(id); onDone() }

    /** User-initiated share of a decrypted copy; the copy is removed on next launch. */
    fun share(onIntent: (Intent) -> Unit) = viewModelScope.launch {
        val doc = document.value ?: return@launch
        val bytes = (repo.readDecrypted(doc.id) as? AppResult.Success)?.value ?: return@launch
        val ext = if (doc.mimeType == "application/pdf") "pdf" else "jpg"
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            File(dir, doc.title.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "document" } + ".$ext").apply { writeBytes(bytes) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        onIntent(Intent(Intent.ACTION_SEND).setType(doc.mimeType).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    override fun onCleared() {
        tempPdf?.delete()
        pages.value.forEach { it.recycle() }
    }
}

@Composable
fun DocumentDetailScreen(onBack: () -> Unit, navigate: (Any) -> Unit) {
    SecureScreen()
    val vm: DocumentDetailViewModel = hiltViewModel()
    val doc by vm.document.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmShare by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    val ocrError = stringResource(R.string.error_ocr)
    val savedText = stringResource(R.string.saved)

    LaunchedEffect(doc) { doc?.let(vm::load) }

    AshScaffold(
        title = doc?.title ?: "",
        onBack = onBack,
        snackbarHost = snackbar,
        actions = {
            IconButton(onClick = { confirmShare = true }) { Icon(Icons.Rounded.Share, stringResource(R.string.action_share)) }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
        },
    ) { padding ->
        val d = doc
        val f = form
        if (d == null || f == null) { CenteredLoading(); return@AshScaffold }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                if (pages.isEmpty()) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(16.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(pages) { page -> ZoomableImage(page) }
                    }
                }
            }
            if (d.needsReview) item { MessageBanner(stringResource(R.string.ocr_review_banner), Tone.SOON) }
            if (d.ocrText == null) {
                item {
                    MessageBanner(
                        stringResource(R.string.error_ocr),
                        Tone.INFO,
                        actionLabel = stringResource(R.string.action_read_again),
                        onAction = { vm.rerunOcr { r -> if (r is AppResult.Failure) scope.launch { snackbar.showSnackbar(ocrError) } } },
                    )
                }
            }
            if (busy) item { MessageBanner(stringResource(R.string.vault_reading), Tone.INFO) }
            item {
                AshTextField(f.title, { v -> vm.update { it.copy(title = v) } }, stringResource(R.string.field_title))
                OptionField(stringResource(R.string.field_doc_type), DocType.entries, f.type, { docTypeLabel(it) }, { t -> vm.update { it.copy(type = t) } })
                AshTextField(f.number, { v -> vm.update { it.copy(number = v) } }, stringResource(R.string.field_doc_number), capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters)
                DateField(f.issuedAt, { v -> vm.update { it.copy(issuedAt = v) } }, stringResource(R.string.field_issued), deadline = false)
                DateField(f.expiresAt, { v -> vm.update { it.copy(expiresAt = v) } }, stringResource(R.string.field_expires))
                AshTextField(f.tags, { v -> vm.update { it.copy(tags = v) } }, stringResource(R.string.field_tags), supportingText = stringResource(R.string.field_tags_hint))
                AshTextField(f.notes, { v -> vm.update { it.copy(notes = v) } }, stringResource(R.string.field_notes), singleLine = false, minLines = 2)
                if (vehicles.isNotEmpty()) {
                    val none = stringResource(R.string.profile_none)
                    OptionField(
                        stringResource(R.string.field_vehicle),
                        listOf<Vehicle?>(null) + vehicles,
                        vehicles.firstOrNull { it.id == f.vehicleId },
                        { it?.name ?: none },
                        { v -> vm.update { it.copy(vehicleId = v?.id) } },
                    )
                }
                ProfilePicker(f.profileId) { id -> vm.update { it.copy(profileId = id) } }
                GroupedSection {
                    SwitchRow(stringResource(R.string.field_sensitive), f.sensitive, { v -> vm.update { it.copy(sensitive = v) } }, subtitle = stringResource(R.string.field_sensitive_desc), showDivider = false)
                }
                PrimaryButton(stringResource(if (d.needsReview) R.string.action_confirm_details else R.string.action_save), onClick = {
                    vm.save { scope.launch { snackbar.showSnackbar(savedText) } }
                }, modifier = Modifier.padding(16.dp))
            }
            d.ocrText?.let { text ->
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        TextButton(onClick = { showText = !showText }) { Text(stringResource(if (showText) R.string.ocr_hide_text else R.string.ocr_show_text)) }
                        if (showText) {
                            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { vm.rerunOcr { r -> if (r is AppResult.Failure) scope.launch { snackbar.showSnackbar(ocrError) } } }) {
                                Text(stringResource(R.string.action_read_again))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_document_title),
            message = stringResource(R.string.delete_document_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmShare) {
        ConfirmDialog(
            title = stringResource(R.string.share_document_title),
            message = stringResource(R.string.share_document_body),
            confirmLabel = stringResource(R.string.action_share),
            onConfirm = {
                confirmShare = false
                vm.share { intent -> context.startActivity(Intent.createChooser(intent, null)) }
            },
            onDismiss = { confirmShare = false },
        )
    }
}

@Composable
private fun ZoomableImage(bitmap: Bitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    val state = rememberTransformableState { zoom, _, _ -> scale = (scale * zoom).coerceIn(1f, 4f) }
    DisposableEffect(bitmap) { onDispose { } }
    Image(
        bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.cd_document_page),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .heightIn(max = 420.dp)
            .clip(MaterialTheme.shapes.large)
            .transformable(state)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    )
}
