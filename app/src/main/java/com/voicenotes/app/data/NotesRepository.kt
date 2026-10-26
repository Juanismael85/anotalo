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

  // ---- backup and restore (S16) -----------------------------------------

  fun snapshotJson(): String = json.encodeToString(_state.value)

  /** Replaces the whole library with a validated backup; garbage leaves the current state intact. */
  fun replaceWithJson(raw: String): Boolean {
    val decoded = runCatching { json.decodeFromString<Library>(raw) }.getOrNull() ?: return false
    commit(decoded)
    return true
  }

  /** Audio paths are absolute; a restore on another install must point at the current folder. */
  fun rebaseAudioPaths(audioDir: String) {
    val dir = audioDir.trimEnd('/')
    commit(
      _state.value.copy(
        notes = _state.value.notes.map { note ->
          val path = note.audioPath
          if (path == null) {
            note
          } else {
            note.copy(audioPath = "$dir/" + File(path).name)
          }
        },
      ),
    )
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

  fun renameFolder(id: String, name: String) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    commit(
      _state.value.copy(
        folders = _state.value.folders.map {
          if (it.id == id) it.copy(name = trimmed) else it
        },
      ),
    )
  }

  /** Deleting a folder keeps its notes, only unfiling them. */
  fun deleteFolder(id: String) {
    commit(
      _state.value.copy(
        folders = _state.value.folders.filterNot { it.id == id },
        notes = _state.value.notes.map {
          if (it.folderId == id) it.copy(folderId = null, updatedAt = clock()) else it
        },
      ),
    )
  }

  fun renameTag(id: String, name: String) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    commit(
      _state.value.copy(
        tags = _state.value.tags.map { if (it.id == id) it.copy(name = trimmed) else it },
      ),
    )
  }

  /** Deleting a tag detaches it from every note. */
  fun deleteTag(id: String) {
    commit(
      _state.value.copy(
        tags = _state.value.tags.filterNot { it.id == id },
        notes = _state.value.notes.map {
          if (it.tagIds.contains(id)) {
            it.copy(tagIds = it.tagIds - id, updatedAt = clock())
          } else {
            it
          }
        },
      ),
    )
  }

  /**
   * Applies a model result (S19): a blank title or transcript keeps what is already there,
   * tags are matched case-insensitively and created when missing.
   */
  fun applyAiResult(
    noteId: String,
    title: String,
    transcript: String,
    summary: String,
    tagNames: List<String>,
  ) {
    var tags = _state.value.tags
    val resolvedIds = tagNames.mapNotNull { raw ->
      val name = raw.trim()
      if (name.isEmpty()) {
        null
      } else {
        val existing = tags.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
          existing.id
        } else {
          val created = Tag(id = newId("tag"), name = name, createdAt = clock())
          tags = tags + created
          created.id
        }
      }
    }

    commit(
      _state.value.copy(
        tags = tags,
        notes = _state.value.notes.map { note ->
          if (note.id != noteId) {
            note
          } else {
            note.copy(
              title = if (title.isBlank()) note.title else title.trim(),
              transcript = if (transcript.isBlank()) note.transcript else transcript.trim(),
              summary = if (summary.isBlank()) note.summary else summary.trim(),
              tagIds = (note.tagIds + resolvedIds).distinct(),
              updatedAt = clock(),
            )
          }
        },
      ),
    )
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
      note.transcript.lowercase().contains(needle) ||
      note.summary.lowercase().contains(needle)

  fun displayTitle(note: Note): String = note.title.trim().ifBlank { "Sin título" }
}
