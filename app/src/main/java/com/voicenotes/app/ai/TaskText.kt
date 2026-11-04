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

  /**
   * Nouns that name a task on their own: "Reunión con el proveedor el martes" asks for something
   * even without a verb. Without this, a dictation that starts with the task name would be dropped.
   */
  private val TASK_NOUNS = listOf(
    "reunión", "reunion", "entrega", "pago", "llamada", "compra", "informe", "visita",
    "mantenimiento", "limpieza", "cita", "auditoría", "auditoria", "capacitación", "capacitacion",
    "pedido", "traslado", "control", "inspección", "inspeccion", "inventario", "stock",
    "firma", "presupuesto", "turno", "entrevista", "presentación", "presentacion",
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
    text.split(Regex("[.!?\\n;]+"))
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .flatMap { splitOnConjunction(it) }

  /** "y" joins two different tasks when what follows can stand on its own. */
  private val CONJUNCTION = Regex("\\s+(?:y|e)\\s+", RegexOption.IGNORE_CASE)

  private val CONNECTORS = listOf(
    "también", "tambien", "después", "despues", "además", "ademas", "luego", "entonces",
  )

  private fun splitOnConjunction(sentence: String): List<String> {
    val parts = sentence.split(CONJUNCTION)
    if (parts.size < 2) return listOf(sentence)
    val out = mutableListOf<String>()
    var current = parts.first()
    parts.drop(1).forEach { next ->
      if (actionable(withoutConnector(next))) {
        out += current.trim()
        current = next
      } else {
        current = "$current y $next"
      }
    }
    out += current.trim()
    return out.filter { it.isNotBlank() }
  }

  private fun withoutConnector(text: String): String {
    var clean = text.trim()
    var changed = true
    while (changed) {
      changed = false
      CONNECTORS.forEach { connector ->
        if (clean.lowercase().startsWith(connector)) {
          clean = clean.substring(connector.length).trimStart(' ', ',')
          changed = true
        }
      }
    }
    return clean
  }

  /** True when the sentence asks for something to be done. */
  fun actionable(sentence: String): Boolean {
    val lower = sentence.lowercase().trim()
    if (lower.isEmpty()) return false
    if (MARKERS.any { lower.contains(it) }) return true
    val first = lower.split(Regex("[\\s,]+")).firstOrNull()?.trim(':') ?: return false
    if (ACTION_VERBS.any { verb -> first == verb || first.startsWith(stem(verb)) }) return true
    return mentionsTaskNoun(lower)
  }

  /**
   * The noun has to open the sentence: "Reunión con el proveedor" is a task, while "estuvimos
   * hablando del presupuesto" mentions one but does not ask for anything.
   */
  private fun mentionsTaskNoun(lower: String): Boolean {
    val start = lower.replaceFirst(Regex("^(el|la|los|las|un|una|unos|unas)\\s+"), "")
    return TASK_NOUNS.any { noun ->
      start.startsWith(noun) && (start.length == noun.length || !start[noun.length].isLetter())
    }
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
   * Every actionable sentence of a note, quoted as the user said it (before shortening). The title
   * is only a candidate when the body says nothing actionable: "Limpieza" labels a note, while
   * "hay que limpiar los quirófanos" is the task.
   */
  fun taskSentences(title: String, summary: String, transcript: String): List<String> {
    val fromBody = (sentences(summary) + sentences(transcript))
      .filter { actionable(it) }
      .distinctBy { it.lowercase() }
    if (fromBody.isNotEmpty()) return fromBody
    return listOf(title).filter { actionable(it) }
  }

  /** The same tasks, cut to card-sized titles. */
  fun tasks(title: String, summary: String, transcript: String): List<String> =
    taskSentences(title, summary, transcript)
      .map { short(it) }
      .filter { it.isNotBlank() }
      .distinctBy { it.lowercase() }
      .take(6)

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
