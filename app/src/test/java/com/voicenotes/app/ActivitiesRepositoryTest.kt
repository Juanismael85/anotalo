package com.voicenotes.app

import com.voicenotes.app.data.Activity
import com.voicenotes.app.data.NotesRepository
import com.voicenotes.app.data.PlannedActivity
import com.voicenotes.app.data.WorkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** S2, S5, S7 and S8 — activities, work config, checklists and finished notes on disk. */
class ActivitiesRepositoryTest {

  private lateinit var storage: FakeStorage
  private lateinit var repo: NotesRepository

  @Before
  fun setUp() {
    storage = FakeStorage()
    repo = NotesRepository(storage) { 1_000L }
    repo.load()
  }

  @Test
  fun `an old notes json without the activity fields still loads`() {
    storage.data = """{"notes":[],"folders":[],"tags":[]}"""
    val reopened = NotesRepository(storage)
    reopened.load()

    assertTrue(reopened.state.value.activities.isEmpty())
    assertTrue(reopened.state.value.checklists.isEmpty())
    assertNull(reopened.state.value.workConfig)
  }

  // ---- S3/S4 work config -----------------------------------------------------

  @Test
  fun `the work config is saved and survives a reload`() {
    repo.setWorkConfig(WorkConfig(dayNumbers = listOf(1, 2, 3, 4, 5, 6), startMinutes = 480, endMinutes = 1020))

    val reopened = NotesRepository(storage)
    reopened.load()
    val config = reopened.state.value.workConfig!!

    assertEquals(listOf(1, 2, 3, 4, 5, 6), config.dayNumbers)
    assertEquals(480, config.startMinutes)
    assertEquals(1020, config.endMinutes)
  }

  @Test
  fun `a null work config clears the choice`() {
    repo.setWorkConfig(WorkConfig())
    repo.setWorkConfig(null)

    assertNull(repo.state.value.workConfig)
  }

  // ---- S2 activities ----------------------------------------------------------

  @Test
  fun `a manual activity is created bounded and persists`() {
    val activity = repo.createActivity("  Revisión de máquinas  ", dayOfWeek = 1, startMinutes = 480)

    assertEquals("Revisión de máquinas", activity.title)
    assertEquals(1, activity.dayOfWeek)
    assertEquals(480, activity.startMinutes)
    assertEquals(Activity.SOURCE_MANUAL, activity.source)

    val reopened = NotesRepository(storage)
    reopened.load()
    assertEquals(1, reopened.state.value.activities.size)
  }

  @Test
  fun `an activity cannot land outside the week`() {
    val activity = repo.createActivity("X", dayOfWeek = 9, startMinutes = 2500)

    assertEquals(7, activity.dayOfWeek)
    assertEquals(1439, activity.startMinutes)
  }

  @Test
  fun `marking an activity as done keeps it until the schedule is regenerated`() {
    val id = repo.createActivity("Residuos", 5, 480).id

    repo.setActivityDone(id, done = true)

    val act = repo.state.value.activities.first { it.id == id }
    assertTrue(act.done)
    assertNotNull(act.doneAt)
  }

  @Test
  fun `the ai replaces its own activities and keeps the manual ones`() {
    repo.createActivity("Manual", 1, 480)
    repo.replaceAiActivities(listOf(PlannedActivity("Primera", 2, 540)))

    repo.replaceAiActivities(
      listOf(PlannedActivity("Segunda", 3, 600), PlannedActivity("Tercera", 3, 660)),
    )

    val activities = repo.state.value.activities
    assertNotNull(activities.first { it.title == "Manual" })
    assertNull(activities.firstOrNull { it.title == "Primera" })
    assertEquals(2, activities.count { it.source == Activity.SOURCE_AI })
    assertTrue(activities.filter { it.source == Activity.SOURCE_AI }.all { it.noteId == null })
  }

  // ---- S7 checklists ----------------------------------------------------------

  @Test
  fun `a checklist goes from empty to checked and survives a reload`() {
    val checklist = repo.createChecklist("Apertura de la sucursal")
    val item = repo.addChecklistItem(checklist.id, "Encender la alarma")!!

    repo.setChecklistItemDone(checklist.id, item.id, done = true)

    val reopened = NotesRepository(storage)
    reopened.load()
    val saved = reopened.state.value.checklists.single()
    assertEquals("Apertura de la sucursal", saved.title)
    assertEquals(1, saved.items.size)
    assertTrue(saved.items.first().done)
    assertNotNull(saved.items.first().id)
  }

  @Test
  fun `empty item texts are refused`() {
    val checklist = repo.createChecklist("Cierre")
    assertNull(repo.addChecklistItem(checklist.id, "   "))
    assertTrue(checklist.items.isEmpty())
  }

  @Test
  fun `a deleted checklist item or checklist stays deleted`() {
    val checklist = repo.createChecklist("Recorrido")
    val a = repo.addChecklistItem(checklist.id, "Punto 1")!!
    val b = repo.addChecklistItem(checklist.id, "Punto 2")!!

    repo.deleteChecklistItem(checklist.id, a.id)
    assertEquals(listOf(b.id), repo.state.value.checklists.single().items.map { it.id })

    repo.deleteChecklist(checklist.id)
    assertTrue(repo.state.value.checklists.isEmpty())
  }

  @Test
  fun `renaming keeps the items`() {
    val checklist = repo.createChecklist("Viejo")
    repo.addChecklistItem(checklist.id, "Punto")

    repo.renameChecklist(checklist.id, "Nuevo")

    val saved = repo.state.value.checklists.single()
    assertEquals("Nuevo", saved.title)
    assertEquals(1, saved.items.size)
  }

  // ---- S8 finished notes ------------------------------------------------------

  @Test
  fun `marking a note as finished is saved and can be undone`() {
    val id = repo.createTextNote(title = "Manifiesto", body = "").id

    repo.setNoteDone(id, done = true)
    assertTrue(repo.state.value.notes.first { it.id == id }.done)

    val reopened = NotesRepository(storage)
    reopened.load()
    assertTrue(reopened.state.value.notes.first { it.id == id }.done)

    repo.setNoteDone(id, done = false)
    assertFalse(repo.state.value.notes.first { it.id == id }.done)
    assertNull(repo.state.value.notes.first { it.id == id }.doneAt)
  }
}
