package com.voicenotes.app

import com.voicenotes.app.data.Activity
import com.voicenotes.app.reminder.DailyAgenda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** S4 of the agenda block — the daily digest: what it lists, how it reads and when it fires. */
class DailyAgendaTest {

  private fun activity(title: String, day: Int, start: Int, repeat: List<Int> = emptyList()) =
    Activity(id = title, title = title, dayOfWeek = day, startMinutes = start, repeatDays = repeat)

  @Test
  fun `the digest holds the blocks of that weekday, repeats included, in time order`() {
    val today = DailyAgenda.today(
      listOf(
        activity("Lámparas", 4, 600),
        activity("Limpieza", 2, 420, repeat = listOf(4)),
        activity("Compras", 5, 480),
      ),
      today = 4,
    )

    assertEquals(listOf("Limpieza", "Lámparas"), today.map { it.title })
  }

  @Test
  fun `the summary shows the first blocks and counts the rest`() {
    val items = listOf(
      activity("Limpieza", 4, 420),
      activity("Lámparas", 4, 510),
      activity("Compras", 4, 600),
      activity("Informe", 4, 660),
      activity("Llamada", 4, 720),
    )

    val summary = DailyAgenda.summary(items)

    assertTrue(summary.startsWith("07:00 Limpieza  ·  08:30 Lámparas  ·  10:00 Compras"))
    assertTrue(summary.endsWith("y 2 más"))
  }

  @Test
  fun `a short day is listed whole and an empty one says so`() {
    assertEquals("07:00 Limpieza", DailyAgenda.summary(listOf(activity("Limpieza", 4, 420))))
    assertEquals("Sin actividades para hoy.", DailyAgenda.summary(emptyList()))
  }

  @Test
  fun `the title counts the activities`() {
    assertEquals("Hoy no hay actividades", DailyAgenda.title(0))
    assertEquals("Hoy tenés 1 actividad", DailyAgenda.title(1))
    assertEquals("Hoy tenés 3 actividades", DailyAgenda.title(3))
  }

  @Test
  fun `the next digest is today when the hour is still ahead`() {
    val zone = ZoneId.of("UTC")
    val day = LocalDate.of(2026, 10, 29)
    val now = day.atTime(6, 0).atZone(zone).toInstant().toEpochMilli()

    assertEquals(
      day.atTime(7, 0).atZone(zone).toInstant().toEpochMilli(),
      DailyAgenda.nextOccurrence(420, now, zone),
    )
  }

  @Test
  fun `the next digest is tomorrow once the hour has passed`() {
    val zone = ZoneId.of("UTC")
    val day = LocalDate.of(2026, 10, 29)
    val now = day.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()

    assertEquals(
      day.plusDays(1).atTime(7, 0).atZone(zone).toInstant().toEpochMilli(),
      DailyAgenda.nextOccurrence(420, now, zone),
    )
  }

  @Test
  fun `an exact hour already reached moves to tomorrow`() {
    val zone = ZoneId.of("UTC")
    val day = LocalDate.of(2026, 10, 29)
    val now = day.atTime(7, 0).atZone(zone).toInstant().toEpochMilli()

    assertEquals(
      day.plusDays(1).atTime(7, 0).atZone(zone).toInstant().toEpochMilli(),
      DailyAgenda.nextOccurrence(420, now, zone),
    )
  }
}
