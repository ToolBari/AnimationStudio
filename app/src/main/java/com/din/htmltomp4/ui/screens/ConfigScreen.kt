package com.din.htmltomp4.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.din.htmltomp4.data.ConversionConfig
import com.din.htmltomp4.data.Resolution
import com.din.htmltomp4.ui.theme.*
import com.din.htmltomp4.viewmodel.ConverterViewModel

@Composable
fun ConfigScreen(
    viewModel: ConverterViewModel,
    onStartConversion: () -> Unit,
    onBack: () -> Unit
) {
    val currentConfig by viewModel.config.collectAsState()

    var orientationIndex by remember {
        mutableStateOf(if (currentConfig.resolution.isPortrait) 0 else 1)
    } // 0 = Portrait 9:16, 1 = Landscape 16:9
    var sizeIndex by remember {
        mutableStateOf(
            if (currentConfig.resolution == Resolution.HD_720P ||
                currentConfig.resolution == Resolution.PORTRAIT_720P
            ) 0 else 1
        )
    } // 0 = 720p, 1 = 1080p
    var frameRate by remember { mutableStateOf(currentConfig.frameRate) }
    var duration by remember { mutableStateOf(currentConfig.durationSeconds) }

    // Resolution is derived from the orientation + size pickers.
    val resolution = when {
        orientationIndex == 0 && sizeIndex == 0 -> Resolution.PORTRAIT_720P
        orientationIndex == 0 -> Resolution.PORTRAIT_1080P
        sizeIndex == 0 -> Resolution.HD_720P
        else -> Resolution.HD_1080P
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnDarkText)
            }
            Text("Export settings", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionLabel("Orientation")
        SegmentedChips(
            options = listOf("Portrait 9:16", "Landscape 16:9"),
            selectedIndex = orientationIndex,
            onSelect = { orientationIndex = it }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("Resolution")
        SegmentedChips(
            options = listOf("720p", "1080p"),
            selectedIndex = sizeIndex,
            onSelect = { sizeIndex = it }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("Frame rate")
        SegmentedChips(
            options = listOf("30 FPS", "60 FPS"),
            selectedIndex = if (frameRate == 30) 0 else 1,
            onSelect = { frameRate = if (it == 0) 30 else 60 }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("Duration: ${duration}s")
        Slider(
            value = duration.toFloat(),
            onValueChange = { duration = it.toInt() },
            valueRange = 1f..120f,
            steps = 118,
            colors = SliderDefaults.colors(
                thumbColor = SecondaryTeal,
                activeTrackColor = SecondaryTeal
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        val estimatedFrames = frameRate * duration
        Text(
            text = "This will capture $estimatedFrames frames at ${resolution.label}.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Note: frame-accurate capture works with CSS animations. " +
                "Pages animated with JavaScript (requestAnimationFrame) " +
                "can't be frame-seeked.",
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkTextMuted,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = {
                viewModel.updateConfig(
                    ConversionConfig(
                        resolution = resolution,
                        frameRate = frameRate,
                        durationSeconds = duration
                    )
                )
                onStartConversion()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
        ) {
            Text("Start Conversion", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SegmentedChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
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
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}