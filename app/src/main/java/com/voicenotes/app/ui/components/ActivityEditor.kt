package com.voicenotes.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.voicenotes.app.data.Activity
import com.voicenotes.app.data.ActivityDates
import com.voicenotes.app.data.ScheduleRules
import java.time.LocalDate

/** What the editor shows and returns: an activity before it exists, or one being edited. */
data class ActivityDraft(
  val id: String? = null,
  val title: String = "",
  /** S11 — ISO date; blank seeds it from [dayOfWeek] the first time the dialog opens. */
  val date: String = "",
  val dayOfWeek: Int = 1,
  val startMinutes: Int = 7 * 60,
  val durationMinutes: Int = ScheduleRules.DEFAULT_DURATION,
  /** S12/S13 — "none", "daily" or "weekly" plus the last date of the series. */
  val repeat: String = Activity.REPEAT_NONE,
  val repeatUntil: String? = null,
  /** S14 — ask me when the occurrence is due. */
  val remind: Boolean = false,
  val repeatDays: List<Int> = emptyList(),
  val noteId: String? = null,
)

val durationChoices = listOf(30, 45, 60, 90, 120)

val repeatChoices = listOf(
  Activity.REPEAT_NONE to "No se repite",
  Activity.REPEAT_DAILY to "Cada día",
  Activity.REPEAT_WEEKLY to "Cada 7 días",
)

/** "07:30" — the schedule writes every hour the same way, with or without a leading zero. */
fun clockText(minutes: Int): String = "%02d:%02d".format(minutes / 60 % 24, minutes % 60)

/** Reads "7:30", "07.30" or "730"; null when it is not a time of the day. */
fun parseClockText(text: String): Int? = runCatching {
  val clean = text.trim().replace('.', ':').replace('h', ':').trimEnd(':')
  if (clean.isEmpty()) return null
  val parts = clean.split(':')
  val hour = parts[0].trim().toIntOrNull() ?: return null
  val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
  if (hour !in 0..23 || minute !in 0..59) null else hour * 60 + minute
}.getOrNull()

/**
 * S1/S2 of the agenda block — one dialog for both ways in: "Agendar" a note and "Nueva actividad".
 * Nothing is written until the user confirms; a blank title or an impossible time stays on screen
 * with the reason. S11/S12 — the activity now has a date and an optional series.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActivityEditorDialog(
  heading: String,
  draft: ActivityDraft,
  onDismiss: () -> Unit,
  onSave: (ActivityDraft) -> Unit,
  today: LocalDate = LocalDate.now(),
) {
  val startDate = remember(draft, today) {
    ActivityDates.parse(draft.date) ?: ActivityDates.nextDateFor(draft.dayOfWeek, today)
  }
  var title by remember(draft) { mutableStateOf(draft.title) }
  var date by remember(draft) { mutableStateOf(startDate) }
  var start by remember(draft) { mutableStateOf(clockText(draft.startMinutes)) }
  var duration by remember(draft) { mutableStateOf(draft.durationMinutes) }
  var repeat by remember(draft) { mutableStateOf(draft.repeat) }
  var until by remember(draft) { mutableStateOf(ActivityDates.parse(draft.repeatUntil)) }
  var remind by remember(draft) { mutableStateOf(draft.remind) }
  var problem by remember { mutableStateOf<String?>(null) }
  var pickingDate by remember { mutableStateOf(false) }
  var pickingUntil by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(heading) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        TextField(
          value = title,
          onValueChange = { title = it.take(80) },
          label = { Text("Actividad") },
          singleLine = true,
        )
        Spacer(Modifier.padding(6.dp))
        Text("Fecha", style = MaterialTheme.typography.titleSmall)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          FilterChip(
            selected = true,
            onClick = { pickingDate = true },
            label = { Text(ActivityDates.shortLabel(date)) },
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.EventAvailable,
                contentDescription = "Elegir fecha",
                modifier = Modifier.size(18.dp),
              )
            },
          )
          FilterChip(
            selected = date == today,
            onClick = { date = today },
            label = { Text("Hoy") },
          )
          FilterChip(
            selected = date == today.plusDays(1),
            onClick = { date = today.plusDays(1) },
            label = { Text("Mañana") },
          )
        }
        Spacer(Modifier.padding(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextField(
            value = start,
            onValueChange = { start = it.take(5) },
            label = { Text("Hora") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          Column(Modifier.weight(1f)) {
            Text("Duración", style = MaterialTheme.typography.titleSmall)
            Text("$duration min", style = MaterialTheme.typography.bodyMedium)
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          durationChoices.forEach { value ->
            FilterChip(
              selected = duration == value,
              onClick = { duration = value },
              label = { Text("$value") },
            )
          }
        }
        Spacer(Modifier.padding(6.dp))
        Text("Repetición", style = MaterialTheme.typography.titleSmall)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          repeatChoices.forEach { (value, label) ->
            FilterChip(
              selected = repeat == value,
              onClick = {
                repeat = value
                if (value == Activity.REPEAT_NONE) until = null
              },
              label = { Text(label) },
            )
          }
        }
        if (repeat != Activity.REPEAT_NONE) {
          Spacer(Modifier.padding(4.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
              selected = until == null,
              onClick = { until = null },
              label = { Text("Hasta cumplirla") },
            )
            FilterChip(
              selected = until != null,
              onClick = { pickingUntil = true },
              label = {
                Text(
                  until?.let { "Hasta el ${ActivityDates.label(it, today)}" } ?: "Hasta una fecha",
                )
              },
            )
          }
        }
        Spacer(Modifier.padding(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            "Avisarme cuando toque",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
          )
          Switch(checked = remind, onCheckedChange = { remind = it })
        }
        if (draft.noteId != null) {
          Spacer(Modifier.padding(4.dp))
          Text(
            "Queda vinculada a la nota de origen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        problem?.let {
          Spacer(Modifier.padding(4.dp))
          Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val minutes = parseClockText(start)
          val end = minutes?.plus(duration)
          val untilDate = until
          when {
            title.isBlank() -> problem = "Poné un nombre a la actividad."
            minutes == null -> problem = "Hora inválida; usá el formato 7:30."
            end != null && end > 24 * 60 -> problem = "La actividad terminaría después de las 24:00."
            untilDate != null && untilDate.isBefore(date) ->
              problem = "La repetición no puede terminar antes de la fecha."
            else -> onSave(
              draft.copy(
                title = title.trim(),
                date = ActivityDates.iso(date),
                dayOfWeek = date.dayOfWeek.value,
                startMinutes = minutes,
                durationMinutes = duration,
                repeat = repeat,
                repeatUntil = untilDate?.let { ActivityDates.iso(it) },
                remind = remind,
              ),
            )
          }
        },
      ) { Text("Guardar") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )

  if (pickingDate) {
    ActivityDatePickerDialog(
      initial = date,
      onDismiss = { pickingDate = false },
      onPick = { picked ->
        date = picked
        pickingDate = false
      },
    )
  }
  if (pickingUntil) {
    ActivityDatePickerDialog(
      initial = until ?: date,
      onDismiss = { pickingUntil = false },
      onPick = { picked ->
        until = picked
        pickingUntil = false
      },
    )
  }
}

/** The one date picker both fields share, so picking a day behaves the same everywhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityDatePickerDialog(
  initial: LocalDate,
  onDismiss: () -> Unit,
  onPick: (LocalDate) -> Unit,
) {
  val state = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L)
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          val millis = state.selectedDateMillis
          if (millis == null) onDismiss() else onPick(LocalDate.ofEpochDay(millis / 86_400_000L))
        },
      ) { Text("Listo") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  ) {
    DatePicker(state = state)
  }
}

/**
 * S1 of the agenda block — a note holds several tasks, so "Agendar" opens this list instead of a
 * single form: every task arrives ticked, each one can be edited, and only the ticked ones are
 * written. The whole batch is saved at once, linked to the note it came from.
 */
@Composable
fun ScheduleSuggestionsDialog(
  drafts: List<ActivityDraft>,
  onDismiss: () -> Unit,
  onSave: (List<ActivityDraft>) -> Unit,
  today: LocalDate = LocalDate.now(),
) {
  var rows by remember(drafts) { mutableStateOf(drafts) }
  var selected by remember(drafts) { mutableStateOf(drafts.indices.toSet()) }
  var editing by remember { mutableStateOf<Int?>(null) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
    ) {
      Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                RoundedCornerShape(14.dp),
              ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.EventAvailable,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          Spacer(Modifier.width(14.dp))
          Column {
            Text(
              text = "¿Agendar esta nota?",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = if (rows.size == 1) {
                "Encontré 1 tarea. Decidí si va al cronograma."
              } else {
                "Encontré ${rows.size} tareas. Elegí cuáles van al cronograma."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Spacer(Modifier.height(18.dp))

        Column(
          modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          rows.forEachIndexed { index, draft ->
            val isSelected = index in selected
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  selected = if (isSelected) selected - index else selected + index
                },
              shape = RoundedCornerShape(18.dp),
              color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
              },
              border = BorderStroke(
                1.dp,
                if (isSelected) {
                  MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                  MaterialTheme.colorScheme.outlineVariant
                },
              ),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(checked = isSelected, onCheckedChange = null)
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                  Text(
                    text = draft.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = "${ActivityDates.label(draftDay(draft, today), today)} · " +
                      "${clockText(draft.startMinutes)} · ${draft.durationMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(onClick = { editing = index }) {
                  Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Editar ${draft.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        Spacer(Modifier.height(14.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onDismiss) { Text("Cancelar") }
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = { onSave(rows.filterIndexed { index, _ -> index in selected }) },
            enabled = selected.isNotEmpty(),
            shape = RoundedCornerShape(14.dp),
          ) {
            Text(
              if (selected.size == 1) "Agendar 1" else "Agendar ${selected.size}",
            )
          }
        }
      }
    }
  }

  editing?.let { index ->
    ActivityEditorDialog(
      heading = "Editar actividad",
      draft = rows[index],
      onDismiss = { editing = null },
      onSave = { saved ->
        rows = rows.toMutableList().also { it[index] = saved }
        editing = null
      },
      today = today,
    )
  }
}

/** S11 — a suggestion may arrive with just a weekday; the label shows the date it will land on. */
private fun draftDay(draft: ActivityDraft, today: LocalDate): LocalDate =
  ActivityDates.parse(draft.date) ?: ActivityDates.nextDateFor(draft.dayOfWeek, today)

