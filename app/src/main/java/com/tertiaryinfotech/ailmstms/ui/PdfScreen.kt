package com.tertiaryinfotech.ailmstms.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** A material shown inside the app: downloading, then a zoomable column of pages. */
@Composable
fun PdfScreen(vm: LmsViewModel, screen: Screen.Pdf) {
    val context = LocalContext.current
    when (val state = vm.pdfState) {
        is Load.Ready -> PdfPages(state.value, onOpenInBrowser = { openLink(context, screen.url) }, onUnreadable = { vm.pdfFallback(screen) })
        is Load.Failed -> Column(Modifier.padding(20.dp)) { ErrorCard(state.message) }
        else -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            val progress = vm.pdfProgress
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(if (progress == null) "Opening ${screen.name}…" else "Downloading ${screen.name} · ${(progress * 100).toInt()}%")
            TextButton(onClick = { vm.back(); openLink(context, screen.url) }) { Text("Open in browser instead") }
        }
    }
}

/** Renders pages on demand. PdfRenderer allows one open page at a time, so access is serialised. */
private class PdfPagesSource(file: File) {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val lock = Mutex()
    private var closed = false
    val pageCount: Int = renderer.pageCount

    /** Width divided by height for each page, read once so the list can reserve space before rendering. */
    val ratios: List<Float> = (0 until pageCount).map { index ->
        renderer.openPage(index).use { page -> page.width.toFloat() / page.height.coerceAtLeast(1) }
    }

    suspend fun render(index: Int, width: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed) return@withLock null
            renderer.openPage(index).use { page ->
                val height = (width / (page.width.toFloat() / page.height.coerceAtLeast(1))).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    suspend fun close() = lock.withLock {
        if (!closed) {
            closed = true
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }
}

@Composable
private fun PdfPages(file: File, onOpenInBrowser: () -> Unit, onUnreadable: () -> Unit) {
    // An encrypted or damaged file cannot be rendered: the caller falls back to the browser.
    val opened by produceState<Result<PdfPagesSource>?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { PdfPagesSource(file) } }
    }
    val source = opened?.getOrNull()
    DisposableEffect(source) {
        // Closed off the main thread: a page may still be rendering.
        onDispose { source?.let { CoroutineScope(Dispatchers.IO).launch { it.close() } } }
    }
    if (opened == null) {
        LoadingCard("Opening…")
        return
    }
    if (source == null || source.pageCount == 0) {
        LaunchedEffect(file) { onUnreadable() }
        return
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    val list = rememberLazyListState()
    val current by remember { derivedStateOf { list.firstVisibleItemIndex + 1 } }
    val sideways = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
                // Two fingers zoom; one finger is left to the lists so scrolling stays natural.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } > 1) {
                                zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val viewport = maxWidth
            // Pages are rendered once at up to one and a half times the screen width, enough to stay sharp when zoomed.
            val renderWidth = with(LocalDensity.current) { (viewport.toPx() * 1.5f).toInt().coerceIn(600, 1600) }
            Box(Modifier.fillMaxSize().horizontalScroll(sideways, enabled = zoom > 1f)) {
                LazyColumn(
                    Modifier.width(viewport * zoom).testTag("pdf-pages"), state = list,
                    contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(source.pageCount, key = { it }) { index ->
                        val bitmap by produceState<Bitmap?>(null, source, index, renderWidth) {
                            value = runCatching { source.render(index, renderWidth) }.getOrNull()
                        }
                        Surface(shape = RoundedCornerShape(4.dp), color = Color.White, shadowElevation = 2.dp) {
                            Box(Modifier.fillMaxWidth().aspectRatio(source.ratios[index].coerceIn(0.2f, 5f)), contentAlignment = Alignment.Center) {
                                val page = bitmap
                                if (page == null) CircularProgressIndicator()
                                else Image(page.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            }
                        }
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Page $current of ${source.pageCount}", Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) }, enabled = zoom > 1f) { Icon(ZoomOutIcon, "Zoom out") }
                IconButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(4f) }, enabled = zoom < 4f) { Icon(ZoomInIcon, "Zoom in") }
                TextButton(onClick = onOpenInBrowser) { Text("Open in browser") }
            }
        }
    }
}

/** A small, clearly fictional PDF drawn on the device, so the demo shows the viewer without any download. */
object DemoPdf {
    suspend fun create(context: Context, title: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "demo").apply { mkdirs() }
        val file = File(dir, "sample-v2-${title.hashCode().toUInt()}.pdf")
        if (file.isFile && file.length() > 0) return@withContext file
        val document = PdfDocument()
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE; textSize = 40f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(201, 218, 255); textSize = 16f }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(17, 26, 43); textSize = 20f }
        val band = Paint().apply { color = AndroidColor.rgb(10, 61, 143) }
        val pages = listOf(
            "Welcome" to listOf("This is a fictional sample document for the TIA TMS demo.", "Real courseware opens here in the same viewer:", "scroll through the pages and pinch to zoom."),
            "Learning outcomes" to listOf("1. Describe the building blocks of an AI application.", "2. Build a small assistant from a worked example.", "3. Evaluate the answers it gives."),
            "Activity" to listOf("Work in pairs. Sketch the steps your assistant", "takes to answer one question from a customer.", "Share one improvement with the class."),
        )
        try {
            pages.forEachIndexed { index, (name, lines) ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                val canvas = page.canvas
                canvas.drawColor(AndroidColor.WHITE)
                canvas.drawRect(0f, 0f, 595f, 170f, band)
                canvas.drawText("DEMO · $title", 40f, 60f, label)
                canvas.drawText(name, 40f, 125f, heading)
                lines.forEachIndexed { i, line -> canvas.drawText(line, 40f, 240f + i * 36f, body) }
                canvas.drawText("Sample page ${index + 1} of ${pages.size} · Fictional content", 40f, 800f, body.apply { textSize = 12f })
                body.textSize = 20f
                document.finishPage(page)
            }
            file.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
        file
    }
}
