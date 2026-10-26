package com.voicenotes.app

import com.voicenotes.app.ai.AiModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The settings picker must offer the chat models the key can use and nothing else. */
class AiModelsTest {

  private val body = """
    {
      "models": [
        {"name": "models/gemini-2.5-pro", "supportedGenerationMethods": ["generateContent", "countTokens"]},
        {"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent"]},
        {"name": "models/gemini-3.5-flash", "supportedGenerationMethods": ["generateContent"]},
        {"name": "models/gemini-2.5-flash-image", "supportedGenerationMethods": ["generateContent"]},
        {"name": "models/gemini-3.8-flash-tts", "supportedGenerationMethods": ["generateContent"]},
        {"name": "models/gemini-3.5-transcribe", "supportedGenerationMethods": ["generateContent"]},
        {"name": "models/gemini-2.5-computer-use-preview-10-2025", "supportedGenerationMethods": ["generateContent"]},
        {"name": "models/deep-research-preview-04-2026", "supportedGenerationMethods": ["generateContent"]},
        {"name": "models/lyria-3.5", "supportedGenerationMethods": ["generateContent"]},
        {"name": "models/gemma-4-26b-a4b-it", "supportedGenerationMethods": ["generateContent"]},
        {"name": "models/gemini-embedding-001", "supportedGenerationMethods": ["embedContent"]},
        {"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent"]}
      ]
    }
  """.trimIndent()

  @Test
  fun `keeps chat models sorted and without duplicates`() {
    val models = AiModels.parse(body)

    assertEquals(
      listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-3.5-flash"),
      models,
    )
  }

  @Test
  fun `drops models that cannot answer a chat request`() {
    val models = AiModels.parse(body).orEmpty()

    assertTrue("gemini-2.5-flash-image" !in models)
    assertTrue("gemini-3.8-flash-tts" !in models)
    assertTrue("gemini-3.5-transcribe" !in models)
    assertTrue("gemini-2.5-computer-use-preview-10-2025" !in models)
    assertTrue("deep-research-preview-04-2026" !in models)
    assertTrue("lyria-3.5" !in models)
    assertTrue("gemma-4-26b-a4b-it" !in models)
    assertTrue("gemini-embedding-001" !in models)
  }

  @Test
  fun `returns null when the body is not the models json`() {
    assertNull(AiModels.parse("<html>error</html>"))
    assertNull(AiModels.parse(""))
  }
}
