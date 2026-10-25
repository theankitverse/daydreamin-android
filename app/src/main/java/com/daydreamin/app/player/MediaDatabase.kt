package com.daydreamin.app.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider

/** The one database Media3 keeps its cache and download indexes in — shared by the streaming cache and downloads. */
internal object MediaDatabase {
    @Volatile private var provider: StandaloneDatabaseProvider? = null

    fun get(context: Context): StandaloneDatabaseProvider =
        provider ?: synchronized(this) {
            provider ?: StandaloneDatabaseProvider(context.applicationContext).also { provider = it }
        }
}
