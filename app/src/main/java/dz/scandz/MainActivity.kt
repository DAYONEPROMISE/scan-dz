package dz.scandz

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import dz.scandz.data.AppDb
import dz.scandz.data.DocItem
import dz.scandz.scan.Bitmaps
import dz.scandz.scan.PdfExporter
import dz.scandz.scan.Pt
import dz.scandz.scan.ScanFilter
import dz.scandz.scan.buildPdfName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Route {
    data object List : Route
    data class Camera(val docTitle: String) : Route
    data class Crop(val photoPath: String, val docTitle: String) : Route
}

/** 2026 baseline theme: dynamic color on Android 12+, system dark mode, M3 typography. */
@Composable
fun ScanDZTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

class MainActivity : ComponentActivity() {
    private lateinit var db: AppDb
    private var hasCam by mutableStateOf(false)
    private val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        hasCam = g
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        db = Room.databaseBuilder(applicationContext, AppDb::class.java, "scandz.db").build()
        hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasCam) perm.launch(Manifest.permission.CAMERA)
        val dir = File(filesDir, "scans").apply { mkdirs() }
        setContent {
            ScanDZTheme {
                var route by remember { mutableStateOf<Route>(Route.List) }
                var query by remember { mutableStateOf("") }
                // collect docs via state
                val docState = remember(query) { db.docs().let { if (query.isBlank()) it.observeAll() else it.search(query) } }.collectAsState(initial = emptyList())
                when (val r = route) {
                    is Route.List -> DocList(
                        docs = docState.value, query = query, onQuery = { query = it },
                        hasCam = hasCam, onGrant = { perm.launch(Manifest.permission.CAMERA) },
                        onNew = { title -> route = Route.Camera(title) },
                        onShare = { sharePdf(it) },
                        onDelete = { delDoc(it) }
                    )
                    is Route.Camera -> CameraScreen(
                        hasCam = hasCam,
                        onCaptured = { path -> route = Route.Crop(path, r.docTitle) },
                        onBack = { route = Route.List }
                    )
                    is Route.Crop -> CropScreen(
                        photoPath = r.photoPath,
                        onDone = { pages -> exportDoc(r.docTitle, pages, dir) { route = Route.List } },
                        onBack = { route = Route.List }
                    )
                }
            }
        }
    }

    private fun delDoc(d: DocItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.docs().delete(d.id)
            try { File(d.pdfPath).delete() } catch (_: Exception) {}
        }
    }

    private fun exportDoc(title: String, pages: List<android.graphics.Bitmap>, dir: File, done: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val name = buildPdfName(title.ifBlank { getString(R.string.app_name) }, System.currentTimeMillis())
            val out = File(dir, name)
            PdfExporter.export(pages, out)
            db.docs().insert(DocItem(title = title.ifBlank { name }, createdAt = System.currentTimeMillis(), pageCount = pages.size, pdfPath = out.absolutePath))
            withContext(Dispatchers.Main) { done() }
        }
    }

    private fun sharePdf(d: DocItem) {
        val uri = FileProvider.getUriForFile(this, "dz.scandz.fileprovider", File(d.pdfPath))
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, getString(R.string.share)))
    }
}

@Composable
fun DocList(
    docs: List<DocItem>, query: String, onQuery: (String) -> Unit,
    hasCam: Boolean, onGrant: () -> Unit, onNew: (String) -> Unit,
    onShare: (DocItem) -> Unit, onDelete: (DocItem) -> Unit
) {
    var title by remember { mutableStateOf("") }
    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = { onNew(title) }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_desc))
        }
    }) { pad ->
        // Adaptive: constrain content width on tablets/foldables, centered.
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxWidth().widthIn(max = 840.dp).padding(16.dp)) {
                Text(stringResource(R.string.doc_list), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                if (!hasCam) {
                    Text(stringResource(R.string.camera_permission))
                    Button(onClick = onGrant) { Text(stringResource(R.string.grant)) }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.new_scan)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = query, onValueChange = onQuery, label = { Text(stringResource(R.string.search_hint)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                if (docs.isEmpty()) Text(stringResource(R.string.empty))
                LazyColumn {
                    items(docs, key = { it.id }) { d ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(d.title, style = MaterialTheme.typography.titleMedium)
                                Text("${d.pageCount} ${stringResource(R.string.pages)}")
                                Row {
                                    TextButton(onClick = { onShare(d) }) { Text(stringResource(R.string.share)) }
                                    TextButton(onClick = { onDelete(d) }) { Text(stringResource(R.string.delete)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CropScreen(photoPath: String, onDone: (List<android.graphics.Bitmap>) -> Unit, onBack: () -> Unit) {
    var filter by remember { mutableStateOf(ScanFilter.GRAY) }
    val src = remember(photoPath) { BitmapFactory.decodeFile(photoPath) }
    // 4 corners as fractions; sliders keep it robust on all devices
    var tlx by remember { mutableStateOf(0.08f) }; var tly by remember { mutableStateOf(0.08f) }
    var trx by remember { mutableStateOf(0.92f) }; var try_ by remember { mutableStateOf(0.08f) }
    var brx by remember { mutableStateOf(0.92f) }; var bry by remember { mutableStateOf(0.92f) }
    var blx by remember { mutableStateOf(0.08f) }; var bly by remember { mutableStateOf(0.92f) }
    var busy by remember { mutableStateOf(false) }
    if (src == null) { Text("Image illisible"); return }
    val preview = remember(src, tlx, tly, trx, try_, brx, bry, blx, bly, filter) {
        val w = src.width; val h = src.height
        val quad = listOf(Pt((tlx * w).toDouble(), (tly * h).toDouble()), Pt((trx * w).toDouble(), (try_ * h).toDouble()), Pt((brx * w).toDouble(), (bry * h).toDouble()), Pt((blx * w).toDouble(), (bly * h).toDouble()))
        val ow = 1200; val oh = (ow * quad.let {
            val top = kotlin.math.hypot(it[1].x - it[0].x, it[1].y - it[0].y)
            val bot = kotlin.math.hypot(it[2].x - it[3].x, it[2].y - it[3].y)
            val left = kotlin.math.hypot(it[3].x - it[0].x, it[3].y - it[0].y)
            val right = kotlin.math.hypot(it[2].x - it[1].x, it[2].y - it[1].y)
            ((left + right) / 2) / ((top + bot) / 2).coerceAtLeast(1.0)
        }).toInt().coerceIn(800, 1700)
        Bitmaps.scanPage(src, quad, ow, oh, filter)
    }
    Scaffold(topBar = { Text(stringResource(R.string.crop_title), modifier = Modifier.padding(16.dp)) }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxWidth().widthIn(max = 840.dp).padding(16.dp)) {
                androidx.compose.foundation.Image(bitmap = preview.asImageBitmap(), contentDescription = stringResource(R.string.preview_desc), modifier = Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Fit)
                Row { FilterButton("Original", filter == ScanFilter.ORIGINAL) { filter = ScanFilter.ORIGINAL }; FilterButton("Gris", filter == ScanFilter.GRAY) { filter = ScanFilter.GRAY }; FilterButton("N/B", filter == ScanFilter.BW) { filter = ScanFilter.BW } }
                CornerSlider("TL.x", tlx) { tlx = it }; CornerSlider("TL.y", tly) { tly = it }
                CornerSlider("TR.x", trx) { trx = it }; CornerSlider("TR.y", try_) { try_ = it }
                CornerSlider("BR.x", brx) { brx = it }; CornerSlider("BR.y", bry) { bry = it }
                CornerSlider("BL.x", blx) { blx = it }; CornerSlider("BL.y", bly) { bly = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { busy = true; onDone(listOf(preview)) }, enabled = !busy) { Text(stringResource(R.string.validate)) }
                }
            }
        }
    }
}

@Composable
fun FilterButton(t: String, sel: Boolean, onClick: () -> Unit) {
    if (sel) Button(onClick = onClick) { Text(t) }
    else OutlinedButton(onClick = onClick) { Text(t) }
}

@Composable
fun CornerSlider(label: String, v: Float, onV: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(48.dp))
        Slider(value = v, onValueChange = onV, modifier = Modifier.weight(1f).semantics { contentDescription = label })
    }
}
