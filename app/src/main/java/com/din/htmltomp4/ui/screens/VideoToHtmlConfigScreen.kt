package com.din.htmltomp4.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.din.htmltomp4.data.HtmlFrameQuality
import com.din.htmltomp4.data.HtmlScale
import com.din.htmltomp4.data.VideoToHtmlConfig
import com.din.htmltomp4.ui.theme.*
import com.din.htmltomp4.viewmodel.VideoToHtmlViewModel

@Composable
fun VideoToHtmlConfigScreen(
    viewModel: VideoToHtmlViewModel,
    onStartConversion: () -> Unit,
    onBack: () -> Unit
) {
    val current by viewModel.config.collectAsState()

    var fps by remember { mutableStateOf(current.targetFps) }
    var scale by remember { mutableStateOf(current.scale) }
    var quality by remember { mutableStateOf(current.quality) }
    var limitEnabled by remember { mutableStateOf(current.limitToSeconds != null) }
    var limitSeconds by remember { mutableStateOf(current.limitToSeconds ?: 15) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnDarkText)
            }
            Text("HTML export settings", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(20.dp))

        SectionLabel("Frame rate")
        Text(
            "Lower FPS keeps the generated HTML file smaller.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        WrappingChipsRow(
            options = listOf("8 FPS", "12 FPS", "15 FPS", "24 FPS", "30 FPS", "60 FPS"),
            selectedIndex = listOf(8, 12, 15, 24, 30, 60).indexOf(fps).coerceAtLeast(1),
            onSelect = { fps = listOf(8, 12, 15, 24, 30, 60)[it] },
            perRow = 3
        )

        Spacer(Modifier.height(22.dp))

        SectionLabel("Resolution")
        Text(
            "Higher resolution keeps more video detail but makes a larger HTML file.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ChipsRow(
            options = HtmlScale.entries.map { it.label },
            selectedIndex = HtmlScale.entries.indexOf(scale),
            onSelect = { scale = HtmlScale.entries[it] }
        )

        Spacer(Modifier.height(22.dp))

        SectionLabel("Frame quality")
        ChipsRow(
            options = HtmlFrameQuality.entries.map { it.label },
            selectedIndex = HtmlFrameQuality.entries.indexOf(quality),
            onSelect = { quality = HtmlFrameQuality.entries[it] }
        )

        Spacer(Modifier.height(22.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            SectionLabel("Limit clip length")
            Switch(
                checked = limitEnabled,
                onCheckedChange = { limitEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = SecondaryTeal, checkedTrackColor = PrimaryPurple)
            )
        }

        if (limitEnabled) {
            Text("First ${limitSeconds}s of the video", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = limitSeconds.toFloat(),
                onValueChange = { limitSeconds = it.toInt() },
                valueRange = 3f..60f,
                colors = SliderDefaults.colors(thumbColor = SecondaryTeal, activeTrackColor = SecondaryTeal)
            )
        } else {
            Text("Full video length will be exported.", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.weight(1f))

        Text(
            "A longer or higher-fps export makes a bigger HTML file, since every frame is embedded directly in it.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = {
                viewModel.updateConfig(
                    VideoToHtmlConfig(
                        targetFps = fps,
                        scale = scale,
                        quality = quality,
                        limitToSeconds = if (limitEnabled) limitSeconds else null
                    )
                )
                onStartConversion()
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
        ) {
            Text("Start Export", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ChipsRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (selected) PrimaryPurple else SurfaceGlass,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) Color.White else OnDarkTextMuted,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** Same look as [ChipsRow] but wraps options across multiple rows, [perRow] chips at a time. */
@Composable
private fun WrappingChipsRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, perRow: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(perRow).forEachIndexed { rowIndex, rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowOptions.forEachIndexed { colIndex, label ->
                    val index = rowIndex * perRow + colIndex
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (selected) PrimaryPurple else SurfaceGlass,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelect(index) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.White else OnDarkTextMuted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                // Pad the final row with invisible spacers so short last rows don't stretch wide.
                repeat(perRow - rowOptions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
