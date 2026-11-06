package com.voicenotes.app

import com.voicenotes.app.data.Activity
import com.voicenotes.app.reminder.ActivityReminders
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S14 — el recordatorio de una actividad suena en la próxima ocurrencia real de su serie, nunca en
 * una fecha que ya pasó y nunca después de que la actividad se cumplió.
 */
class ActivityRemindersTest {

  private val zone = ZoneOffset.UTC
  private val monday = LocalDate.of(2026, 11, 9)

  private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
    LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).toInstant(zone).toEpochMilli()

  private fun activity(
    date: LocalDate = monday,
    startMinutes: Int = 9 * 60,
    repeat: String = Activity.REPEAT_NONE,
    repeatUntil: String? = null,
    done: Boolean = false,
  ) = Activity(
    id = "a1",
    title = "Revisar el stock",
    date = date.toString(),
    startMinutes = startMinutes,
    repeat = repeat,
    repeatUntil = repeatUntil,
    done = done,
  )

  @Test
  fun `it fires later the same day when the hour has not come`() {
    val at = ActivityReminders.nextAt(activity(), at(monday, 8), zone)

    assertEquals(at(monday, 9), at)
  }

  @Test
  fun `a one-off activity that already passed has no reminder`() {
    assertNull(ActivityReminders.nextAt(activity(), at(monday, 10), zone))
  }

  @Test
  fun `a daily series fires again the next day`() {
    val at = ActivityReminders.nextAt(activity(repeat = Activity.REPEAT_DAILY), at(monday, 10), zone)

    assertEquals(at(monday.plusDays(1), 9), at)
  }

  @Test
  fun `a weekly series fires seven days later`() {
    val at = ActivityReminders.nextAt(activity(repeat = Activity.REPEAT_WEEKLY), at(monday, 10), zone)

    assertEquals(at(monday.plusDays(7), 9), at)
  }

  @Test
  fun `a finished activity never fires again`() {
    assertNull(ActivityReminders.nextAt(activity(done = true), at(monday, 8), zone))
  }

  @Test
  fun `a series that already ended never fires again`() {
    val ended = activity(
      repeat = Activity.REPEAT_DAILY,
      repeatUntil = monday.minusDays(1).toString(),
    )

    assertNull(ActivityReminders.nextAt(ended, at(monday, 8), zone))
  }
}
