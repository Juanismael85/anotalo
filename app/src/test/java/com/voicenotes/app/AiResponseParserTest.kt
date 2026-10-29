package com.voicenotes.app

import com.voicenotes.app.ai.AiResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseParserTest {

  @Test
  fun `parses a plain json answer`() {
    val result = AiResponseParser.parse(
      """{"title":"Llamada con Ana","transcript":"Coordinar la entrega.","summary":"Se acordó el lunes.","tags":["trabajo","entrega"]}""",
    )

    assertEquals("Llamada con Ana", result?.title)
    assertEquals("Coordinar la entrega.", result?.transcript)
    assertEquals("Se acordó el lunes.", result?.summary)
    assertEquals(listOf("trabajo", "entrega"), result?.tags)
  }

  @Test
  fun `parses json wrapped in a markdown fence with prose around it`() {
    val raw = """
      Claro, acá está el resultado:
      ```json
      {"title":"","transcript":"Texto limpio.","summary":"Resumen.","tags":["idea"]}
      ```
      Avisame si necesitás algo más.
    """.trimIndent()

    val result = AiResponseParser.parse(raw)

    assertEquals("", result?.title)
    assertEquals("Texto limpio.", result?.transcript)
    assertEquals(listOf("idea"), result?.tags)
  }

  @Test
  fun `returns null when there is no json object`() {
    assertNull(AiResponseParser.parse("No pude procesar el audio."))
    assertNull(AiResponseParser.parse(""))
  }

  @Test
  fun `trims values, drops blank tags and keeps at most four`() {
    val result = AiResponseParser.parse(
      """{"title":"  Reunión  ","transcript":"a","summary":" b ","tags":[" uno ","","dos","tres","cuatro","cinco"]}""",
    )

    assertEquals("Reunión", result?.title)
    assertEquals("b", result?.summary)
    assertEquals(4, result?.tags?.size)
    assertTrue(result?.tags?.first() == "uno")
  }

  // S21 — the model may also answer the reminder of the note
  @Test
  fun `parses the reminder the model detected`() {
    val result = AiResponseParser.parse(
      """{"title":"Turno","transcript":"Es el lunes a las 5.","summary":"Turno.","tags":["salud"],"reminder":"2026-10-27T17:00"}""",
    )

    assertEquals("2026-10-27T17:00", result?.reminder)
  }

  @Test
  fun `a missing or null reminder stays null`() {
    assertNull(
      AiResponseParser.parse("""{"title":"Nota","transcript":"","summary":"","tags":[]}""")?.reminder,
    )
    assertNull(
      AiResponseParser.parse(
        """{"title":"Nota","transcript":"","summary":"","tags":[],"reminder":null}""",
      )?.reminder,
    )
    assertNull(
      AiResponseParser.parse(
        """{"title":"Nota","transcript":"","summary":"","tags":[],"reminder":"null"}""",
      )?.reminder,
    )
  }
}
