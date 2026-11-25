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
     * waits for pageFinished, then walks the animation clock forward and
     * captures one PixelCopy frame per tick, writing sequential JPEGs into
     * a fresh temp directory. Emits progress as a cold Flow so the caller
     * can cancel cleanly (e.g. if the user backs out of Processing screen).
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
                }

                val frameIntervalMs = 1000L / config.frameRate
                val totalFrames = config.totalFrames
                val captureStartTime = System.currentTimeMillis()

                while (frameIndex < totalFrames) {
                    val bitmap = withContext(Dispatchers.Main) {
                        captureSingleFrame(view, config.resolution.width, config.resolution.height)
                    }

                    val framePath = File(frameDir, "frame_%06d.jpg".format(frameIndex)).absolutePath
                    withContext(Dispatchers.IO) {
                        saveBitmapAsJpeg(bitmap, framePath, quality = 92)
                    }
                    bitmap.recycle()

                    frameIndex++
                    trySend(FrameCaptureEvent.Progress(frameIndex, totalFrames, framePath))

                    // Target absolute elapsed time (frameIndex * interval) rather
                    // than sleeping a flat interval after each frame. Capturing
                    // + JPEG-encoding a frame takes real time on its own — if we
                    // just slept frameIntervalMs on top of that every iteration,
                    // the WebView's real (wall-clock-driven) animation would
                    // advance further per output frame than intended, so
                    // playback at the declared FPS ends up looking sped up.
                    // Sleeping only the remainder to hit the next absolute
                    // target keeps output frame N aligned with real elapsed
                    // time N * frameIntervalMs, and any per-frame overhead that
                    // can't be caught up on gets absorbed instead of compounding.
                    val targetElapsed = frameIndex * frameIntervalMs
                    val actualElapsed = System.currentTimeMillis() - captureStartTime
                    val remaining = targetElapsed - actualElapsed
                    if (remaining > 0) {
                        withContext(Dispatchers.Main) {
                            kotlinx.coroutines.delay(remaining)
                        }
                    }
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