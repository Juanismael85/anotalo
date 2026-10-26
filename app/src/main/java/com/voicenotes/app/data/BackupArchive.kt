package com.voicenotes.app.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * S16 — a backup is a ZIP with `notes.json` plus the audio files under `audio/`.
 * Extraction goes to a staging directory so an invalid backup never touches the library.
 */
object BackupArchive {

  const val NOTES_ENTRY = "notes.json"
  private const val AUDIO_PREFIX = "audio/"
  private const val MAX_ENTRY_BYTES = 512L * 1024 * 1024

  data class Extracted(val notesJson: String, val audioFiles: List<File>)

  fun write(out: OutputStream, notesJson: String, audioFiles: List<File>) {
    ZipOutputStream(out.buffered()).use { zip ->
      zip.putNextEntry(ZipEntry(NOTES_ENTRY))
      zip.write(notesJson.toByteArray())
      zip.closeEntry()
      audioFiles.filter { it.isFile }.forEach { file ->
        zip.putNextEntry(ZipEntry(AUDIO_PREFIX + file.name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
      }
    }
  }

  /** Returns null when the archive has no readable `notes.json`; no library data is touched. */
  fun extract(input: InputStream, stagingDir: File): Extracted? {
    val dir = stagingDir.apply { mkdirs() }
    val notes = StringBuilder()
    val audio = mutableListOf<File>()
    val found = runCatching {
      ZipInputStream(input.buffered()).use { zip ->
        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
          val name = entry.name
          when {
            name == NOTES_ENTRY -> notes.append(String(zip.readEntryBytes(), Charsets.UTF_8))
            name.startsWith(AUDIO_PREFIX) && !entry.isDirectory -> {
              val safe = File(name).name
              if (safe.endsWith(".m4a", ignoreCase = true)) {
                val target = File(dir, safe)
                target.outputStream().use { zip.copyTo(it) }
                audio += target
              }
            }
          }
          zip.closeEntry()
          entry = zip.nextEntry
        }
      }
    }.isSuccess

    if (!found || notes.isBlank()) return null
    return Extracted(notesJson = notes.toString(), audioFiles = audio)
  }

  private fun ZipInputStream.readEntryBytes(): ByteArray {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
      val read = read(chunk)
      if (read <= 0) break
      total += read
      if (total > MAX_ENTRY_BYTES) error("entrada demasiado grande")
      buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
  }
}
