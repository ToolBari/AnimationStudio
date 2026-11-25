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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.din.htmltomp4.data.ConversionHistoryItem
import com.din.htmltomp4.data.ConversionState
import com.din.htmltomp4.data.ConversionType
import com.din.htmltomp4.engine.HtmlAnimationCaptureEngine
import com.din.htmltomp4.engine.HtmlSourceResolver
import com.din.htmltomp4.ui.theme.*
import com.din.htmltomp4.viewmodel.ConverterViewModel
import com.din.htmltomp4.viewmodel.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ProcessingScreen(
    viewModel: ConverterViewModel,
    historyViewModel: HistoryViewModel,
    onFinished: () -> Unit,
    onCancelled: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val htmlUri by viewModel.selectedHtmlUri.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // Kick off the conversion exactly once when this screen appears.
    LaunchedEffect(htmlUri) {
        val uri = htmlUri ?: return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        val htmlFile = withContext(Dispatchers.IO) {
            HtmlSourceResolver.resolve(context, uri, uri.lastPathSegment ?: "upload.html")
        }
        viewModel.startConversion(htmlFile) { HtmlAnimationCaptureEngine(act) }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is ConversionState.Success) {
            val cfg = viewModel.config.value
            historyViewModel.addItem(
                ConversionHistoryItem(
                    id = File(s.outputPath).nameWithoutExtension,
                    fileName = File(s.outputPath).name,
                    thumbnailPath = null,
                    outputPath = s.outputPath,
                    resolution = cfg.resolution.label,
                    durationSeconds = cfg.durationSeconds,
                    createdAtMillis = System.currentTimeMillis(),
                    type = ConversionType.HTML_TO_MP4
                )
            )
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            when (val s = state) {
                is ConversionState.Capturing -> {
                    CircularProgressRing(progress = s.progress * 0.7f) // capture = first 70% of overall bar
                    Spacer(Modifier.height(24.dp))
                    FramePreview(path = s.previewBitmapPath)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Capturing Frame ${s.currentFrame}/${s.totalFrames}...",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                is ConversionState.Encoding -> {
                    CircularProgressRing(progress = 0.7f + (s.progressPercent / 100f) * 0.3f)
                    Spacer(Modifier.height(24.dp))
                    Text("Encoding video... ${s.progressPercent}%", style = MaterialTheme.typography.titleLarge)
                }

                is ConversionState.Error -> {
                    Text("Something went wrong", style = MaterialTheme.typography.titleLarge, color = ErrorRed)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onCancelled) { Text("Go back") }
                }

                else -> {
                    CircularProgressBar()
                    Spacer(Modifier.height(16.dp))
                    Text("Preparing renderer...", style = MaterialTheme.typography.titleLarge)
                }
            }

            Spacer(Modifier.height(32.dp))

            if (state !is ConversionState.Error) {
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
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CircularProgressBar() {
    CircularProgressIndicator(
        modifier = Modifier.size(120.dp),
        strokeWidth = 10.dp,
        color = SecondaryTeal,
        trackColor = SurfaceGlass
    )
}

@Composable
private fun FramePreview(path: String?) {
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
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Live frame preview",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("Rendering...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
