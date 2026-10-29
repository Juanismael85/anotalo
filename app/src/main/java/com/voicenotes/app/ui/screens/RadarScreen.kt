package com.voicenotes.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicenotes.app.data.Note
import com.voicenotes.app.reminder.ReminderBucket
import com.voicenotes.app.reminder.ReminderFormat
import com.voicenotes.app.reminder.ReminderScheduler
import com.voicenotes.app.reminder.Reminders
import com.voicenotes.app.ui.NotesViewModel
import com.voicenotes.app.ui.components.ScreenGradient

/**
 * S22 — every pending reminder grouped by urgency. Fulfilling or snoozing one here writes the note
 * through the same repository the detail screen uses, so both stay in sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
  viewModel: NotesViewModel,
  onOpenNote: (String) -> Unit,
  bottomBar: @Composable () -> Unit,
) {
  val notes by viewModel.allNotes.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var notificationsBlocked by remember {
    mutableStateOf(!NotificationManagerCompat.from(context).areNotificationsEnabled())
  }
  val scheduler = remember { ReminderScheduler(context) }
  var exactAlarmsBlocked by remember { mutableStateOf(!scheduler.canScheduleExact()) }
  var snoozeTarget by remember { mutableStateOf<Note?>(null) }

  // Re-read both permissions when coming back from the system settings.
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        notificationsBlocked = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        val blockedNow = !scheduler.canScheduleExact()
        // Granting the permission does not re-arm what was already scheduled, so do it here.
        if (exactAlarmsBlocked && !blockedNow) viewModel.rescheduleReminders()
        exactAlarmsBlocked = blockedNow
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Recomputed when the data changes; a stale "now" only affects the group labels.
  val now = remember(notes) { System.currentTimeMillis() }
  val pending = Reminders.pending(notes)

  Scaffold(
    containerColor = Color.Transparent,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Radar", style = MaterialTheme.typography.headlineMedium)
            Text(
              if (pending.size == 1) "1 recordatorio pendiente" else "${pending.size} recordatorios pendientes",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
      )
    },
    bottomBar = bottomBar,
  ) { padding ->
    ScreenGradient()
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      if (notificationsBlocked) {
        NotificationsBlockedBanner(context)
      }
      if (exactAlarmsBlocked) {
        ExactAlarmsBanner(context)
      }
      if (pending.isEmpty()) {
        EmptyRadar()
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          sections.forEach { (bucket, title) ->
            val group = Reminders.inBucket(notes, bucket, now)
            if (group.isNotEmpty()) {
              item(key = "header-$bucket") {
                SectionHeader(title, group.size, isOverdue = bucket == ReminderBucket.OVERDUE)
              }
              items(group, key = { it.id }) { note ->
                ReminderRow(
                  note = note,
                  now = now,
                  overdue = bucket == ReminderBucket.OVERDUE,
                  title = viewModel.displayTitle(note),
                  onOpen = { onOpenNote(note.id) },
                  onDone = { viewModel.markReminderDone(note.id) },
                  onSnooze = { snoozeTarget = note },
                )
              }
            }
          }
        }
      }
    }
  }

  snoozeTarget?.let { note ->
    AlertDialog(
      onDismissRequest = { snoozeTarget = null },
      title = { Text("Posponer") },
      text = {
        Column {
          Text(viewModel.displayTitle(note), style = MaterialTheme.typography.bodyMedium)
          Spacer(Modifier.height(12.dp))
          snoozeOptions.forEach { (label, minutes) ->
            TextButton(
              onClick = {
                if (minutes > 0) {
                  viewModel.snoozeReminder(note.id, minutes)
                } else {
                  viewModel.snoozeUntilTomorrow(note.id)
                }
                snoozeTarget = null
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(label, modifier = Modifier.fillMaxWidth())
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { snoozeTarget = null }) { Text("Cancelar") }
      },
    )
  }
}

private val sections = listOf(
  ReminderBucket.OVERDUE to "Vencidos",
  ReminderBucket.TODAY to "Hoy",
  ReminderBucket.TOMORROW to "Mañana",
  ReminderBucket.LATER to "Próximos",
)

private val snoozeOptions = listOf(
  "10 minutos" to 10,
  "1 hora" to 60,
  "Mañana 9:00" to 0,
)

@Composable
private fun SectionHeader(title: String, count: Int, isOverdue: Boolean) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 12.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      title,
      style = MaterialTheme.typography.titleSmall,
      color = if (isOverdue) {
        MaterialTheme.colorScheme.error
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
    )
    Spacer(Modifier.weight(1f))
    Text(
      count.toString(),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ReminderRow(
  note: Note,
  now: Long,
  overdue: Boolean,
  title: String,
  onOpen: () -> Unit,
  onDone: () -> Unit,
  onSnooze: () -> Unit,
) {
  val at = note.reminderAt ?: return
  Card(
    onClick = onOpen,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Filled.Alarm,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
          )
          Spacer(Modifier.padding(start = 6.dp))
          Text(
            ReminderFormat.label(at, now),
            style = MaterialTheme.typography.labelLarge,
            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
          )
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        val preview = note.summary.ifBlank { note.transcript.ifBlank { note.body } }.trim()
        if (preview.isNotBlank()) {
          Text(
            preview,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
          )
        }
        if (note.reminderFromAi) {
          Text(
            "Sugerido por la IA",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      IconButton(onClick = onDone) {
        Icon(Icons.Filled.Check, contentDescription = "Marcar como hecho")
      }
      IconButton(onClick = onSnooze) {
        Icon(Icons.Filled.Schedule, contentDescription = "Posponer")
      }
    }
  }
}

@Composable
private fun EmptyRadar() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      Icons.Filled.Alarm,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Text("No hay recordatorios pendientes", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    Text(
      "Agregá fecha y hora en una nota, o dejá que la IA la detecte cuando la nota menciona una cita.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ExactAlarmsBanner(context: android.content.Context) {
  Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Filled.Alarm,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
      )
      Spacer(Modifier.padding(start = 12.dp))
      Text(
        "Sin permiso de alarmas exactas el aviso puede llegar unos minutos tarde.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          runCatching { context.startActivity(intent) }
        }
      }) {
        Text("Permitir")
      }
    }
  }
}

@Composable
private fun NotificationsBlockedBanner(context: android.content.Context) {
  Surface(color = MaterialTheme.colorScheme.errorContainer) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Filled.NotificationsOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onErrorContainer,
      )
      Spacer(Modifier.padding(start = 12.dp))
      Text(
        "Las notificaciones están bloqueadas: el radar no va a poder avisarte.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
          .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
      }) {
        Text("Activar")
      }
    }
  }
}
