package ru.staschig.zvezdy

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var web: WebView

    // Голос: стандартный синтезатор речи Android (без интернета, если на телефоне есть русский голос).
    private var tts: TextToSpeech? = null
    @Volatile private var ttsState = "init"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Игра по https-адресу, а не file://, чтобы localStorage (прогресс, рисунки) работал надёжно.
        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this).apply {
            setBackgroundColor(0xFF121838.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.textZoom = 100
            isHapticFeedbackEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assets.shouldInterceptRequest(request.url)

                // Наружу игра не ходит: любые внешние ссылки блокируются.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.host != "appassets.androidplatform.net"
            }
        }
        // Монотонные часы для ограничения времени игры: перевод часов на телефоне их не меняет.
        val version = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        tts = TextToSpeech(this) { status ->
            ttsState = if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("ru", "RU")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) "nolang" else "ok"
            } else "none"
        }
        web.addJavascriptInterface(Bridge(version), "UhuNative")
        setContentView(web)
        hideSystemBars()

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    // Код прогресса отправляется через стандартное меню «Поделиться» (например, себе в мессенджер).
    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Прогресс Совёнка Уху")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Сохранить код прогресса"))
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Кнопка «Назад» сначала возвращает на карту, и только с карты закрывает игру.
        web.evaluateJavascript("(window.uhuBack ? window.uhuBack() : false)") { handled ->
            if (handled != "true") {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        tts?.stop()
        web.evaluateJavascript("window.uhuPause && window.uhuPause()", null)
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        web.evaluateJavascript("window.uhuResume && window.uhuResume()", null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        tts?.shutdown()
        web.destroy()
        super.onDestroy()
    }

    /** Мост для игры: часы, версия, «Поделиться» и голос. */
    private inner class Bridge(private val versionName: String) {
        @JavascriptInterface
        fun uptime(): Long = SystemClock.elapsedRealtime()

        @JavascriptInterface
        fun version(): String = versionName

        @JavascriptInterface
        fun share(text: String) = runOnUiThread { shareText(text) }

        @JavascriptInterface
        fun ttsStatus(): String = ttsState

        @JavascriptInterface
        fun speak(text: String, rate: Double) {
            val t = tts ?: return
            if (ttsState != "ok") return
            t.setSpeechRate(rate.toFloat())
            t.setPitch(1.1f)
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "uhu")
        }

        @JavascriptInterface
        fun stop() {
            tts?.stop()
        }
    }
}
