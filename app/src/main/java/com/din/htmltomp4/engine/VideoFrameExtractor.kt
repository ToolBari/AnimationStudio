package com.din.htmltomp4.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.din.htmltomp4.data.HtmlFrameQuality
import com.din.htmltomp4.data.HtmlScale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

sealed class FrameExtractEvent {
    data class Progress(val currentFrame: Int, val totalFrames: Int, val framePath: String) : FrameExtractEvent()
    data class Done(val frameDir: File, val frameCount: Int) : FrameExtractEvent()
    data class Failed(val error: Throwable) : FrameExtractEvent()
}

/**
 * Extracts a video into a numbered JPEG frame sequence via FFmpeg, at a
 * reduced FPS/resolution/quality so the resulting frame set stays a
 * reasonable size once [HtmlFromFramesBuilder] base64-embeds it.
 */
class VideoFrameExtractor {

    /** Probes the source video's duration in seconds via MediaMetadataRetriever (no FFmpeg needed for this). */
    fun probeDurationSeconds(context: Context, videoUri: android.net.Uri): Double {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ms / 1000.0
        } finally {
            retriever.release()
        }
    }

    fun extractFrames(
        videoFile: File,
        outputDir: File,
        fps: Int,
        scale: HtmlScale,
        quality: HtmlFrameQuality,
        limitSeconds: Int?,
        expectedDurationSeconds: Double
    ): Flow<FrameExtractEvent> = callbackFlow {
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val effectiveDuration = limitSeconds?.toDouble()?.coerceAtMost(
            if (expectedDurationSeconds > 0) expectedDurationSeconds else Double.MAX_VALUE
        ) ?: expectedDurationSeconds
        val totalFrames = if (effectiveDuration > 0) {
            kotlin.math.ceil(effectiveDuration * fps).toInt().coerceAtLeast(1)
        } else {
            1
        }

        val framePattern = File(outputDir, "frame_%05d.jpg").absolutePath
        // ffmpeg qscale (-q:v) runs 2 (best) .. 31 (worst); map our 0-100
        // "quality" concept onto that range.
        val qv = (2 + (100 - quality.jpegQuality) * 29.0 / 100).toInt().coerceIn(2, 31)

        val command = buildString {
            append("-y -i \"${videoFile.absolutePath}\" ")
            if (limitSeconds != null) append("-t $limitSeconds ")
            append("-vf \"fps=$fps,scale=${scale.width}:-2\" ")
            append("-q:v $qv ")
            append("\"$framePattern\"")
        }

        val session = FFmpegKit.executeAsync(
            command,
            { session ->
                val rc = session.returnCode
                if (ReturnCode.isSuccess(rc)) {
                    val count = outputDir.listFiles { f -> f.extension.equals("jpg", true) }?.size ?: 0
                    trySend(FrameExtractEvent.Done(outputDir, count))
                } else {
                    trySend(FrameExtractEvent.Failed(
                        IllegalStateException(session.allLogsAsString?.takeLast(400) ?: "Unknown FFmpeg error")
                    ))
                }
                close()
            },
            { /* per-line log, omitted from UI */ },
            { statistics: Statistics ->
                val current = statistics.videoFrameNumber.coerceAtMost(totalFrames)
                if (current > 0) {
                    val framePath = File(outputDir, "frame_%05d.jpg".format(current)).absolutePath
                    trySend(FrameExtractEvent.Progress(current, totalFrames, framePath))
                }
            }
        )

        awaitClose { FFmpegKit.cancel(session.sessionId) }
    }

    fun cleanup(frameDir: File) {
        frameDir.deleteRecursively()
    }

    /**
     * Extracts the video's audio track as AAC-in-M4A via FFmpeg. Returns
     * null (not a failure) if the source has no audio stream at all —
     * callers should fall back to a silent export rather than failing the
     * whole conversion over a video that was never going to have sound.
     */
    fun extractAudio(videoFile: File, outputFile: File): File? {
        outputFile.parentFile?.mkdirs()
        val command = "-y -i \"${videoFile.absolutePath}\" -vn -c:a aac -b:a 128k \"${outputFile.absolutePath}\""
        val session = FFmpegKit.execute(command)
        return if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0) {
            outputFile
        } else {
            outputFile.delete()
            null
        }
    }
}
