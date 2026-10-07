package ru.staschig.guitar.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.staschig.guitar.audio.MetronomeEngine
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.TabDoc
import ru.staschig.guitar.data.TabKind
import ru.staschig.guitar.lessons.Curriculum
import java.io.File

private const val ASSET_HOST = "https://appassets.androidplatform.net"

/**
 * Ноты + таб + воспроизведение через alphaTab (assets/alphatab), полностью офлайн.
 * [query] — параметры страницы: "tex=songs/x.alphatex" или "file=/tabfiles/имя".
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlphaTabScreen(title: String, query: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        MetronomeEngine.stop()
        onDispose { view.keepScreenOn = false }
    }
    val loader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/tabfiles/", WebViewAssetLoader.InternalStoragePathHandler(
                context, File(context.filesDir, "tabfiles")))
            .build()
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { webView?.destroy() } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            actions = { actions() },
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? =
                            loader.shouldInterceptRequest(request.url)
                    }
                    loadUrl("$ASSET_HOST/assets/alphatab/index.html?$query")
                    webView = this
                }
            },
        )
    }
}

/** Встроенная пьеса из песенника/урока. */
@Composable
fun PieceScreen(asset: String, onBack: () -> Unit) {
    val title = Curriculum.builtInPieces.firstOrNull { it.asset == asset }?.title ?: "Интерактивный таб"
    AlphaTabScreen(title, "tex=songs/${Uri.encode(asset)}.alphatex", onBack)
}

/** Открывает сохранённый таб нужным просмотрщиком. */
@Composable
fun TabDocScreen(store: AppStore, id: String, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val lib = store.library
    val doc = lib.byId(id)
    if (doc == null) {
        Text("Таб не найден", Modifier.padding(16.dp))
        return
    }
    val context = LocalContext.current
    val delete: @Composable () -> Unit = {
        IconButton(onClick = { lib.delete(doc); onBack() }) { Icon(Icons.Filled.Delete, "Удалить") }
    }
    when (doc.kind) {
        TabKind.GP -> AlphaTabScreen(doc.title, "file=/tabfiles/${Uri.encode(doc.fileName ?: "")}", onBack) {
            OpenExternallyButton(store, doc); delete()
        }
        TabKind.PDF -> PdfScreen(store, doc, onBack) { OpenExternallyButton(store, doc); delete() }
        TabKind.TEXT -> TextTabScreen(doc, onBack) {
            IconButton(onClick = { onEdit(doc.id) }) { Icon(Icons.Filled.Edit, "Редактировать") }
            delete()
        }
        TabKind.OTHER -> {
            LaunchedEffect(doc.id) { openExternally(context, store, doc) }
            SimpleBack(doc.title, onBack) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Этот формат приложение не показывает само — файл открыт во внешнем приложении.")
                    OutlinedButton(onClick = { openExternally(context, store, doc) }) { Text("Открыть ещё раз") }
                    OutlinedButton(onClick = { lib.delete(doc); onBack() }) { Text("Удалить из библиотеки") }
                }
            }
        }
    }
}

@Composable
private fun OpenExternallyButton(store: AppStore, doc: TabDoc) {
    val context = LocalContext.current
    IconButton(onClick = { openExternally(context, store, doc) }) { Icon(Icons.Filled.OpenInNew, "Открыть в другом приложении") }
}

private fun openExternally(context: android.content.Context, store: AppStore, doc: TabDoc) {
    val file = store.library.file(doc) ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Открыть таб")) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleBack(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            actions = { actions() },
        )
        content()
    }
}

/** PDF-таб: страницы рендерятся системным PdfRenderer. */
@Composable
private fun PdfScreen(store: AppStore, doc: TabDoc, onBack: () -> Unit, actions: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val width = context.resources.displayMetrics.widthPixels
    val pages by produceState<List<Bitmap>?>(null, doc.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = store.library.file(doc) ?: return@runCatching emptyList()
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { r ->
                        (0 until minOf(r.pageCount, 40)).map { i ->
                            r.openPage(i).use { page ->
                                val w = (width * 1.5f).toInt()
                                val h = (w.toFloat() * page.height / page.width).toInt()
                                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(AndroidColor.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
    SimpleBack(doc.title, onBack, actions) {
        when {
            pages == null -> Text("Открываю…", Modifier.padding(16.dp))
            pages!!.isEmpty() -> Text("Не удалось открыть PDF", Modifier.padding(16.dp))
            else -> LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
                items(pages!!) { bmp ->
                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                }
            }
        }
    }
}

/** Текстовый таб: моноширинный шрифт, масштаб, автопрокрутка. */
@Composable
private fun TextTabScreen(doc: TabDoc, onBack: () -> Unit, actions: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    var fontSize by rememberSaveable { mutableFloatStateOf(13f) }
    var autoScroll by remember { mutableStateOf(false) }
    var speed by rememberSaveable { mutableFloatStateOf(25f) } // пикселей в секунду
    val vScroll = rememberScrollState()

    LaunchedEffect(autoScroll) {
        var last = 0L
        while (autoScroll) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dy = speed * (now - last) / 1_000_000_000f
                    vScroll.dispatchRawDelta(dy)
                }
                last = now
            }
            if (vScroll.value >= vScroll.maxValue) autoScroll = false
        }
    }

    SimpleBack(doc.title, onBack, actions) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(8f) }) { Text("A−") }
                OutlinedButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(24f) }) { Text("A+") }
                FilterChip(autoScroll, { autoScroll = !autoScroll }, { Text("Прокрутка") })
            }
            if (autoScroll) {
                Slider(speed, { speed = it }, valueRange = 5f..120f, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Text(
                doc.text.orEmpty(),
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.3f).sp,
                softWrap = false,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
    }
}

/** Создание/редактирование текстового таба (в т.ч. сохранённого со страницы сайта). */
@Composable
fun TabEditScreen(store: AppStore, id: String?, onDone: (String?) -> Unit) {
    val lib = store.library
    val initial = remember { id?.let { lib.byId(it) } ?: lib.draft ?: lib.newText("", "") }
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var artist by rememberSaveable { mutableStateOf(initial.artist) }
    var text by rememberSaveable { mutableStateOf(initial.text.orEmpty()) }

    SimpleBack(if (id == null) "Новый таб" else "Редактирование", { lib.draft = null; onDone(null) }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(artist, { artist = it }, label = { Text("Исполнитель") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Text(
                "Оставьте только таб: лишний текст страницы можно удалить прямо здесь.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                text, { text = it },
                label = { Text("Таб") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(autoCorrect = false),
                modifier = Modifier.fillMaxWidth(),
                minLines = 10,
            )
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    val doc = initial.copy(title = title.ifBlank { "Без названия" }, artist = artist, text = text)
                    lib.save(doc)
                    lib.draft = null
                    onDone(doc.id)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить в приложение") }
        }
    }
}
