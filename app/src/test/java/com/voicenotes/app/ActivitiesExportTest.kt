package com.voicenotes.app

import com.voicenotes.app.data.Activity
import com.voicenotes.app.data.Checklist
import com.voicenotes.app.data.ChecklistItem
import com.voicenotes.app.data.WorkConfig
import com.voicenotes.app.export.ChecklistExporter
import com.voicenotes.app.export.ScheduleExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S6/S7 — the schedule and checklist exports are the text the share sheet receives. */
class ActivitiesExportTest {

  private fun activity(
    title: String,
    day: Int,
    minutes: Int,
    done: Boolean = false,
  ) = Activity(
    id = "act-${title.hashCode()}-$day",
    title = title,
    dayOfWeek = day,
    startMinutes = minutes,
    done = done,
  )

  @Test
  fun `schedule groups by day in week order with times and done marks`() {
    val text = ScheduleExporter.plainText(
      listOf(
        activity("Fiesta de personal", 2, 11 * 60, done = true),
        activity("Limpiar quirófanos", 1, 8 * 60),
      ),
      WorkConfig(dayNumbers = listOf(1, 2, 3, 4, 5), startMinutes = 7 * 60, endMinutes = 16 * 60),
    )

    val lunes = text.indexOf("Lunes")
    val martes = text.indexOf("Martes")
    assertTrue("Monday comes before Tuesday", lunes in 0..<martes)
    assertTrue("working hours are exported", text.contains("07:00 a 16:00"))
    assertTrue("pending entries use an empty box", text.contains("[ ] 08:00  Limpiar quirófanos"))
    assertTrue("done entries use a checked box", text.contains("[x] 11:00  Fiesta de personal"))
    assertFalse(
      "days without activities are omitted",
      text.lines().any { it.trim() == "Miércoles" },
    )
  }

  @Test
  fun `schedule markdown keeps the day headings and the same time format`() {
    val text = ScheduleExporter.markdown(
      listOf(activity("Comprar vasos", 3, 9 * 60 + 30)),
      null,
    )

    assertTrue(text.startsWith("# Cronograma semanal"))
    assertTrue(text.contains("## Miércoles"))
    assertTrue(text.contains("- [ ] 09:30 Comprar vasos"))
    assertFalse("no working hours when unset", text.contains("Horario de trabajo"))
  }

  @Test
  fun `schedule export orders activities inside the day by time`() {
    val text = ScheduleExporter.plainText(
      listOf(
        activity("Tarde", 1, 15 * 60),
        activity("Mañana", 1, 8 * 60),
      ),
      null,
    )

    assertTrue(text.indexOf("Mañana") < text.indexOf("Tarde"))
  }

  @Test
  fun `checklist export counts progress and marks finished items`() {
    val checklist = Checklist(
      id = "list-1",
      title = "Compras",
      items = listOf(
        ChecklistItem("i1", "Refresco", done = true),
        ChecklistItem("i2", "Torta", done = false),
      ),
    )

    val text = ChecklistExporter.plainText(checklist)
    assertTrue(text.startsWith("Compras"))
    assertTrue(text.contains("1 de 2 listos"))
    assertTrue(text.contains("[x] Refresco"))
    assertTrue(text.contains("[ ] Torta"))

    val markdown = ChecklistExporter.markdown(checklist)
    assertTrue(markdown.startsWith("# Compras"))
    assertTrue(markdown.contains("- [x] Refresco"))
  }

  @Test
  fun `checklist file names are lowercase and safe`() {
    val checklist = Checklist(id = "list-1", title = "Compras / Quirófano 3")
    assertEquals("compras-quirófano-3.txt", ChecklistExporter.fileName(checklist, "txt"))
    assertEquals(
      "compras-quirófano-3.md",
      ChecklistExporter.fileName(checklist.copy(title = "  COMPRAS   Quirófano 3 "), "md"),
    )
    assertEquals("checklist.txt", ChecklistExporter.fileName(checklist.copy(title = "///"), "txt"))
  }

  @Test
  fun `schedule file name is stable`() {
    assertEquals("cronograma-semanal.txt", ScheduleExporter.fileName("txt"))
  }
}
