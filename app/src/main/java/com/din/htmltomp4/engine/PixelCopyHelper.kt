package com.din.htmltomp4.engine

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps the PixelCopy API to capture a single high-fidelity frame from a
 * (hidden / off-screen) View such as our animation WebView.
 *
 * Two capture paths are supported:
 *  - Window-based (Android's documented, GPU-composited path) — used when the
 *    WebView is attached to a real (even 1x1 / off-screen-positioned) window.
 *  - View-based fallback via View#getBitmap for API levels or view types where
 *    PixelCopy against a bare View isn't reliable (used only as last resort,
 *    since it can render blank on hardware-accelerated WebViews pre-API 26).
 *
 * All PixelCopy requests MUST be issued from the main thread, and the
 * listener also fires on a Handler you provide — we bounce back into a
 * coroutine so calling code can just `capture()` sequentially in a loop.
 */
class PixelCopyHelper(private val handler: Handler = Handler(Looper.getMainLooper())) {

    /**
     * Captures the current contents of [view] within its own [window] into a
     * new ARGB_8888 bitmap sized [width]x[height].
     *
     * Must be called from the main thread (Compose/ViewModel code should
     * launch this from Dispatchers.Main).
     */
    suspend fun capture(window: Window, view: View, width: Int, height: Int): Bitmap {
        require(Looper.myLooper() == Looper.getMainLooper()) {
            "PixelCopyHelper.capture() must be invoked from the main thread"
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        return suspendCancellableCoroutine { cont ->
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val srcRect = android.graphics.Rect(
                location[0],
                location[1],
                location[0] + view.width,
                location[1] + view.height
            )

            try {
                PixelCopy.request(
                    window,
                    srcRect,
                    bitmap,
                    { result ->
                        if (result == PixelCopy.SUCCESS) {
                            cont.resume(bitmap)
                        } else {
                            bitmap.recycle()
                            cont.resumeWithException(
                                IllegalStateException("PixelCopy failed with result code $result")
                            )
                        }
                    },
                    handler
                )
            } catch (t: Throwable) {
                bitmap.recycle()
                cont.resumeWithException(t)
            }
        }
    }

    /**
     * Fallback capture path using View#draw into a software Canvas. Lower
     * fidelity for hardware-accelerated content but works without a Window
     * reference (e.g. if the WebView is never actually attached to a
     * visible window). Prefer [capture] whenever a Window is available.
     */
    fun captureFallback(view: View, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    companion object {
        val isPixelCopyForViewsSupported: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O // API 26
    }
}
