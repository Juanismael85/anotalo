package com.voicenotes.app

import com.voicenotes.app.ui.components.dayLabel
import com.voicenotes.app.ui.components.shortDayName
import org.junit.Assert.assertEquals
import org.junit.Test

/** S9 (agenda) — the "Hoy" shortcut and the label every row uses for a day. */
class DayLabelsTest {

  @Test
  fun `the day of today is labelled hoy`() {
    assertEquals("Hoy", dayLabel(5, today = 5))
  }

  @Test
  fun `another day keeps its short name`() {
    assertEquals("Lun", dayLabel(1, today = 5))
    assertEquals("Dom", dayLabel(7, today = 5))
  }

  @Test
  fun `the short name is still the three letters`() {
    assertEquals("Mié", shortDayName(3))
  }

  @Test
  fun `an out of range day falls back to monday`() {
    assertEquals("Lun", dayLabel(0, today = 5))
  }
}
