package com.din.htmltomp4.engine

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

sealed class EncodeEvent {
    data class Progress(val percent: Int, val frame: Int, val fps: Float) : EncodeEvent()
    data class Success(val outputFile: File) : EncodeEvent()
    data class Failed(val logs: String, val returnCode: Int?) : EncodeEvent()
}

/**
 * Thin wrapper around ffmpeg-kit's async command execution, exposed as a
 * cold Flow so the ViewModel can collect progress the same way it does for
 * frame capture.
 *
 * Command shape (adapted from the original spec):
 *   -framerate [fps] -i [frame_path_pattern] -c:v mpeg4 -pix_fmt yuv420p [output_path]
 *   (originally libx264, swapped to mpeg4 — see comment in encode() for why)
 *
 * We additionally set -crf for quality and -movflags +faststart so the MP4
 * is immediately seekable/streamable (needed for the in-app preview player).
 */
class FFmpegEncoder {

    fun encode(
        frameDir: File,
        fps: Double,
        totalFrames: Int,
        outputFile: File
    ): Flow<EncodeEvent> = callbackFlow {
        val framePattern = File(frameDir, "frame_%06d.jpg").absolutePath
        val fpsArg = String.format(java.util.Locale.US, "%.3f", fps)
        val command = buildString {
            append("-y ")                                   // overwrite if exists
            append("-framerate $fpsArg ")
            append("-i \"$framePattern\" ")
            // NOTE: libx264 is GPL-licensed and NOT included in the
            // ffmpeg-kit-16kb LGPL build (Maven Central only publishes the
            // LGPL variant for this fork — libx264/libx265 require a
            // separately-built -gpl artifact that isn't on Central). mpeg4
            // is bundled in every FFmpeg build regardless of license
            // variant, so it works out of the box here. If you later swap
            // in a GPL-flavored ffmpeg-kit build, you can switch this back
            // to "-c:v libx264 -preset medium -crf 20" for better
            // compression at the same quality.
            append("-c:v mpeg4 ")
            append("-q:v 3 ")                                // quality: 1 (best) - 31 (worst), 3 is visually near-lossless
            append("-pix_fmt yuv420p ")
            append("-movflags +faststart ")
            append("\"${outputFile.absolutePath}\"")
        }

        val session = FFmpegKit.executeAsync(
            command,
            { session ->
                val rc = session.returnCode
                if (ReturnCode.isSuccess(rc)) {
                    trySend(EncodeEvent.Success(outputFile))
                } else {
                    trySend(EncodeEvent.Failed(session.allLogsAsString ?: "Unknown FFmpeg error", rc?.value))
                }
                close()
            },
            { log -> /* per-line ffmpeg log, useful for debugging, omitted from UI */ },
            { statistics: Statistics ->
                val percent = if (totalFrames > 0) {
                    ((statistics.videoFrameNumber.toFloat() / totalFrames) * 100).toInt().coerceIn(0, 100)
                } else 0
                trySend(EncodeEvent.Progress(percent, statistics.videoFrameNumber, statistics.videoFps))
            }
        )

        awaitClose {
            // Cancel the ffmpeg session if the collecting coroutine is torn down early
            FFmpegKit.cancel(session.sessionId)
        }
    }

    companion object {
        /** Call once (e.g. in Application.onCreate) if you need custom logging/session config. */
        fun configureLogging(enabled: Boolean) {
            if (!enabled) {
                FFmpegKitConfig.enableLogCallback(null)
            }
        }
    }
}