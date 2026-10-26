package com.daydreamin.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface ITunesApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("limit") limit: Int = 25,
        @Query("country") country: String = "IN",
    ): ItunesSearchResponse

    /** The iTunes Store's paid-download chart — only a fallback now; see [AppleChartsApi]. */
    @GET("{country}/rss/topsongs/limit=50/json")
    suspend fun chart(@retrofit2.http.Path("country") country: String): ItunesRssResponse
}

@Serializable
data class ItunesSearchResponse(val results: List<ItunesSearchItem> = emptyList())

@Serializable
data class ItunesSearchItem(
    val trackId: Long = 0,
    val collectionId: Long = 0,
    val trackName: String = "",
    val artistName: String = "",
    val artistId: Long = 0,
    val collectionName: String = "Single",
    val artworkUrl100: String = "",
    val trackTimeMillis: Long = 0,
    val primaryGenreName: String = "Music",
)

@Serializable
data class ItunesRssResponse(val feed: ItunesRssFeed = ItunesRssFeed())

@Serializable
data class ItunesRssFeed(val entry: List<ItunesRssEntry> = emptyList())

@Serializable
data class ItunesRssEntry(
    @SerialName("im:name") val name: ItunesLabel = ItunesLabel(),
    @SerialName("im:artist") val artist: ItunesArtistEntry = ItunesArtistEntry(),
    @SerialName("im:image") val images: List<ItunesLabel> = emptyList(),
    @SerialName("im:collection") val collection: ItunesCollection = ItunesCollection(),
    val category: ItunesCategory = ItunesCategory(),
    val id: ItunesIdEntry = ItunesIdEntry(),
)

@Serializable
data class ItunesLabel(val label: String = "")

@Serializable
data class ItunesArtistEntry(val label: String = "")

@Serializable
data class ItunesCollection(@SerialName("im:name") val name: ItunesLabel = ItunesLabel())

@Serializable
data class ItunesCategory(val attributes: ItunesCategoryAttrs = ItunesCategoryAttrs())

@Serializable
data class ItunesCategoryAttrs(val label: String = "Music")

@Serializable
data class ItunesIdEntry(val attributes: ItunesIdAttrs = ItunesIdAttrs())

@Serializable
data class ItunesIdAttrs(@SerialName("im:id") val imId: String = "0")
