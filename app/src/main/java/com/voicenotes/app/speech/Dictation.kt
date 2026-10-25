package com.voicenotes.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Thin wrapper around the system SpeechRecognizer used for dictation (S5). */
class DictationController(
  private val context: Context,
  private val onText: (String) -> Unit,
  private val onError: (String) -> Unit,
) {
  private var recognizer: SpeechRecognizer? = null

  var listening by mutableStateOf(false)
    private set

  fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

  fun start() {
    if (listening) return
    if (!isAvailable()) {
      onError(NO_TRANSCRIPTION)
      return
    }

    if (recognizer == null) {
      recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
        setRecognitionListener(object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {
            listening = true
          }

          override fun onBeginningOfSpeech() = Unit

          override fun onRmsChanged(rmsdB: Float) = Unit

          override fun onBufferReceived(buffer: ByteArray?) = Unit

          override fun onEndOfSpeech() {
            listening = false
          }

          override fun onError(error: Int) {
            listening = false
            onError(NO_TRANSCRIPTION)
          }

          override fun onResults(results: Bundle?) {
            listening = false
            val text = results
              ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              ?.firstOrNull()
              ?.trim()
              .orEmpty()
            if (text.isEmpty()) onError(NO_TRANSCRIPTION) else onText(text)
          }

          override fun onPartialResults(partialResults: Bundle?) = Unit

          override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
      }
    }

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
      putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    listening = true
    runCatching { recognizer?.startListening(intent) }
      .onFailure {
        listening = false
        onError(NO_TRANSCRIPTION)
      }
  }

  fun stop() {
    listening = false
    runCatching { recognizer?.stopListening() }
  }

  fun release() {
    listening = false
    runCatching { recognizer?.destroy() }
    recognizer = null
  }

  companion object {
    const val NO_TRANSCRIPTION = "No pude transcribir el audio."
  }
}

val LocalDictationFactory = staticCompositionLocalOf<(onText: (String) -> Unit, onError: (String) -> Unit) -> DictationController> {
  error("Dictation not provided")
}

@Composable
fun rememberDictation(onText: (String) -> Unit, onError: (String) -> Unit): DictationController {
  val context = LocalContext.current
  val latestText by rememberUpdatedState(onText)
  val latestError by rememberUpdatedState(onError)
  val controller = remember {
    DictationController(
      context = context.applicationContext,
      onText = { latestText(it) },
      onError = { latestError(it) },
    )
  }
  DisposableEffect(controller) {
    onDispose { controller.release() }
  }
  return controller
}
