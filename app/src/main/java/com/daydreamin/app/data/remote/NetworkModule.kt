package com.daydreamin.app.data.remote

import com.daydreamin.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.daydreamin.app.data.remote.LegacyTls.withLegacyRoots
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Everything here talks directly to free, public, unauthenticated APIs — no backend of ours
 * involved. iTunes for search/chart metadata, LRCLIB for lyrics; audio itself is resolved
 * on-device via [com.daydreamin.app.data.youtube.YouTubeExtractorService].
 */
object NetworkModule {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        // Identify ourselves. LRCLIB (behind Cloudflare) answers OkHttp's default "okhttp/x.y.z"
        // User-Agent with HTTP 520 — every lyrics lookup failed until this — and its API docs ask
        // clients to name themselves anyway.
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Daydreamin/${BuildConfig.VERSION_NAME} (Android; +https://github.com/daydreamin)")
                    .build(),
            )
        }
        .addInterceptor(logging)
        .withLegacyRoots()
        .build()

    private fun retrofit(baseUrl: String): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    val itunes: ITunesApi by lazy { retrofit("https://itunes.apple.com/").create(ITunesApi::class.java) }
    val lrcLib: LrcLibApi by lazy { retrofit("https://lrclib.net/").create(LrcLibApi::class.java) }
    val appleCharts: AppleChartsApi by lazy { retrofit("https://rss.marketingtools.apple.com/").create(AppleChartsApi::class.java) }
}
