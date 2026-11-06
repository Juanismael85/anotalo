package com.voicenotes.app

import com.voicenotes.app.data.Activity
import com.voicenotes.app.data.ActivityDates
import com.voicenotes.app.data.CalendarAgenda
import com.voicenotes.app.data.NotesRepository
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S10/S11 — el calendario de la app lee las mismas actividades del cronograma. Ahora cada una tiene
 * una fecha concreta y, si se repite, su serie: aparece solo en los días en que de verdad toca.
 */
class CalendarAgendaTest {

  private lateinit var storage: FakeStorage
  private lateinit var repo: NotesRepository

  private val monday = LocalDate.of(2026, 11, 9)
  private val tuesday = LocalDate.of(2026, 11, 10)
  private val wednesday = LocalDate.of(2026, 11, 11)
  private val nextMonday = LocalDate.of(2026, 11, 16)

  @Before
  fun setUp() {
    storage = FakeStorage()
    repo = NotesRepository(storage) { 1_000L }
    repo.load()
  }

  private fun activities() = repo.state.value.activities

  private fun create(
    title: String,
    date: LocalDate = monday,
    startMinutes: Int = 480,
    repeat: String = Activity.REPEAT_NONE,
    repeatUntil: String? = null,
  ) = repo.createActivity(
    title = title,
    startMinutes = startMinutes,
    date = date.toString(),
    repeat = repeat,
    repeatUntil = repeatUntil,
  )

  @Test
  fun `an activity shows only on its date`() {
    create("Revisar el stock")

    val day = CalendarAgenda.onDay(activities(), monday)

    assertEquals(1, day.size)
    assertEquals("Revisar el stock", day.first().title)
    assertTrue(CalendarAgenda.onDay(activities(), tuesday).isEmpty())
    assertTrue(CalendarAgenda.onDay(activities(), nextMonday).isEmpty())
    assertFalse(CalendarAgenda.hasOn(activities(), nextMonday))
  }

  @Test
  fun `a weekly series comes back every seven days`() {
    create("Revisar el stock", repeat = Activity.REPEAT_WEEKLY)

    assertTrue(CalendarAgenda.onDay(activities(), monday).isNotEmpty())
    assertTrue(CalendarAgenda.onDay(activities(), nextMonday).isNotEmpty())
    assertTrue(CalendarAgenda.onDay(activities(), tuesday).isEmpty())
  }

  @Test
  fun `a daily series shows every day`() {
    create("Limpieza", repeat = Activity.REPEAT_DAILY)

    assertTrue(CalendarAgenda.onDay(activities(), monday).isNotEmpty())
    assertTrue(CalendarAgenda.onDay(activities(), tuesday).isNotEmpty())
    assertTrue(CalendarAgenda.onDay(activities(), wednesday).isNotEmpty())
    assertTrue(CalendarAgenda.onDay(activities(), monday.minusDays(1)).isEmpty())
  }

  @Test
  fun `the series stops on its last date`() {
    create(
      "Limpieza",
      repeat = Activity.REPEAT_DAILY,
      repeatUntil = tuesday.toString(),
    )

    assertTrue(CalendarAgenda.onDay(activities(), tuesday).isNotEmpty())
    assertTrue(CalendarAgenda.onDay(activities(), wednesday).isEmpty())
  }

  @Test
  fun `a finished activity stops the series but stays on its date`() {
    val created = create("Revisar el stock", repeat = Activity.REPEAT_DAILY)

    repo.setActivityDone(created.id, true)

    assertTrue(CalendarAgenda.onDay(activities(), monday).isNotEmpty())
    assertTrue(CalendarAgenda.onDay(activities(), tuesday).isEmpty())
  }

  @Test
  fun `the day is ordered by hour`() {
    create("Tarde", startMinutes = 900)
    create("Temprano", startMinutes = 420)

    val day = CalendarAgenda.onDay(activities(), monday)

    assertEquals(listOf("Temprano", "Tarde"), day.map { it.title })
  }

  @Test
  fun `changing the date moves the activity in the calendar`() {
    val created = create("Revisar el stock")

    repo.updateActivity(
      created.id,
      title = "Revisar el stock",
      startMinutes = 480,
      durationMinutes = 60,
      date = tuesday.toString(),
    )

    assertTrue(CalendarAgenda.onDay(activities(), monday).isEmpty())
    assertEquals(1, CalendarAgenda.onDay(activities(), tuesday).size)
  }

  @Test
  fun `changing the hour is reflected`() {
    val created = create("Revisar el stock")

    repo.updateActivity(
      created.id,
      title = "Revisar el stock",
      startMinutes = 600,
      durationMinutes = 60,
      date = monday.toString(),
    )

    assertEquals(600, CalendarAgenda.onDay(activities(), monday).first().startMinutes)
  }

  @Test
  fun `deleting the activity clears the day`() {
    val created = create("Revisar el stock")

    repo.deleteActivity(created.id)

    assertTrue(CalendarAgenda.onDay(activities(), monday).isEmpty())
  }

  // ---- S11/S12/S13, the pure rules ------------------------------------------------

  @Test
  fun `the next date of a weekday is that same day when it matches`() {
    assertEquals(monday, ActivityDates.nextDateFor(1, monday))
    assertEquals(nextMonday, ActivityDates.nextDateFor(1, tuesday))
  }

  @Test
  fun `the next occurrence of a weekly series is seven days later`() {
    val activity = Activity(
      id = "a",
      title = "Revisar",
      date = monday.toString(),
      startMinutes = 480,
      repeat = Activity.REPEAT_WEEKLY,
    )

    assertEquals(monday, ActivityDates.nextOn(activity, monday))
    assertEquals(nextMonday, ActivityDates.nextOn(activity, tuesday))
  }

  @Test
  fun `a finished activity has no next occurrence`() {
    val activity = Activity(
      id = "a",
      title = "Revisar",
      date = monday.toString(),
      startMinutes = 480,
      repeat = Activity.REPEAT_DAILY,
      done = true,
    )

    assertNull(ActivityDates.nextOn(activity, monday))
  }
}
