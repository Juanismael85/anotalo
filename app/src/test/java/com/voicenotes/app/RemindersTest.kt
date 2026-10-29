package com.voicenotes.app

import com.voicenotes.app.data.Note
import com.voicenotes.app.reminder.CalendarGrid
import com.voicenotes.app.reminder.ReminderBucket
import com.voicenotes.app.reminder.ReminderFormat
import com.voicenotes.app.reminder.Reminders
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S22 and S24 — radar grouping, day filtering and the month grid. */
class RemindersTest {

  private val zone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
  private val now: Long = millis(2026, 10, 26, 10, 0)

  private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

  private fun note(id: String, at: Long?, done: Boolean = false): Note =
    Note(id = id, title = id, reminderAt = at, reminderDone = done)

  @Test
  fun `pending keeps only notes with a date that are not fulfilled and sorts them`() {
    val notes = listOf(
      note("sin-fecha", null),
      note("tarde", millis(2026, 10, 27, 18, 0)),
      note("hecho", millis(2026, 10, 26, 9, 0), done = true),
      note("temprano", millis(2026, 10, 26, 12, 0)),
    )

    assertEquals(listOf("temprano", "tarde"), Reminders.pending(notes).map { it.id })
  }

  @Test
  fun `buckets split overdue today tomorrow and later`() {
    assertEquals(ReminderBucket.OVERDUE, Reminders.bucket(millis(2026, 10, 26, 9, 59), now, zone))
    assertEquals(ReminderBucket.TODAY, Reminders.bucket(millis(2026, 10, 26, 12, 0), now, zone))
    assertEquals(ReminderBucket.TOMORROW, Reminders.bucket(millis(2026, 10, 27, 9, 0), now, zone))
    assertEquals(ReminderBucket.LATER, Reminders.bucket(millis(2026, 10, 29, 9, 0), now, zone))
  }

  @Test
  fun `in bucket keeps the time order`() {
    val notes = listOf(
      note("a", millis(2026, 10, 26, 15, 0)),
      note("b", millis(2026, 10, 26, 11, 0)),
      note("c", millis(2026, 10, 27, 11, 0)),
    )

    assertEquals(listOf("b", "a"), Reminders.inBucket(notes, ReminderBucket.TODAY, now, zone).map { it.id })
    assertEquals(listOf("c"), Reminders.inBucket(notes, ReminderBucket.TOMORROW, now, zone).map { it.id })
  }

  @Test
  fun `on day lists fulfilled and pending reminders of that date`() {
    val day = LocalDate.of(2026, 10, 27)
    val notes = listOf(
      note("manana", millis(2026, 10, 27, 8, 0)),
      note("manana-hecho", millis(2026, 10, 27, 9, 0), done = true),
      note("otro", millis(2026, 10, 28, 9, 0)),
    )

    assertEquals(listOf("manana", "manana-hecho"), Reminders.onDay(notes, day, zone).map { it.id })
  }

  // ---- S24 calendar grid --------------------------------------------------

  @Test
  fun `october 2026 starts on the fourth column and fits in five weeks`() {
    val cells = CalendarGrid.month(2026, 10)

    assertEquals(35, cells.size)
    // Monday first: 1 Oct 2026 is a Thursday, so three days of September come first.
    assertEquals(LocalDate.of(2026, 9, 28), cells.first().date)
    assertFalse(cells.first().inMonth)
    assertEquals(LocalDate.of(2026, 10, 1), cells[3].date)
    assertTrue(cells[3].inMonth)
    assertEquals(31, cells.count { it.inMonth })
  }

  @Test
  fun `a leap february has twenty nine days`() {
    val cells = CalendarGrid.month(2028, 2)

    assertEquals(29, cells.count { it.inMonth })
    assertEquals(0, cells.size % 7)
    assertTrue(cells.any { it.inMonth && it.date.dayOfMonth == 29 })
  }

  @Test
  fun `every month is whole weeks and starts on monday`() {
    (1..12).forEach { month ->
      val cells = CalendarGrid.month(2026, month)
      assertEquals("month $month", 0, cells.size % 7)
      assertEquals("month $month", 1, cells.first().date.dayOfWeek.value)
      assertEquals(
        "month $month",
        YearMonth.of(2026, month).lengthOfMonth(),
        cells.count { it.inMonth },
      )
    }
  }

  // ---- labels -------------------------------------------------------------

  @Test
  fun `labels use today tomorrow yesterday and the date`() {
    assertEquals("Hoy 12:00", ReminderFormat.label(millis(2026, 10, 26, 12, 0), now, zone))
    assertEquals("Mañana 09:00", ReminderFormat.label(millis(2026, 10, 27, 9, 0), now, zone))
    assertEquals("Ayer 23:59", ReminderFormat.label(millis(2026, 10, 25, 23, 59), now, zone))
    assertEquals("29 oct 15:30", ReminderFormat.label(millis(2026, 10, 29, 15, 30), now, zone))
  }

  @Test
  fun `times are zero padded and the calendar title is in spanish`() {
    assertEquals("05:07", ReminderFormat.time(millis(2026, 10, 27, 5, 7), zone))
    assertEquals("27 oct 15:30", ReminderFormat.dayAndTime(millis(2026, 10, 27, 15, 30), zone))
    assertEquals("Octubre 2026", ReminderFormat.monthTitle(2026, 10))
    assertEquals("27 de octubre de 2026", ReminderFormat.dayLabel(LocalDate.of(2026, 10, 27)))
  }
}
