package com.voicenotes.app

import com.voicenotes.app.data.AiBatch
import com.voicenotes.app.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Batch AI — which notes the batch touches and when it has to stop. */
class AiBatchTest {

  private fun note(
    id: String,
    transcript: String = "texto dictado",
    body: String = "",
    summary: String = "",
    audioPath: String? = null,
    reminderFromAi: Boolean = false,
    aiAppliedAt: Long? = null,
  ) = Note(
    id = id,
    title = id,
    body = body,
    transcript = transcript,
    summary = summary,
    audioPath = audioPath,
    reminderFromAi = reminderFromAi,
    aiAppliedAt = aiAppliedAt,
  )

  @Test
  fun `a note that never saw the ai is pending`() {
    val pending = AiBatch.pending(listOf(note("n1")))

    assertEquals(listOf("n1"), pending.map { it.id })
  }

  @Test
  fun `a stamped note is skipped`() {
    val pending = AiBatch.pending(listOf(note("n1", aiAppliedAt = 1_000L)))

    assertTrue(pending.isEmpty())
  }

  @Test
  fun `a note saved before the stamp existed counts as applied when it has a summary`() {
    val pending = AiBatch.pending(listOf(note("vieja", summary = "Resumen que escribió la IA")))

    assertTrue(pending.isEmpty())
  }

  @Test
  fun `a note with an ai reminder counts as applied too`() {
    val pending = AiBatch.pending(listOf(note("vieja", reminderFromAi = true)))

    assertTrue(pending.isEmpty())
  }

  @Test
  fun `a note with nothing to read is left out`() {
    val pending = AiBatch.pending(
      listOf(note("vacia", transcript = "", body = ""), note("audio", transcript = "", audioPath = "/x.m4a")),
    )

    assertEquals(listOf("audio"), pending.map { it.id })
  }

  @Test
  fun `a written note is pending because its text lives in the body`() {
    val pending = AiBatch.pending(listOf(note("escrita", transcript = "", body = "Comprar guantes")))

    assertEquals(listOf("escrita"), pending.map { it.id })
  }

  @Test
  fun `the batch keeps the order of the library`() {
    val pending = AiBatch.pending(
      listOf(note("uno"), note("dos", aiAppliedAt = 5L), note("tres")),
    )

    assertEquals(listOf("uno", "tres"), pending.map { it.id })
  }

  @Test
  fun `a quota or key failure stops the batch`() {
    assertTrue(AiBatch.stopsBatch("Se agotó la cuota de la IA. Probá más tarde."))
    assertTrue(AiBatch.stopsBatch("La clave de API no es válida o el modelo no existe."))
    assertTrue(AiBatch.stopsBatch("Configurá tu clave de API en Ajustes para usar la IA."))
  }

  @Test
  fun `a failure about one note does not stop the batch`() {
    assertFalse(AiBatch.stopsBatch("La IA no devolvió texto. Probá de nuevo."))
    assertFalse(AiBatch.stopsBatch(null))
    assertFalse(AiBatch.stopsBatch(""))
  }

  @Test
  fun `an applied note is recognised without looking at the text`() {
    assertTrue(AiBatch.isApplied(note("n", aiAppliedAt = 7L)))
    assertFalse(AiBatch.isApplied(note("n")))
  }
}
