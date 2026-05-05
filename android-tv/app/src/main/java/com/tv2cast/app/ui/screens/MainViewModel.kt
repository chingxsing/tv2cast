package com.tv2cast.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv2cast.app.data.api.ApiClient
import com.tv2cast.app.data.ServerDiscovery
import com.tv2cast.app.data.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI states for the app
 */
sealed class UiState {
    object Searching : UiState()
    object ServerInput : UiState()
    object Loading : UiState()
    data class VideoList(val videos: List<Video>) : UiState()
    data class Playing(
        val video: Video,
        val audioTracks: List<com.tv2cast.app.data.model.MediaTrack> = emptyList(),
        val subtitleTracks: List<com.tv2cast.app.data.model.MediaTrack> = emptyList(),
        val selectedAudioIndex: Int = -1,
        val selectedSubtitleIndex: Int = -1,
        val useCompatMode: Boolean = false
    ) : UiState()
    data class Error(val message: String) : UiState()
}

/**
 * ViewModel for managing server connection and video list
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Searching)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()
    
    private val discovery = ServerDiscovery(application)
    
    init {
        startDiscovery()
    }
    
    /**
     * Start UDP discovery to find the server automatically
     */
    fun startDiscovery() {
        _uiState.value = UiState.Searching
        viewModelScope.launch {
            val url = discovery.discoverServer()
            if (url != null) {
                _serverUrl.value = url
                connectToServer(url)
            } else {
                _uiState.value = UiState.ServerInput
            }
        }
    }
    
    private var allVideos: List<Video> = emptyList()
    
    /**
     * Connect to the Tv2Cast server
     */
    fun connectToServer(url: String) {
        val baseUrl = normalizeUrl(url)
        if (baseUrl.isEmpty()) return
        
        _serverUrl.value = baseUrl
        _uiState.value = UiState.Loading
        
        viewModelScope.launch {
            try {
                val api = ApiClient.getApi(baseUrl)
                val response = api.getVideos()
                allVideos = response.videos
                _uiState.value = UiState.VideoList(allVideos)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    "Cannot connect to server at $baseUrl\nCheck your WiFi or IP address.\nError: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    private fun normalizeUrl(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) return ""
        
        // Add http:// if missing (defaults to http for local servers)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        
        // Ensure it ends with / (Retrofit requirement for base URL)
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        
        return url
    }
    
    /**
     * Search videos by name
     */
    fun searchVideos(query: String) {
        val q = query.lowercase().trim()
        val filtered = if (q.isEmpty()) {
            allVideos
        } else {
            allVideos.filter {
                it.name.lowercase().contains(q) ||
                it.filename.lowercase().contains(q) ||
                it.extension.lowercase().contains(q)
            }
        }
        _uiState.value = UiState.VideoList(filtered)
    }
    
    /**
     * Play a video and fetch its tracks
     */
    fun playVideo(video: Video) {
        _uiState.value = UiState.Playing(video)
        
        // Fetch tracks in background
        viewModelScope.launch {
            try {
                val baseUrl = _serverUrl.value
                val api = ApiClient.getApi(baseUrl)
                val tracks = api.getTracks(video.id)
                
                val current = _uiState.value
                if (current is UiState.Playing && current.video.id == video.id) {
                    // Pre-select default tracks
                    val defaultAudio = tracks.audioTracks.indexOfFirst { it.default }.let { if (it == -1 && tracks.audioTracks.isNotEmpty()) 0 else it }
                    val defaultSub = tracks.subtitleTracks.indexOfFirst { it.default }
                    
                    _uiState.value = current.copy(
                        audioTracks = tracks.audioTracks,
                        subtitleTracks = tracks.subtitleTracks,
                        selectedAudioIndex = defaultAudio,
                        selectedSubtitleIndex = defaultSub
                    )
                }
            } catch (e: Exception) {
                // Ignore track error, just play without secondary tracks
            }
        }
    }
    
    fun selectAudio(index: Int) {
        val current = _uiState.value
        if (current is UiState.Playing) {
            _uiState.value = current.copy(selectedAudioIndex = index)
        }
    }
    
    fun selectSubtitle(index: Int) {
        val current = _uiState.value
        if (current is UiState.Playing) {
            _uiState.value = current.copy(selectedSubtitleIndex = index)
        }
    }
    
    fun toggleCompatMode() {
        val current = _uiState.value
        if (current is UiState.Playing) {
            _uiState.value = current.copy(useCompatMode = !current.useCompatMode)
        }
    }
    
    /**
     * Go back to video list
     */
    fun backToList() {
        _uiState.value = UiState.VideoList(allVideos)
    }
    
    /**
     * Disconnect and go back to server input
     */
    fun disconnect() {
        ApiClient.clear()
        allVideos = emptyList()
        _uiState.value = UiState.ServerInput
    }
    
    /**
     * Rescan directories
     */
    fun rescan() {
        val baseUrl = _serverUrl.value
        if (baseUrl.isEmpty()) return
        
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val api = ApiClient.getApi(baseUrl)
                val response = api.getVideos(rescan = "true")
                allVideos = response.videos
                _uiState.value = UiState.VideoList(allVideos)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Rescan failed: ${e.message}")
            }
        }
    }
    
    /**
     * Get the stream URL for a video
     */
    fun getStreamUrl(video: Video): String {
        return video.streamUrl(_serverUrl.value)
    }
}
