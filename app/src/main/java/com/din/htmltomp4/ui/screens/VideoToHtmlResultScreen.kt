package com.din.htmltomp4.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.din.htmltomp4.data.VideoToHtmlState
import com.din.htmltomp4.ui.theme.*
import com.din.htmltomp4.viewmodel.VideoToHtmlViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale

@Composable
fun VideoToHtmlResultScreen(
    viewModel: VideoToHtmlViewModel,
    onDone: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    val success = state as? VideoToHtmlState.Success

    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundDark).padding(20.dp)
    ) {
        Text("Your HTML file is ready", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (success != null) {
            Column(
                modifier = Modifier.fillMaxWidth().glassCard(18).padding(18.dp)
            ) {
                InfoRow("Frames embedded", "${success.frameCount}")
                Spacer(Modifier.height(8.dp))
                InfoRow("File size", formatSize(success.fileSizeBytes))
                Spacer(Modifier.height(8.dp))
                InfoRow("File name", File(success.outputPath).name)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            saveToDownloads(context, File(success.outputPath))
                        }
                        saving = false
                        savedMessage = if (ok) "Saved to Downloads" else "Save failed"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                enabled = !saving
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (saving) "Saving..." else "Save to Downloads", fontWeight = FontWeight.Bold)
            }

            savedMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(success.outputUri, "text/html")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open / Preview")
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
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb) else String.format(Locale.US, "%.0f KB", kb)
}

/**
 * Copies the generated .html into the public Downloads collection.
 * MediaStore.Downloads only exists on API 29+, so that path is isolated in
 * its own method — referencing that class directly inside an if-branch of
 * a single method can still trigger a class-verification crash on API
 * 26-28 devices (the class isn't in their framework at all), so callers
 * must never let both branches live in the same method body.
 */
private fun saveToDownloads(context: android.content.Context, sourceFile: File): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveToDownloadsViaMediaStore(context, sourceFile)
    } else {
        saveToDownloadsLegacy(sourceFile)
    }
}

private fun saveToDownloadsViaMediaStore(context: android.content.Context, sourceFile: File): Boolean {
    return try {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.Downloads.MIME_TYPE, "text/html")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false

        resolver.openOutputStream(itemUri)?.use { out ->
            FileInputStream(sourceFile).use { input -> input.copyTo(out) }
        } ?: return false

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)
        true
    } catch (t: Throwable) {
        false
    }
}

@Suppress("DEPRECATION")
private fun saveToDownloadsLegacy(sourceFile: File): Boolean {
    return try {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val destFile = File(downloadsDir, sourceFile.name)
        FileInputStream(sourceFile).use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        true
    } catch (t: Throwable) {
        false
    }
}
