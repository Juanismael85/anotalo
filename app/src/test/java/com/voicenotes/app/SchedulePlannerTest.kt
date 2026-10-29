package com.voicenotes.app

import com.voicenotes.app.ai.SchedulePlanner
import com.voicenotes.app.ai.ScheduleSource
import com.voicenotes.app.data.WorkConfig
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S5 — the model's answer only becomes valid activities inside the configured work window. */
class SchedulePlannerTest {

  private val config = WorkConfig(
    dayNumbers = listOf(1, 2, 3, 4, 5),
    startMinutes = 480,
    endMinutes = 1020,
  )

  @Test
  fun `the prompt names the enabled days and the working hours`() {
    val prompt = SchedulePlanner.prompt(config, listOf(ScheduleSource("Manifiesto", "Descarga a las 9")))

    assertTrue(prompt.contains("[1, 2, 3, 4, 5]"))
    assertTrue(prompt.contains("08:00 a 17:00"))
    assertTrue(prompt.contains("[0] Manifiesto — Descarga a las 9"))
  }

  @Test
  fun `a bare array is parsed into planned activities with the note id`() {
    val plan = SchedulePlanner.parse(
      """[{"title":"Descarga cisterna","day":1,"time":"09:00","noteIndex":0}]""",
      config,
      sources = listOf(ScheduleSource("Manifiesto", noteId = "note-1")),
    )

    assertEquals(1, plan.size)
    assertEquals("Descarga cisterna", plan.first().title)
    assertEquals(DayOfWeek.MONDAY.value, plan.first().dayOfWeek)
    assertEquals(7 * 60 + 30 + 90, plan.first().startMinutes)
    assertEquals("note-1", plan.first().noteId)
  }

  @Test
  fun `an activities object is accepted too`() {
    val plan = SchedulePlanner.parse(
      """{"activities":[{"title":"Mantención","day":2,"time":"08:25","noteIndex":null}]}""",
      config,
      sources = emptyList(),
    )

    assertEquals(8 * 60 + 25, plan.single().startMinutes)
    assertTrue(plan.single().noteId == null)
  }

  @Test
  fun `entries outside the enabled days or the working hours are dropped`() {
    val plan = SchedulePlanner.parse(
      """
      [
        {"title":"Antes","day":1,"time":"07:00"},
        {"title":"Después","day":1,"time":"17:00"},
        {"title":"Domingo","day":7,"time":"09:00"},
        {"title":"Válido","day":3,"time":"10:00"}
      ]
      """.trimIndent(),
      config,
      sources = emptyList(),
    )

    assertEquals(listOf("Válido"), plan.map { it.title })
  }

  @Test
  fun `malformed and blank entries are refused silently`() {
    assertTrue(SchedulePlanner.parse("no es json", config, emptyList()).isEmpty())
    assertTrue(SchedulePlanner.parse("""[{"title":" ","day":1,"time":"09:00"}]""", config, emptyList()).isEmpty())
    assertTrue(SchedulePlanner.parse("""[{"title":"Sin forma","day":1,"time":"mañana"}]""", config, emptyList()).isEmpty())
  }

  @Test
  fun `duplicate slots on the same day collapse to one`() {
    val plan = SchedulePlanner.parse(
      """
      [
        {"title":"Reunión","day":1,"time":"10:00"},
        {"title":"reunión","day":1,"time":"10:00"},
        {"title":"Otra","day":1,"time":"11:00"}
      ]
      """.trimIndent(),
      config,
      sources = emptyList(),
    )

    assertEquals(2, plan.size)
  }

  @Test
  fun `a note index beyond the list becomes null and never a crash`() {
    val plan = SchedulePlanner.parse(
      """[{"title":"X","day":1,"time":"09:00","noteIndex":9}]""",
      config,
      sources = listOf(ScheduleSource("A", noteId = "note-a"), ScheduleSource("B", noteId = "note-b")),
    )

    assertTrue(plan.single().noteId == null)
  }
}
