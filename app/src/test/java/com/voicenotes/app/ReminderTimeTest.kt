package com.voicenotes.app

import com.voicenotes.app.data.Note
import com.voicenotes.app.reminder.ReminderBucket
import com.voicenotes.app.reminder.Reminders
import com.voicenotes.app.reminder.ReminderTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S21 — the rules that turn what the model answers into a reminder, and S22 — the buckets the
 * radar groups them by. Fixed zone so the assertions do not depend on the machine.
 */
class ReminderTimeTest {

  private val zone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
  private val now: Long = LocalDateTime.of(2026, 10, 26, 10, 0).atZone(zone).toInstant().toEpochMilli()

  private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

  @Test
  fun `parses a local date and time`() {
    assertEquals(at(2026, 10, 27, 17, 0), ReminderTime.parse("2026-10-27T17:00", now, zone))
  }

  @Test
  fun `a date without time means nine in the morning`() {
    assertEquals(at(2026, 10, 27, 9, 0), ReminderTime.parse("2026-10-27", now, zone))
  }

  @Test
  fun `accepts seconds a space instead of T and an offset`() {
    assertEquals(at(2026, 10, 27, 17, 0), ReminderTime.parse("2026-10-27T17:00:00", now, zone))
    assertEquals(at(2026, 10, 27, 17, 0), ReminderTime.parse("2026-10-27 17:00", now, zone))
    assertEquals(at(2026, 10, 27, 17, 0), ReminderTime.parse("2026-10-27T17:00:00-03:00", now, zone))
  }

  @Test
  fun `blank null and unparseable values are not reminders`() {
    assertNull(ReminderTime.parse(null, now, zone))
    assertNull(ReminderTime.parse("", now, zone))
    assertNull(ReminderTime.parse("null", now, zone))
    assertNull(ReminderTime.parse("mañana a la tarde", now, zone))
    assertNull(ReminderTime.parse("2026-13-45T99:99", now, zone))
  }

  @Test
  fun `a value in the past is discarded`() {
    assertNull(ReminderTime.parse("2026-10-20T10:00", now, zone))
    assertNull(ReminderTime.parse("2026-10-26T09:59", now, zone))
  }

  @Test
  fun `a value later today is kept`() {
    assertEquals(at(2026, 10, 26, 18, 30), ReminderTime.parse("2026-10-26T18:30", now, zone))
  }

  @Test
  fun `picker utc midnight becomes the chosen local day`() {
    val utcMidnight = LocalDate.of(2026, 10, 27).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    assertEquals(LocalDate.of(2026, 10, 27), ReminderTime.pickerDate(utcMidnight))
    assertEquals(at(2026, 10, 27, 17, 30), ReminderTime.fromPickers(utcMidnight, 17, 30, zone))
  }

  @Test
  fun `an existing reminder keeps its time when the dialog reopens`() {
    assertEquals(17 to 30, ReminderTime.hourMinute(at(2026, 10, 27, 17, 30), zone))
  }

  @Test
  fun `the default time is the next full hour`() {
    assertEquals(11 to 0, ReminderTime.defaultHourMinute(now, zone))
    val lateNight = LocalDateTime.of(2026, 10, 26, 23, 20).atZone(zone).toInstant().toEpochMilli()
    assertEquals(0 to 0, ReminderTime.defaultHourMinute(lateNight, zone))
  }
}
