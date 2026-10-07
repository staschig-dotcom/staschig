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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.SavedTab
import ru.staschig.guitar.lessons.Curriculum

/**
 * Встроенный браузер для сайта с табами. Сайт не парсится — страница показывается как есть,
 * а приложение лишь запоминает ссылки (это устойчиво к изменениям вёрстки сайта).
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabBrowserScreen(store: AppStore, startUrl: String, lessonId: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    var pageTitle by remember { mutableStateOf("Загрузка…") }
    var pageUrl by remember { mutableStateOf(startUrl.ifEmpty { Curriculum.TABS_LIBRARY_URL }) }
    var loading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }

    fun save(forLesson: Boolean) {
        store.addTab(SavedTab(pageTitle, pageUrl, if (forLesson) lessonId else null))
        Toast.makeText(context, if (forLesson) "Прикреплено к уроку" else "Сохранено в «Табы»", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            },
            actions = {
                if (lessonId != null) {
                    IconButton(onClick = { save(true) }) { Icon(Icons.Filled.AttachFile, "Прикрепить к уроку") }
                }
                IconButton(onClick = { save(false) }) { Icon(Icons.Filled.Star, "В закладки") }
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)))
                }) { Icon(Icons.Filled.OpenInBrowser, "Открыть в браузере") }
            },
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
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
                    // Файлы (Guitar Pro, PDF) отдаём системе — откроются в Songsterr/Guitar Pro/просмотрщике.
                    setDownloadListener { url, _, _, _, _ ->
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }
                    loadUrl(pageUrl)
                    webView = this
                }
            },
        )
    }
}
