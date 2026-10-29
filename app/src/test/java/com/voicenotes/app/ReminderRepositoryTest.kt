package com.voicenotes.app

import com.voicenotes.app.data.NotesRepository
import com.voicenotes.app.reminder.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** S20, S21 and S25 — how a reminder is stored, fulfilled, postponed and merged with the AI. */
class ReminderRepositoryTest {

  private lateinit var storage: FakeStorage
  private lateinit var repo: NotesRepository
  private val at = 1_800_000_000_000L

  @Before
  fun setUp() {
    storage = FakeStorage()
    repo = NotesRepository(storage) { 1_000L }
    repo.load()
  }

  private fun newNote(): String = repo.createTextNote(title = "Llamar al dentista", body = "").id

  private fun note(id: String) = repo.state.value.notes.first { it.id == id }

  @Test
  fun `a manual reminder is saved and survives a reload`() {
    val id = newNote()

    repo.setReminder(id, at)

    assertEquals(at, note(id).reminderAt)
    assertFalse(note(id).reminderDone)
    assertFalse(note(id).reminderFromAi)

    val reopened = NotesRepository(storage)
    reopened.load()
    assertEquals(at, reopened.state.value.notes.first().reminderAt)
  }

  @Test
  fun `clearing removes the reminder`() {
    val id = newNote()
    repo.setReminder(id, at)

    repo.clearReminder(id)

    assertNull(note(id).reminderAt)
    assertFalse(note(id).reminderDone)
  }

  @Test
  fun `a fulfilled reminder leaves the radar but stays on the note`() {
    val id = newNote()
    repo.setReminder(id, at)

    repo.markReminderDone(id)

    assertEquals(at, note(id).reminderAt)
    assertTrue(note(id).reminderDone)
    assertTrue(Reminders.pending(repo.state.value.notes).isEmpty())
  }

  @Test
  fun `a fulfilled reminder can go back to pending`() {
    val id = newNote()
    repo.setReminder(id, at)
    repo.markReminderDone(id)

    repo.markReminderDone(id, done = false)

    assertFalse(note(id).reminderDone)
    assertEquals(1, Reminders.pending(repo.state.value.notes).size)
  }

  @Test
  fun `snoozing moves the date and clears the done state`() {
    val id = newNote()
    repo.setReminder(id, at)
    repo.markReminderDone(id)

    repo.snoozeReminder(id, at + 600_000L)

    assertEquals(at + 600_000L, note(id).reminderAt)
    assertFalse(note(id).reminderDone)
    assertTrue(Reminders.pending(repo.state.value.notes).isNotEmpty())
  }

  // ---- S21 AI merge -------------------------------------------------------

  @Test
  fun `the ai sets a reminder when the note has none`() {
    val id = newNote()

    repo.applyAiResult(id, title = "", transcript = "Reunión el lunes a las 5.", summary = "", tagNames = emptyList(), reminderAt = at)

    assertEquals(at, note(id).reminderAt)
    assertTrue(note(id).reminderFromAi)
  }

  @Test
  fun `the ai never overwrites a manual reminder`() {
    val id = newNote()
    repo.setReminder(id, at)

    repo.applyAiResult(id, title = "", transcript = "", summary = "", tagNames = emptyList(), reminderAt = at + 86_400_000L)

    assertEquals(at, note(id).reminderAt)
    assertFalse(note(id).reminderFromAi)
  }

  @Test
  fun `the ai replaces its own previous reminder`() {
    val id = newNote()
    repo.applyAiResult(id, title = "", transcript = "", summary = "", tagNames = emptyList(), reminderAt = at)

    repo.applyAiResult(id, title = "", transcript = "", summary = "", tagNames = emptyList(), reminderAt = at + 3_600_000L)

    assertEquals(at + 3_600_000L, note(id).reminderAt)
    assertTrue(note(id).reminderFromAi)
  }

  @Test
  fun `the ai leaves a fulfilled reminder alone`() {
    val id = newNote()
    repo.applyAiResult(id, title = "", transcript = "", summary = "", tagNames = emptyList(), reminderAt = at)
    repo.markReminderDone(id)

    repo.applyAiResult(id, title = "", transcript = "", summary = "", tagNames = emptyList(), reminderAt = at + 86_400_000L)

    assertEquals(at, note(id).reminderAt)
    assertTrue(note(id).reminderDone)
  }

  @Test
  fun `an ai answer without a date keeps the note as it was`() {
    val id = newNote()
    repo.setReminder(id, at)

    repo.applyAiResult(id, title = "Dentista", transcript = "", summary = "", tagNames = emptyList(), reminderAt = null)

    assertEquals(at, note(id).reminderAt)
    assertEquals("Dentista", note(id).title)
  }

  @Test
  fun `deleting the note removes it from the radar`() {
    val id = newNote()
    repo.setReminder(id, at)

    repo.deleteNote(id)

    assertTrue(Reminders.pending(repo.state.value.notes).isEmpty())
  }
}
