package com.voicenotes.app.reminder

import com.voicenotes.app.data.Activity
import com.voicenotes.app.data.ScheduleRules
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * S4 of the agenda block — what the daily notification says. Pure rules: which blocks belong to a
 * weekday, how the summary reads and when the next one is due, all testable on the JVM.
 */
object DailyAgenda {

  fun today(activities: List<Activity>, today: Int): List<Activity> =
    activities
      .filter { today in ScheduleRules.daysOf(it) }
      .sortedWith(compareBy({ it.startMinutes }, { it.title }))

  /** "07:00 Limpieza · 08:30 Lámparas y 2 más": the notification shows a few, not all. */
  fun summary(items: List<Activity>, shown: Int = 3): String {
    if (items.isEmpty()) return "Sin actividades para hoy."
    val head = items.take(shown).joinToString("  ·  ") {
      "%02d:%02d %s".format(it.startMinutes / 60, it.startMinutes % 60, it.title)
    }
    val rest = items.size - shown.coerceAtMost(items.size)
    return if (rest > 0) "$head  y $rest más" else head
  }

  fun title(count: Int): String = when (count) {
    0 -> "Hoy no hay actividades"
    1 -> "Hoy tenés 1 actividad"
    else -> "Hoy tenés $count actividades"
  }

  /** The next occurrence of [startMinutes] on the device clock: today if still ahead, else tomorrow. */
  fun nextOccurrence(startMinutes: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val zoned = Instant.ofEpochMilli(now).atZone(zone)
    val at = LocalTime.of(startMinutes / 60 % 24, startMinutes % 60)
    val today = zoned.toLocalDate().atTime(at).atZone(zone).toInstant().toEpochMilli()
    return if (today > now) today else zoned.toLocalDate().plusDays(1).atTime(at)
      .atZone(zone).toInstant().toEpochMilli()
  }
}
