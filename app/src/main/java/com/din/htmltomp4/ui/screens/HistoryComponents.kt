package com.din.htmltomp4.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.din.htmltomp4.data.ConversionHistoryItem
import com.din.htmltomp4.data.ConversionType
import com.din.htmltomp4.ui.theme.*
import java.io.File

/** Empty-state hint shown when there's no conversion history yet. */
@Composable
fun EmptyHistoryHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(22)
            .padding(vertical = 34.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(SecondaryTeal.copy(alpha = 0.24f), PrimaryPurpleVariant.copy(alpha = 0.24f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = SecondaryTeal,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No conversions yet",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Upload an HTML animation or video above to get started",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** One row representing a single conversion result. Shared by Dashboard and History screens. */
@Composable
fun HistoryRow(
    item: ConversionHistoryItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isVideo = item.type == ConversionType.HTML_TO_MP4

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(20)
            .clickable { onOpen() }
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(SecondaryTeal.copy(alpha = 0.30f), PrimaryPurpleVariant.copy(alpha = 0.22f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isVideo) Icons.Default.PlayCircle else Icons.Default.Description,
                contentDescription = if (isVideo) "Play video" else "Open HTML",
                tint = SecondaryTeal,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.fileName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text("${item.resolution} • ${item.durationSeconds}s", style = MaterialTheme.typography.bodyMedium)
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = OnDarkTextMuted)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        confirmDelete = true
                    }
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this ${if (isVideo) "video" else "file"}?") },
            text = { Text("\"${item.fileName}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

/** Opens the file with an appropriate viewer — a video player for MP4 output, a browser for HTML output. */
fun openHistoryItem(context: android.content.Context, item: ConversionHistoryItem) {
    val file = File(item.outputPath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = if (item.type == ConversionType.HTML_TO_MP4) "video/mp4" else "text/html"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

fun shareHistoryItem(context: android.content.Context, item: ConversionHistoryItem) {
    val file = File(item.outputPath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = if (item.type == ConversionType.HTML_TO_MP4) "video/mp4" else "text/html"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooserTitle = if (item.type == ConversionType.HTML_TO_MP4) "Share video" else "Share HTML file"
    runCatching { context.startActivity(Intent.createChooser(intent, chooserTitle)) }
}