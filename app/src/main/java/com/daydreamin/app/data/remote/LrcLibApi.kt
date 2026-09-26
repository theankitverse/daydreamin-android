package com.daydreamin.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibApi {
    @GET("api/search")
    suspend fun search(
        @Query("artist_name") artist: String,
        @Query("track_name") title: String,
    ): List<LrcLibEntry>

    /** Loose combined-text search — used as a fallback when artist/title fielded search misses (e.g. a noisy YouTube uploader name). */
    @GET("api/search")
    suspend fun searchByQuery(@Query("q") q: String): List<LrcLibEntry>

    @GET("api/get")
    suspend fun get(
        @Query("artist_name") artist: String,
        @Query("track_name") title: String,
    ): LrcLibEntry
}

@Serializable
data class LrcLibEntry(
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
)
