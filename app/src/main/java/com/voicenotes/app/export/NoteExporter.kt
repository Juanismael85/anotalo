package com.voicenotes.app.export

import com.voicenotes.app.data.Note
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * S15 — builds the shareable text of a note. Pure so it can be tested on the JVM;
 * the PDF and the Android intents live in [PdfExport] and the UI layer.
 */
object NoteExporter {

  private val dateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

  fun plainText(note: Note, folderName: String?, tagNames: List<String>): String =
    buildString {
      appendLine(titleOf(note))
      appendLine(dateOf(note))
      if (!folderName.isNullOrBlank()) appendLine("Carpeta: $folderName")
      if (tagNames.isNotEmpty()) appendLine("Etiquetas: ${tagNames.joinToString(", ")}")
      section("", note.body)
      section("Transcripción", note.transcript)
      section("Resumen", note.summary)
    }.trim() + "\n"

  fun markdown(note: Note, folderName: String?, tagNames: List<String>): String =
    buildString {
      appendLine("# ${titleOf(note)}")
      appendLine()
      appendLine("_${dateOf(note)}_")
      if (!folderName.isNullOrBlank()) appendLine("- **Carpeta:** $folderName")
      if (tagNames.isNotEmpty()) appendLine("- **Etiquetas:** ${tagNames.joinToString(", ")}")
      markdownSection("Nota", note.body)
      markdownSection("Transcripción", note.transcript)
      markdownSection("Resumen", note.summary)
    }.trim() + "\n"

  fun fileName(note: Note, extension: String): String {
    val base = titleOf(note)
      .lowercase()
      .replace(Regex("[^a-z0-9áéíóúñü ]"), "")
      .trim()
      .replace(Regex("\\s+"), "-")
      .take(48)
      .ifBlank { "nota" }
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
      .withZone(ZoneId.systemDefault())
      .format(Instant.ofEpochMilli(if (note.createdAt > 0) note.createdAt else System.currentTimeMillis()))
    return "$base-$stamp.$extension"
  }

  private fun titleOf(note: Note): String =
    note.title.trim().ifBlank { "Nota sin título" }

  private fun dateOf(note: Note): String =
    dateFormat.format(Instant.ofEpochMilli(if (note.createdAt > 0) note.createdAt else System.currentTimeMillis()))

  private fun StringBuilder.section(heading: String, text: String) {
    val clean = text.trim()
    if (clean.isEmpty()) return
    appendLine()
    if (heading.isNotEmpty()) appendLine("$heading:")
    appendLine(clean)
  }

  private fun StringBuilder.markdownSection(heading: String, text: String) {
    val clean = text.trim()
    if (clean.isEmpty()) return
    appendLine()
    appendLine("## $heading")
    appendLine()
    appendLine(clean)
  }
}
