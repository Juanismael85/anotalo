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
 * S1 of the agenda block — turns a note into draft activities: the tasks it mentions, the day and
 * hour each one hints at, and the working hours as the default. A note is a container of tasks, so
 * it usually produces several drafts; the user chooses which ones go to the schedule and nothing is
 * written until they confirm.
 */
object TaskExtractor {

  private const val DEFAULT_START = 9 * 60

  /**
   * One suggestion per task found in the note, in the order they were dictated. Tasks inherit the
   * day and hour hinted in the rest of the note, and never overlap: each one starts when the
   * previous one ends, and a task that would cross the closing time moves to the next workday.
   */
  fun suggestAll(
    title: String,
    summary: String,
    transcript: String,
    config: WorkConfig?,
    today: Int,
  ): List<ActivitySuggestion> {
    val sentences = TaskText.taskSentences(title, summary, transcript)
    if (sentences.isEmpty()) return emptyList()

    val noteText = listOf(title, summary, transcript).joinToString(" ")
    var day = (TaskText.dayHint(noteText, today) ?: ScheduleRules.nextWorkday(config, today)).coerceIn(1, 7)
    var cursor = TaskText.timeHint(noteText) ?: config?.startMinutes ?: DEFAULT_START
    if (config != null && cursor !in config.startMinutes until config.endMinutes) {
      cursor = config.startMinutes
    }

    val suggestions = mutableListOf<ActivitySuggestion>()
    sentences.forEach { sentence ->
      val taskTitle = TaskText.short(sentence)
      if (taskTitle.isBlank()) return@forEach

      val hintedDay = TaskText.dayHint(sentence, today)
      if (hintedDay != null && hintedDay != day) {
        day = hintedDay
        cursor = config?.startMinutes ?: DEFAULT_START
      }

      var start = TaskText.timeHint(sentence) ?: cursor
      var duration = durationFor(start, config)
      if (config != null && (start < config.startMinutes || start + duration > config.endMinutes)) {
        day = ScheduleRules.nextWorkday(config, day)
        start = config.startMinutes
        duration = durationFor(start, config)
      }

      suggestions += ActivitySuggestion(
        title = taskTitle,
        dayOfWeek = day.coerceIn(1, 7),
        startMinutes = start.coerceIn(0, 1439),
        durationMinutes = duration.coerceIn(ScheduleRules.MIN_DURATION, ScheduleRules.MAX_DURATION),
      )
      cursor = start + duration
    }
    return suggestions.distinctBy { it.title.lowercase() }
  }

  /** The first task of the note; kept for callers that only need one draft. */
  fun suggest(
    title: String,
    summary: String,
    transcript: String,
    config: WorkConfig?,
    today: Int,
  ): ActivitySuggestion =
    suggestAll(title, summary, transcript, config, today).firstOrNull()
      ?: fallback(title, summary, transcript, config, today)

  /** No task found: the note title still deserves a slot the user can edit before saving. */
  private fun fallback(
    title: String,
    summary: String,
    transcript: String,
    config: WorkConfig?,
    today: Int,
  ): ActivitySuggestion {
    val text = listOf(title, summary, transcript).joinToString(" ")
    val day = (TaskText.dayHint(text, today) ?: ScheduleRules.nextWorkday(config, today)).coerceIn(1, 7)
    val start = (TaskText.timeHint(text) ?: config?.startMinutes ?: DEFAULT_START).coerceIn(0, 1439)
    return ActivitySuggestion(
      title = TaskText.short(title.ifBlank { "Actividad" }),
      dayOfWeek = day,
      startMinutes = start,
      durationMinutes = durationFor(start, config),
    )
  }

  /** Long tasks take the time left before closing when that is under an hour; otherwise an hour. */
  private fun durationFor(start: Int, config: WorkConfig?): Int =
    if (config != null && config.endMinutes - start in 1..59) {
      config.endMinutes - start
    } else {
      ScheduleRules.DEFAULT_DURATION
    }
}
