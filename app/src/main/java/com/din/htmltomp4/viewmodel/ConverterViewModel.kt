package com.din.htmltomp4.viewmodel

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.din.htmltomp4.data.ConversionConfig
import com.din.htmltomp4.data.ConversionState
import com.din.htmltomp4.engine.EncodeEvent
import com.din.htmltomp4.engine.FFmpegEncoder
import com.din.htmltomp4.engine.FrameCaptureEvent
import com.din.htmltomp4.engine.HtmlAnimationCaptureEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

class ConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val state: StateFlow<ConversionState> = _state.asStateFlow()

    private val _selectedHtmlUri = MutableStateFlow<Uri?>(null)
    val selectedHtmlUri: StateFlow<Uri?> = _selectedHtmlUri.asStateFlow()

    private val _config = MutableStateFlow(ConversionConfig())
    val config: StateFlow<ConversionConfig> = _config.asStateFlow()

    private var conversionJob: Job? = null
    private var captureEngine: HtmlAnimationCaptureEngine? = null
    private val encoder = FFmpegEncoder()

    fun onHtmlSelected(uri: Uri) {
        _selectedHtmlUri.value = uri
    }

    fun updateConfig(newConfig: ConversionConfig) {
        _config.value = newConfig
    }

    /**
     * [captureEngineProvider] is a lambda so the ViewModel doesn't need a
     * direct Activity reference (which would leak it) — the calling Screen
     * supplies a fresh engine bound to the current Activity right before
     * starting.
     *
     * NOTE: history recording is NOT done here — it moved to the shared
     * HistoryViewModel, written from the Processing screen's LaunchedEffect
     * when it observes ConversionState.Success. Keeping it out of this
     * ViewModel is what lets Video→HTML conversions (a completely separate
     * ViewModel) land in the same "Recent conversions" list.
     */
    fun startConversion(
        htmlFile: File,
        captureEngineProvider: () -> HtmlAnimationCaptureEngine
    ) {
        if (conversionJob?.isActive == true) return

        conversionJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val engine = captureEngineProvider()
            captureEngine = engine
            val cfg = _config.value

            try {
                var frameDirResult: File? = null

                engine.captureFrames(context, htmlFile, cfg).collect { event ->
                    when (event) {
                        is FrameCaptureEvent.Progress -> {
                            _state.value = ConversionState.Capturing(
                                currentFrame = event.frameIndex,
                                totalFrames = event.totalFrames,
                                previewBitmapPath = event.framePath
                            )
                        }
                        is FrameCaptureEvent.Done -> {
                            frameDirResult = event.frameDir
                        }
                        is FrameCaptureEvent.Failed -> {
                            val detail = "${event.error.javaClass.simpleName}: ${event.error.message}"
                            _state.value = ConversionState.Error("Frame capture failed — $detail", event.error)
                        }
                    }
                }

                val frameDir = frameDirResult ?: return@launch
                if (_state.value is ConversionState.Error) return@launch

                _state.value = ConversionState.Encoding(0)

                val outputDir = File(context.getExternalFilesDir(null), "videos").apply { mkdirs() }
                val outputFile = File(outputDir, "${cfg.outputFileName}.mp4")

                // Encode at the CONFIG fps (30/60 as chosen). Capture is now
                // deterministic — every frame was seeked to its exact
                // animation time — so the wall-clock capture duration is
                // meaningless and must not set the output fps (the old
                // measured-fps mux stretched a 39s animation into 387s).
                val fps = cfg.frameRate.toDouble()

                encoder.encode(frameDir, fps, cfg.totalFrames, outputFile).collect { event ->
                    when (event) {
                        is EncodeEvent.Progress -> {
                            _state.value = ConversionState.Encoding(event.percent)
                        }
                        is EncodeEvent.Success -> {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                event.outputFile
                            )
                            _state.value = ConversionState.Success(event.outputFile.absolutePath, uri)
                            engine.cleanupFrames(frameDir)
                        }
                        is EncodeEvent.Failed -> {
                            _state.value = ConversionState.Error("Encoding failed: ${event.logs.takeLast(300)}")
                            engine.cleanupFrames(frameDir)
                        }
                    }
                }
            } catch (t: Throwable) {
                _state.value = ConversionState.Error(t.message ?: "Unknown error", t)
            }
        }
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        captureEngine?.releaseWebView()
        _state.value = ConversionState.Idle
    }

    fun resetState() {
        _state.value = ConversionState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        captureEngine?.releaseWebView()
    }
}
