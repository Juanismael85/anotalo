package com.voicenotes.app.reminder

import com.voicenotes.app.data.Activity
import com.voicenotes.app.data.ActivityDates
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * S14 — when the next alarm of an activity is due. Pure so the JVM tests can pin it without Android:
 * the alarm follows the real occurrences of the series, never a date that already passed and never
 * after the activity was marked as done.
 */
object ActivityReminders {

  /** Epoch millis of the next occurrence after [now], or null when the series has no future. */
  fun nextAt(activity: Activity, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val startMinutes = activity.startMinutes.coerceIn(0, 1439)
    val time = LocalTime.of(startMinutes / 60, startMinutes % 60)
    var from = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    // At most the start date plus one turn of the series: enough to skip today's passed hour.
    repeat(3) {
      val day = ActivityDates.nextOn(activity, from) ?: return null
      val at = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
      if (at > now) return at
      from = day.plusDays(1)
    }
    return null
  }

  /** The same thing for the scheduler, which also needs the activity to be asking for a reminder. */
  fun nextAtIfAsked(activity: Activity, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? =
    if (activity.remind) nextAt(activity, now, zone) else null
}
