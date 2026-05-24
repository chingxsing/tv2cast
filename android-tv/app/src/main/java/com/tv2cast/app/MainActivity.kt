package com.tv2cast.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.tv2cast.app.ui.screens.*
import com.tv2cast.app.ui.theme.*

/**
 * Main Activity for Tv2Cast Android TV App
 * Handles navigation between Connect, Browse, and Player screens
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            Tv2CastTheme {
                Tv2CastApp()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Tv2CastApp(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    
    when (val state = uiState) {
        is UiState.Searching -> {
            // Discovery screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgPrimary),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Accent,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Searching for Tv2Cast server...",
                        fontSize = 16.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        is UiState.ServerInput -> {
            ConnectScreen(
                onConnect = { url -> viewModel.connectToServer(url) },
                onDiscover = { viewModel.startDiscovery() }
            )
        }
        
        is UiState.Loading -> {
            // Loading screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgPrimary),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Accent,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Connecting to server...",
                        fontSize = 16.sp,
                        color = TextSecondary
                    )
                }
            }
        }
        
        is UiState.VideoList -> {
            BrowseScreen(
                videos = state.videos,
                serverUrl = serverUrl,
                onVideoClick = { video -> viewModel.playVideo(video) },
                onRescan = { viewModel.rescan() },
                onDisconnect = { viewModel.disconnect() },
                onSearch = { viewModel.searchVideos(it) }
            )
        }
        
        is UiState.Playing -> {
            val playingState = state as UiState.Playing
            PlayerScreen(
                video = playingState.video,
                audioTracks = playingState.audioTracks,
                subtitleTracks = playingState.subtitleTracks,
                selectedAudioIndex = playingState.selectedAudioIndex,
                selectedSubtitleIndex = playingState.selectedSubtitleIndex,
                useCompatMode = playingState.useCompatMode,
                streamUrl = if (playingState.useCompatMode) 
                    "${viewModel.serverUrl.value}/api/videos/${playingState.video.id}/compat-stream"
                else 
                    viewModel.getStreamUrl(playingState.video),
                onBack = { viewModel.backToList() },
                onSelectAudio = { viewModel.selectAudio(it) },
                onSelectSubtitle = { viewModel.selectSubtitle(it) },
                onToggleCompat = { viewModel.toggleCompatMode() }
            )
        }
        
        is UiState.Error -> {
            ConnectScreen(
                onConnect = { url -> viewModel.connectToServer(url) },
                onDiscover = { viewModel.startDiscovery() },
                errorMessage = "Connection Failed!\n${state.message}"
            )
        }
    }
}
