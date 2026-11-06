package com.voicenotes.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** What the model returns for one note (S19). */
data class AiResult(
  val title: String,
  val transcript: String,
  val summary: String,
  val tags: List<String>,
  /** ISO-8601 date and time the note mentions, or null when the model found none (S21). */
  val reminder: String? = null,
)

/** Thrown with a message already phrased for the user. */
class AiException(message: String) : Exception(message)

interface AiClient {
  suspend fun process(
    apiKey: String,
    model: String,
    audio: File?,
    transcript: String,
  ): AiResult

  /** Cheap round trip used by "Probar conexión" in the settings drawer. */
  /** S5 — builds the weekly schedule for the activities section from the existing notes. */
  suspend fun generateSchedule(
    apiKey: String,
    model: String,
    config: com.voicenotes.app.data.WorkConfig,
    sources: List<ScheduleSource>,
    today: Int? = null,
    includeToday: Boolean = true,
  ): List<com.voicenotes.app.data.PlannedActivity>

  suspend fun check(apiKey: String, model: String): Result<Unit>

  /** Text-capable models the key can use, so the model field can list them. */
  suspend fun listModels(apiKey: String): List<String>
}

/**
 * Parses the model answer. Models sometimes wrap JSON in a markdown fence or add prose before it,
 * so the first balanced object is extracted instead of trusting the whole body.
 */
object AiResponseParser {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  fun parse(raw: String): AiResult? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val candidate = text.substring(start, end + 1)
    val obj = runCatching { json.parseToJsonElement(candidate).jsonObject }.getOrNull() ?: return null
    val tags = (obj["tags"] as? JsonArray)
      ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull()?.trim() }
      ?.filter { it.isNotEmpty() }
      ?: emptyList()
    return AiResult(
      title = obj.string("title"),
      transcript = obj.string("transcript"),
      summary = obj.string("summary"),
      tags = tags.take(4),
      reminder = obj.string("reminder")
        .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
    )
  }

  private fun JsonObject.string(key: String): String =
    runCatching { this[key]?.jsonPrimitive?.content?.trim().orEmpty() }.getOrDefault("")
}

/**
 * Reads the `models` list from the REST API and keeps the models that can answer a chat request
 * with the JSON this app asks for. Image, speech, research and embedding models answer
 * `generateContent` too but cannot, so they stay out of the settings picker.
 */
object AiModels {
  private val excluded = listOf(
    "-image", "tts", "transcribe", "computer-use", "robotics", "deep-research",
    "antigravity", "embedding", "nano-banana", "customtools", "lyria", "aqa",
  )

  fun parse(body: String): List<String>? {
    val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    return root["models"]?.jsonArray.orEmpty()
      .mapNotNull { entry ->
        val model = runCatching { entry.jsonObject }.getOrNull() ?: return@mapNotNull null
        val name = model["name"]?.jsonPrimitive?.content?.removePrefix("models/") ?: return@mapNotNull null
        val methods = model["supportedGenerationMethods"]?.jsonArray.orEmpty()
          .mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
        if ("generateContent" !in methods) return@mapNotNull null
        if (!canChat(name)) return@mapNotNull null
        name
      }
      .distinct()
      .sorted()
  }

  private fun canChat(name: String): Boolean {
    val model = name.lowercase()
    if (!model.startsWith("gemini-")) return false
    return excluded.none { model.contains(it) }
  }
}

/**
 * Gemini REST client (S19). Uses the inline audio part so a recorded file can be transcribed
 * without an extra speech backend; SpeechRecognizer only listens to the live microphone.
 */
class GeminiClient(
  private val endpoint: String = "https://generativelanguage.googleapis.com/v1beta",
) : AiClient {

  override suspend fun process(
    apiKey: String,
    model: String,
    audio: File?,
    transcript: String,
  ): AiResult {
    val key = apiKey.trim()
    if (key.isEmpty()) throw AiException("Configurá tu clave de API en Ajustes para usar la IA.")

    val parts = buildJsonArray {
      add(buildJsonObject { put("text", prompt(transcript)) })
      if (audio != null && audio.exists() && audio.length() > 0) {
        val encoded = Base64.getEncoder().encodeToString(audio.readBytes())
        add(
          buildJsonObject {
            putJsonObject("inline_data") {
              put("mime_type", mimeFor(audio.name))
              put("data", encoded)
            }
          },
        )
      }
    }

    val body = buildJsonObject {
      putJsonArray("contents") {
        add(buildJsonObject { put("parts", parts) })
      }
      putJsonObject("generationConfig") {
        put("temperature", 0.2)
        put("maxOutputTokens", 2048)
        put("responseMimeType", "application/json")
      }
    }

    val response = post("$endpoint/models/${model.trim()}:generateContent?key=$key", body.toString())
    val text = extractText(response)
      ?: throw AiException("La IA no devolvió texto. Probá de nuevo.")
    return AiResponseParser.parse(text)
      ?: throw AiException("No pude interpretar la respuesta de la IA.")
  }

  override suspend fun generateSchedule(
    apiKey: String,
    model: String,
    config: com.voicenotes.app.data.WorkConfig,
    sources: List<ScheduleSource>,
    today: Int?,
    includeToday: Boolean,
  ): List<com.voicenotes.app.data.PlannedActivity> {
    val key = apiKey.trim()
    if (key.isEmpty()) throw AiException("Configurá tu clave de API en Ajustes para usar la IA.")
    val body = buildJsonObject {
      putJsonArray("contents") {
        add(
          buildJsonObject {
            put(
              "parts",
              buildJsonArray { add(buildJsonObject { put("text", SchedulePlanner.prompt(config, sources, today, includeToday)) }) },
            )
          },
        )
      }
      putJsonObject("generationConfig") {
        put("temperature", 0.2)
        put("maxOutputTokens", 2048)
        put("responseMimeType", "application/json")
      }
    }
    val response = post("$endpoint/models/${model.trim()}:generateContent?key=$key", body.toString())
    val text = extractText(response)
      ?: throw AiException("La IA no devolvió texto. Probá de nuevo.")
    val plan = SchedulePlanner.parse(text, config, sources, today, includeToday)
    if (plan.isEmpty()) throw AiException("La IA no produjo actividades dentro del horario configurado.")
    return plan
  }

  override suspend fun check(apiKey: String, model: String): Result<Unit> {
    val key = apiKey.trim()
    if (key.isEmpty()) return Result.failure(AiException("Escribí una clave de API."))
    return runCatching {
      val body = buildJsonObject {
        putJsonArray("contents") {
          add(
            buildJsonObject {
              putJsonArray("parts") { add(buildJsonObject { put("text", "Respondé solo: OK") }) }
            },
          )
        }
        putJsonObject("generationConfig") { put("maxOutputTokens", 16) }
      }
      post("$endpoint/models/${model.trim()}:generateContent?key=$key", body.toString())
      Unit
    }.recoverCatching { error ->
      throw if (error is AiException) error else AiException(connectionMessage(error))
    }
  }

  override suspend fun listModels(apiKey: String): List<String> {
    val key = apiKey.trim()
    if (key.isEmpty()) throw AiException("Escribí una clave de API.")
    val response = runCatching { get("$endpoint/models?pageSize=200&key=$key") }
      .getOrElse { throw if (it is AiException) it else AiException(connectionMessage(it)) }
    return AiModels.parse(response) ?: throw AiException("No pude leer la lista de modelos.")
  }

  /** SSL, DNS and socket errors often arrive without a message; name the type instead of "null". */
  private fun connectionMessage(error: Throwable): String {
    val hint = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    return "No pude conectar: $hint"
  }

  private fun prompt(transcript: String): String = buildString {
    val now = java.time.Instant.now().atZone(java.time.ZoneId.systemDefault())
    val day = DAYS[now.dayOfWeek.value - 1]
    appendLine("Sos un asistente que ordena notas de voz en español.")
    appendLine("Devolvé SOLO un objeto JSON, sin explicaciones, con estas claves exactas:")
    appendLine("""{"title": "", "transcript": "", "summary": "", "tags": [], "reminder": null}""")
    appendLine("- title: título corto de hasta 6 palabras. Dejalo vacío si el título ya existe.")
    appendLine("- transcript: la transcripción corregida; si te paso un audio, transcribilo primero.")
    appendLine("  Corregí puntuación, mayúsculas y muletillas, sin cambiar el significado.")
    appendLine("- summary: resumen de 2 o 3 frases.")
    appendLine("- tags: entre 1 y 4 etiquetas cortas, en minúsculas.")
    appendLine("- reminder: fecha y hora del recordatorio en formato ISO-8601 sin zona")
    appendLine("  (por ejemplo 2026-10-27T17:00), o null si la nota no menciona ninguna fecha.")
    appendLine("  Reglas: si hay fecha pero no hora, usá 09:00. Si hay hora pero no fecha, usá hoy")
    appendLine("  si esa hora todavía no pasó y si no mañana.")
    appendLine("  Resolvé expresiones relativas: mañana, el lunes, pasado mañana,")
    appendLine("  la semana que viene, el día 3 del mes que viene.")
    appendLine("  No inventes un recordatorio: si no hay una cita, vencimiento o tarea con momento, va null.")
    appendLine("Mantené el idioma del texto original.")
    appendLine()
    appendLine("Ahora es: ${now.toLocalDateTime().withSecond(0).withNano(0)} ($day).")
    appendLine("Zona horaria: ${java.time.ZoneId.systemDefault().id}.")
    if (transcript.isNotBlank()) {
      appendLine()
      appendLine("Transcripción a corregir:")
      appendLine(transcript.trim())
    }
  }

  private val DAYS = listOf(
    "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo",
  )

  private fun mimeFor(name: String): String = when {
    name.endsWith(".m4a", true) || name.endsWith(".mp4", true) -> "audio/mp4"
    name.endsWith(".wav", true) -> "audio/wav"
    name.endsWith(".mp3", true) -> "audio/mpeg"
    name.endsWith(".ogg", true) -> "audio/ogg"
    else -> "audio/mp4"
  }

  // Blocking sockets must never run on the caller's thread: the UI calls these from
  // viewModelScope, which dispatches on Main.
  private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 20_000
      readTimeout = 30_000
    }
    try {
      val code = connection.responseCode
      val stream = if (code in 200..299) connection.inputStream else connection.errorStream
      val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
      if (code !in 200..299) throw AiException(describe(code, text))
      text
    } finally {
      connection.disconnect()
    }
  }

  private suspend fun post(url: String, payload: String): String = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      connectTimeout = 20_000
      readTimeout = 120_000
      doOutput = true
      setRequestProperty("Content-Type", "application/json; charset=utf-8")
    }
    try {
      connection.outputStream.use { it.write(payload.toByteArray()) }
      val code = connection.responseCode
      val stream = if (code in 200..299) connection.inputStream else connection.errorStream
      val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
      if (code !in 200..299) throw AiException(describe(code, text))
      text
    } finally {
      connection.disconnect()
    }
  }

  private fun describe(code: Int, body: String): String {
    val detail = runCatching {
      Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")
        ?.jsonPrimitive?.content
    }.getOrNull()
    return when (code) {
      400 -> "La clave de API no es válida o el modelo no existe."
      401, 403 -> "La clave de API fue rechazada."
      429 -> "Se agotó la cuota de la IA. Probá más tarde."
      else -> detail?.let { "Error de la IA: $it" } ?: "Error de la IA ($code)."
    }
  }

  private fun extractText(response: String): String? {
    val root = runCatching { Json.parseToJsonElement(response).jsonObject }.getOrNull() ?: return null
    val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
      ?.get("content")?.jsonObject?.get("parts")?.jsonArray
      ?: return null
    return parts.mapNotNull {
      runCatching { it.jsonObject["text"]?.jsonPrimitive?.content }.getOrNull()
    }.joinToString("").ifBlank { null }
  }
}
