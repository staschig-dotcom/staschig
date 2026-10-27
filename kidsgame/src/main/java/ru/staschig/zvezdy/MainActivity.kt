package ru.staschig.zvezdy

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var web: WebView

    // Голос: стандартный синтезатор речи Android (без интернета, если на телефоне есть русский голос).
    private var tts: TextToSpeech? = null
    @Volatile private var ttsState = "init"

    // Раскраска по фото: игра просит картинку, а снимок делает системная камера или берётся из галереи.
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val photoFile by lazy { File(File(cacheDir, "photos").apply { mkdirs() }, "photo.jpg") }

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
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return openPhotoChooser()
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

    private fun openPhotoChooser(): Boolean {
        photoFile.delete()
        val gallery = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        val camera = try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", photoFile)
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            null
        }
        val chooser = Intent.createChooser(gallery, "Фото для раскраски").apply {
            if (camera != null) putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
        }
        return try {
            @Suppress("DEPRECATION")
            startActivityForResult(chooser, PICK_PHOTO)
            true
        } catch (e: Exception) {
            fileCallback = null
            false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_PHOTO) return
        val callback = fileCallback ?: return
        fileCallback = null
        val uri = when {
            resultCode != RESULT_OK -> null
            data?.data != null -> data.data
            photoFile.length() > 0 -> FileProvider.getUriForFile(this, "$packageName.files", photoFile)
            else -> null
        }
        callback.onReceiveValue(uri?.let { arrayOf(it) })
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
        // Снимок нужен только на время раскраски: на устройстве он не остаётся.
        photoFile.delete()
        tts?.shutdown()
        web.destroy()
        super.onDestroy()
    }

    private companion object {
        const val PICK_PHOTO = 41
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
