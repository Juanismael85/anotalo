package com.voicenotes.app.ai

/**
 * Reads tasks, days and hours out of what the user dictated (S1/S3 of the agenda block). Pure
 * Kotlin on purpose: the rules that decide "this note holds a task, for tomorrow at eight" must be
 * pinned by tests, not by an API call.
 */
object TaskText {

  /**
   * Verbs that start an actionable sentence. Only the stem is compared, so "limpiamos" and
   * "limpiar" both count.
   */
  private val ACTION_VERBS = listOf(
    "limpiar", "revisar", "comprar", "llamar", "enviar", "preparar", "instalar", "reparar",
    "coordinar", "organizar", "entregar", "pagar", "cargar", "actualizar", "agendar", "confirmar",
    "firmar", "recoger", "buscar", "solicitar", "hacer", "realizar", "colocar", "cambiar",
    "verificar", "programar", "reunir", "presentar", "armar", "sacar", "llevar", "chequear",
  )

  private val MARKERS = listOf(
    "hay que", "tenemos que", "debemos", "tengo que", "necesito", "vamos a", "debo",
    "se debe", "falta", "pendiente",
  )

  /** Phrases that only set the scene; the task starts after them. */
  private val LEADS = listOf(
    "pasado mañana", "para mañana", "mañana", "hoy", "para el", "para la",
    "hay que", "tenemos que", "debemos", "tengo que", "necesito", "vamos a", "debo", "se debe",
  )

  private val WEEKDAYS = listOf(
    "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo",
  )

  fun sentences(text: String): List<String> =
    text.split(Regex("[.!?\\n;]+")).map { it.trim() }.filter { it.isNotEmpty() }

  /** True when the sentence asks for something to be done. */
  fun actionable(sentence: String): Boolean {
    val lower = sentence.lowercase().trim()
    if (lower.isEmpty()) return false
    if (MARKERS.any { lower.contains(it) }) return true
    val first = lower.split(Regex("[\\s,]+")).firstOrNull()?.trim(':') ?: return false
    return ACTION_VERBS.any { verb -> first == verb || first.startsWith(stem(verb)) }
  }

  /** "a las 8", "8:30", "18 hs": a leading time only sets the scene too. */
  private val LEAD_TIME = Regex("^(a\\s+las?\\s+\\d{1,2}(?::\\d{2})?|\\d{1,2}:\\d{2}|\\d{1,2}\\s*hs?)\\b")

  /** The task without the scene-setting words, cut to a card-sized title. */
  fun short(sentence: String, max: Int = 60): String {
    var text = sentence.trim().replace(Regex("\\s+"), " ")
    var stripped = true
    while (stripped) {
      stripped = false
      LEADS.forEach { lead ->
        if (text.lowercase().startsWith(lead)) {
          text = text.substring(lead.length).trimStart(' ', ',', ':', '-')
          stripped = true
        }
      }
      val time = LEAD_TIME.find(text)
      if (time != null) {
        text = text.substring(time.value.length).trimStart(' ', ',', ':', '-')
        stripped = true
      }
    }
    text = text.trimEnd('.', ',', ';', ':')
    val clipped = if (text.length <= max) {
      text
    } else {
      val cut = text.take(max)
      (cut.substringBeforeLast(' ', cut).ifBlank { cut }).trimEnd()
    }
    return clipped.replaceFirstChar { it.uppercase() }
  }

  /**
   * Every actionable sentence of a note, without duplicates, as ready-to-use titles. The title is
   * only a candidate when the body says nothing actionable: "Limpieza" labels a note, while
   * "hay que limpiar los quirófanos" is the task.
   */
  fun tasks(title: String, summary: String, transcript: String): List<String> {
    val fromBody = (sentences(summary) + sentences(transcript)).asSequence()
      .filter { actionable(it) }
      .map { short(it) }
      .filter { it.isNotBlank() }
      .distinctBy { it.lowercase() }
      .take(6)
      .toList()
    if (fromBody.isNotEmpty()) return fromBody
    return listOf(title).filter { actionable(it) }.map { short(it) }.filter { it.isNotBlank() }
  }

  /**
   * ISO day hinted by the text: "hoy", "mañana", "pasado mañana" or a weekday name. "de la
   * mañana" is a part of the day, not tomorrow.
   */
  fun dayHint(text: String, today: Int): Int? {
    val lower = text.lowercase()
    if (lower.contains("pasado mañana")) return ((today + 1) % 7) + 1
    if (Regex("\\bhoy\\b").containsMatchIn(lower)) return today
    val morning = Regex("(de|en|por|esta|temprano en)\\s+la?\\s*mañana").containsMatchIn(lower)
    if (Regex("\\bmañana\\b").containsMatchIn(lower) && !morning) return (today % 7) + 1
    WEEKDAYS.forEachIndexed { index, name ->
      if (Regex("\\b$name\\b").containsMatchIn(lower)) return index + 1
    }
    return null
  }

  /**
   * Minutes from midnight hinted by the text. Only explicit hints count ("8:30", "a las 8",
   * "8 hs"): a bare number is usually a date or a count, not a time.
   */
  fun timeHint(text: String): Int? {
    val patterns = listOf(
      Regex("(\\d{1,2}):(\\d{2})"),
      Regex("a\\s+las?\\s+(\\d{1,2})(?!\\d)"),
      Regex("(\\d{1,2})\\s*(?:hs?|horas)\\b"),
    )
    patterns.forEach { pattern ->
      val match = pattern.find(text) ?: return@forEach
      val hour = match.groupValues[1].toIntOrNull() ?: return@forEach
      val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
      if (hour in 0..23 && minute in 0..59) return hour * 60 + minute
    }
    return null
  }

  private fun stem(verb: String): String = if (verb.length > 3) verb.dropLast(2) else verb
}
