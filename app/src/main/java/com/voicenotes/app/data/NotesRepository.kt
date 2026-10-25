package com.voicenotes.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Minimal text storage so the repository stays testable without Android. */
interface Storage {
  fun read(): String?
  fun write(text: String)
}

/** File-backed storage used by the app (S10). */
class FileStorage(private val file: File) : Storage {
  override fun read(): String? = runCatching { if (file.exists()) file.readText() else null }.getOrNull()

  override fun write(text: String) {
    runCatching {
      file.parentFile?.mkdirs()
      file.writeText(text)
    }
  }
}

class NotesRepository(
  private val storage: Storage,
  private val clock: () -> Long = { System.currentTimeMillis() },
) {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private val _state = MutableStateFlow(Library())
  val state: StateFlow<Library> = _state.asStateFlow()

  private var sequence = 0

  /** Loads persisted data; a corrupt or missing file starts empty (S10). */
  fun load() {
    val raw = storage.read()
    _state.value = if (raw.isNullOrBlank()) {
      Library()
    } else {
      runCatching { json.decodeFromString<Library>(raw) }.getOrDefault(Library())
    }
  }

  private fun commit(next: Library) {
    _state.value = next
    runCatching { storage.write(json.encodeToString(next)) }
  }

  private fun newId(prefix: String): String {
    sequence += 1
    return "$prefix-${clock()}-$sequence"
  }

  // ---- notes -------------------------------------------------------------

  fun createTextNote(title: String, body: String, folderId: String? = null): Note {
    val now = clock()
    val note = Note(
      id = newId("note"),
      title = title,
      body = body,
      folderId = folderId,
      createdAt = now,
      updatedAt = now,
    )
    commit(_state.value.copy(notes = listOf(note) + _state.value.notes))
    return note
  }

  fun createAudioNote(audioPath: String, durationMs: Long, folderId: String? = null): Note {
    val now = clock()
    val note = Note(
      id = newId("note"),
      audioPath = audioPath,
      durationMs = durationMs,
      folderId = folderId,
      createdAt = now,
      updatedAt = now,
    )
    commit(_state.value.copy(notes = listOf(note) + _state.value.notes))
    return note
  }

  fun updateNote(id: String, title: String, body: String) {
    commit(
      _state.value.copy(
        notes = _state.value.notes.map {
          if (it.id == id) it.copy(title = title, body = body, updatedAt = clock()) else it
        },
      ),
    )
  }

  fun appendTranscript(id: String, text: String) {
    val addition = text.trim()
    if (addition.isEmpty()) return
    commit(
      _state.value.copy(
        notes = _state.value.notes.map {
          if (it.id != id) it
          else {
            val merged = if (it.transcript.isBlank()) addition else "${it.transcript} $addition"
            it.copy(transcript = merged, updatedAt = clock())
          }
        },
      ),
    )
  }

  fun deleteNote(id: String) {
    commit(_state.value.copy(notes = _state.value.notes.filterNot { it.id == id }))
  }

  fun assignFolder(noteId: String, folderId: String?) {
    commit(
      _state.value.copy(
        notes = _state.value.notes.map {
          if (it.id == noteId) it.copy(folderId = folderId, updatedAt = clock()) else it
        },
      ),
    )
  }

  fun toggleTag(noteId: String, tagId: String) {
    commit(
      _state.value.copy(
        notes = _state.value.notes.map { note ->
          if (note.id != noteId) note
          else {
            val next = if (note.tagIds.contains(tagId)) note.tagIds - tagId else note.tagIds + tagId
            note.copy(tagIds = next, updatedAt = clock())
          }
        },
      ),
    )
  }

  // ---- folders and tags --------------------------------------------------

  fun createFolder(name: String): Folder {
    val trimmed = name.trim().ifBlank { "Carpeta" }
    val folder = Folder(id = newId("folder"), name = trimmed, createdAt = clock())
    commit(_state.value.copy(folders = _state.value.folders + folder))
    return folder
  }

  fun createTag(name: String): Tag {
    val trimmed = name.trim().ifBlank { "etiqueta" }
    val tag = Tag(id = newId("tag"), name = trimmed, createdAt = clock())
    commit(_state.value.copy(tags = _state.value.tags + tag))
    return tag
  }

  // ---- queries -----------------------------------------------------------

  /** Applies folder filter (S7), tag filter (S8) and text search (S9). */
  fun visibleNotes(folderId: String?, tagId: String?, query: String): List<Note> {
    val needle = query.trim().lowercase()
    return _state.value.notes.filter { note ->
      val folderOk = folderId == null || note.folderId == folderId
      val tagOk = tagId == null || note.tagIds.contains(tagId)
      val queryOk = needle.isEmpty() || matches(note, needle)
      folderOk && tagOk && queryOk
    }
  }

  private fun matches(note: Note, needle: String): Boolean =
    note.title.lowercase().contains(needle) ||
      note.body.lowercase().contains(needle) ||
      note.transcript.lowercase().contains(needle)

  fun displayTitle(note: Note): String = note.title.trim().ifBlank { "Sin título" }
}
