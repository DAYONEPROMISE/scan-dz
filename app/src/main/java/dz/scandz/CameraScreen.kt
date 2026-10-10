package dz.scandz

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun CameraScreen(hasCam: Boolean, onCaptured: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val lc = LocalLifecycleOwner.current
    var capture: ImageCapture? by remember { mutableStateOf(null) }
    var err by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (!hasCam) { Text(stringResource(R.string.camera_permission)); Button(onClick = onBack) { Text("←") }; return }
        AndroidView(factory = { c ->
            PreviewView(c).also { pv ->
                val fut = ProcessCameraProvider.getInstance(c)
                fut.addListener({
                    val provider = fut.get()
                    val preview = androidx.camera.core.Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                    capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(lc, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    } catch (e: Exception) { err = e.message }
                }, ContextCompat.getMainExecutor(c))
            }
        }, modifier = Modifier.fillMaxWidth().weight(1f))
        err?.let { Text(it) }
        Row {
            Button(onClick = onBack) { Text("←") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val cap = capture ?: return@Button
                val f = File(ctx.cacheDir, "cap-${System.currentTimeMillis()}.jpg")
                cap.takePicture(
                    ImageCapture.OutputFileOptions.Builder(f).build(),
                    ContextCompat.getMainExecutor(ctx),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(o: ImageCapture.OutputFileResults) { onCaptured(f.absolutePath) }
                        override fun onError(e: ImageCaptureException) { err = e.message }
                    }
                )
            }) { Text(stringResource(R.string.capture)) }
        }
    }
}
