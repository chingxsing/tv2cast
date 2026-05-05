package com.tv2cast.app.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Singleton API client builder
 */
object ApiClient {
    
    private var currentBaseUrl: String = ""
    private var api: Tv2CastApi? = null
    
    /**
     * Get or create the API instance for a given server URL
     */
    fun getApi(baseUrl: String): Tv2CastApi {
        if (api == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            api = retrofit.create(Tv2CastApi::class.java)
        }
        return api!!
    }
    
    /**
     * Clear the cached API client
     */
    fun clear() {
        api = null
        currentBaseUrl = ""
    }
}
