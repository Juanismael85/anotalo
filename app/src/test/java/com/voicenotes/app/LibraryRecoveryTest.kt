package com.voicenotes.app

import com.voicenotes.app.data.NotesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A file the app cannot decode must never cost the user the whole library. Today `load()` starts
 * empty and the next save rewrites `notes.json` with only the new data, so one bad value in one
 * record deletes every note, tag, folder and activity.
 */
class LibraryRecoveryTest {

  /** A file like the ones the app already wrote, with one value the strict decoder rejects. */
  private val recoverableJson = """
    {
      "notes": [{"id": "n1", "title": null, "body": "hola"}],
      "folders": [{"id": "f1", "name": "juan"}],
      "tags": [{"id": "t1", "name": "fiesta"}, {"id": "t2", "name": "personal"}],
      "activities": [{"id": "a1", "title": "Revisar bombas", "dayOfWeek": 1, "startMinutes": 480}],
      "checklists": [],
      "workConfig": {"dayNumbers": [1, 2, 3], "startMinutes": 420, "endMinutes": 960}
    }
  """.trimIndent()

  @Test
  fun `one bad value does not throw away the rest of the library`() {
    val storage = FakeStorage(recoverableJson)
    val repo = NotesRepository(storage)
    repo.load()

    assertEquals(2, repo.state.value.tags.size)
    assertEquals(1, repo.state.value.folders.size)
    assertEquals(1, repo.state.value.activities.size)
    assertEquals(1, repo.state.value.notes.size)
    assertEquals("", repo.state.value.notes.first().title)
  }

  @Test
  fun `an unreadable file is kept aside before the next save overwrites it`() {
    val broken = """{"notes": "esto no es una lista"}"""
    val storage = FakeStorage(broken)
    val repo = NotesRepository(storage)
    repo.load()

    assertEquals(0, repo.state.value.notes.size)
    assertEquals(broken, storage.preserved)

    repo.createTextNote(title = "Nueva", body = "Después del error")

    // The save rewrites notes.json, but the original bytes are still there to recover from.
    assertEquals(broken, storage.preserved)
    assertNotNull(storage.data)
    assertTrue(storage.data!!.contains("Nueva"))
  }

  @Test
  fun `a healthy file reports no warning`() {
    val storage = FakeStorage()
    val repo = NotesRepository(storage)
    repo.load()
    repo.createTag("trabajo")

    val reopened = NotesRepository(storage)
    reopened.load()

    assertEquals(1, reopened.state.value.tags.size)
    assertEquals(null, reopened.loadWarning)
    assertEquals(null, storage.preserved)
  }
}
