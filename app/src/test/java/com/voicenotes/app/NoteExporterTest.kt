package com.voicenotes.app

import com.voicenotes.app.data.Note
import com.voicenotes.app.export.NoteExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteExporterTest {

  private val note = Note(
    id = "note-1",
    title = "Llamada con Ana",
    body = "Cuerpo de la nota",
    transcript = "Texto transcripto",
    summary = "Resumen breve",
    folderId = "folder-1",
    createdAt = 1_700_000_000_000L,
  )

  @Test
  fun `plain text includes metadata, body, transcript and summary`() {
    val text = NoteExporter.plainText(note, folderName = "Trabajo", tagNames = listOf("urgente", "ana"))

    assertTrue(text.contains("Llamada con Ana"))
    assertTrue(text.contains("Carpeta: Trabajo"))
    assertTrue(text.contains("Etiquetas: urgente, ana"))
    assertTrue(text.contains("Cuerpo de la nota"))
    assertTrue(text.contains("Transcripción:"))
    assertTrue(text.contains("Texto transcripto"))
    assertTrue(text.contains("Resumen:"))
    assertTrue(text.contains("Resumen breve"))
  }

  @Test
  fun `plain text skips empty sections instead of leaving blank headings`() {
    val onlyTitle = Note(id = "n", title = "Solo título")

    val text = NoteExporter.plainText(onlyTitle, folderName = null, tagNames = emptyList())

    assertFalse(text.contains("Carpeta:"))
    assertFalse(text.contains("Etiquetas:"))
    assertFalse(text.contains("Transcripción:"))
    assertFalse(text.contains("Resumen:"))
  }

  @Test
  fun `markdown uses a heading per section and a fallback title`() {
    val markdown = NoteExporter.markdown(note.copy(title = "  "), folderName = null, tagNames = emptyList())

    assertTrue(markdown.startsWith("# Nota sin título"))
    assertTrue(markdown.contains("## Transcripción"))
    assertTrue(markdown.contains("## Resumen"))
    assertTrue(markdown.contains("## Nota"))
  }

  @Test
  fun `file name is slugged from the title and keeps the extension`() {
    val name = NoteExporter.fileName(note, "md")

    assertTrue(name.startsWith("llamada-con-ana-"))
    assertTrue(name.endsWith(".md"))
    assertEquals(0, name.count { it == ' ' })
  }
}
