package com.daydreamin.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Apple Music's public charts. "Most played" is what people are actually streaming in a
 * country right now — unlike the iTunes Store chart, which ranks paid downloads (and so fills
 * up with ringtones and one-off purchases).
 */
interface AppleChartsApi {
    @GET("api/v2/{country}/music/most-played/{limit}/songs.json")
    suspend fun mostPlayed(@Path("country") country: String, @Path("limit") limit: Int = 50): AppleChartResponse
}

@Serializable
data class AppleChartResponse(val feed: AppleChartFeed = AppleChartFeed())

@Serializable
data class AppleChartFeed(val results: List<AppleChartSong> = emptyList())

@Serializable
data class AppleChartSong(
    val id: String = "",
    val name: String = "",
    val artistName: String = "",
    val artworkUrl100: String = "",
    val genres: List<AppleGenre> = emptyList(),
)

@Serializable
data class AppleGenre(val name: String = "")
