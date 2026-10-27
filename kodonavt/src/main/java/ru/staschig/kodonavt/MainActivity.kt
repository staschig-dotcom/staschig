package ru.staschig.kodonavt

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Игра по https-адресу, а не file://: так работают модули JS, fetch для Python и localStorage.
        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true) // chrome://inspect на компьютере
        }

        web = WebView(this).apply {
            setBackgroundColor(0xFF0A0E1F.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.textZoom = 100
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val resp = assets.shouldInterceptRequest(request.url) ?: return null
                    // Pyodide и модули JS требуют точных типов; стандартный загрузчик не знает .wasm.
                    // Любой сбой здесь не должен ронять приложение: тогда отдаём ответ загрузчика как есть.
                    return try {
                        val mime = mimeFor(request.url.path ?: "") ?: return resp
                        WebResourceResponse(mime, resp.encoding, resp.data)
                    } catch (e: Exception) {
                        resp
                    }
                }

                // Наружу игра не ходит.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.host != "appassets.androidplatform.net"
            }
        }
        web.addJavascriptInterface(Bridge(), "KodonavtNative")
        setContentView(web)

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    private fun mimeFor(path: String): String? = when (path.substringAfterLast('.', "")) {
        "wasm" -> "application/wasm"
        "js", "mjs" -> "text/javascript"
        "json", "webmanifest" -> "application/json"
        "zip" -> "application/zip"
        "css" -> "text/css"
        "html" -> "text/html"
        "svg" -> "image/svg+xml"
        else -> null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // «Назад» сначала возвращает из уровня в меню, и только из меню закрывает игру.
        web.evaluateJavascript("(window.kodonavtBack ? window.kodonavtBack() : false)") { handled ->
            if (handled != "true") {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    /** Маркер для игры: она запущена как приложение (service worker не нужен). */
    private class Bridge {
        @JavascriptInterface
        fun isApp(): Boolean = true
    }
}
