package com.tv2cast.app.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.tv2cast.app.data.model.MediaTrack
import com.tv2cast.app.data.model.Video
import com.tv2cast.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Fullscreen video player screen using ExoPlayer with track selection
 */
@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    video: Video,
    streamUrl: String,
    audioTracks: List<MediaTrack>,
    subtitleTracks: List<MediaTrack>,
    selectedAudioIndex: Int,
    selectedSubtitleIndex: Int,
    useCompatMode: Boolean,
    onBack: () -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onToggleCompat: () -> Unit
) {
    val context = LocalContext.current
    var showTrackMenu by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            val subtitleConfigs = subtitleTracks
                .filter { !it.embedded }
                .map { sub ->
                    val serverUrl = streamUrl.substringBefore("/api/videos/")
                    MediaItem.SubtitleConfiguration.Builder(
                        android.net.Uri.parse("$serverUrl/api/videos/${video.id}/subtitle/${sub.index}")
                    )
                        .setMimeType(androidx.media3.common.MimeTypes.TEXT_VTT)
                        .setLanguage(sub.language)
                        .setLabel(sub.label)
                        .setSelectionFlags(if (sub.default) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                }

            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setSubtitleConfigurations(subtitleConfigs)
                .build()

            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var playbackState by remember { mutableStateOf(exoPlayer.playbackState) }
    var playerError by remember { mutableStateOf<PlaybackException?>(null) }
    var exoTracks by remember { mutableStateOf(Tracks.EMPTY) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) { playbackState = state }
            override fun onPlayerError(error: PlaybackException) { playerError = error }
            override fun onTracksChanged(tracks: Tracks) { exoTracks = tracks }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    val playPauseButtonRequester = remember { FocusRequester() }
    val menuRequester = remember { FocusRequester() }
    val playerBoxRequester = remember { FocusRequester() }

    fun togglePlayPause() {
        val player = exoPlayer
        val state = player.playbackState

        if (state == Player.STATE_IDLE) {
            player.prepare()
            player.play()
        } else if (state == Player.STATE_ENDED) {
            player.seekTo(0)
            player.play()
        } else {
            if (player.isPlaying) player.pause() else player.play()
        }
        showControls = true
    }

    LaunchedEffect(Unit) {
        playerBoxRequester.requestFocus()
    }

    // Poll playback position every 500ms
    LaunchedEffect(Unit) {
        while (true) {
            position = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500L)
        }
    }

    LaunchedEffect(showControls, showTrackMenu) {
        if (showControls && !showTrackMenu) {
            delay(5000L)
            showControls = false
        }
        if (!showControls && !showTrackMenu) {
            playerBoxRequester.requestFocus()
        }
    }

    LaunchedEffect(selectedAudioIndex, selectedSubtitleIndex, exoTracks) {
        val builder = exoPlayer.trackSelectionParameters.buildUpon()

        // Audio selection by language (usually works since audio tracks have distinct languages)
        if (selectedAudioIndex >= 0 && selectedAudioIndex < audioTracks.size) {
            builder.setPreferredAudioLanguage(audioTracks[selectedAudioIndex].language)
        }

        // Subtitle: use TrackSelectionOverride to select by group index, not language
        // This works even when multiple tracks have language "und"
        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (selectedSubtitleIndex == -1) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else if (selectedSubtitleIndex < subtitleTracks.size) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            // Map selectedSubtitleIndex → ExoPlayer text track group by counting
            var textGroupIdx = 0
            var targetGroup: androidx.media3.common.TrackGroup? = null
            for (group in exoTracks.groups) {
                if (group.type == C.TRACK_TYPE_TEXT) {
                    if (textGroupIdx == selectedSubtitleIndex) {
                        targetGroup = group.mediaTrackGroup
                        break
                    }
                    textGroupIdx++
                }
            }
            if (targetGroup != null) {
                builder.addOverride(TrackSelectionOverride(targetGroup, 0))
            } else {
                // Fallback when tracks not yet loaded: prefer by language
                builder.setPreferredTextLanguage(subtitleTracks[selectedSubtitleIndex].language)
                builder.setSelectUndeterminedTextLanguage(true)
            }
        }

        exoPlayer.trackSelectionParameters = builder.build()
    }

    DisposableEffect(streamUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerBoxRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            val newPos = (exoPlayer.currentPosition - 30_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            showControls = true
                            true
                        }
                        Key.DirectionRight -> {
                            val dur = exoPlayer.duration.coerceAtLeast(0L)
                            val newPos = (exoPlayer.currentPosition + 30_000L).coerceAtMost(if (dur > 0) dur else Long.MAX_VALUE)
                            exoPlayer.seekTo(newPos)
                            showControls = true
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            togglePlayPause()
                            true
                        }
                        Key.Back -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (playerError == null) {
                    showControls = !showControls
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Playback Error",
                        color = Color.Red,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Could not play video.\nThe format might be unsupported or there could be a network issue.",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onBack) {
                        Text("Go Back")
                    }
                }
            }
        } else {
            val isBuffering = playbackState == Player.STATE_BUFFERING
            if (isBuffering) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            AnimatedVisibility(
                visible = showControls && !showTrackMenu && !isBuffering,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                LaunchedEffect(Unit) {
                    playPauseButtonRequester.requestFocus()
                }
                androidx.tv.material3.IconButton(
                    onClick = { togglePlayPause() },
                    modifier = Modifier
                        .size(80.dp)
                        .focusRequester(playPauseButtonRequester),
                    shape = ButtonDefaults.shape(CircleShape),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Accent
                    )
                ) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        fontSize = 48.sp,
                        color = Color.White
                    )
                }
            }

            AnimatedVisibility(
                visible = showControls && !showTrackMenu,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xCC000000), Color.Transparent)
                            )
                        )
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.tv.material3.IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text("←", fontSize = 24.sp, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${video.extension.uppercase()} • ${video.sizeFormatted} ${if (useCompatMode) "• Compatibility Mode" else ""}",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    androidx.tv.material3.Button(
                        onClick = { showTrackMenu = true },
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Accent
                        )
                    ) {
                        Text("Settings", color = Color.White)
                    }
                }
            }

            // Bottom seek bar
            AnimatedVisibility(
                visible = showControls && !showTrackMenu,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(position),
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "◀ ▶  ±30s",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = formatTime(duration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                        ) {
                            if (duration > 0L) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = (position.toFloat() / duration).coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .background(Accent, RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                }
            }

            if (showTrackMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { showTrackMenu = false },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(350.dp)
                            .background(BgSecondary)
                            .padding(24.dp)
                            .focusRequester(menuRequester)
                            .focusable()
                            .clickable(enabled = false) {},
                    ) {
                        LaunchedEffect(Unit) {
                            menuRequester.requestFocus()
                        }
                        Text(
                            text = "Playback Settings",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                TrackSectionHeader("Video Quality")
                                TrackMenuItem(
                                    label = if (useCompatMode) "Compatibility Mode (720p H.264)" else "High Quality (Original)",
                                    isSelected = useCompatMode,
                                    onClick = {
                                        onToggleCompat()
                                        showTrackMenu = false
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            if (audioTracks.isNotEmpty()) {
                                item { TrackSectionHeader("Audio") }
                                items(audioTracks.size) { index ->
                                    val track = audioTracks[index]
                                    TrackMenuItem(
                                        label = track.label,
                                        isSelected = selectedAudioIndex == index,
                                        onClick = { onSelectAudio(index) }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }
                            item { TrackSectionHeader("Subtitles") }
                            item {
                                TrackMenuItem(
                                    label = "None",
                                    isSelected = selectedSubtitleIndex == -1,
                                    onClick = { onSelectSubtitle(-1) }
                                )
                            }
                            items(subtitleTracks.size) { index ->
                                val track = subtitleTracks[index]
                                TrackMenuItem(
                                    label = track.label + (if (!track.embedded) " (External)" else ""),
                                    isSelected = selectedSubtitleIndex == index,
                                    onClick = { onSelectSubtitle(index) }
                                )
                            }
                        }
                        androidx.tv.material3.Button(
                            onClick = { showTrackMenu = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            colors = ButtonDefaults.colors(containerColor = BgCard)
                        ) {
                            Text("Close", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TrackSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TrackMenuItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Accent.copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = Accent
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = if (isSelected) Color.White else TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Text("✓", color = AccentCyan, fontWeight = FontWeight.Bold)
            }
        }
    }
}
