package com.voicenotes.app.data

import java.time.LocalDate

/**
 * S10/S11 of the agenda block — the calendar of the app reads the same activities the schedule
 * writes. An activity has a concrete date (S11) plus an optional series, so it lands only on the
 * days it is really due. There is no copy: editing the activity moves it here at once, and deleting
 * it clears the day.
 */
object CalendarAgenda {

  /** The activities that fall on [date], earliest first; the dated series counts as well. */
  fun onDay(activities: List<Activity>, date: LocalDate): List<Activity> =
    activities
      .filter { ActivityDates.occursOn(it, date) }
      .sortedWith(compareBy({ it.startMinutes }, { it.title.lowercase() }))

  /** Whether the month grid paints the day as busy. */
  fun hasOn(activities: List<Activity>, date: LocalDate): Boolean =
    activities.any { ActivityDates.occursOn(it, date) }
}
