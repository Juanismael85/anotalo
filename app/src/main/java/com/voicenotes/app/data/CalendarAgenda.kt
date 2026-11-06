package com.voicenotes.app.data

import java.time.LocalDate

/**
 * S10 of the agenda block — the calendar of the app reads the same activities the schedule writes.
 * An activity has a weekday, not a date, so it lands on every date that weekday covers (its own day
 * plus the repeat days). There is no copy: editing the activity moves it here at once, and deleting
 * it clears the day.
 */
object CalendarAgenda {

  /** The activities that fall on [date], earliest first; the repeat days count as well. */
  fun onDay(activities: List<Activity>, date: LocalDate): List<Activity> {
    val weekday = date.dayOfWeek.value
    return activities
      .filter { weekday in ScheduleRules.daysOf(it) }
      .sortedWith(compareBy({ it.startMinutes }, { it.title.lowercase() }))
  }

  /** Whether the month grid paints the day as busy. */
  fun hasOn(activities: List<Activity>, date: LocalDate): Boolean {
    val weekday = date.dayOfWeek.value
    return activities.any { weekday in ScheduleRules.daysOf(it) }
  }
}
