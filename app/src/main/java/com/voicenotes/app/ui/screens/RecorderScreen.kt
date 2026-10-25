package com.voicenotes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voicenotes.app.audio.VoiceRecorder
import com.voicenotes.app.ui.components.PulseCircle
import com.voicenotes.app.ui.components.formatDuration
import kotlinx.coroutines.delay

/** Full-screen recorder with live timer (S2). */
@Composable
fun RecorderScreen(
  onFinished: (path: String, durationMs: Long) -> Unit,
  onCancel: () -> Unit,
) {
  val context = LocalContext.current
  val recorder = remember { VoiceRecorder(context.applicationContext) }
  var elapsed by remember { mutableLongStateOf(0L) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var finished by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    runCatching { recorder.start() }
      .onFailure { errorMessage = "No pude acceder al micrófono." }
    while (true) {
      elapsed = recorder.elapsedMillis()
      delay(120)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      if (!finished) recorder.cancel()
    }
  }

  Box(
    Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.background,
          ),
        ),
      ),
  ) {
    IconButton(
      onClick = {
        finished = true
        recorder.cancel()
        onCancel()
      },
      modifier = Modifier.padding(12.dp).align(Alignment.TopStart),
    ) {
      Icon(Icons.Filled.Close, contentDescription = "Cancelar grabación")
    }

    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = if (errorMessage == null) "Grabando" else "Sin micrófono",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(24.dp))

      PulseCircle(active = errorMessage == null) {
        Box(
          modifier = Modifier
            .size(132.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Filled.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(56.dp),
          )
        }
      }

      Spacer(Modifier.height(24.dp))
      Text(
        text = formatDuration(elapsed),
        style = MaterialTheme.typography.displayMedium,
      )

      Spacer(Modifier.height(8.dp))
      Text(
        text = errorMessage ?: "Hablá con naturalidad. Al detener se guarda la nota.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(Modifier.height(36.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
      ) {
        Button(
          onClick = {
            val result = recorder.stop()
            finished = true
            if (result != null) {
              onFinished(result.first.absolutePath, result.second)
            } else {
              errorMessage = "La grabación quedó vacía."
              onCancel()
            }
          },
          enabled = errorMessage == null,
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        ) {
          Icon(Icons.Filled.Stop, contentDescription = null)
          Spacer(Modifier.size(8.dp))
          Text("Detener y guardar")
        }
      }
    }
  }
}
