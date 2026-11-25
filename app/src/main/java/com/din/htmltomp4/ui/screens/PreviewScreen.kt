package com.din.htmltomp4.ui.screens

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.din.htmltomp4.data.ConversionState
import com.din.htmltomp4.ui.theme.*
import com.din.htmltomp4.viewmodel.ConverterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

@Composable
fun PreviewScreen(
    viewModel: ConverterViewModel,
    onDone: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    val successState = state as? ConversionState.Success

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Text("Your video is ready", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (successState != null) {
            VideoPlayer(path = successState.outputPath)

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            saveToGallery(context, File(successState.outputPath))
                        }
                        saving = false
                        savedMessage = if (ok) "Saved to gallery" else "Save failed"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                enabled = !saving
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (saving) "Saving..." else "Save to Gallery", fontWeight = FontWeight.Bold)
            }

            savedMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Convert another")
            }
        } else {
            Text("No output available.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDone) { Text("Back to Dashboard") }
        }
    }
}

@Composable
private fun VideoPlayer(path: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path))))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
    )
}

/**
 * Saves the finished MP4 into the device's public Movies collection using
 * MediaStore (scoped storage safe on API 29+, falls back to legacy path
 * write on older devices).
 */
private fun saveToGallery(context: android.content.Context, sourceFile: File): Boolean {
    return try {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/HtmlToMp4")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, values) ?: return false

        resolver.openOutputStream(itemUri)?.use { out ->
            FileInputStream(sourceFile).use { input -> input.copyTo(out) }
        } ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        }
        true
    } catch (t: Throwable) {
        false
    }
}