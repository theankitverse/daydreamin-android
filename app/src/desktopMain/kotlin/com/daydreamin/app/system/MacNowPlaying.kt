package com.daydreamin.app.system

import com.daydreamin.app.platform.Log
import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TAG = "MacNowPlaying"

/** The handful of Objective-C runtime calls needed to use Apple's MediaPlayer framework from the JVM. */
private interface ObjCRuntime : Library {
    fun objc_getClass(name: String): Pointer?
    fun sel_registerName(name: String): Pointer
    fun objc_allocateClassPair(superclass: Pointer, name: String, extraBytes: Long): Pointer?
    fun objc_registerClassPair(cls: Pointer)
    fun class_addMethod(cls: Pointer, name: Pointer, imp: Callback, types: String): Boolean
}

/** An Objective-C method that receives a command event and returns MPRemoteCommandHandlerStatus. */
private interface CommandHandler : Callback {
    fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long
}

/** CGSize, passed by value to the artwork block. */
@Structure.FieldOrder("width", "height")
class CGSize : Structure(), Structure.ByValue {
    @JvmField var width = 0.0
    @JvmField var height = 0.0
}

/** The artwork block's body: returns an NSImage for the size asked for. */
private interface ArtworkBlockInvoke : Callback {
    fun invoke(block: Pointer?, size: CGSize): Pointer?
}

/**
 * macOS's Now Playing: the song in Control Center and on the lock screen, and the media keys,
 * AirPods and headphone buttons — through MPNowPlayingInfoCenter and MPRemoteCommandCenter.
 */
class MacNowPlaying : MediaBridge {
    private val objc = Native.load("objc", ObjCRuntime::class.java)
    private val msgSend = Function.getFunction("objc", "objc_msgSend")
    private val mediaPlayer = NativeLibrary.getInstance("/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer")
    // AppKit (NSImage) is loaded by the JVM's own windowing already; this just finds it.
    private val appKit = NativeLibrary.getInstance("/System/Library/Frameworks/AppKit.framework/AppKit")

    /** Kept reachable for as long as the app runs: the native side calls back into them. */
    private val handlers = mutableListOf<Callback>()
    private val target: Pointer
    private val artworkLoader = Executors.newSingleThreadExecutor { Thread(it, "now-playing-art").apply { isDaemon = true } }
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    @Volatile private var artworkFor: String? = null
    @Volatile private var artwork: Pointer? = null // a retained MPMediaItemArtwork
    @Volatile private var lastState: NowPlaying? = null

    init {
        target = makeTarget()
        val center = send(cls("MPRemoteCommandCenter"), "sharedCommandCenter")
        fun hook(command: String, action: String) {
            val cmd = send(center, command)
            send(cmd, "setEnabled:", true)
            send(cmd, "addTarget:action:", target, sel(action))
        }
        hook("playCommand", "daydreaminPlay:")
        hook("pauseCommand", "daydreaminPause:")
        hook("togglePlayPauseCommand", "daydreaminToggle:")
        hook("nextTrackCommand", "daydreaminNext:")
        hook("previousTrackCommand", "daydreaminPrevious:")
        hook("changePlaybackPositionCommand", "daydreaminSeek:")
    }

    /** An Objective-C object whose methods are the command handlers. */
    private fun makeTarget(): Pointer {
        val nsObject = cls("NSObject")
        val name = "DaydreaminMediaTarget"
        val existing = objc.objc_getClass(name)
        val klass = existing ?: objc.objc_allocateClassPair(nsObject, name, 0)!!.also { klass ->
            fun method(selector: String, action: (Pointer?) -> Unit) {
                val h = object : CommandHandler {
                    override fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long {
                        runCatching { action(event) }.onFailure { Log.w(TAG, "command $selector failed: ${it.message}") }
                        return 0 // MPRemoteCommandHandlerStatusSuccess
                    }
                }
                handlers += h
                objc.class_addMethod(klass, sel(selector), h, "q@:@")
            }
            method("daydreaminPlay:") { MediaCommands.play() }
            method("daydreaminPause:") { MediaCommands.pause() }
            method("daydreaminToggle:") { MediaCommands.toggle() }
            method("daydreaminNext:") { MediaCommands.next() }
            method("daydreaminPrevious:") { MediaCommands.previous() }
            method("daydreaminSeek:") { event ->
                val seconds = msgSend.invokeDouble(arrayOf(event, sel("positionTime")))
                MediaCommands.seekTo((seconds * 1000).toLong())
            }
            objc.objc_registerClassPair(klass)
        }
        return send(send(klass, "alloc"), "init")!!
    }

    override fun update(state: NowPlaying?) {
        lastState = state
        val pool = send(send(cls("NSAutoreleasePool"), "alloc"), "init")
        try {
            val center = send(cls("MPNowPlayingInfoCenter"), "defaultCenter")
            if (state == null) {
                send(center, "setNowPlayingInfo:", Pointer.NULL)
                send(center, "setPlaybackState:", 0L) // unknown
                return
            }
            val info = send(cls("NSMutableDictionary"), "dictionary")
            put(info, "MPMediaItemPropertyTitle", nsString(state.song.title))
            put(info, "MPMediaItemPropertyArtist", nsString(state.song.artist))
            if (state.song.album.isNotBlank() && state.song.album != "Single") put(info, "MPMediaItemPropertyAlbumTitle", nsString(state.song.album))
            if (state.durationMs > 0) put(info, "MPMediaItemPropertyPlaybackDuration", nsNumber(state.durationMs / 1000.0))
            put(info, "MPNowPlayingInfoPropertyElapsedPlaybackTime", nsNumber(state.positionMs / 1000.0))
            put(info, "MPNowPlayingInfoPropertyPlaybackRate", nsNumber(if (state.playing) 1.0 else 0.0))
            val art = artwork
            if (art != null && artworkFor == state.artworkUrl) put(info, "MPMediaItemPropertyArtwork", art)
            send(center, "setNowPlayingInfo:", info)
            send(center, "setPlaybackState:", if (state.playing) 1L else 2L) // playing / paused
        } finally {
            send(pool, "drain")
        }
        if (state?.artworkUrl != null && state.artworkUrl != artworkFor) loadArtwork(state.artworkUrl)
    }

    /** Fetches the cover once per song and shows it as soon as it's here. */
    private fun loadArtwork(url: String) {
        artworkFor = url
        artwork = null
        artworkLoader.execute {
            val bytes = runCatching {
                http.newCall(Request.Builder().url(com.daydreamin.app.ui.theme.artworkModel(url)).build()).execute().use { it.body?.bytes() }
            }.getOrNull() ?: return@execute
            if (artworkFor != url) return@execute
            val made = runCatching { makeArtwork(bytes) }.onFailure { Log.w(TAG, "no artwork: ${it.message}") }.getOrNull() ?: return@execute
            if (artworkFor != url) return@execute
            artwork = made
            lastState?.let { update(it) }
        }
    }

    /** MPMediaItemArtwork for an image file's bytes. Its initializer wants a block that hands back the image. */
    private fun makeArtwork(bytes: ByteArray): Pointer? {
        val buffer = Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
        val data = send(cls("NSData"), "dataWithBytes:length:", buffer, bytes.size.toLong())
        val image = send(send(cls("NSImage"), "alloc"), "initWithData:", data) ?: return null // retained, kept
        val invoke = object : ArtworkBlockInvoke {
            override fun invoke(block: Pointer?, size: CGSize): Pointer? = image
        }
        handlers += invoke
        // A global block literal: isa, flags, reserved, invoke, descriptor {reserved, size}.
        val descriptor = Memory(16).apply { setLong(0, 0); setLong(8, 32) }
        val block = Memory(32).apply {
            setPointer(0, appKitOrSystem("_NSConcreteGlobalBlock"))
            setInt(8, 1 shl 28) // BLOCK_IS_GLOBAL
            setInt(12, 0)
            setPointer(16, com.sun.jna.CallbackReference.getFunctionPointer(invoke))
            setPointer(24, descriptor)
        }
        keepAlive += listOf(buffer, descriptor, block)
        val size = CGSize().apply { width = 512.0; height = 512.0 }
        val alloc = send(cls("MPMediaItemArtwork"), "alloc")
        return msgSend.invoke(Pointer::class.java, arrayOf(alloc, sel("initWithBoundsSize:requestHandler:"), size, block)) as Pointer?
    }

    private val keepAlive = mutableListOf<Memory>()

    private fun appKitOrSystem(symbol: String): Pointer =
        runCatching { NativeLibrary.getInstance("System").getGlobalVariableAddress(symbol) }.getOrNull()
            ?: appKit.getGlobalVariableAddress(symbol)

    // ---- Objective-C helpers

    private fun cls(name: String): Pointer = objc.objc_getClass(name) ?: error("no class $name")
    private fun sel(name: String): Pointer = objc.sel_registerName(name)
    private fun send(receiver: Pointer?, selector: String, vararg args: Any?): Pointer? =
        msgSend.invoke(Pointer::class.java, arrayOf(receiver, sel(selector), *args)) as Pointer?

    private fun nsString(s: String): Pointer? = send(cls("NSString"), "stringWithUTF8String:", s)
    private fun nsNumber(d: Double): Pointer? = send(cls("NSNumber"), "numberWithDouble:", d)

    /** The framework's own key strings (MPMediaItemPropertyTitle and friends). */
    private fun key(name: String): Pointer = mediaPlayer.getGlobalVariableAddress(name).getPointer(0)
    private fun put(dict: Pointer?, keyName: String, value: Pointer?) {
        if (value != null) send(dict, "setObject:forKey:", value, key(keyName))
    }

    override fun close() = update(null)
}
