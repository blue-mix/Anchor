package com.example.anchor.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import kotlin.math.abs

/**
 * Full-screen media player screen.
 *
 * No type changes from original — [PlayerViewModel] is clean.
 * Only change: [viewModel()] → [koinViewModel()].
 */
@Composable
fun PlayerScreen(
    mediaUrl: String,
    mediaTitle: String,
    mimeType: String,
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(mediaUrl) {
        viewModel.initializePlayer(mediaUrl, mediaTitle, mimeType)
    }

    LaunchedEffect(uiState.isPlaying) {
        while (true) {
            viewModel.updatePosition()
            delay(500)
        }
    }

    LaunchedEffect(uiState.showControls, uiState.isPlaying) {
        if (uiState.showControls && uiState.isPlaying) {
            delay(3_000)
            viewModel.hideControls()
        }
    }

    DisposableEffect(uiState.isFullscreen) {
        val activity = context as? Activity
        val window = activity?.window

        if (uiState.isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, false)
                WindowInsetsControllerCompat(win, view).let { ctrl ->
                    ctrl.hide(WindowInsetsCompat.Type.systemBars())
                    ctrl.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, true)
                WindowInsetsControllerCompat(win, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, true)
                WindowInsetsControllerCompat(win, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(enabled = uiState.isFullscreen) { viewModel.toggleFullscreen() }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {

            VideoPlayer(
                player = viewModel.player,
                aspectRatio = uiState.aspectRatio,
                isFullscreen = uiState.isFullscreen,
                onTap = { viewModel.toggleControls() },
                onDoubleTapLeft = { viewModel.seekBackward() },
                onDoubleTapRight = { viewModel.seekForward() },
                onHorizontalSwipe = { delta ->
                    val seekAmount = (delta * 100).toLong()
                    if (seekAmount > 0) viewModel.seekForward(abs(seekAmount))
                    else viewModel.seekBackward(abs(seekAmount))
                }
            )

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            if (uiState.isBuffering && !uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    strokeWidth = 2.dp
                )
            }

            AnimatedVisibility(
                visible = uiState.showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PlayerControls(
                    uiState = uiState,
                    onBackClick = {
                        if (uiState.isFullscreen) viewModel.toggleFullscreen()
                        else onNavigateBack()
                    },
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onSeekBackward = { viewModel.seekBackward() },
                    onSeekForward = { viewModel.seekForward() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                    onFullscreenToggle = { viewModel.toggleFullscreen() }
                )
            }

            uiState.errorMessage?.let { error ->
                ErrorOverlay(
                    message = error,
                    onDismiss = { viewModel.clearError() },
                    onRetry = { viewModel.clearError(); viewModel.play() }
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    player: ExoPlayer?,
    aspectRatio: Float,
    isFullscreen: Boolean,
    onTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onHorizontalSwipe: (Float) -> Unit
) {
    var lastTapTime by remember { mutableStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val now = System.currentTimeMillis()
                        val delta = now - lastTapTime
                        if (delta < 300) {
                            if (offset.x < size.width / 2) onDoubleTapLeft()
                            else onDoubleTapRight()
                        } else {
                            onTap()
                        }
                        lastTapTime = now
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        player?.let { exoPlayer ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exoPlayer
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setKeepContentOnPlayerReset(true)
                    }
                },
                modifier = if (isFullscreen) Modifier.fillMaxSize()
                else Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio.coerceIn(0.5f, 3f)),
                update = { it.player = exoPlayer }
            )
        }
    }
}

@Composable
private fun PlayerControls(
    uiState: PlayerUiState,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onFullscreenToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    text = uiState.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                SpeedSelector(currentSpeed = uiState.playbackSpeed, onSpeedChange = onSpeedChange)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSeekBackward,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                Icon(
                    Icons.Rounded.Replay10,
                    "Rewind 10s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            IconButton(
                onClick = onSeekForward,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                Icon(
                    Icons.Rounded.Forward10,
                    "Forward 10s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SeekBar(
                currentPosition = uiState.currentPosition,
                duration = uiState.duration,
                onSeekTo = onSeekTo
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatDuration(uiState.currentPosition)} / ${formatDuration(uiState.duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                IconButton(onClick = onFullscreenToggle) {
                    Icon(
                        imageVector = if (uiState.isFullscreen) Icons.Rounded.FullscreenExit
                        else Icons.Rounded.Fullscreen,
                        contentDescription = if (uiState.isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SeekBar(currentPosition: Long, duration: Long, onSeekTo: (Long) -> Unit) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val progress = if (duration > 0) {
        if (isDragging) dragPosition else currentPosition.toFloat() / duration.toFloat()
    } else 0f

    Slider(
        value = progress.coerceIn(0f, 1f),
        onValueChange = { isDragging = true; dragPosition = it },
        onValueChangeFinished = {
            onSeekTo((dragPosition * duration).toLong()); isDragging = false
        },
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
        )
    )
}

@Composable
private fun SpeedSelector(currentSpeed: Float, onSpeedChange: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    Box {
        IconButton(onClick = { expanded = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Speed,
                    "Playback speed",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "${currentSpeed}x",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            speeds.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${speed}x",
                            color = if (speed == currentSpeed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onSpeedChange(speed); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ErrorOverlay(message: String, onDismiss: () -> Unit, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("Playback Error", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onDismiss) { Text("Dismiss", color = Color.White) }
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0)
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    else
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}