package com.voicenotes.app

import com.voicenotes.app.ai.LocalSchedulePlanner
import com.voicenotes.app.ai.SchedulePlanner
import com.voicenotes.app.ai.ScheduleSource
import com.voicenotes.app.ai.TaskExtractor
import com.voicenotes.app.data.ScheduleRules
import com.voicenotes.app.data.WorkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S8 (agenda) — el cronograma también se puede armar para hoy. Hasta ahora todo caía siempre en el
 * día hábil siguiente, así que un viernes a la mañana no aparecía nada para el viernes.
 */
class TodayScheduleTest {

  private val mondayToFriday = WorkConfig(
    dayNumbers = listOf(1, 2, 3, 4, 5),
    startMinutes = 420,
    endMinutes = 960,
  )

  // ---- qué día se usa --------------------------------------------------------

  @Test
  fun `today counts when it is a workday and the day is not over`() {
    assertEquals(5, ScheduleRules.startDay(mondayToFriday, today = 5, nowMinutes = 600))
  }

  @Test
  fun `a finished day moves to the next workday`() {
    assertEquals(1, ScheduleRules.startDay(mondayToFriday, today = 5, nowMinutes = 1000))
  }

  @Test
  fun `a day that is not a workday moves to the next one`() {
    assertEquals(1, ScheduleRules.startDay(mondayToFriday, today = 6, nowMinutes = 600))
  }

  @Test
  fun `turning today off keeps the old behaviour`() {
    assertEquals(
      1,
      ScheduleRules.startDay(mondayToFriday, today = 5, nowMinutes = 600, includeToday = false),
    )
  }

  // ---- el cronograma generado ------------------------------------------------

  @Test
  fun `the weekly plan starts today`() {
    val plan = LocalSchedulePlanner.plan(
      mondayToFriday,
      listOf(ScheduleSource("Revisar el stock", noteId = "n1")),
      today = 5,
      nowMinutes = 600,
    )

    assertEquals(1, plan.size)
    assertEquals(5, plan.first().dayOfWeek)
    assertEquals(420, plan.first().startMinutes)
  }

  @Test
  fun `without today the plan starts on the first workday`() {
    val plan = LocalSchedulePlanner.plan(
      mondayToFriday,
      listOf(ScheduleSource("Revisar el stock", noteId = "n1")),
      today = 5,
      nowMinutes = 600,
      includeToday = false,
    )

    assertEquals(1, plan.first().dayOfWeek)
  }

  @Test
  fun `a plan made after closing time starts on the next workday`() {
    val plan = LocalSchedulePlanner.plan(
      mondayToFriday,
      listOf(ScheduleSource("Revisar el stock", noteId = "n1")),
      today = 5,
      nowMinutes = 1000,
    )

    assertEquals(1, plan.first().dayOfWeek)
  }

  @Test
  fun `the day after today still fits before wrapping to monday`() {
    val plan = LocalSchedulePlanner.plan(
      mondayToFriday,
      listOf(
        ScheduleSource("Revisar el stock", noteId = "n1"),
        ScheduleSource("Limpiar los pisos", noteId = "n2"),
      ),
      today = 5,
      nowMinutes = 600,
    )

    assertEquals(5, plan.first().dayOfWeek)
  }

  // ---- la sugerencia de una nota --------------------------------------------

  @Test
  fun `a note without hints is proposed for today when there is time`() {
    val suggestion = TaskExtractor.suggest(
      title = "Instalar las lámparas",
      summary = "",
      transcript = "",
      config = mondayToFriday,
      today = 5,
    )

    assertEquals(5, suggestion.dayOfWeek)
  }

  @Test
  fun `a note suggested after closing time goes to the next workday`() {
    val suggestion = TaskExtractor.suggest(
      title = "Instalar las lámparas",
      summary = "",
      transcript = "",
      config = mondayToFriday,
      today = 5,
      nowMinutes = 1000,
    )

    assertEquals(1, suggestion.dayOfWeek)
  }

  // ---- el pedido a la IA -----------------------------------------------------

  @Test
  fun `the prompt tells the model to start today`() {
    val prompt = SchedulePlanner.prompt(
      mondayToFriday,
      listOf(ScheduleSource("Revisar el stock")),
      today = 5,
    )

    assertTrue("el prompt nombra el día de hoy", prompt.contains("viernes"))
    assertTrue("el prompt pide empezar hoy", prompt.contains("hoy"))
  }

  @Test
  fun `the prompt asks for the next workday when today is off`() {
    val prompt = SchedulePlanner.prompt(
      mondayToFriday,
      listOf(ScheduleSource("Revisar el stock")),
      today = 5,
      includeToday = false,
    )

    assertTrue(prompt.contains("hoy") || prompt.contains("siguiente"))
  }
}
