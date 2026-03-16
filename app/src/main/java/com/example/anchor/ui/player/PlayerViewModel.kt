// app/src/main/java/com/example/anchor/ui/screens/player/PlayerViewModel.kt

package com.example.anchor.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1f,
    val errorMessage: String? = null,
    val showControls: Boolean = true,
    val isFullscreen: Boolean = false,
    val title: String = "",
    val aspectRatio: Float = 16f / 9f
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var _player: ExoPlayer? = null
    val player: ExoPlayer?
        get() = _player

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update { state ->
                state.copy(
                    isLoading = playbackState == Player.STATE_BUFFERING && state.currentPosition == 0L,
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    duration = _player?.duration?.takeIf { it > 0 } ?: state.duration
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    errorMessage = "Playback error: ${error.message}",
                    isLoading = false
                )
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                _uiState.update { it.copy(aspectRatio = ratio) }
            }
        }
    }

    fun initializePlayer(mediaUrl: String, mediaTitle: String, mimeType: String) {
        if (_player != null) return

        val context = getApplication<Application>()

        _uiState.update { it.copy(title = mediaTitle, isLoading = true) }

        _player = ExoPlayer.Builder(context)
            .build()
            .apply {
                addListener(playerListener)

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(mediaUrl))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(mediaTitle)
                            .build()
                    )
                    .apply {
                        // Set MIME type if provided
                        if (mimeType.isNotEmpty()) {
                            setMimeType(mimeType)
                        }
                    }
                    .build()

                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }

    fun play() {
        _player?.play()
    }

    fun pause() {
        _player?.pause()
    }

    fun togglePlayPause() {
        _player?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun seekTo(position: Long) {
        _player?.seekTo(position)
    }

    fun seekForward(milliseconds: Long = 10000) {
        _player?.let { player ->
            val newPosition = (player.currentPosition + milliseconds)
                .coerceAtMost(player.duration)
            player.seekTo(newPosition)
        }
    }

    fun seekBackward(milliseconds: Long = 10000) {
        _player?.let { player ->
            val newPosition = (player.currentPosition - milliseconds)
                .coerceAtLeast(0)
            player.seekTo(newPosition)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _player?.setPlaybackSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun updatePosition() {
        _player?.let { player ->
            _uiState.update {
                it.copy(
                    currentPosition = player.currentPosition,
                    duration = player.duration.takeIf { d -> d > 0 } ?: it.duration
                )
            }
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun showControls() {
        _uiState.update { it.copy(showControls = true) }
    }

    fun hideControls() {
        _uiState.update { it.copy(showControls = false) }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        _player?.let { player ->
            player.removeListener(playerListener)
            player.release()
        }
        _player = null
    }
}