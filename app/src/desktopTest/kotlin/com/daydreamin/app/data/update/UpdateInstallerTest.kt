package com.daydreamin.app.data.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateInstallerTest {
    private val payload = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/Daydreamin-9.9.9-test.bin") { ex -> ex.sendResponseHeaders(200, payload.size.toLong()); ex.responseBody.use { it.write(payload) } }
        start()
    }
    private val url = "http://127.0.0.1:${server.address.port}/Daydreamin-9.9.9-test.bin"
    private val key = UpdateInstaller.downloadKey()!!

    @AfterTest fun tearDown() {
        server.stop(0)
        File(System.getProperty("user.home"), "Downloads/Daydreamin-9.9.9-test.bin").delete()
        File(System.getProperty("java.io.tmpdir"), "Daydreamin-9.9.9-test.bin").delete()
        UpdateInstaller.reset()
    }

    private fun run(download: DesktopDownload): Pair<InstallState, File?> {
        var opened: File? = null
        UpdateInstaller.opener = { opened = it }
        UpdateInstaller.reset()
        UpdateInstaller.start(RemoteVersion(9, "9.9.9", "https://example.invalid", downloads = mapOf(key to download)), CoroutineScope(Dispatchers.IO))
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 15_000) {
            val s = UpdateInstaller.state.value
            if (s is InstallState.Failed || (s is InstallState.Installing && opened != null)) break
            Thread.sleep(20)
        }
        return UpdateInstaller.state.value to opened
    }

    @Test fun downloadsChecksAndOpensTheInstaller() {
        val (state, opened) = run(DesktopDownload(url, payload.size.toLong(), sha))
        assertIs<InstallState.Installing>(state)
        assertTrue(opened!!.readBytes().contentEquals(payload), "opened the exact file")
        println("[update-test] $key installer downloaded, verified and opened: ${opened.absolutePath}")
    }

    @Test fun refusesAFileThatDoesNotMatchTheRelease() {
        val (state, opened) = run(DesktopDownload(url, payload.size.toLong(), "0".repeat(64)))
        assertIs<InstallState.Failed>(state)
        assertEquals(null, opened)
        val downloads = File(System.getProperty("user.home"), "Downloads")
        assertTrue(downloads.listFiles().orEmpty().none { it.name.startsWith("Daydreamin-9.9.9-test") }, "nothing left behind")
        println("[update-test] mismatch refused: ${(state as InstallState.Failed).message}")
    }
}
