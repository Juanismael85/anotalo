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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicenotes.app.data.Activity
import com.voicenotes.app.data.Checklist
import com.voicenotes.app.data.WorkConfig
import com.voicenotes.app.export.ChecklistExporter
import com.voicenotes.app.export.NoteSharing
import com.voicenotes.app.export.ScheduleExporter
import com.voicenotes.app.ui.NotesViewModel

/** S1–S5 — the activities section: weekly schedule, work setup and the AI generator. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen(
  viewModel: NotesViewModel,
  bottomBar: @Composable () -> Unit,
) {
  val activities by viewModel.activities.collectAsStateWithLifecycle()
  val workConfig by viewModel.workConfig.collectAsStateWithLifecycle()
  val scheduleStatus by viewModel.scheduleStatus.collectAsStateWithLifecycle()
  val generating by viewModel.generating.collectAsStateWithLifecycle()
  val aiConfigured by viewModel.aiConfigured.collectAsStateWithLifecycle()
  val checklists by viewModel.checklists.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var showSetup by remember { mutableStateOf(false) }
  var showChecklistDialog by remember { mutableStateOf(false) }
  var showExport by remember { mutableStateOf(false) }

  androidx.compose.material3.Scaffold(
    containerColor = androidx.compose.ui.graphics.Color.Transparent,
    topBar = {
      androidx.compose.material3.TopAppBar(
        title = { Text("Actividades", style = MaterialTheme.typography.headlineMedium) },
        actions = {
          IconButton(
            onClick = { showExport = true },
            enabled = activities.isNotEmpty(),
          ) {
            Icon(Icons.Filled.FactCheck, contentDescription = "Exportar cronograma")
          }
          IconButton(onClick = { showSetup = true }) {
            Icon(Icons.Filled.Settings, contentDescription = "Configuración")
          }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
          containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
      )
    },
    bottomBar = bottomBar,
  ) { padding ->
    com.voicenotes.app.ui.components.ScreenGradient()
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
      WorkSummary(workConfig)
      scheduleStatus?.let { message ->
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        LaunchedEffect(message) {
          kotlinx.coroutines.delay(6_000)
          viewModel.clearScheduleStatus()
        }
      }
      Spacer(Modifier.padding(4.dp))

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
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
        WeeklySchedule(
          activities,
          onToggleDone = { id, done -> viewModel.toggleActivityDone(id, done) },
          onDelete = viewModel::deleteActivity,
        )
      }

      Spacer(Modifier.padding(12.dp))

      ChecklistsSection(
        checklists = checklists,
        onNew = { showChecklistDialog = true },
        onAddItem = viewModel::addChecklistItem,
        onToggleItem = viewModel::toggleChecklistItem,
        onDeleteItem = viewModel::deleteChecklistItem,
        onDelete = viewModel::deleteChecklist,
        onShare = { checklist ->
          NoteSharing.shareText(
            context,
            checklist.title,
            ChecklistExporter.plainText(checklist),
          )
        },
      )
    }
  }

  if (showSetup) {
    WorkSetupDialog(workConfig, onDismiss = { showSetup = false }, onSave = {
      viewModel.saveWorkConfig(it)
      showSetup = false
    })
  }

  if (showExport) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { showExport = false },
      title = { Text("Exportar cronograma") },
      text = { Text("¿Cómo querés compartirlo?") },
      confirmButton = {
        TextButton(
          onClick = {
            showExport = false
            val file = NoteSharing.schedulePdfFor(context, activities, workConfig)
            NoteSharing.shareFile(context, file, "application/pdf", "Cronograma semanal")
          },
        ) { Text("Como PDF") }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showExport = false
            NoteSharing.shareText(
              context,
              "Cronograma semanal",
              ScheduleExporter.plainText(activities, workConfig),
            )
          },
        ) { Text("Como texto") }
      },
    )
  }

  if (showChecklistDialog) {
    NewChecklistDialog(
      onDismiss = { showChecklistDialog = false },
      onCreate = { title ->
        viewModel.createChecklist(title)
        showChecklistDialog = false
      },
    )
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


/** S7 — the checklists the user keeps next to the schedule; each one can be exported. */
@Composable
private fun ChecklistsSection(
  checklists: List<Checklist>,
  onNew: () -> Unit,
  onAddItem: (String, String) -> Unit,
  onToggleItem: (String, String, Boolean) -> Unit,
  onDeleteItem: (String, String) -> Unit,
  onDelete: (String) -> Unit,
  onShare: (Checklist) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("Checklists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    TextButton(onClick = onNew) { Text("Nueva") }
  }
  if (checklists.isEmpty()) {
    Text(
      "Todavía no hay listas. Creá una para ir marcando lo que ya está listo.",
      style = MaterialTheme.typography.bodyMedium,
    )
    return
  }
  checklists.forEach { checklist ->
    ChecklistCard(
      checklist = checklist,
      onAddItem = { text -> onAddItem(checklist.id, text) },
      onToggleItem = { itemId, done -> onToggleItem(checklist.id, itemId, done) },
      onDeleteItem = { itemId -> onDeleteItem(checklist.id, itemId) },
      onDelete = { onDelete(checklist.id) },
      onShare = { onShare(checklist) },
    )
  }
}

@Composable
private fun ChecklistCard(
  checklist: Checklist,
  onAddItem: (String) -> Unit,
  onToggleItem: (String, Boolean) -> Unit,
  onDeleteItem: (String) -> Unit,
  onDelete: () -> Unit,
  onShare: () -> Unit,
) {
  var draft by remember { mutableStateOf("") }
  ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(checklist.title, style = MaterialTheme.typography.titleMedium)
          Text(
            "${checklist.items.count { it.done }} de ${checklist.items.size} listos",
            style = MaterialTheme.typography.bodySmall,
          )
        }
        IconButton(onClick = onShare) {
          Icon(Icons.Filled.Share, contentDescription = "Exportar lista")
        }
        IconButton(onClick = onDelete) {
          Icon(Icons.Filled.Delete, contentDescription = "Eliminar lista")
        }
      }
      checklist.items.forEach { item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = item.done, onCheckedChange = { onToggleItem(item.id, it) })
          Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium.copy(
              textDecoration = if (item.done) {
                androidx.compose.ui.text.style.TextDecoration.LineThrough
              } else {
                androidx.compose.ui.text.style.TextDecoration.None
              },
            ),
            color = if (item.done) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = { onDeleteItem(item.id) }) {
            Icon(Icons.Filled.Close, contentDescription = "Quitar ítem")
          }
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = draft,
          onValueChange = { draft = it },
          label = { Text("Agregar ítem") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        TextButton(
          onClick = {
            if (draft.isNotBlank()) {
              onAddItem(draft)
              draft = ""
            }
          },
        ) { Text("Sumar") }
      }
    }
  }
}

@Composable
private fun NewChecklistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
  var title by remember { mutableStateOf("") }
  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Nueva lista") },
    text = {
      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Nombre de la lista") },
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(onClick = { if (title.isNotBlank()) onCreate(title) }) { Text("Crear") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}
