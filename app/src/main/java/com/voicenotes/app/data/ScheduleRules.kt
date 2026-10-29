package com.voicenotes.app.data

/**
 * Rules of the agenda block (S5 of the activities block, S2/S5 of the agenda block): which weekdays
 * one activity occupies, when two blocks collide, what was left over from earlier days and where a
 * leftover should move. Pure Kotlin so the JVM tests can pin the behaviour without Android.
 */
object ScheduleRules {

  const val DEFAULT_DURATION = 60
  const val MIN_DURATION = 15
  const val MAX_DURATION = 8 * 60

  /** The anchor day plus every repeat day, without duplicates and only valid ISO weekdays. */
  fun daysOf(activity: Activity): List<Int> =
    (listOf(activity.dayOfWeek) + activity.repeatDays)
      .filter { it in 1..7 }
      .distinct()
      .sorted()

  fun durationOf(activity: Activity): Int =
    activity.durationMinutes.coerceIn(MIN_DURATION, MAX_DURATION)

  /** Minutes from midnight when the block ends, capped at the end of the day. */
  fun endOf(activity: Activity): Int =
    (activity.startMinutes + durationOf(activity)).coerceAtMost(24 * 60)

  data class Clash(
    val dayOfWeek: Int,
    val firstTitle: String,
    val secondTitle: String,
    val start: Int,
    val end: Int,
  )

  /** S2 — the pairs of blocks that share a day and overlap in time. */
  fun clashes(activities: List<Activity>): List<Clash> {
    val result = mutableListOf<Clash>()
    (1..7).forEach { day ->
      val onDay = activities.filter { day in daysOf(it) }.sortedBy { it.startMinutes }
      for (first in onDay.indices) {
        for (second in first + 1 until onDay.size) {
          val a = onDay[first]
          val b = onDay[second]
          if (b.startMinutes < endOf(a)) {
            result += Clash(day, a.title, b.title, b.startMinutes, maxOf(endOf(a), endOf(b)))
          }
        }
      }
    }
    return result.sortedWith(compareBy({ it.dayOfWeek }, { it.start }, { it.firstTitle }))
  }

  /**
   * S5 — blocks from earlier weekdays, or today's already finished ones, that were never marked
   * done. A weekly repeat is not a leftover: it is supposed to come back.
   */
  fun leftovers(activities: List<Activity>, today: Int, nowMinutes: Int): List<Activity> =
    activities
      .filter { activity ->
        !activity.done && activity.repeatDays.isEmpty() &&
          (activity.dayOfWeek < today ||
            (activity.dayOfWeek == today && endOf(activity) <= nowMinutes))
      }
      .sortedWith(compareBy({ it.dayOfWeek }, { it.startMinutes }))

  /** The next enabled workday strictly after [today], wrapping around the week. */
  fun nextWorkday(config: WorkConfig?, today: Int): Int {
    val days = (config?.dayNumbers ?: (1..7).toList())
      .filter { it in 1..7 }
      .distinct()
      .sorted()
    if (days.isEmpty()) return if (today in 1..7) today else 1
    return days.firstOrNull { it > today } ?: days.first()
  }
}
