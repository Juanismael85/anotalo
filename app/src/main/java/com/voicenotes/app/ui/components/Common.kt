package com.voicenotes.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(millis: Long): String {
  val totalSeconds = (millis / 1000).coerceAtLeast(0)
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

fun formatDate(millis: Long): String {
  if (millis <= 0L) return ""
  val formatter = SimpleDateFormat("d MMM · HH:mm", Locale.forLanguageTag("es"))
  return formatter.format(Date(millis))
}

@Composable
fun ScreenGradient(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background,
          ),
        ),
      ),
  )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
      modifier = Modifier.size(56.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
      subtitle,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

/** Soft pulsing rings shown while recording (S2). */
@Composable
fun PulseCircle(active: Boolean, content: @Composable () -> Unit) {
  val transition = rememberInfiniteTransition(label = "pulse")
  val scale by transition.animateFloat(
    initialValue = 1f,
    targetValue = if (active) 1.18f else 1f,
    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    label = "scale",
  )
  Box(contentAlignment = Alignment.Center) {
    Box(
      modifier = Modifier
        .size(180.dp)
        .scale(scale)
        .background(
          MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
          CircleShape,
        ),
    )
    content()
  }
}
