package com.din.htmltomp4.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.din.htmltomp4.ui.theme.*
import com.din.htmltomp4.viewmodel.ConverterViewModel
import com.din.htmltomp4.viewmodel.HistoryViewModel
import com.din.htmltomp4.viewmodel.VideoToHtmlViewModel

/** Number of items shown in the Dashboard's "Recent conversions" preview before a "See all" is offered. */
private const val RECENT_PREVIEW_COUNT = 5

@Composable
fun DashboardScreen(
    viewModel: ConverterViewModel,
    videoToHtmlViewModel: VideoToHtmlViewModel,
    historyViewModel: HistoryViewModel,
    onHtmlPicked: () -> Unit,
    onVideoPicked: () -> Unit,
    onSeeAllHistory: () -> Unit = {}
) {
    val history by historyViewModel.history.collectAsState()
    val context = LocalContext.current

    val htmlFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.onHtmlSelected(uri)
            onHtmlPicked()
        }
    }

    val videoFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            videoToHtmlViewModel.onVideoSelected(uri)
            onVideoPicked()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF16131F), BackgroundDark, BackgroundDark)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp)
        ) {
            item {
                HeaderSection()

                Spacer(modifier = Modifier.height(26.dp))

                ConversionCard(
                    actions = listOf(
                        ConversionAction(
                            title = "HTML → MP4",
                            subtitle = "Pick a .html file or a .zip bundle",
                            icon = Icons.Default.UploadFile,
                            gradient = AppGradient,
                            onClick = {
                                // Accept both raw HTML and zipped bundles (index.html + assets)
                                htmlFilePicker.launch("*/*")
                            }
                        ),
                        ConversionAction(
                            title = "Video → HTML",
                            subtitle = "Turn a video into a playable HTML animation",
                            icon = Icons.Default.Videocam,
                            gradient = Brush.linearGradient(colors = listOf(SecondaryTeal, PrimaryPurpleVariant)),
                            onClick = { videoFilePicker.launch("video/*") }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(30.dp))

                RecentConversionsHeader(
                    showSeeAll = true,
                    onSeeAllClick = onSeeAllHistory
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (history.isEmpty()) {
                item { EmptyHistoryHint() }
            } else {
                items(history.take(RECENT_PREVIEW_COUNT), key = { it.id }) { item ->
                    HistoryRow(
                        item = item,
                        onOpen = { openHistoryItem(context, item) },
                        onShare = { shareHistoryItem(context, item) },
                        onDelete = { historyViewModel.deleteItem(item) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HeaderSection() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = "Animation Studio",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Convert between HTML animations and video",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun RecentConversionsHeader(
    showSeeAll: Boolean,
    onSeeAllClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Recent conversions",
            style = MaterialTheme.typography.titleLarge
        )
        if (showSeeAll) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSeeAllClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "See all",
                    color = SecondaryTeal,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "See all conversions",
                    tint = SecondaryTeal,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** One action inside the combined [ConversionCard] — a single convert direction. */
private data class ConversionAction(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val gradient: Brush,
    val onClick: () -> Unit
)

/**
 * A single outer card that houses multiple conversion actions (e.g. HTML → MP4 and
 * Video → HTML) as internal tiles, instead of two separate floating cards. Each tile
 * keeps its own gradient identity but the whole group reads as one unified surface.
 * The card fades/slides in on first composition and each tile animates on tap.
 */
@Composable
private fun ConversionCard(actions: List<ConversionAction>) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(450)) +
            slideInVertically(
                animationSpec = tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                initialOffsetY = { it / 6 }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 18.dp, shape = RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.5f))
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceDark)
                .border(width = 1.dp, brush = GlassBorderBrush, shape = RoundedCornerShape(28.dp))
                .padding(10.dp)
        ) {
            actions.forEachIndexed { index, action ->
                ConversionTile(action = action, delayMillis = index * 90)
                if (index != actions.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ConversionTile(action: ConversionAction, delayMillis: Int) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        appeared = true
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tilePressScale"
    )
    val entranceAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(350),
        label = "tileEntranceAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = entranceAlpha
            }
            .clip(RoundedCornerShape(18.dp))
            .background(action.gradient)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.02f))
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = action.onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.20f))
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.25f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = action.subtitle,
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
