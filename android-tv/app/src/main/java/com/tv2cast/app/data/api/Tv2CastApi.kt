package com.tv2cast.app.data.api

import com.tv2cast.app.data.model.ServerInfo
import com.tv2cast.app.data.model.VideoListResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit API interface for Tv2Cast server
 */
interface Tv2CastApi {
    
    @GET("/api/videos")
    suspend fun getVideos(
        @Query("search") search: String? = null,
        @Query("rescan") rescan: String? = null
    ): VideoListResponse
    
    @GET("/api/server-info")
    suspend fun getServerInfo(): ServerInfo
    
    @GET("/api/videos/{id}")
    suspend fun getVideoInfo(
        @retrofit2.http.Path("id") id: String
    ): com.tv2cast.app.data.model.Video
    
    @GET("/api/videos/{id}/tracks")
    suspend fun getTracks(
        @retrofit2.http.Path("id") id: String
    ): com.tv2cast.app.data.model.VideoTracksResponse
    
    @POST("/api/rescan")
    suspend fun rescan(): Map<String, Any>
}
