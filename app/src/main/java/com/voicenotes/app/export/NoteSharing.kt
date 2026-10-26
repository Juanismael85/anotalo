package com.voicenotes.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.voicenotes.app.data.Note
import java.io.File

/** S15 — Android intents behind "Compartir". */
object NoteSharing {

  fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, subject)
      putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir nota"))
  }

  fun shareFile(context: Context, file: File, mime: String, subject: String) {
    if (!file.exists()) return
    val uri: Uri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = mime
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, subject)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir nota"))
  }

  /** Writes the PDF into cacheDir/exports so the FileProvider can expose it. */
  fun pdfFor(
    context: Context,
    note: Note,
    folderName: String?,
    tagNames: List<String>,
  ): File {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, NoteExporter.fileName(note, "pdf"))
    PdfExport.write(file, note, folderName, tagNames)
    return file
  }
}
