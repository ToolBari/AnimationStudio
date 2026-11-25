package com.din.htmltomp4.viewmodel

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.din.htmltomp4.data.VideoToHtmlConfig
import com.din.htmltomp4.data.VideoToHtmlState
import com.din.htmltomp4.engine.FrameExtractEvent
import com.din.htmltomp4.engine.HtmlBuildEvent
import com.din.htmltomp4.engine.HtmlFromFramesBuilder
import com.din.htmltomp4.engine.VideoFrameExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class VideoToHtmlViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<VideoToHtmlState>(VideoToHtmlState.Idle)
    val state: StateFlow<VideoToHtmlState> = _state.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    private val _config = MutableStateFlow(VideoToHtmlConfig())
    val config: StateFlow<VideoToHtmlConfig> = _config.asStateFlow()

    private var job: Job? = null
    private val extractor = VideoFrameExtractor()
    private val builder = HtmlFromFramesBuilder()

    fun onVideoSelected(uri: Uri) {
        _selectedVideoUri.value = uri
    }

    fun updateConfig(newConfig: VideoToHtmlConfig) {
        _config.value = newConfig
    }

    fun startConversion() {
        if (job?.isActive == true) return
        val uri = _selectedVideoUri.value ?: return
        val cfg = _config.value

        job = viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                // Copy the picked video into app cache so FFmpeg gets a plain
                // file path instead of a content:// Uri (avoids SAF quirks).
                val videoFile = withContext(Dispatchers.IO) { copyVideoToCache(context, uri) }

                val durationSec = extractor.probeDurationSeconds(context, uri)
                val frameDir = File(context.cacheDir, "video_frames_${System.currentTimeMillis()}")

                var frameDirResult: File? = null

                extractor.extractFrames(
                    videoFile = videoFile,
                    outputDir = frameDir,
                    fps = cfg.targetFps,
                    scale = cfg.scale,
                    quality = cfg.quality,
                    limitSeconds = cfg.limitToSeconds,
                    expectedDurationSeconds = durationSec
                ).collect { event ->
                    when (event) {
                        is FrameExtractEvent.Progress -> {
                            _state.value = VideoToHtmlState.ExtractingFrames(
                                currentFrame = event.currentFrame,
                                totalFrames = event.totalFrames,
                                previewFramePath = event.framePath
                            )
                        }
                        is FrameExtractEvent.Done -> {
                            frameDirResult = event.frameDir
                        }
                        is FrameExtractEvent.Failed -> {
                            val detail = "${event.error.javaClass.simpleName}: ${event.error.message}"
                            _state.value = VideoToHtmlState.Error("Frame extraction failed — $detail", event.error)
                        }
                    }
                }

                val extractedDir = frameDirResult ?: return@launch
                if (_state.value is VideoToHtmlState.Error) return@launch

                _state.value = VideoToHtmlState.BuildingHtml(0)

                // Extract the audio track too (if the source has one) so
                // the exported HTML isn't silent. Runs quickly compared to
                // frame extraction since it's just an audio re-encode, no
                // per-frame image work — a plain synchronous FFmpeg call is
                // fine here rather than a progress-reporting Flow.
                val audioFile = File(context.cacheDir, "audio_extract_${System.currentTimeMillis()}.m4a")
                val extractedAudio = withContext(Dispatchers.IO) {
                    extractor.extractAudio(videoFile, audioFile)
                }

                val outputDir = File(context.getExternalFilesDir(null), "html_exports").apply { mkdirs() }
                val outputFile = File(outputDir, "${cfg.outputFileName}.html")

                builder.build(extractedDir, cfg.targetFps, outputFile, extractedAudio).collect { event ->
                    when (event) {
                        is HtmlBuildEvent.Progress -> {
                            _state.value = VideoToHtmlState.BuildingHtml(event.percent)
                        }
                        is HtmlBuildEvent.Done -> {
                            val uriForFile = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                event.outputFile
                            )
                            val frameCount = extractedDir.listFiles()?.size ?: 0
                            _state.value = VideoToHtmlState.Success(
                                outputPath = event.outputFile.absolutePath,
                                outputUri = uriForFile,
                                frameCount = frameCount,
                                fileSizeBytes = event.outputFile.length()
                            )
                            extractor.cleanup(extractedDir)
                            extractedAudio?.delete()
                        }
                        is HtmlBuildEvent.Failed -> {
                            _state.value = VideoToHtmlState.Error(
                                "Building HTML failed: ${event.error.message}", event.error
                            )
                            extractor.cleanup(extractedDir)
                            extractedAudio?.delete()
                        }
                    }
                }
            } catch (t: Throwable) {
                _state.value = VideoToHtmlState.Error(t.message ?: "Unknown error", t)
            }
        }
    }

    private suspend fun copyVideoToCache(context: android.content.Context, uri: Uri): File {
        val destFile = File(context.cacheDir, "input_video_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Could not open the selected video")
        return destFile
    }

    fun cancelConversion() {
        job?.cancel()
        _state.value = VideoToHtmlState.Idle
    }

    fun resetState() {
        _state.value = VideoToHtmlState.Idle
    }
}
