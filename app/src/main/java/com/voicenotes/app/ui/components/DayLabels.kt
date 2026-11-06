package com.voicenotes.app.ui.components

/** ISO numbering, the same the schedule uses: index 0 is Monday. */
val dayNames = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/** "Mar", "Mié": the three letters the schedule uses in every row. */
fun shortDayName(dayOfWeek: Int): String =
  dayNames.getOrElse(dayOfWeek - 1) { dayNames[0] }
    .replaceFirstChar { it.uppercase() }
    .take(3)

/**
 * S9 of the agenda block — "Hoy" when the day is today, otherwise the short name. The user should
 * not have to remember which number today is, either to pick a day or to read the schedule.
 */
fun dayLabel(dayOfWeek: Int, today: Int): String =
  if (dayOfWeek == today) "Hoy" else shortDayName(dayOfWeek)
