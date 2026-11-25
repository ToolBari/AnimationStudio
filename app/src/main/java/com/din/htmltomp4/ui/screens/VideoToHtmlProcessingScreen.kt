package com.din.htmltomp4.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.din.htmltomp4.data.ConversionHistoryItem
import com.din.htmltomp4.data.ConversionType
import com.din.htmltomp4.data.VideoToHtmlState
import com.din.htmltomp4.ui.theme.*
import com.din.htmltomp4.viewmodel.HistoryViewModel
import com.din.htmltomp4.viewmodel.VideoToHtmlViewModel
import java.io.File
import kotlin.math.roundToInt

@Composable
fun VideoToHtmlProcessingScreen(
    viewModel: VideoToHtmlViewModel,
    historyViewModel: HistoryViewModel,
    onFinished: () -> Unit,
    onCancelled: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startConversion()
    }

    LaunchedEffect(state) {
        val s = state
        if (s is VideoToHtmlState.Success) {
            val cfg = viewModel.config.value
            // No exact source-clip duration is carried in the Success event,
            // so approximate it from frame count / fps — matches how many
            // real seconds of video were actually extracted.
            val approxDurationSeconds = if (cfg.targetFps > 0) {
                (s.frameCount / cfg.targetFps.toDouble()).roundToInt()
            } else 0

            historyViewModel.addItem(
                ConversionHistoryItem(
                    id = File(s.outputPath).nameWithoutExtension,
                    fileName = File(s.outputPath).name,
                    thumbnailPath = null,
                    outputPath = s.outputPath,
                    resolution = cfg.scale.label,
                    durationSeconds = approxDurationSeconds,
                    createdAtMillis = System.currentTimeMillis(),
                    type = ConversionType.VIDEO_TO_HTML
                )
            )
            onFinished()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            when (val s = state) {
                is VideoToHtmlState.ExtractingFrames -> {
                    CircularProgressRing(progress = s.progress * 0.6f) // extraction = first 60%
                    Spacer(Modifier.height(24.dp))
                    FramePreviewBox(path = s.previewFramePath)
                    Spacer(Modifier.height(16.dp))
                    Text("Extracting Frame ${s.currentFrame}/${s.totalFrames}...", style = MaterialTheme.typography.titleLarge)
                }

                is VideoToHtmlState.BuildingHtml -> {
                    CircularProgressRing(progress = 0.6f + (s.progressPercent / 100f) * 0.4f)
                    Spacer(Modifier.height(24.dp))
                    Text("Building HTML file... ${s.progressPercent}%", style = MaterialTheme.typography.titleLarge)
                }

                is VideoToHtmlState.Error -> {
                    Text("Something went wrong", style = MaterialTheme.typography.titleLarge, color = ErrorRed)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onCancelled) { Text("Go back") }
                }

                else -> {
                    CircularProgressIndicator(modifier = Modifier.size(120.dp), strokeWidth = 10.dp, color = SecondaryTeal, trackColor = SurfaceGlass)
                    Spacer(Modifier.height(16.dp))
                    Text("Reading video...", style = MaterialTheme.typography.titleLarge)
                }
            }

            Spacer(Modifier.height(32.dp))

            if (state !is VideoToHtmlState.Error) {
                OutlinedButton(onClick = {
                    viewModel.cancelConversion()
                    onCancelled()
                }) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun CircularProgressRing(progress: Float) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(120.dp),
            strokeWidth = 10.dp,
            color = SecondaryTeal,
            trackColor = SurfaceGlass
        )
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FramePreviewBox(path: String?) {
    val bitmap = remember(path) {
        path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Box(
        modifier = Modifier
            .size(width = 220.dp, height = 130.dp)
            .clip(RoundedCornerShape(16.dp))
            .glassCard(16),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Extracted frame preview", modifier = Modifier.fillMaxSize())
        } else {
            Text("Reading frame...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
