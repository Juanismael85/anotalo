package com.voicenotes.app

import com.voicenotes.app.ai.ActivitySuggestion
import com.voicenotes.app.ai.LocalSchedulePlanner
import com.voicenotes.app.ai.ScheduleSource
import com.voicenotes.app.ai.TaskExtractor
import com.voicenotes.app.ai.TaskText
import com.voicenotes.app.data.WorkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S1 and S3 of the agenda block — reading tasks out of a note and planning without the AI. */
class LocalPlanningTest {

  private val mondayToFriday = WorkConfig(dayNumbers = listOf(1, 2, 3, 4, 5), startMinutes = 420, endMinutes = 960)

  // ---- reading the text -----------------------------------------------------

  @Test
  fun `a task is recognised by its verb or by a marker`() {
    assertTrue(TaskText.actionable("Mañana hay que limpiar los quirófanos"))
    assertTrue(TaskText.actionable("Limpiamos los pisos al final"))
    assertTrue(TaskText.actionable("Instalar las lámparas"))
    assertTrue(TaskText.actionable("Necesito comprar guantes"))
    assertFalse(TaskText.actionable("Buenas tardes, estimado"))
    assertFalse(TaskText.actionable(""))
  }

  @Test
  fun `the title keeps the task and drops the scene`() {
    assertEquals("Limpiar los quirófanos", TaskText.short("Para mañana hay que limpiar los quirófanos."))
    assertEquals("Comprar guantes", TaskText.short("tengo que comprar guantes"))
    assertEquals("Instalar las lámparas", TaskText.short("Instalar las lámparas"))
  }

  @Test
  fun `a long sentence is cut at a word boundary`() {
    val long = "Revisar el inventario de la sala de espera del piso tres y anotar lo que falta de material"
    val short = TaskText.short(long)

    assertTrue(short.length <= 60)
    assertFalse(short.endsWith(" "))
    assertTrue(long.startsWith(short.dropLast(1).take(20)))
  }

  @Test
  fun `the day comes from the words the user said`() {
    assertEquals(4, TaskText.dayHint("mañana a las 8", today = 3))
    assertEquals(5, TaskText.dayHint("pasado mañana", today = 3))
    assertEquals(3, TaskText.dayHint("hoy mismo", today = 3))
    assertEquals(1, TaskText.dayHint("el lunes temprano", today = 3))
    assertEquals(7, TaskText.dayHint("el domingo de la fiesta", today = 3))
    assertNull(TaskText.dayHint("en la mañana revisamos todo", today = 3))
    assertNull(TaskText.dayHint("no dice cuándo", today = 3))
  }

  @Test
  fun `the hour only comes from an explicit hint`() {
    assertEquals(480, TaskText.timeHint("a las 8 en punto"))
    assertEquals(510, TaskText.timeHint("8:30 en la sala"))
    assertEquals(1080, TaskText.timeHint("18 hs"))
    assertNull(TaskText.timeHint("el 26 de octubre"))
    assertNull(TaskText.timeHint("comprar 12 guantes"))
  }

  @Test
  fun `a note yields its actionable sentences without duplicates`() {
    val tasks = TaskText.tasks(
      title = "Limpieza",
      summary = "Hay que limpiar los quirófanos. Comprar guantes.",
      transcript = "hay que limpiar los quirófanos",
    )

    assertEquals(listOf("Limpiar los quirófanos", "Comprar guantes"), tasks)
  }

  // ---- the fallback planner -------------------------------------------------

  private val sources = listOf(
    ScheduleSource("Limpieza", "Hay que limpiar los quirófanos", "", "n1"),
    ScheduleSource("Lámparas", "Instalar las lámparas de la sala de espera", "", "n2"),
    ScheduleSource("Fiesta", "Comprar los globos", "", "n3"),
  )

  @Test
  fun `the fallback fills the first day from the start of the working hours`() {
    val plan = LocalSchedulePlanner.plan(mondayToFriday, sources)

    assertEquals(3, plan.size)
    assertEquals(1, plan[0].dayOfWeek)
    assertEquals(420, plan[0].startMinutes)
    assertEquals(480, plan[1].startMinutes)
    assertEquals("n1", plan[0].noteId)
  }

  @Test
  fun `the fallback never plans outside the working hours`() {
    val short = WorkConfig(dayNumbers = listOf(1), startMinutes = 420, endMinutes = 480)
    val plan = LocalSchedulePlanner.plan(short, sources)

    assertEquals(1, plan.size)
    plan.forEach { assertTrue(it.startMinutes >= 420 && it.startMinutes + 60 <= 480) }
  }

  @Test
  fun `the fallback spreads the tasks over the configured days`() {
    val twoHours = WorkConfig(dayNumbers = listOf(1, 2), startMinutes = 420, endMinutes = 540)
    val plan = LocalSchedulePlanner.plan(twoHours, sources)

    assertEquals(listOf(1, 1, 2), plan.map { it.dayOfWeek })
  }

  @Test
  fun `the fallback is deterministic`() {
    assertEquals(LocalSchedulePlanner.plan(mondayToFriday, sources), LocalSchedulePlanner.plan(mondayToFriday, sources))
  }

  @Test
  fun `the fallback does nothing without days or hours`() {
    assertTrue(LocalSchedulePlanner.plan(WorkConfig(dayNumbers = emptyList()), sources).isEmpty())
    assertTrue(LocalSchedulePlanner.plan(WorkConfig(startMinutes = 480, endMinutes = 480), sources).isEmpty())
    assertTrue(LocalSchedulePlanner.plan(mondayToFriday, emptyList()).isEmpty())
  }

  // ---- the suggestion for one note ------------------------------------------

  @Test
  fun `the suggestion takes the task, the day and the hour from the note`() {
    val suggestion = TaskExtractor.suggest(
      title = "Limpieza de los quirófanos",
      summary = "Para mañana a las 8 hay que limpiar los quirófanos.",
      transcript = "",
      config = mondayToFriday,
      today = 3,
    )

    assertEquals(ActivitySuggestion("Limpiar los quirófanos", 4, 480, 60), suggestion)
  }

  @Test
  fun `without hints the suggestion lands on today at the start hour`() {
    val suggestion = TaskExtractor.suggest(
      title = "Instalar las lámparas",
      summary = "",
      transcript = "",
      config = mondayToFriday,
      today = 5,
    )

    assertEquals(5, suggestion.dayOfWeek)
    assertEquals(420, suggestion.startMinutes)
  }

  @Test
  fun `without a work config the suggestion still holds a sane default`() {
    val suggestion = TaskExtractor.suggest("Comprar guantes", "", "", config = null, today = 2)

    assertEquals(2, suggestion.dayOfWeek)
    assertEquals(9 * 60, suggestion.startMinutes)
    assertEquals("Comprar guantes", suggestion.title)
  }

  @Test
  fun `a note with no task at all still offers its title`() {
    val suggestion = TaskExtractor.suggest(
      title = "Reunión del comité",
      summary = "Estuvimos hablando del presupuesto",
      transcript = "",
      config = mondayToFriday,
      today = 1,
    )

    assertEquals("Reunión del comité", suggestion.title)
    assertEquals(1, suggestion.dayOfWeek)
  }
}
