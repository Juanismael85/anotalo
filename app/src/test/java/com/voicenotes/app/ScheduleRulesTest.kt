package com.voicenotes.app

import com.voicenotes.app.data.Activity
import com.voicenotes.app.data.NotesRepository
import com.voicenotes.app.data.ScheduleRules
import com.voicenotes.app.data.WorkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** S2 and S5 of the agenda block — repeat days, duration, clashes, leftovers and repository writes. */
class ScheduleRulesTest {

  private fun activity(
    title: String,
    day: Int,
    start: Int,
    duration: Int = 60,
    repeat: List<Int> = emptyList(),
    done: Boolean = false,
    id: String = title,
  ) = Activity(
    id = id,
    title = title,
    dayOfWeek = day,
    startMinutes = start,
    durationMinutes = duration,
    repeatDays = repeat,
    done = done,
  )

  // ---- repeat days ----------------------------------------------------------

  @Test
  fun `an activity occupies its day plus every repeat day`() {
    assertEquals(listOf(1, 3, 5), ScheduleRules.daysOf(activity("Limpieza", 1, 480, repeat = listOf(5, 3))))
  }

  @Test
  fun `repeat days outside the week are ignored`() {
    assertEquals(listOf(2), ScheduleRules.daysOf(activity("Limpieza", 2, 480, repeat = listOf(0, 9))))
  }

  @Test
  fun `the end of a block adds its duration and never leaves the day`() {
    assertEquals(540, ScheduleRules.endOf(activity("Limpieza", 1, 480, duration = 60)))
    assertEquals(24 * 60, ScheduleRules.endOf(activity("Limpieza", 1, 1400, duration = 60)))
  }

  @Test
  fun `a duration out of range is clamped`() {
    assertEquals(ScheduleRules.MIN_DURATION, ScheduleRules.durationOf(activity("A", 1, 480, duration = 1)))
    assertEquals(ScheduleRules.MAX_DURATION, ScheduleRules.durationOf(activity("A", 1, 480, duration = 5000)))
  }

  // ---- clashes --------------------------------------------------------------

  @Test
  fun `two blocks on the same day that touch in time clash`() {
    val clashes = ScheduleRules.clashes(
      listOf(
        activity("Limpieza", 2, 480, duration = 60),
        activity("Lámparas", 2, 510, duration = 60),
      ),
    )

    assertEquals(1, clashes.size)
    assertEquals(2, clashes.first().dayOfWeek)
    assertEquals("Limpieza", clashes.first().firstTitle)
    assertEquals("Lámparas", clashes.first().secondTitle)
  }

  @Test
  fun `blocks that only follow each other do not clash`() {
    val clashes = ScheduleRules.clashes(
      listOf(
        activity("Limpieza", 2, 480, duration = 60),
        activity("Lámparas", 2, 540, duration = 60),
      ),
    )

    assertTrue(clashes.isEmpty())
  }

  @Test
  fun `a repeat day is enough to clash with a block anchored there`() {
    val clashes = ScheduleRules.clashes(
      listOf(
        activity("Limpieza", 1, 480, duration = 60, repeat = listOf(3)),
        activity("Lámparas", 3, 500, duration = 30),
      ),
    )

    assertEquals(1, clashes.size)
    assertEquals(3, clashes.first().dayOfWeek)
  }

  // ---- leftovers ------------------------------------------------------------

  @Test
  fun `earlier pending blocks are leftovers and done ones are not`() {
    val leftovers = ScheduleRules.leftovers(
      listOf(
        activity("Lunes pendiente", 1, 480),
        activity("Lunes hecho", 1, 540, done = true),
        activity("Hoy terminada", 3, 480, duration = 60),
        activity("Hoy en curso", 3, 600, duration = 60),
        activity("Repetida", 2, 480, repeat = listOf(2, 4)),
      ),
      today = 3,
      nowMinutes = 640,
    )

    assertEquals(listOf("Lunes pendiente", "Hoy terminada"), leftovers.map { it.title })
  }

  @Test
  fun `no leftovers when everything is still ahead`() {
    assertTrue(
      ScheduleRules.leftovers(listOf(activity("Viernes", 5, 480)), today = 1, nowMinutes = 300).isEmpty(),
    )
  }

  // ---- next workday ---------------------------------------------------------

  @Test
  fun `the next workday is the next enabled day of the week`() {
    val config = WorkConfig(dayNumbers = listOf(1, 2, 3, 4, 5))
    assertEquals(2, ScheduleRules.nextWorkday(config, today = 1))
    assertEquals(1, ScheduleRules.nextWorkday(config, today = 5))
  }

  @Test
  fun `without a config every day is a workday`() {
    assertEquals(4, ScheduleRules.nextWorkday(null, today = 3))
    assertEquals(1, ScheduleRules.nextWorkday(null, today = 7))
  }

  @Test
  fun `a single enabled day returns itself`() {
    assertEquals(6, ScheduleRules.nextWorkday(WorkConfig(dayNumbers = listOf(6)), today = 2))
  }

  // ---- repository -----------------------------------------------------------

  private lateinit var storage: FakeStorage
  private lateinit var repo: NotesRepository

  @Before
  fun setUp() {
    storage = FakeStorage()
    repo = NotesRepository(storage) { 1_000L }
    repo.load()
  }

  @Test
  fun `an activity is created with its duration and repeat days`() {
    val created = repo.createActivity(
      title = "  Limpieza  ",
      dayOfWeek = 2,
      startMinutes = 480,
      noteId = "note-1",
      durationMinutes = 45,
      repeatDays = listOf(4, 2, 9),
    )

    assertEquals("Limpieza", created.title)
    assertEquals(45, created.durationMinutes)
    assertEquals(listOf(2, 4), created.repeatDays)
    assertEquals("note-1", created.noteId)
    assertEquals(Activity.SOURCE_MANUAL, created.source)
  }

  @Test
  fun `an activity keeps its identity when it is edited`() {
    val created = repo.createActivity("Limpieza", 1, 480)
    repo.updateActivity(created.id, "Limpieza profunda", 3, 600, 90, listOf(5))

    val updated = repo.state.value.activities.single()
    assertEquals(created.id, updated.id)
    assertEquals("Limpieza profunda", updated.title)
    assertEquals(3, updated.dayOfWeek)
    assertEquals(600, updated.startMinutes)
    assertEquals(90, updated.durationMinutes)
    assertEquals(listOf(5), updated.repeatDays)
  }

  @Test
  fun `moving leftovers changes the day and keeps the hour`() {
    val first = repo.createActivity("Limpieza", 1, 480)
    val kept = repo.createActivity("Lámparas", 4, 600)

    val moved = repo.moveActivities(listOf(first.id), dayOfWeek = 3)

    assertEquals(1, moved)
    val activities = repo.state.value.activities.associateBy { it.id }
    assertEquals(3, activities.getValue(first.id).dayOfWeek)
    assertEquals(480, activities.getValue(first.id).startMinutes)
    assertEquals(4, activities.getValue(kept.id).dayOfWeek)
  }

  @Test
  fun `moving nothing changes nothing`() {
    repo.createActivity("Limpieza", 1, 480)
    assertEquals(0, repo.moveActivities(emptyList(), dayOfWeek = 3))
    assertEquals(1, repo.state.value.activities.single().dayOfWeek)
  }

  @Test
  fun `an activity with the new fields survives a reload and an old json still opens`() {
    repo.createActivity("Limpieza", 2, 480, durationMinutes = 45, repeatDays = listOf(4))
    val reopened = NotesRepository(storage)
    reopened.load()

    val activity = reopened.state.value.activities.single()
    assertEquals(45, activity.durationMinutes)
    assertEquals(listOf(4), activity.repeatDays)
    assertFalse(activity.done)

    storage.data = """{"notes":[],"folders":[],"tags":[],"activities":[{"id":"a","title":"Vieja","dayOfWeek":1,"startMinutes":480}]}"""
    val old = NotesRepository(storage)
    old.load()
    assertEquals(60, old.state.value.activities.single().durationMinutes)
    assertTrue(old.state.value.activities.single().repeatDays.isEmpty())
  }
}
