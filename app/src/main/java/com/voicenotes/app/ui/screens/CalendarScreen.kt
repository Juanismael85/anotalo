package com.voicenotes.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicenotes.app.data.Activity
import com.voicenotes.app.data.CalendarAgenda
import com.voicenotes.app.reminder.CalendarGrid
import com.voicenotes.app.reminder.ReminderFormat
import com.voicenotes.app.reminder.ReminderTime
import com.voicenotes.app.reminder.Reminders
import com.voicenotes.app.ui.NotesViewModel
import com.voicenotes.app.ui.components.ActivityDraft
import com.voicenotes.app.ui.components.ActivityEditorDialog
import com.voicenotes.app.ui.components.ScreenGradient
import com.voicenotes.app.ui.components.clockText
import com.voicenotes.app.data.ActivityDates
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * S24/S10 — month grid with a dot on every day that has a reminder or a scheduled activity. Picking
 * a day lists its activities and its reminders below: an activity opens the shared editor, so the
 * change shows up in the schedule at once, and a reminder opens its note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
  viewModel: NotesViewModel,
  onOpenNote: (String) -> Unit,
  bottomBar: @Composable () -> Unit,
) {
  val notes by viewModel.allNotes.collectAsStateWithLifecycle()
  val activities by viewModel.activities.collectAsStateWithLifecycle()
  val zone: ZoneId = remember { ZoneId.systemDefault() }
  val today = remember { LocalDate.now(zone) }
  var month by remember { mutableStateOf(YearMonth.from(today)) }
  var selected by remember { mutableStateOf(today) }
  val cells = remember(month) { CalendarGrid.month(month.year, month.monthValue) }
  val dayNotes = Reminders.onDay(notes, selected)
  val dayActivities = CalendarAgenda.onDay(activities, selected)
  val withReminder = remember(notes) { notes.filter { it.reminderAt != null } }
  var editing by remember { mutableStateOf<ActivityDraft?>(null) }

  Scaffold(
    containerColor = Color.Transparent,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Calendario", style = MaterialTheme.typography.headlineMedium)
            Text(
              "${activities.size} actividad${if (activities.size == 1) "" else "es"} · " +
                "${withReminder.size} con recordatorio",
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
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { month = month.minusMonths(1) }) {
          Icon(Icons.Filled.ChevronLeft, contentDescription = "Mes anterior")
        }
        Text(
          ReminderFormat.monthTitle(month.year, month.monthValue),
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { month = month.plusMonths(1) }) {
          Icon(Icons.Filled.ChevronRight, contentDescription = "Mes siguiente")
        }
      }
      TextButton(onClick = {
        month = YearMonth.from(today)
        selected = today
      }) {
        Text("Hoy")
      }

      Row(Modifier.fillMaxWidth()) {
        weekdays.forEach { name ->
          Text(
            name,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Spacer(Modifier.height(4.dp))

      cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
          week.forEach { cell ->
            val isSelected = cell.date == selected
            val isToday = cell.date == today
            val marked = withReminder.any { ReminderTime.localDay(it.reminderAt!!, zone) == cell.date } ||
              CalendarAgenda.hasOn(activities, cell.date)
            Box(
              modifier = Modifier
                .weight(1f)
                .padding(2.dp)
                .height(44.dp),
              contentAlignment = Alignment.Center,
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .background(
                    color = when {
                      isSelected -> MaterialTheme.colorScheme.primary
                      isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                      else -> Color.Transparent
                    },
                    shape = CircleShape,
                  )
                  .clickable {
                    selected = cell.date
                    if (cell.date.monthValue != month.monthValue) month = YearMonth.from(cell.date)
                  },
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  cell.date.dayOfMonth.toString(),
                  style = MaterialTheme.typography.bodyMedium,
                  color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    cell.inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                  },
                )
                if (marked) {
                  Box(
                    modifier = Modifier
                      .align(Alignment.BottomCenter)
                      .padding(bottom = 3.dp)
                      .size(4.dp)
                      .background(
                        color = if (isSelected) {
                          MaterialTheme.colorScheme.onPrimary
                        } else {
                          MaterialTheme.colorScheme.primary
                        },
                        shape = CircleShape,
                      ),
                  )
                }
              }
            }
          }
        }
      }

      Spacer(Modifier.height(16.dp))
      Text(
        ReminderFormat.dayLabel(selected),
        style = MaterialTheme.typography.titleSmall,
      )
      Spacer(Modifier.height(8.dp))

      dayActivities.forEach { activity ->
        Card(
          onClick = {
            editing = ActivityDraft(
              id = activity.id,
              title = activity.title,
              dayOfWeek = activity.dayOfWeek,
              startMinutes = activity.startMinutes,
              durationMinutes = activity.durationMinutes,
              repeatDays = activity.repeatDays,
              repeat = activity.repeat,
              repeatUntil = activity.repeatUntil,
              remind = activity.remind,
              noteId = activity.noteId,
            )
          },
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
          ),
        ) {
          Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              clockText(activity.startMinutes),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.padding(start = 12.dp))
            Column(Modifier.weight(1f)) {
              Text(
                activity.title,
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                activityMeta(activity),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Checkbox(
              checked = activity.done,
              onCheckedChange = { done -> viewModel.toggleActivityDone(activity.id, done) },
            )
          }
        }
      }

      if (dayActivities.isEmpty() && dayNotes.isEmpty()) {
        Text(
          "Sin actividades ni recordatorios este día.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        dayNotes.forEach { note ->
          val at = note.reminderAt ?: return@forEach
          Card(
            onClick = { onOpenNote(note.id) },
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                ReminderFormat.time(at, zone),
                style = MaterialTheme.typography.labelLarge,
                color = if (note.reminderDone) {
                  MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                  MaterialTheme.colorScheme.primary
                },
              )
              Spacer(Modifier.padding(start = 12.dp))
              Column(Modifier.weight(1f)) {
                Text(viewModel.displayTitle(note), style = MaterialTheme.typography.titleSmall)
                if (note.reminderDone) {
                  Text(
                    "Cumplido",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }
      Spacer(Modifier.height(96.dp))
    }
  }

  editing?.let { draft ->
    ActivityEditorDialog(
      heading = "Editar actividad",
      draft = draft,
      onDismiss = { editing = null },
      onSave = { saved ->
        viewModel.saveActivity(saved)
        editing = null
      },
      today = today,
    )
  }
}

/** "60 min · repite miércoles" — the line under the activity in the day list. */
private fun activityMeta(activity: Activity): String =
  buildString {
    append("${activity.durationMinutes} min")
    if (activity.repeat != Activity.REPEAT_NONE) {
      append(" · ")
      append(ActivityDates.repeatLabel(activity).lowercase())
      ActivityDates.untilOf(activity)?.let { until ->
        append(" hasta el ")
        append(until.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))))
      }
    }
  }

private val weekdays = listOf("L", "M", "M", "J", "V", "S", "D")
