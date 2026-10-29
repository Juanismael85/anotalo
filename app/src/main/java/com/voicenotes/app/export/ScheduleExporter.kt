package com.voicenotes.app.export

import com.voicenotes.app.data.Activity
import com.voicenotes.app.data.Checklist
import com.voicenotes.app.data.WorkConfig

/**
 * S6/S7 — shareable text for the weekly schedule and the checklists. Pure so the JVM tests can
 * pin the exact shape; the Android intents stay in [NoteSharing].
 */
object ScheduleExporter {

  private val dayNames =
    listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

  fun plainText(activities: List<Activity>, config: WorkConfig?): String =
    buildString {
      appendLine("Cronograma semanal")
      workHours(config)?.let { appendLine(it) }
      grouped(activities).forEach { (day, list) ->
        appendLine()
        appendLine(dayNames[day - 1])
        list.forEach { activity ->
          appendLine("${mark(activity.done)}${clock(activity.startMinutes)}  ${activity.title}")
        }
      }
    }.trim() + "\n"

  fun markdown(activities: List<Activity>, config: WorkConfig?): String =
    buildString {
      appendLine("# Cronograma semanal")
      appendLine()
      workHours(config)?.let { appendLine("_${it}_") }
      grouped(activities).forEach { (day, list) ->
        appendLine()
        appendLine("## ${dayNames[day - 1]}")
        list.forEach { activity ->
          appendLine("- ${mark(activity.done)}${clock(activity.startMinutes)} ${activity.title}")
        }
      }
    }.trim() + "\n"

  fun fileName(extension: String): String = "cronograma-semanal.$extension"

  private fun mark(done: Boolean): String = if (done) "[x] " else "[ ] "

  private fun workHours(config: WorkConfig?): String? {
    if (config == null || config.dayNumbers.isEmpty()) return null
    val days = config.dayNumbers.sorted().joinToString(", ") { dayNames[it - 1] }
    return "Horario de trabajo: $days de ${clock(config.startMinutes)} a ${clock(config.endMinutes)}"
  }

  private fun grouped(activities: List<Activity>): Map<Int, List<Activity>> =
    activities
      .filter { it.title.isNotBlank() }
      .sortedWith(compareBy({ it.dayOfWeek }, { it.startMinutes }, { it.title }))
      .groupBy { it.dayOfWeek.coerceIn(1, 7) }
      .toSortedMap()

  private fun clock(minutes: Int): String =
    "%02d:%02d".format(minutes / 60 % 24, minutes % 60)
}

object ChecklistExporter {

  fun plainText(checklist: Checklist): String =
    buildString {
      appendLine(checklist.title)
      appendLine(progress(checklist))
      if (checklist.items.isNotEmpty()) appendLine()
      checklist.items.forEach { item ->
        appendLine("${mark(item.done)}${item.text}")
      }
    }.trim() + "\n"

  fun markdown(checklist: Checklist): String =
    buildString {
      appendLine("# ${checklist.title}")
      appendLine()
      appendLine("_${progress(checklist)}_")
      if (checklist.items.isNotEmpty()) appendLine()
      checklist.items.forEach { item ->
        appendLine("- ${mark(item.done)}${item.text}")
      }
    }.trim() + "\n"

  fun fileName(checklist: Checklist, extension: String): String {
    val base = checklist.title
      .lowercase()
      .replace(Regex("[^a-z0-9áéíóúñü ]"), "")
      .trim()
      .replace(Regex("\\s+"), "-")
      .ifBlank { "checklist" }
    return "$base.$extension"
  }

  private fun mark(done: Boolean): String = if (done) "[x] " else "[ ] "

  private fun progress(checklist: Checklist): String =
    "${checklist.items.count { it.done }} de ${checklist.items.size} listos"
}
