package com.voicenotes.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Note(
  val id: String,
  val title: String = "",
  val body: String = "",
  val transcript: String = "",
  val summary: String = "",
  val audioPath: String? = null,
  val durationMs: Long = 0L,
  val folderId: String? = null,
  val tagIds: List<String> = emptyList(),
  val createdAt: Long = 0L,
  val updatedAt: Long = 0L,
  /** Epoch millis of the reminder, or null when the note has none (S20). */
  val reminderAt: Long? = null,
  /** A reminder the user already fulfilled stays on the note but leaves the radar (S25). */
  val reminderDone: Boolean = false,
  /** True when the reminder came from the AI, so a manual one is never overwritten (S21). */
  val reminderFromAi: Boolean = false,
  /** When the AI last processed this note; null means it never ran on it (batch AI). */
  val aiAppliedAt: Long? = null,
  /** A note the user marked as finished on the home screen (S8 of activities). */
  val done: Boolean = false,
  val doneAt: Long? = null,
)

@Serializable
data class Folder(val id: String, val name: String, val createdAt: Long = 0L)

@Serializable
data class Tag(val id: String, val name: String, val createdAt: Long = 0L)

/**
 * S3/S4 — which days count as workdays (ISO numbers, 1 = Monday .. 7 = Sunday) and the working
 * hours as minutes from midnight, e.g. 7 a 16 -> 420..960.
 */
@Serializable
data class WorkConfig(
  val dayNumbers: List<Int> = listOf(1, 2, 3, 4, 5),
  val startMinutes: Int = 420,
  val endMinutes: Int = 960,
)

/** S2 — one slot of the weekly activity schedule. */
@Serializable
data class Activity(
  val id: String,
  val title: String,
  /** ISO day number, 1 = Monday .. 7 = Sunday. */
  val dayOfWeek: Int,
  /** Minutes from midnight when the activity starts. */
  val startMinutes: Int,
  /** "manual" or "ai": the AI only replaces its own activities, never the manual ones. */
  val source: String = SOURCE_MANUAL,
  val noteId: String? = null,
  val done: Boolean = false,
  val doneAt: Long? = null,
  /** S2 (agenda) — how long the block lasts; a point in time is not a plan. */
  val durationMinutes: Int = 60,
  /** S2 (agenda) — other weekdays it repeats on besides [dayOfWeek]. Empty means a one-off. */
  val repeatDays: List<Int> = emptyList(),
) {
  companion object {
    const val SOURCE_MANUAL = "manual"
    const val SOURCE_AI = "ai"
  }
}

/** What the schedule generator returns before ids are assigned. */
data class PlannedActivity(
  val title: String,
  val dayOfWeek: Int,
  val startMinutes: Int,
  val noteId: String? = null,
)

@Serializable
data class ChecklistItem(
  val id: String,
  val text: String,
  val done: Boolean = false,
)

@Serializable
data class Checklist(
  val id: String,
  val title: String,
  val items: List<ChecklistItem> = emptyList(),
  val createdAt: Long = 0L,
)

@Serializable
data class Library(
  val notes: List<Note> = emptyList(),
  val folders: List<Folder> = emptyList(),
  val tags: List<Tag> = emptyList(),
  val activities: List<Activity> = emptyList(),
  val checklists: List<Checklist> = emptyList(),
  val workConfig: WorkConfig? = null,
)
