package com.voicenotes.app.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A finished recording waiting to become a note (S14). */
data class RecordedAudio(val path: String, val durationMs: Long)

/**
 * Bridges the foreground service and the UI without letting the service write `notes.json`
 * (two writers on the same file would lose notes). If nobody is listening when the recording
 * ends, the audio file stays on disk and is imported on the next launch.
 */
object RecordingSession {
  private val _active = MutableStateFlow(false)
  val active: StateFlow<Boolean> = _active.asStateFlow()

  private val _elapsed = MutableStateFlow(0L)
  val elapsed: StateFlow<Long> = _elapsed.asStateFlow()

  private val _result = MutableStateFlow<RecordedAudio?>(null)
  val result: StateFlow<RecordedAudio?> = _result.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  internal fun setActive(value: Boolean) {
    _active.value = value
  }

  internal fun setElapsed(value: Long) {
    _elapsed.value = value
  }

  internal fun publish(result: RecordedAudio?) {
    _result.value = result
  }

  internal fun setError(message: String?) {
    _error.value = message
  }

  fun consumeResult() {
    _result.value = null
  }
}
