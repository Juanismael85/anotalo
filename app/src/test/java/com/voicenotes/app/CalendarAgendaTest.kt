package com.voicenotes.app

import com.voicenotes.app.data.CalendarAgenda
import com.voicenotes.app.data.NotesRepository
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S10 (agenda) — el calendario de la app lee las mismas actividades del cronograma. Una actividad
 * cae en todas las fechas de su día, y editarla en cualquier lado la mueve en todos lados.
 */
class CalendarAgendaTest {

  private lateinit var storage: FakeStorage
  private lateinit var repo: NotesRepository

  // 2026-11-09 is a Monday; the fixture moves around it.
  private val monday = LocalDate.of(2026, 11, 9)
  private val tuesday = LocalDate.of(2026, 11, 10)
  private val wednesday = LocalDate.of(2026, 11, 11)

  @Before
  fun setUp() {
    storage = FakeStorage()
    repo = NotesRepository(storage) { 1_000L }
    repo.load()
  }

  private fun activities() = repo.state.value.activities

  @Test
  fun `an activity shows on its weekday`() {
    repo.createActivity("Revisar el stock", dayOfWeek = 1, startMinutes = 480)

    val day = CalendarAgenda.onDay(activities(), monday)

    assertEquals(1, day.size)
    assertEquals("Revisar el stock", day.first().title)
  }

  @Test
  fun `another weekday stays empty`() {
    repo.createActivity("Revisar el stock", dayOfWeek = 1, startMinutes = 480)

    assertTrue(CalendarAgenda.onDay(activities(), tuesday).isEmpty())
    assertFalse(CalendarAgenda.hasOn(activities(), tuesday))
  }

  @Test
  fun `a repeated day also shows`() {
    repo.createActivity("Limpieza", dayOfWeek = 1, startMinutes = 420, repeatDays = listOf(3))

    assertTrue(CalendarAgenda.onDay(activities(), monday).isNotEmpty())
    assertTrue(CalendarAgenda.onDay(activities(), wednesday).isNotEmpty())
  }

  @Test
  fun `the day is ordered by hour`() {
    repo.createActivity("Tarde", dayOfWeek = 1, startMinutes = 900)
    repo.createActivity("Temprano", dayOfWeek = 1, startMinutes = 420)

    val day = CalendarAgenda.onDay(activities(), monday)

    assertEquals(listOf("Temprano", "Tarde"), day.map { it.title })
  }

  @Test
  fun `editing the day moves the activity in the calendar`() {
    val created = repo.createActivity("Revisar el stock", dayOfWeek = 1, startMinutes = 480)

    repo.updateActivity(
      created.id,
      title = "Revisar el stock",
      dayOfWeek = 2,
      startMinutes = 480,
      durationMinutes = 60,
      repeatDays = emptyList(),
    )

    assertTrue(CalendarAgenda.onDay(activities(), monday).isEmpty())
    assertEquals(1, CalendarAgenda.onDay(activities(), tuesday).size)
  }

  @Test
  fun `editing the hour is reflected`() {
    val created = repo.createActivity("Revisar el stock", dayOfWeek = 1, startMinutes = 480)

    repo.updateActivity(
      created.id,
      title = "Revisar el stock",
      dayOfWeek = 1,
      startMinutes = 600,
      durationMinutes = 60,
      repeatDays = emptyList(),
    )

    assertEquals(600, CalendarAgenda.onDay(activities(), monday).first().startMinutes)
  }

  @Test
  fun `deleting the activity clears the day`() {
    val created = repo.createActivity("Revisar el stock", dayOfWeek = 1, startMinutes = 480)

    repo.deleteActivity(created.id)

    assertTrue(CalendarAgenda.onDay(activities(), monday).isEmpty())
  }
}
