package com.din.htmltomp4.data

import android.net.Uri

/** User-selectable output settings from the Configuration screen. */
data class ConversionConfig(
    val resolution: Resolution = Resolution.HD_1080P,
    val frameRate: Int = 30,           // 30 or 60
    val durationSeconds: Int = 5,      // how long to record the animation
    val outputFileName: String = "animation_${System.currentTimeMillis()}"
) {
    val totalFrames: Int get() = frameRate * durationSeconds
}

enum class Resolution(val width: Int, val height: Int, val label: String) {
    HD_720P(1280, 720, "720p"),
    HD_1080P(1920, 1080, "1080p")
}

/** Drives the Processing screen UI. */
sealed class ConversionState {
    data object Idle : ConversionState()

    data class Capturing(
        val currentFrame: Int,
        val totalFrames: Int,
        val previewBitmapPath: String? = null
    ) : ConversionState() {
        val progress: Float get() = if (totalFrames == 0) 0f else currentFrame / totalFrames.toFloat()
    }

    data class Encoding(val progressPercent: Int) : ConversionState()

    data class Success(val outputPath: String, val outputUri: Uri) : ConversionState()

    data class Error(val message: String, val throwable: Throwable? = null) : ConversionState()
}

/** One row in the "Recent conversions" list on the Dashboard. */
enum class ConversionType { HTML_TO_MP4, VIDEO_TO_HTML }

data class ConversionHistoryItem(
    val id: String,
    val fileName: String,
    val thumbnailPath: String?,
    val outputPath: String,
    val resolution: String,
    val durationSeconds: Int,
    val createdAtMillis: Long,
    val type: ConversionType = ConversionType.HTML_TO_MP4
)
