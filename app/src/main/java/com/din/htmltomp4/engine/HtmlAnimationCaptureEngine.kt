package com.din.htmltomp4.engine

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.unit.dp
import com.din.htmltomp4.data.ConversionConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class FrameCaptureEvent {
    data class Progress(val frameIndex: Int, val totalFrames: Int, val framePath: String) : FrameCaptureEvent()
    data class Done(val frameDir: File, val framesCaptured: Int, val actualDurationMs: Long) : FrameCaptureEvent()
    data class Failed(val error: Throwable) : FrameCaptureEvent()
}

/**
 * Owns the hidden WebView lifecycle and the frame-capture loop.
 *
 * IMPORTANT: the WebView is attached to a real (but off-screen positioned,
 * 1x1-alpha) window-backed container so that PixelCopy has something to read
 * from. A WebView that's never attached to a window will not composite
 * frames for PixelCopy to see.
 */
class HtmlAnimationCaptureEngine(private val activity: Activity) {

    private val pixelCopyHelper = PixelCopyHelper()
    private var webView: WebView? = null
    private var hostContainer: ViewGroup? = null

    /**
     * Loads [htmlFile] into a hidden WebView sized to [config.resolution],
     * waits for pageFinished, then captures one PixelCopy frame per tick,
     * writing sequential JPEGs into a fresh temp directory. Emits progress
     * as a cold Flow so the caller can cancel cleanly (e.g. if the user
     * backs out of Processing screen).
     *
     * DETERMINISTIC CAPTURE: the animation clock is frozen right after
     * load (every CSS animation paused via JS), and before each frame
     * every animation is SEEKED to that frame's exact animation time
     * (N * frameIntervalMs). Frame N therefore shows exactly animation
     * time N / frameRate, no matter how slow the phone captures — the
     * output no longer depends on wall-clock capture speed. Pages driven
     * by requestAnimationFrame will not seek (only CSS animations do).
     */
    fun captureFrames(context: Context, htmlFile: File, config: ConversionConfig): Flow<FrameCaptureEvent> =
        callbackFlow {
            val frameDir = File(context.cacheDir, "frames_${System.currentTimeMillis()}").apply { mkdirs() }
            var frameIndex = 0

            try {
                val view = withContext(Dispatchers.Main) {
                    createHiddenWebView(config.resolution.width, config.resolution.height)
                }

                withContext(Dispatchers.Main) {
                    val loaded = CompletableDeferred<Unit>()
                    view.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(v: WebView?, url: String?) {
                            if (!loaded.isCompleted) loaded.complete(Unit)
                        }
                    }
                    view.loadUrl("file://${htmlFile.absolutePath}")
                    loaded.await()
                    // Small settle delay so CSS animations/webfonts/JS init
                    // have a beat to start from frame 0 consistently.
                    kotlinx.coroutines.delay(150)
                    // Freeze the animation clock: pause every CSS animation
                    // so nothing advances on wall-clock time from here on.
                    // Each frame below is SEEKED to its exact time instead.
                    evalJs(view, "document.getAnimations().forEach(a => a.pause())")
                }

                // Exact per-frame animation time in ms. A Double, so 60fps
                // (16.67ms) and 30fps (33.33ms) don't drift from integer
                // truncation over a long capture.
                val frameIntervalMs = 1000.0 / config.frameRate
                val totalFrames = config.totalFrames
                val captureStartTime = System.currentTimeMillis()

                while (frameIndex < totalFrames) {
                    // Seek every animation to this frame's exact animation
                    // time (frame N shows animation time N / frameRate),
                    // then let the WebView apply the new state before the
                    // synchronous draw below.
                    val targetTimeMs = (frameIndex * frameIntervalMs).toLong()
                    val bitmap = withContext(Dispatchers.Main) {
                        evalJs(view, "document.getAnimations().forEach(a => a.currentTime = $targetTimeMs)")
                        kotlinx.coroutines.delay(16)
                        captureSingleFrame(view, config.resolution.width, config.resolution.height)
                    }

                    val framePath = File(frameDir, "frame_%06d.jpg".format(frameIndex)).absolutePath
                    withContext(Dispatchers.IO) {
                        saveBitmapAsJpeg(bitmap, framePath, quality = 92)
                    }
                    bitmap.recycle()

                    frameIndex++
                    trySend(FrameCaptureEvent.Progress(frameIndex, totalFrames, framePath))
                }

                trySend(FrameCaptureEvent.Done(frameDir, frameIndex, System.currentTimeMillis() - captureStartTime))
            } catch (t: Throwable) {
                trySend(FrameCaptureEvent.Failed(t))
            } finally {
                withContext(Dispatchers.Main) { releaseWebView() }
            }

            // IMPORTANT: without this, the flow never completes — awaitClose
            // below only unblocks on cancellation, so the collector on the
            // ViewModel side would sit forever after the last frame, never
            // moving on to the encoding step (this was the "stuck at
            // Capturing Frame N/N" bug).
            close()

            awaitClose { /* releaseWebView() already called above */ }
        }

    private fun createHiddenWebView(width: Int, height: Int): WebView {
        val view = WebView(activity).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
        }

        // Host it inside the decor view, off-screen but still attached &
        // composited, so PixelCopy has a real window surface to read from.
        val root = activity.window.decorView as ViewGroup
        val container = android.widget.FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(width, height)
            translationX = -10000f // push off screen instead of using 0-alpha/GONE
        }
        container.addView(view, ViewGroup.LayoutParams(width, height))
        root.addView(container, ViewGroup.LayoutParams(width, height))

        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)

        hostContainer = container
        webView = view
        return view
    }

    /**
     * Runs [script] in the WebView and suspends until it completes.
     * Must be called from the main thread (evaluateJavascript requires it).
     */
    private suspend fun evalJs(view: WebView, script: String) {
        val done = CompletableDeferred<Unit>()
        view.evaluateJavascript(script) { done.complete(Unit) }
        done.await()
    }

    private fun captureSingleFrame(view: WebView, width: Int, height: Int): Bitmap {
        // NOTE: we deliberately do NOT use PixelCopy.request(window, ...) here.
        // The capture WebView is intentionally positioned off-screen
        // (translationX = -10000f) so it's invisible to the user, but that
        // also means it falls outside the Activity window's actual composited
        // surface bounds — PixelCopy-from-Window can only read pixels that
        // are within the visible screen area, so it would fail every time
        // for an off-screen view. Drawing the view directly into a software
        // Canvas sidesteps that entirely: it renders the view's current
        // content regardless of where it sits on screen. Paired with
        // LAYER_TYPE_SOFTWARE on the WebView, this reliably captures full
        // frames including CSS/JS-driven animation state.
        return pixelCopyHelper.captureFallback(view, width, height)
    }

    private fun saveBitmapAsJpeg(bitmap: Bitmap, path: String, quality: Int) {
        FileOutputStream(path).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    }

    /** Detaches and destroys the hidden WebView. Always call from main thread. */
    fun releaseWebView() {
        webView?.apply {
            stopLoading()
            clearHistory()
            destroy()
        }
        (hostContainer?.parent as? ViewGroup)?.removeView(hostContainer)
        webView = null
        hostContainer = null
    }

    /** Deletes the temp JPEG frame directory after a successful/failed encode. */
    fun cleanupFrames(frameDir: File) {
        frameDir.listFiles()?.forEach { it.delete() }
        frameDir.delete()
    }
}