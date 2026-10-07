package ru.staschig.guitar.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.json.JSONArray
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.SavedTab
import ru.staschig.guitar.lessons.Curriculum

/** Достаёт со страницы текст таба: блоки <pre>, выделение или основной текст статьи. */
private const val EXTRACT_JS = """
(function () {
  var pre = Array.prototype.slice.call(document.querySelectorAll('pre'))
    .map(function (e) { return e.innerText; })
    .filter(function (t) { return t.trim().length > 0; });
  if (pre.length) return pre.join('\n\n');
  var sel = window.getSelection ? String(window.getSelection()) : '';
  if (sel.trim().length) return sel;
  var main = document.querySelector('article, .entry-content, .post-content, main');
  return (main || document.body).innerText;
})()
"""

/**
 * Встроенный браузер для сайта с табами. Страница показывается как есть; со страницы можно
 * сохранить таб в офлайн-библиотеку: файлы (Guitar Pro, PDF) скачиваются, текст копируется.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabBrowserScreen(
    store: AppStore,
    startUrl: String,
    lessonId: String?,
    onBack: () -> Unit,
    onOpenDoc: (String) -> Unit,
    onEditDraft: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageTitle by remember { mutableStateOf("Загрузка…") }
    var pageUrl by remember { mutableStateOf(startUrl.ifEmpty { Curriculum.TABS_LIBRARY_URL }) }
    var loading by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val openDoc by rememberUpdatedState(onOpenDoc)

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }

    fun bookmark(forLesson: Boolean) {
        store.addTab(SavedTab(pageTitle, pageUrl, if (forLesson) lessonId else null))
        Toast.makeText(context, if (forLesson) "Ссылка прикреплена к уроку" else "Ссылка сохранена", Toast.LENGTH_SHORT).show()
    }

    fun saveText() {
        webView?.evaluateJavascript(EXTRACT_JS) { json ->
            val text = runCatching { JSONArray("[$json]").getString(0) }.getOrNull().orEmpty().trim()
            if (text.isEmpty()) {
                Toast.makeText(context, "На странице нет текста таба. Если таб — файл, нажмите «Скачать».", Toast.LENGTH_LONG).show()
            } else {
                store.library.draft = store.library.newText(pageTitle, text, pageUrl, lessonId)
                onEditDraft()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            },
            actions = {
                IconButton(onClick = { saveText() }) { Icon(Icons.Filled.DownloadForOffline, "Сохранить таб в приложение") }
                if (lessonId != null) {
                    IconButton(onClick = { bookmark(true) }) { Icon(Icons.Filled.AttachFile, "Прикрепить ссылку к уроку") }
                }
                IconButton(onClick = { bookmark(false) }) { Icon(Icons.Filled.Star, "В закладки") }
                IconButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl))) }
                }) { Icon(Icons.Filled.OpenInBrowser, "Открыть в браузере") }
            },
        )
        if (loading || downloading) LinearProgressIndicator(Modifier.fillMaxWidth())
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            loading = true
                            pageUrl = url
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            pageUrl = url
                            pageTitle = view.title?.ifBlank { null } ?: url
                        }
                    }
                    // Файлы табов (Guitar Pro, PDF) скачиваются прямо в офлайн-библиотеку.
                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                        downloading = true
                        Toast.makeText(ctx, "Скачиваю таб в приложение…", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            runCatching {
                                store.library.download(url, userAgent, contentDisposition, mimeType, lessonId)
                            }.onSuccess { doc ->
                                downloading = false
                                Toast.makeText(ctx, "Сохранено: ${doc.title}", Toast.LENGTH_SHORT).show()
                                openDoc(doc.id)
                            }.onFailure { e ->
                                downloading = false
                                Toast.makeText(ctx, "Не удалось скачать: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    loadUrl(pageUrl)
                    webView = this
                }
            },
        )
    }
}
