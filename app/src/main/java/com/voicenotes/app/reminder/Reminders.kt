package com.voicenotes.app.reminder

import com.voicenotes.app.data.Note
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Turns what the model answered into an epoch, and what the pickers chose into a real local time.
 * Kept free of Android so the rules can be tested on the JVM (S21).
 */
object ReminderTime {

  /**
   * Reads the ISO-8601 value the model returns ("2026-10-27T17:00", with seconds or an offset, or
   * a bare date which means 09:00). An empty, malformed or past value is not a reminder.
   */
  fun parse(raw: String?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty() || text.equals("null", ignoreCase = true)) return null
    val normalized = if (text.length > 10) text.replace(' ', 'T') else text

    val millis = attempt { LocalDateTime.parse(normalized).atZone(zone).toInstant().toEpochMilli() }
      ?: attempt { OffsetDateTime.parse(normalized).toInstant().toEpochMilli() }
      ?: attempt {
        LocalDate.parse(normalized).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
      }
      ?: return null
    // A reminder in the past would only show up as overdue the moment it is created.
    return millis.takeIf { it > now }
  }

  /** The Material date picker reports UTC midnight, so it must be read as a calendar date. */
  fun pickerDate(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

  fun fromPickers(
    dateUtcMillis: Long,
    hour: Int,
    minute: Int,
    zone: ZoneId = ZoneId.systemDefault(),
  ): Long = pickerDate(dateUtcMillis)
    .atTime(hour, minute)
    .atZone(zone)
    .toInstant()
    .toEpochMilli()

  /** Opens the pickers on the next full hour, which is the usual reminder. */
  fun defaultHourMinute(now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
    val local = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
    return ((local.hour + 1) % 24) to 0
  }

  fun localDay(at: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

  fun hourMinute(at: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
    val local = Instant.ofEpochMilli(at).atZone(zone).toLocalTime()
    return local.hour to local.minute
  }

  private fun attempt(block: () -> Long): Long? = runCatching(block).getOrNull()
}

enum class ReminderBucket { OVERDUE, TODAY, TOMORROW, LATER }

/** The radar rules (S22): which reminders are pending and how they group. */
object Reminders {

  fun withReminder(notes: List<Note>): List<Note> = notes.filter { it.reminderAt != null }

  /** Pending means "has a date and is not fulfilled", oldest first. */
  fun pending(notes: List<Note>): List<Note> =
    withReminder(notes)
      .filterNot { it.reminderDone }
      .sortedBy { it.reminderAt ?: Long.MAX_VALUE }

  fun bucket(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): ReminderBucket {
    if (at < now) return ReminderBucket.OVERDUE
    val today = ReminderTime.localDay(now, zone)
    val day = ReminderTime.localDay(at, zone)
    return when (day) {
      today -> ReminderBucket.TODAY
      today.plusDays(1) -> ReminderBucket.TOMORROW
      else -> ReminderBucket.LATER
    }
  }

  fun inBucket(
    notes: List<Note>,
    bucket: ReminderBucket,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
  ): List<Note> = pending(notes).filter { note ->
    note.reminderAt?.let { Reminders.bucket(it, now, zone) == bucket } == true
  }

  /** Reminders on one calendar day, for the calendar screen (S24). */
  fun onDay(
    notes: List<Note>,
    day: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
  ): List<Note> = withReminder(notes)
    .filter { note ->
      note.reminderAt?.let { ReminderTime.localDay(it, zone) == day } == true
    }
    .sortedBy { it.reminderAt ?: Long.MAX_VALUE }
}

/** Labels for the list and the radar, independent of the device locale. */
object ReminderFormat {
  private val shortMonths =
    listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

  fun time(at: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val local = Instant.ofEpochMilli(at).atZone(zone).toLocalTime()
    return "%02d:%02d".format(local.hour, local.minute)
  }

  fun label(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = ReminderTime.localDay(at, zone)
    val today = ReminderTime.localDay(now, zone)
    val hour = time(at, zone)
    return when (day) {
      today.minusDays(1) -> "Ayer $hour"
      today -> "Hoy $hour"
      today.plusDays(1) -> "Mañana $hour"
      else -> "${day.dayOfMonth} ${shortMonths[day.monthValue - 1]} $hour"
    }
  }

  fun dayAndTime(at: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = ReminderTime.localDay(at, zone)
    return "${day.dayOfMonth} ${shortMonths[day.monthValue - 1]} ${time(at, zone)}"
  }

  fun monthTitle(year: Int, month: Int): String = "${fullMonths[month - 1]} $year"

  /** "26 de octubre de 2026" for the calendar's selected day. */
  fun dayLabel(day: LocalDate): String =
    "${day.dayOfMonth} de ${fullMonths[day.monthValue - 1].lowercase()} de ${day.year}"

  private val fullMonths = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
  )
}

/** Month grid for the calendar screen (S24); Monday is the first column. */
object CalendarGrid {
  data class Cell(val date: LocalDate, val inMonth: Boolean)

  fun month(year: Int, month: Int): List<Cell> {
    val first = LocalDate.of(year, month, 1)
    val leading = first.dayOfWeek.value - 1
    val length = first.lengthOfMonth()
    val cells = ((leading + length + 6) / 7) * 7
    return (0 until cells).map { index ->
      val date = first.plusDays((index - leading).toLong())
      Cell(date, date.monthValue == month)
    }
  }
}
