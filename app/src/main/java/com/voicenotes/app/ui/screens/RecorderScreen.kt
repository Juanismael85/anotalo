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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voicenotes.app.audio.RecordingService
import com.voicenotes.app.audio.RecordingSession
import com.voicenotes.app.ui.components.PulseCircle
import com.voicenotes.app.ui.components.formatDuration
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Full-screen recorder with live timer (S2). Recording itself runs in a service (S14). */
@Composable
fun RecorderScreen(
  onStop: () -> Unit,
  onCancel: () -> Unit,
  onCompleted: () -> Unit,
) {
  val context = LocalContext.current
  val active by RecordingSession.active.collectAsStateWithLifecycle()
  val elapsed by RecordingSession.elapsed.collectAsStateWithLifecycle()
  val errorMessage by RecordingSession.error.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    if (!RecordingSession.active.value) {
      RecordingService.start(context)
    }
  }

  // Stopped from the notification while this screen sat in the background. Only navigate once
  // the session was really running, otherwise the first "not active yet" value would bounce back.
  var wasActive by remember { mutableStateOf(false) }
  LaunchedEffect(active) {
    if (active) {
      wasActive = true
    } else if (wasActive) {
      wasActive = false
      onCompleted()
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
      onClick = onCancel,
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
        text = errorMessage
          ?: "Hablá con naturalidad. Podés salir de la app: la grabación sigue.",
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
          onClick = onStop,
          enabled = errorMessage == null && active,
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
