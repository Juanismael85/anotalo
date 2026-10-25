package com.voicenotes.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records AAC/M4A into filesDir/audio (S2). */
class VoiceRecorder(private val context: Context) {

  private var recorder: MediaRecorder? = null
  private var target: File? = null
  private var startedAtMillis: Long = 0L
  private var running = false

  val isRecording: Boolean get() = running

  fun start(): File {
    stopInternal(discard = true)

    val dir = File(context.filesDir, "audio").apply { mkdirs() }
    val file = File(dir, "note-${System.currentTimeMillis()}.m4a")

    val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      MediaRecorder(context)
    } else {
      @Suppress("DEPRECATION")
      MediaRecorder()
    }

    created.setAudioSource(MediaRecorder.AudioSource.MIC)
    created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    created.setAudioEncodingBitRate(96_000)
    created.setAudioSamplingRate(44_100)
    created.setOutputFile(file.absolutePath)
    created.prepare()
    created.start()

    recorder = created
    target = file
    startedAtMillis = System.currentTimeMillis()
    running = true
    return file
  }

  /** Stops the capture and returns the file with its duration in millis, or null. */
  fun stop(): Pair<File, Long>? {
    val file = target
    if (!running || file == null) {
      stopInternal(discard = false)
      return null
    }
    val elapsed = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
    stopInternal(discard = false)
    return if (file.exists() && file.length() > 0) file to elapsed else null
  }

  /** Aborts and deletes the partial file. */
  fun cancel() {
    val file = target
    stopInternal(discard = true)
    runCatching { file?.delete() }
  }

  fun elapsedMillis(): Long = if (running) System.currentTimeMillis() - startedAtMillis else 0L

  private fun stopInternal(discard: Boolean) {
    val active = recorder
    recorder = null
    running = false
    if (active != null) {
      runCatching { if (!discard) active.stop() }
      runCatching { active.reset() }
      runCatching { active.release() }
    }
    if (discard) runCatching { target?.delete() }
    if (!discard) target = null
    if (discard) target = null
    startedAtMillis = 0L
  }
}
