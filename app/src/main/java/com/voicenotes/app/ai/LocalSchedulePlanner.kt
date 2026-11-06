package com.voicenotes.app.ai

import com.voicenotes.app.data.PlannedActivity
import com.voicenotes.app.data.ScheduleRules
import com.voicenotes.app.data.WorkConfig

/**
 * S3 of the agenda block — the schedule that always works. When the AI cannot answer (quota, no
 * key, no network) this spreads the tasks found in the notes over the configured days and hours
 * with plain rules: same input, same schedule, no request. It is the fallback, never the default.
 *
 * S8 — the week starts at [today] when it is a workday and there is still time left, so a schedule
 * built on Friday morning fills Friday first instead of jumping to Monday.
 */
object LocalSchedulePlanner {

  private const val MAX_ACTIVITIES = 30
  private const val BLOCK_MINUTES = 60

  fun plan(
    config: WorkConfig,
    sources: List<ScheduleSource>,
    today: Int = config.dayNumbers.firstOrNull() ?: 1,
    nowMinutes: Int = 0,
    includeToday: Boolean = true,
  ): List<PlannedActivity> {
    val days = orderedDays(config, today, nowMinutes, includeToday)
    val span = config.endMinutes - config.startMinutes
    if (days.isEmpty() || span < 30) return emptyList()

    val perDay = (span / BLOCK_MINUTES).coerceAtLeast(1)
    val tasks = sources
      .flatMap { source ->
        TaskText.tasks(source.title, source.summary, source.transcript).map { task -> source to task }
      }
      .distinctBy { it.second.lowercase() }
      .take(MAX_ACTIVITIES)

    // One block per slot: a day holds perDay blocks and anything past the last day does not fit.
    val capacity = perDay * days.size
    val planned = mutableListOf<PlannedActivity>()
    tasks.take(capacity).forEachIndexed { index, (source, task) ->
      val day = days[(index / perDay) % days.size]
      val start = config.startMinutes + (index % perDay) * BLOCK_MINUTES
      if (start + BLOCK_MINUTES > config.endMinutes) return@forEachIndexed
      planned += PlannedActivity(title = task, dayOfWeek = day, startMinutes = start, noteId = source.noteId)
    }

    return planned.distinctBy { it.dayOfWeek to it.startMinutes to it.title.lowercase() }
  }

  /** The configured days in the order the schedule fills them: starting today, then the week. */
  private fun orderedDays(
    config: WorkConfig,
    today: Int,
    nowMinutes: Int,
    includeToday: Boolean,
  ): List<Int> {
    val ordered = config.dayNumbers.filter { it in 1..7 }.distinct().sorted()
    if (ordered.isEmpty()) return emptyList()
    val start = ScheduleRules.startDay(config, today, nowMinutes, includeToday)
    return ordered.filter { it >= start } + ordered.filter { it < start }
  }
}
