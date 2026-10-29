package com.voicenotes.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicenotes.app.data.Activity
import com.voicenotes.app.data.WorkConfig
import com.voicenotes.app.ui.NotesViewModel

/** S1–S5 — the activities section: weekly schedule, work setup and the AI generator. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen(
  viewModel: NotesViewModel,
  onExport: () -> Unit = {},
  bottomBar: @Composable () -> Unit,
) {
  val activities by viewModel.activities.collectAsStateWithLifecycle()
  val workConfig by viewModel.workConfig.collectAsStateWithLifecycle()
  val scheduleStatus by viewModel.scheduleStatus.collectAsStateWithLifecycle()
  val generating by viewModel.generating.collectAsStateWithLifecycle()
  val aiConfigured by viewModel.aiConfigured.collectAsStateWithLifecycle()
  var showSetup by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Actividades", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
      IconButton(onClick = onExport) {
        Icon(Icons.Filled.FactCheck, contentDescription = "Exportar cronograma")
      }
      IconButton(onClick = { showSetup = true }) {
        Icon(Icons.Filled.Settings, contentDescription = "Configuración")
      }
    }

    WorkSummary(workConfig)
    scheduleStatus?.let {
      Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
      LaunchedEffect(it) { viewModel.clearScheduleStatus() }
    }
    Spacer(Modifier.padding(4.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp))
    {
      AssistChip(
        onClick = { viewModel.generateWeeklySchedule() },
        enabled = aiConfigured && workConfig != null && !generating,
        label = { Text(if (generating) "Armando cronograma..." else "Generar con la IA") },
      )
      if (generating) CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
    }

    if (!aiConfigured) {
      Text(
        "Configurá tu clave de IA en Ajustes para armar el cronograma.",
        style = MaterialTheme.typography.bodySmall,
      )
    }

    Spacer(Modifier.padding(8.dp))

    if (activities.isEmpty()) {
      Text(
        "Sin actividades todavía. Graba notas con tareas y generá el cronograma con la IA.",
        style = MaterialTheme.typography.bodyMedium,
      )
    } else {
      WeeklySchedule(activities, onToggleDone = { id, done -> viewModel.toggleActivityDone(id, done) }, onDelete = viewModel::deleteActivity)
    }
  }

  bottomBar()

  if (showSetup) {
    WorkSetupDialog(workConfig, onDismiss = { showSetup = false }, onSave = {
      viewModel.saveWorkConfig(it)
      showSetup = false
    })
  }
}

@Composable
private fun WorkSummary(config: WorkConfig?) {
  Text(
    text = if (config == null) {
      "Días y horario de trabajo sin configurar."
    } else {
      "Trabajo: " + config.dayNumbers.joinToString(" · ") { _DAY_NAMES[it - 1] } +
        " de " + clock(config.startMinutes) + " a " + clock(config.endMinutes)
    },
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** S3/S4 — choose the workdays (with presets) and the working hour range. */
private fun parseClock(text: String): Int? = runCatching {
  val parts = text.trim().split(":")
  val hour = parts[0].toInt()
  val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
  if (hour !in 0..23 || minute !in 0..59) null else hour * 60 + minute
}.getOrDefault(null)

@Composable
private fun WeeklySchedule(
  activities: List<Activity>,
  onToggleDone: (String, Boolean) -> Unit,
  onDelete: (String) -> Unit,
) {
  (1..7).forEach { day ->
    val dayActivities = activities.filter { it.dayOfWeek == day }
    if (dayActivities.isEmpty()) return@forEach
    Text(
      _DAY_NAMES[day - 1].replaceFirstChar { it.uppercase() },
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    dayActivities.forEach { activity ->
      ElevatedCard(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 8.dp),
        ) {
          Checkbox(
            checked = activity.done,
            onCheckedChange = { checked -> onToggleDone(activity.id, checked) },
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              clock(activity.startMinutes) + "  " + activity.title,
              style = if (activity.done) {
                MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
              } else {
                MaterialTheme.typography.bodyLarge
              },
            )
            if (activity.source == Activity.SOURCE_AI) {
              Text(
                if (activity.noteId != null) "De tus notas" else "Sugerido por la IA",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
          IconButton(onClick = { onDelete(activity.id) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Eliminar actividad")
          }
        }
      }
    }
  }
}

private val _DAY_NAMES = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

private fun clock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
private fun WorkSetupDialog(
  current: WorkConfig?,
  onDismiss: () -> Unit,
  onSave: (WorkConfig) -> Unit,
) {
  var days by remember {
    mutableStateOf(current?.dayNumbers?.toSet() ?: setOf(1, 2, 3, 4, 5))
  }
  var start by remember { mutableStateOf(clock(current?.startMinutes ?: 420)) }
  var end by remember { mutableStateOf(clock(current?.endMinutes ?: 960)) }
  var problem by remember { mutableStateOf<String?>(null) }

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Horario de trabajo") },
    text = {
      Column {
        Text("Días laborales", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
          TextButton(enabled = days.size > 0, onClick = { days = (1..5).toSet() }) { Text("Lun a Vie") }
          TextButton(onClick = { days = setOf(6, 7) }) { Text("Fin de semana") }
          TextButton(onClick = { days = (1..7).toSet() }) { Text("Todos") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          (1..7).forEach { day ->
            FilterChip(
              selected = day in days,
              onClick = {
                days = if (day in days) days - day else days + day
              },
              label = { Text(_DAY_NAMES[day - 1].take(2).replaceFirstChar { c -> c.uppercase() }) },
            )
          }
        }
        Spacer(Modifier.padding(6.dp))
        Text("Horas laborales", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          androidx.compose.material3.TextField(
            value = start,
            onValueChange = { start = it.take(5) },
            label = { Text("Desde") },
            modifier = Modifier.weight(1f),
          )
          androidx.compose.material3.TextField(
            value = end,
            onValueChange = { end = it.take(5) },
            label = { Text("Hasta") },
            modifier = Modifier.weight(1f),
          )
        }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text(
          "Formato \"7:00\" o \"16:30\"",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = {
        val from = parseClock(start)
        val to = parseClock(end)
        when {
          days.isEmpty() -> problem = "Elegí al menos un día laboral."
          from == null || to == null -> problem = "Horarios incompletos; usá el formato 7:00."
          from >= to -> problem = "La hora de entrada tiene que ser anterior a la de salida."
          else -> onSave(WorkConfig(dayNumbers = days.sorted(), startMinutes = from, endMinutes = to))
        }
      }) { Text("Guardar") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}

