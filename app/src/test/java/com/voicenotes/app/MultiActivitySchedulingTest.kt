package com.voicenotes.app

import com.voicenotes.app.ai.TaskExtractor
import com.voicenotes.app.ai.TaskText
import com.voicenotes.app.data.NewActivity
import com.voicenotes.app.data.NotesRepository
import com.voicenotes.app.data.WorkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S1 (agenda) — a note is a container of tasks: dictating two things must propose two activities,
 * each with its own day and hour, never one activity that swallows the rest.
 */
class MultiActivitySchedulingTest {

  private val mondayToFriday = WorkConfig(
    dayNumbers = listOf(1, 2, 3, 4, 5),
    startMinutes = 420,
    endMinutes = 960,
  )

  private lateinit var storage: FakeStorage
  private lateinit var repo: NotesRepository

  @Before
  fun setUp() {
    storage = FakeStorage()
    repo = NotesRepository(storage) { 1_000L }
    repo.load()
  }

  // ---- reading several tasks out of one note --------------------------------

  @Test
  fun `a noun that names a task counts as one`() {
    assertTrue(TaskText.actionable("Reunión con el proveedor"))
    assertTrue(TaskText.actionable("Informe de mantenimiento"))
    assertFalse(TaskText.actionable("Buenas tardes, estimado"))
  }

  @Test
  fun `a dictated note without punctuation still splits on the conjunction`() {
    val sentences = TaskText.taskSentences(
      title = "Pendientes del martes",
      summary = "",
      transcript = "Reunión con el proveedor el martes y hay que revisar el stock",
    )

    assertEquals(2, sentences.size)
    assertEquals("Reunión con el proveedor el martes", sentences[0])
    assertEquals("hay que revisar el stock", sentences[1])
  }

  @Test
  fun `one note with two tasks becomes two activities`() {
    val items = TaskExtractor.suggestAll(
      title = "Pendientes del martes",
      summary = "",
      transcript = "Reunión con el proveedor el martes y hay que revisar el stock",
      config = mondayToFriday,
      today = 3,
    )

    assertEquals(2, items.size)
    assertEquals(listOf("Reunión con el proveedor el martes", "Revisar el stock"), items.map { it.title })
    assertEquals(listOf(2, 2), items.map { it.dayOfWeek })
  }

  @Test
  fun `the second task starts when the first one ends`() {
    val items = TaskExtractor.suggestAll(
      title = "Pendientes",
      summary = "",
      transcript = "Revisar el stock y hay que limpiar los pisos",
      config = mondayToFriday,
      today = 3,
    )

    assertEquals(2, items.size)
    assertEquals(420, items[0].startMinutes)
    assertEquals(480, items[1].startMinutes)
    assertEquals(420 + items[0].durationMinutes, items[1].startMinutes)
  }

  @Test
  fun `a day spoken inside the task wins over the rest of the note`() {
    val items = TaskExtractor.suggestAll(
      title = "Pendientes",
      summary = "",
      transcript = "Revisar el stock el jueves y hay que limpiar los pisos el lunes",
      config = mondayToFriday,
      today = 3,
    )

    assertEquals(listOf(4, 1), items.map { it.dayOfWeek })
  }

  @Test
  fun `a task that does not fit before closing time moves to the next workday`() {
    val items = TaskExtractor.suggestAll(
      title = "Pendientes",
      summary = "",
      transcript = "Revisar el stock a las 15:30 y hay que limpiar los pisos",
      config = mondayToFriday,
      today = 3,
    )

    assertEquals(2, items.size)
    assertEquals(930, items[0].startMinutes)
    assertEquals(420, items[1].startMinutes)
    assertTrue("el que no entra pasa al día siguiente", items[1].dayOfWeek > items[0].dayOfWeek)
  }

  @Test
  fun `a note without tasks proposes nothing`() {
    val items = TaskExtractor.suggestAll(
      title = "Saludos",
      summary = "",
      transcript = "Buenas tardes, estimado",
      config = mondayToFriday,
      today = 3,
    )

    assertTrue(items.isEmpty())
  }

  // ---- writing several activities at once -----------------------------------

  @Test
  fun `createActivities saves every slot in one write and links the note`() {
    val created = repo.createActivities(
      listOf(
        NewActivity("Revisar el stock", dayOfWeek = 2, startMinutes = 420, noteId = "note-1"),
        NewActivity("Limpiar los pisos", dayOfWeek = 2, startMinutes = 480, noteId = "note-1"),
      ),
    )

    assertEquals(2, created.size)
    assertEquals(2, repo.state.value.activities.size)
    assertEquals(listOf("note-1", "note-1"), repo.state.value.activities.map { it.noteId })
    assertEquals("una sola escritura para todo el lote", 1, storage.writes)
  }

  @Test
  fun `a batch of activities survives a reload`() {
    repo.createActivities(
      listOf(
        NewActivity("Revisar el stock", dayOfWeek = 2, startMinutes = 420, noteId = "note-1"),
        NewActivity("Limpiar los pisos", dayOfWeek = 3, startMinutes = 480, noteId = "note-1"),
      ),
    )

    val reopened = NotesRepository(storage) { 2_000L }
    reopened.load()

    assertEquals(2, reopened.state.value.activities.size)
    assertEquals(listOf(2, 3), reopened.state.value.activities.map { it.dayOfWeek })
  }

  @Test
  fun `an empty batch writes nothing`() {
    repo.createActivities(emptyList())

    assertTrue(repo.state.value.activities.isEmpty())
    assertEquals(0, storage.writes)
  }
}
