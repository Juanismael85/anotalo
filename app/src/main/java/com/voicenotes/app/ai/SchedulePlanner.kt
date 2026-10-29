package com.voicenotes.app.ai

import com.voicenotes.app.data.PlannedActivity
import com.voicenotes.app.data.WorkConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A note reduced to what the schedule generator needs. */
data class ScheduleSource(
  val title: String,
  val summary: String = "",
  val transcript: String = "",
  /** The real note id, so a generated activity links back to its note. */
  val noteId: String? = null,
)

/**
 * S5 — turns the workdays, the working hours and the existing notes into a weekly schedule.
 * The prompt builds the request; [parse] is the safety net: whatever the model answers, the
 * result only holds valid activities, inside the enabled days and the working hours.
 */
object SchedulePlanner {

  private const val MAX_ACTIVITIES = 30

  /** One-based, ISO numbering, so it matches [WorkConfig.dayNumbers]. */
  private val DAYS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

  fun prompt(config: WorkConfig, sources: List<ScheduleSource>): String = buildString {
    appendLine("Sos un asistente que arma un cronograma semanal de trabajo en español.")
    appendLine("Devolvé SOLO un array JSON, un elemento por actividad, sin explicaciones:")
    appendLine("""[{"title": "", "day": 1, "time": "07:30", "noteIndex": null}]""")
    appendLine("- title: hasta 6 palabras, en el idioma de la nota original.")
    appendLine("- day: número de la semana, 1 = lunes .. 7 = domingo. Usá SOLO estos días: ${config.dayNumbers}.")
    appendLine("- time: \"HH:MM\" en 24 horas, dentro del horario laboral ${clock(config.startMinutes)} a ${clock(config.endMinutes)}.")
    appendLine("- noteIndex: índice (desde 0) de la nota que originó la actividad, o null.")
    appendLine("Reglas: no inventes notas ni tareas que no estén en la lista; una nota sin")
    appendLine("tarea accionable se omite; si una nota menciona un plazo, respetalo dentro del")
    appendLine("horario; no repitas actividades idénticas; como máximo $MAX_ACTIVITIES actividades.")
    if (sources.isEmpty()) {
      appendLine("No hay notas: devolvé un array vacío.")
    } else {
      appendLine("Notas disponibles:")
      sources.forEachIndexed { index, source ->
        appendLine("[$index] ${source.title} — ${source.summary.ifBlank { source.transcript.take(160) }}")
      }
    }
  }

  /** Accepts a bare array or an object with an "activities" key; anything invalid is dropped. */
  fun parse(raw: String, config: WorkConfig, sources: List<ScheduleSource>): List<PlannedActivity> = runCatching {
    val root = Json.parseToJsonElement(raw)
    val entries = root.jsonArrayOrNull ?: root.jsonObject["activities"]?.jsonArrayOrNull
      ?: return emptyList()

    entries
      .take(MAX_ACTIVITIES)
      .mapNotNull { entry ->
        val obj = entry.jsonObject
        val title = runCatching { obj["title"]?.jsonPrimitive?.content?.trim() }.getOrNull()
          ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val day = runCatching { obj["day"]?.jsonPrimitive?.contentTrimmedInt() }.getOrNull()?.takeIf { it in 1..7 }
          ?: return@mapNotNull null
        if (day !in config.dayNumbers) return@mapNotNull null
        val time = runCatching { obj["time"]?.jsonPrimitive?.content?.trim() }.getOrNull()
          ?.let { minutesOf(it) } ?: return@mapNotNull null

        val noteIndex = runCatching { obj["noteIndex"]?.jsonPrimitive?.contentTrimmedInt() }.getOrNull()
        PlannedActivity(
          title = title.take(60),
          dayOfWeek = day,
          startMinutes = time,
          noteId = noteIndex?.takeIf { it in sources.indices }?.let { sources[it].noteId },
        )
      }
      .let { activities -> if (config.endMinutes <= config.startMinutes) emptyList() else activities }
      .filter { it.startMinutes in config.startMinutes until config.endMinutes }
      .distinctBy { it.dayOfWeek to it.startMinutes to it.title.lowercase() }
  }.getOrDefault(emptyList())

  private fun minutesOf(text: String): Int? = runCatching {
    val parts = text.replace("h", ":").split(":").filter { it.isNotBlank() }
    val hour = parts[0].trim().toInt()
    val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
    if (hour !in 0..23 || minute !in 0..59) return null
    hour * 60 + minute
  }.getOrNull()

  private fun clock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

  private val kotlinx.serialization.json.JsonElement?.jsonArrayOrNull
    get() = runCatching { this?.jsonArray }.getOrNull()

  private fun kotlinx.serialization.json.JsonElement.contentTrimmedInt(): Int =
    jsonPrimitive.content.toInt()
}
