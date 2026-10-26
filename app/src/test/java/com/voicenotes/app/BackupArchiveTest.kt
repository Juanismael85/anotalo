package com.voicenotes.app

import com.voicenotes.app.data.BackupArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class BackupArchiveTest {

  @Test
  fun `a written archive restores the notes json and the audio files`() {
    val source = Files.createTempDirectory("vn-source").toFile()
    val audio = File(source, "note-1.m4a").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
    val out = ByteArrayOutputStream()

    BackupArchive.write(out, notesJson = """{"notes":[]}""", audioFiles = listOf(audio))

    val target = Files.createTempDirectory("vn-target").toFile()
    val extracted = BackupArchive.extract(ByteArrayInputStream(out.toByteArray()), target)

    assertEquals("""{"notes":[]}""", extracted?.notesJson)
    assertEquals(1, extracted?.audioFiles?.size)
    assertEquals(listOf(1.toByte(), 2, 3, 4), extracted?.audioFiles?.first()?.readBytes()?.toList())
    assertTrue(File(target, "note-1.m4a").exists())
  }

  @Test
  fun `an archive without notes json is rejected`() {
    val out = ByteArrayOutputStream()
    BackupArchive.write(out, notesJson = "", audioFiles = emptyList())

    assertNull(BackupArchive.extract(ByteArrayInputStream(out.toByteArray()), Files.createTempDirectory("vn").toFile()))
  }

  @Test
  fun `a file that is not a zip is rejected instead of throwing`() {
    val bytes = "esto no es un zip".toByteArray()

    assertNull(BackupArchive.extract(ByteArrayInputStream(bytes), Files.createTempDirectory("vn").toFile()))
  }
}
