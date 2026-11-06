package com.voicenotes.app.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * S11/S12/S13 — an activity is a date plus an optional series. The occurrences are computed here
 * and never stored: one row per activity, no copies per date. Marking it done ends the series.
 */
object ActivityDates {

  private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
  private val short = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es"))

  fun parse(iso: String?): LocalDate? =
    if (iso.isNullOrBlank()) null else runCatching { LocalDate.parse(iso.trim(), ISO) }.getOrNull()

  fun iso(date: LocalDate): String = date.format(ISO)

  fun startOf(activity: Activity): LocalDate? = parse(activity.date)

  fun untilOf(activity: Activity): LocalDate? = parse(activity.repeatUntil)

  /** S11/S12 — whether the activity lands on [date]. A finished one only shows on its own date. */
  fun occursOn(activity: Activity, date: LocalDate): Boolean {
    val start = startOf(activity) ?: return false
    if (date < start) return false
    if (activity.done) return date == start
    val until = untilOf(activity)
    if (until != null && date > until) return false
    return when (activity.repeat) {
      Activity.REPEAT_DAILY -> true
      Activity.REPEAT_WEEKLY -> ChronoUnit.DAYS.between(start, date) % 7 == 0L
      else -> date == start
    }
  }

  /** The next day the series lands on, [from] included; null when it never comes back. */
  fun nextOn(activity: Activity, from: LocalDate): LocalDate? {
    val start = startOf(activity) ?: return null
    if (activity.done) return null
    val until = untilOf(activity)
    var day = if (from.isBefore(start)) start else from
    val limit = until ?: day.plusDays(400)
    while (!day.isAfter(limit)) {
      if (occursOn(activity, day)) return day
      day = day.plusDays(1)
    }
    return null
  }

  /** The next [dayOfWeek] (ISO 1..7) counted from [from], that same day included. */
  fun nextDateFor(dayOfWeek: Int, from: LocalDate): LocalDate {
    val target = dayOfWeek.coerceIn(1, 7)
    var day = from
    repeat(7) {
      if (day.dayOfWeek.value == target) return day
      day = day.plusDays(1)
    }
    return from
  }

  /** "Vie 13 nov" — always the real date, used where "Hoy" would repeat a shortcut. */
  fun shortLabel(date: LocalDate): String =
    date.format(short).replaceFirstChar { it.uppercase() }

  /** "Hoy", "Mañana", "Ayer" or "Vie 13 nov". */
  fun label(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoy"
    today.plusDays(1) -> "Mañana"
    today.minusDays(1) -> "Ayer"
    else -> shortLabel(date)
  }

  /** "Cada día", "Cada 7 días hasta el 20 nov" or "No se repite" — the line under the activity. */
  fun repeatLabel(activity: Activity): String = when (activity.repeat) {
    Activity.REPEAT_DAILY -> "Cada día"
    Activity.REPEAT_WEEKLY -> "Cada 7 días"
    else -> "No se repite"
  }
}
