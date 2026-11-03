package com.voicenotes.app.data

/** How far the batch run is: how many were done, how many there were, and which one is running. */
data class AiBatchProgress(
  val done: Int,
  val total: Int,
  val currentTitle: String,
)

/**
 * Batch AI — which notes still need the AI, and when a failure means the whole batch has to stop.
 * Pure rules so the "already applied" decision is pinned by tests instead of guessed from a blank
 * summary at runtime.
 */
object AiBatch {

  /**
   * A note counts as processed when the AI stamped it, or when it already carries what only the AI
   * writes: a summary or an AI reminder. The second half covers the notes saved before the stamp
   * existed, so the first batch does not reprocess them.
   */
  fun isApplied(note: Note): Boolean =
    note.aiAppliedAt != null || note.summary.isNotBlank() || note.reminderFromAi

  /** Nothing to process without something to read or listen to. */
  fun hasContent(note: Note): Boolean =
    note.transcript.isNotBlank() || note.body.isNotBlank() || note.audioPath != null

  /** The notes the batch will run on, in the same order the library shows them. */
  fun pending(notes: List<Note>): List<Note> =
    notes.filter { !isApplied(it) && hasContent(it) }

  /**
   * A quota or key failure repeats for every remaining note, so the batch stops instead of burning
   * the rest of the quota. Any other failure is about one note and the batch goes on.
   */
  fun stopsBatch(message: String?): Boolean {
    val text = message?.lowercase().orEmpty()
    return text.contains("cuota") || text.contains("clave de api")
  }
}
