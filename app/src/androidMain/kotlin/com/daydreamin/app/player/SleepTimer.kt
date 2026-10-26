package com.daydreamin.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Pauses playback after N minutes. Used by the Now Playing screen's sleep-timer action. */
object SleepTimer {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _remainingSeconds = MutableStateFlow<Int?>(null)
    val remainingSeconds: StateFlow<Int?> = _remainingSeconds

    fun start(minutes: Int) {
        job?.cancel()
        var seconds = minutes * 60
        _remainingSeconds.value = seconds
        job = scope.launch {
            while (isActive && seconds > 0) {
                kotlinx.coroutines.delay(1000)
                seconds -= 1
                _remainingSeconds.value = seconds
            }
            if (isActive) {
                PlayerController.togglePlayPauseIfPlaying()
                _remainingSeconds.value = null
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _remainingSeconds.value = null
    }
}
