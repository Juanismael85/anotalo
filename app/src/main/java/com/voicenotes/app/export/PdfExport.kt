package com.voicenotes.app.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.voicenotes.app.data.Note
import java.io.File

/** S15 — one page per chunk of text, with a simple word wrap; no external library. */
object PdfExport {

  private const val PAGE_WIDTH = 595
  private const val PAGE_HEIGHT = 842
  private const val MARGIN = 48f
  private const val LINE_HEIGHT = 18f

  fun write(
    file: File,
    note: Note,
    folderName: String?,
    tagNames: List<String>,
  ) {
    val document = PdfDocument()
    val titlePaint = Paint().apply {
      typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
      textSize = 22f
      isAntiAlias = true
    }
    val metaPaint = Paint().apply {
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
      textSize = 11f
      isAntiAlias = true
    }
    val bodyPaint = Paint().apply {
      typeface = Typeface.DEFAULT
      textSize = 12f
      isAntiAlias = true
    }

    val lines = mutableListOf<Pair<String, Paint>>()
    lines += (note.title.trim().ifBlank { "Nota sin título" }) to titlePaint
    lines += "" to metaPaint
    lines += (buildString {
      append(folderName?.let { "Carpeta: $it · " }.orEmpty())
      append(if (tagNames.isEmpty()) "Sin etiquetas" else tagNames.joinToString(", "))
    }) to metaPaint
    note.body.trim().takeIf { it.isNotEmpty() }?.let {
      lines += "" to bodyPaint
      wrapped(it, bodyPaint).forEach { line -> lines += line to bodyPaint }
    }
    note.transcript.trim().takeIf { it.isNotEmpty() }?.let {
      lines += "" to bodyPaint
      lines += "Transcripción" to titlePaint
      wrapped(it, bodyPaint).forEach { line -> lines += line to bodyPaint }
    }
    note.summary.trim().takeIf { it.isNotEmpty() }?.let {
      lines += "" to bodyPaint
      lines += "Resumen" to titlePaint
      wrapped(it, bodyPaint).forEach { line -> lines += line to bodyPaint }
    }

    val usable = PAGE_HEIGHT - MARGIN * 2
    var index = 0
    var pageNumber = 1
    while (index < lines.size) {
      val page = document.startPage(
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
      )
      val canvas = page.canvas
      var y = MARGIN + 12f
      while (index < lines.size && (y - MARGIN) < usable) {
        val (text, paint) = lines[index]
        if (text.isNotEmpty()) canvas.drawText(text, MARGIN, y, paint)
        y += if (paint === titlePaint) LINE_HEIGHT + 6f else LINE_HEIGHT
        index++
      }
      document.finishPage(page)
      pageNumber++
    }

    file.parentFile?.mkdirs()
    file.outputStream().use { document.writeTo(it) }
    document.close()
  }

  private fun wrapped(text: String, paint: Paint): List<String> {
    val maxWidth = PAGE_WIDTH - MARGIN * 2
    val result = mutableListOf<String>()
    text.split('\n').forEach { paragraph ->
      var remaining = paragraph.trim()
      while (remaining.isNotEmpty()) {
        val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
        result += remaining.substring(0, count).trimEnd()
        remaining = remaining.substring(count).trimStart()
      }
    }
    return result
  }
}
