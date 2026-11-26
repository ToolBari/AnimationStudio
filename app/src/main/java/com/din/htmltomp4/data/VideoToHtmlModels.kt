package com.din.htmltomp4.data

import android.net.Uri

/** User-selectable settings for extracting a video into a playable HTML file. */
data class VideoToHtmlConfig(
    // Shorts default per FIX_SPEC: 24 FPS / 720p / Medium.
    // 12 FPS is choppy, 30 FPS makes the file much bigger.
    val targetFps: Int = 24,
    val scale: HtmlScale = HtmlScale.P720,
    val quality: HtmlFrameQuality = HtmlFrameQuality.MEDIUM,
    val limitToSeconds: Int? = null,   // null = full video length
    val outputFileName: String = "video_export_${System.currentTimeMillis()}"
)

enum class HtmlScale(val width: Int, val label: String) {
    P360(360, "360p"),
    P480(480, "480p"),
    P720(720, "720p"),
    P1080(1080, "1080p")
}

enum class HtmlFrameQuality(val jpegQuality: Int, val label: String) {
    LOW(50, "Low (smaller file)"),
    MEDIUM(70, "Medium"),
    HIGH(85, "High (larger file)")
}

/** Drives the Processing screen UI for the video → HTML direction. */
sealed class VideoToHtmlState {
    data object Idle : VideoToHtmlState()

    data class ExtractingFrames(
        val currentFrame: Int,
        val totalFrames: Int,
        val previewFramePath: String? = null
    ) : VideoToHtmlState() {
        val progress: Float get() = if (totalFrames == 0) 0f else currentFrame / totalFrames.toFloat()
    }

    data class BuildingHtml(val progressPercent: Int) : VideoToHtmlState()

    data class Success(
        val outputPath: String,
        val outputUri: Uri,
        val frameCount: Int,
        val fileSizeBytes: Long
    ) : VideoToHtmlState()

    data class Error(val message: String, val throwable: Throwable? = null) : VideoToHtmlState()
}
