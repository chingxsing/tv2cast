package com.tv2cast.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Video item from the Tv2Cast server API
 */
data class Video(
    val id: String,
    val name: String,
    val filename: String,
    val extension: String,
    val size: Long,
    @SerializedName("sizeFormatted")
    val sizeFormatted: String,
    val modified: String,
    val directory: String
) {
    /**
     * Build the streaming URL for this video
     */
    fun streamUrl(baseUrl: String): String = "$baseUrl/api/videos/$id/stream"
}

/**
 * API response wrapper for video list
 */
data class VideoListResponse(
    val total: Int,
    val videos: List<Video>
)

/**
 * Server info response
 */
data class ServerInfo(
    val name: String,
    val version: String,
    val port: Int,
    val addresses: List<NetworkAddress>,
    val videoCount: Int
)

data class NetworkAddress(
    val name: String,
    val address: String
)

/**
 * Audio or subtitle track info
 */
data class MediaTrack(
    val index: Int,
    val label: String,
    val language: String,
    val codec: String,
    val embedded: Boolean = true,
    val default: Boolean = false,
    @SerializedName("forced")
    val forced: Boolean = false,
    val filename: String? = null
)

/**
 * Response for video tracks info
 */
data class VideoTracksResponse(
    val audioTracks: List<MediaTrack>,
    val subtitleTracks: List<MediaTrack>,
    val duration: Double?
)
