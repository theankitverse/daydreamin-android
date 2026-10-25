package com.daydreamin.app.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.daydreamin.app.data.download.Downloads

/**
 * Sends a downloaded song (`daydreamin://` address) to the download store and everything else to
 * the streaming path, so the player needs no idea which is which.
 */
@UnstableApi
internal class DownloadRoutingDataSourceFactory(
    private val streaming: DataSource.Factory,
    private val downloads: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = Routing(streaming.createDataSource(), downloads.createDataSource())

    private class Routing(private val streaming: DataSource, private val downloads: DataSource) : DataSource {
        private var current: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            streaming.addTransferListener(transferListener)
            downloads.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val source = if (dataSpec.uri.scheme == Downloads.SCHEME) downloads else streaming
            current = source
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            current?.read(buffer, offset, length) ?: throw IllegalStateException("read before open")

        override fun getUri(): Uri? = current?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

        override fun close() {
            try { current?.close() } finally { current = null }
        }
    }
}
