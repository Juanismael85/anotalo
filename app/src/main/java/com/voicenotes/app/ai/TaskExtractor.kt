package com.voicenotes.app.ai

import com.voicenotes.app.data.ScheduleRules
import com.voicenotes.app.data.WorkConfig

/** What the "Agendar" button proposes for one note, before the user confirms it (S1). */
data class ActivitySuggestion(
  val title: String,
  val dayOfWeek: Int,
  val startMinutes: Int,
  val durationMinutes: Int = ScheduleRules.DEFAULT_DURATION,
)

/**
 * S1 of the agenda block — turns a note into a draft activity: the task it mentions, the day and
 * hour it hints at, and the working hours as the default. The draft is only a proposal: the user
 * edits it in the dialog and nothing is written until they confirm.
 */
object TaskExtractor {

  fun suggest(
    title: String,
    summary: String,
    transcript: String,
    config: WorkConfig?,
    today: Int,
  ): ActivitySuggestion {
    val text = listOf(title, summary, transcript).joinToString(" ")
    val taskTitle = TaskText.tasks(title, summary, transcript).firstOrNull()
      ?: TaskText.short(title.ifBlank { "Actividad" })

    val day = TaskText.dayHint(text, today) ?: ScheduleRules.nextWorkday(config, today)
    val start = TaskText.timeHint(text) ?: config?.startMinutes ?: 9 * 60
    val duration = if (config != null && config.endMinutes - start in 1..59) {
      config.endMinutes - start
    } else {
      ScheduleRules.DEFAULT_DURATION
    }

    return ActivitySuggestion(
      title = taskTitle,
      dayOfWeek = day.coerceIn(1, 7),
      startMinutes = start.coerceIn(0, 1439),
      durationMinutes = duration.coerceIn(ScheduleRules.MIN_DURATION, ScheduleRules.MAX_DURATION),
    )
  }
}
