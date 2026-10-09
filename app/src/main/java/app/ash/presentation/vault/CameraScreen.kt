package app.ash.presentation.vault

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import app.ash.R
import app.ash.common.AppResult
import app.ash.designsystem.AshFormat
import app.ash.designsystem.EmptyState
import app.ash.designsystem.SecureScreen
import app.ash.document.ImageTools
import app.ash.domain.repository.DocumentRepository
import app.ash.logging.AshLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(val repo: DocumentRepository) : ViewModel()

/**
 * In-app document capture with CameraX (§11). The photo never touches the gallery: it goes
 * straight from memory into the encrypted vault.
 */
@Composable
fun CameraScreen(onBack: () -> Unit, onSaved: (Long) -> Unit) {
    SecureScreen()
    val vm: CameraViewModel = hiltViewModel()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    var captured by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    }
    val scanTitle = stringResource(R.string.vault_scan_title, AshFormat.shortDate(System.currentTimeMillis()))

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            !granted -> Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    Icons.Rounded.CameraAlt,
                    stringResource(R.string.camera_denied_title),
                    stringResource(R.string.camera_denied_body),
                    actionLabel = if (asked) stringResource(R.string.action_open_settings) else stringResource(R.string.action_allow),
                    onAction = {
                        if (asked) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        else permission.launch(Manifest.permission.CAMERA)
                    },
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.action_cancel)) }
            }
            captured != null -> {
                Image(captured!!.asImageBitmap(), contentDescription = stringResource(R.string.cd_document_page), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { captured = null }, enabled = !saving, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_retake), color = Color.White) }
                    Button(onClick = {
                        val bitmap = captured ?: return@Button
                        saving = true
                        scope.launch {
                            val bytes = withContext(Dispatchers.Default) { ImageTools.toJpeg(ImageTools.scaleToMax(bitmap, 2400), 88) }
                            when (val result = vm.repo.importImageBytes(bytes, scanTitle)) {
                                is AppResult.Success -> onSaved(result.value)
                                is AppResult.Failure -> saving = false
                            }
                        }
                    }, enabled = !saving, modifier = Modifier.weight(1f)) { Text(stringResource(if (saving) R.string.saving else R.string.action_use_photo)) }
                }
            }
            else -> {
                AndroidView(
                    factory = { ctx -> PreviewView(ctx).apply { this.controller = controller; controller.bindToLifecycle(lifecycle) } },
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(onClick = onBack, modifier = Modifier.safeDrawingPadding().padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back), tint = Color.White)
                }
                Text(
                    stringResource(R.string.camera_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 64.dp),
                )
                FilledIconButton(
                    onClick = {
                        controller.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val rotation = image.imageInfo.rotationDegrees
                                val raw = image.toBitmap()
                                image.close()
                                captured = if (rotation == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                AshLogger.e("Camera", "Capture failed", exception)
                            }
                        })
                    },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 32.dp).size(76.dp),
                ) { Icon(Icons.Rounded.CameraAlt, stringResource(R.string.action_capture), modifier = Modifier.size(32.dp)) }
            }
        }
    }
}
