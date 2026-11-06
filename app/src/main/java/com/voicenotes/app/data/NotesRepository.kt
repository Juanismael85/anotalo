package com.voicenotes.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Minimal text storage so the repository stays testable without Android. */
interface Storage {
  fun read(): String?
  fun write(text: String)

  /** Keeps a payload the app could not decode, so no save can destroy it (S10). */
  fun preserveCorrupt(text: String): String? = null
}

/** File-backed storage used by the app (S10). */
class FileStorage(private val file: File) : Storage {
  override fun read(): String? = runCatching { if (file.exists()) file.readText() else null }.getOrNull()

  /**
   * Writes through a sibling temp file so a process killed mid-write cannot leave a truncated
   * `notes.json` behind, which would decode as an empty library.
   */
  override fun write(text: String) {
    runCatching {
      val directory = file.parentFile
      directory?.mkdirs()
      val temp = File(directory, "${file.name}.tmp")
      temp.writeText(text)
      if (!temp.renameTo(file)) {
        file.writeText(text)
        temp.delete()
      }
    }
  }

  /** A copy next to the original, so a later save of `notes.json` cannot erase what was there. */
  override fun preserveCorrupt(text: String): String? = runCatching {
    val directory = file.parentFile ?: return null
    val copy = File(directory, "${file.nameWithoutExtension}.corrupt-${System.currentTimeMillis()}.json")
    copy.writeText(text)
    copy.absolutePath
  }.getOrNull()
}

class NotesRepository(
  private val storage: Storage,
  private val clock: () -> Long = { System.currentTimeMillis() },
) {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    // A null (or a value out of range) in a field we have a default for must not reject the file:
    // rejecting it would start the app empty and the next save would erase the library (S10).
    coerceInputValues = true
  }

  private val _state = MutableStateFlow(Library())
  val state: StateFlow<Library> = _state.asStateFlow()

  private var sequence = 0

  /** Absolute path of the copy kept when the stored file could not be decoded (S10). */
  var loadWarning: String? = null
    private set

  /** Loads persisted data; a corrupt or missing file starts empty (S10). */
  fun load() {
    loadWarning = null
    val raw = storage.read()
    if (raw.isNullOrBlank()) {
      _state.value = Library()
      return
    }
    val decoded = runCatching { json.decodeFromString<Library>(migrateActivities(raw)) }.getOrNull()
    if (decoded != null) {
      _state.value = decoded
      return
    }
    // Starting empty is only safe if the bytes stay recoverable: the next save rewrites the file.
    loadWarning = storage.preserveCorrupt(raw)
    _state.value = Library()
  }

  /** Today in the device zone, through the same clock the rest of the repository uses. */
  private fun todayDate(): LocalDate =
    Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate()

  /**
   * S11 — files written before the activities had a date only carry a weekday. The activity moves to
   * the next date of that weekday and the old weekday repeats are dropped, because the series is
   * dated from now on. A file that already has dates is returned untouched.
   */
  private fun migrateActivities(raw: String): String {
    val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return raw
    val activities = (root["activities"] as? JsonArray) ?: return raw
    var changed = false
    val migrated = activities.map { element ->
      val obj = element.jsonObject
      val current = (obj["date"] as? JsonPrimitive)?.content
      if (!current.isNullOrBlank()) return@map element
      changed = true
      val day = (obj["dayOfWeek"] as? JsonPrimitive)?.intOrNull ?: 1
      JsonObject(
        obj + mapOf(
          "date" to JsonPrimitive(ActivityDates.iso(ActivityDates.nextDateFor(day, todayDate()))),
          "repeat" to JsonPrimitive(Activity.REPEAT_NONE),
          "repeatDays" to JsonArray(emptyList()),
        ),
      )
    }
    if (!changed) return raw
    return json.encodeToString(JsonElement.serializer(), JsonObject(root + ("activities" to JsonArray(migrated))))
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

  // ---- reminders (S20, S25) ----------------------------------------------

  /** Sets or clears the reminder of a note; clearing also drops the reminder state. */
  fun setReminder(noteId: String, at: Long?, fromAi: Boolean = false) {
    commit(
      _state.value.copy(
        notes = _state.value.notes.map { note ->
          if (note.id != noteId) {
            note
          } else {
            note.copy(
              reminderAt = at,
              reminderDone = false,
              reminderFromAi = at != null && fromAi,
              updatedAt = clock(),
            )
          }
        },
      ),
    )
  }

  fun clearReminder(noteId: String) = setReminder(noteId, null)

  /** A fulfilled reminder keeps its date on the note but stops showing up in the radar. */
  fun markReminderDone(noteId: String, done: Boolean = true) {
    commit(
      _state.value.copy(
        notes = _state.value.notes.map { note ->
          if (note.id != noteId || note.reminderAt == null) {
            note
          } else {
            note.copy(reminderDone = done, updatedAt = clock())
          }
        },
      ),
    )
  }

  fun snoozeReminder(noteId: String, until: Long) {
    commit(
      _state.value.copy(
        notes = _state.value.notes.map { note ->
          if (note.id != noteId) {
            note
          } else {
            note.copy(reminderAt = until, reminderDone = false, updatedAt = clock())
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
   * Applies a model result (S19, S21): a blank title or transcript keeps what is already there,
   * tags are matched case-insensitively and created when missing. The reminder is only taken when
   * the note has none, or when the one it has was also proposed by the AI; a manual reminder and a
   * fulfilled one are never overwritten.
   */
  fun applyAiResult(
    noteId: String,
    title: String,
    transcript: String,
    summary: String,
    tagNames: List<String>,
    reminderAt: Long? = null,
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
            val reminderApplies = reminderAt != null &&
              !note.reminderDone &&
              (note.reminderAt == null || note.reminderFromAi)
            note.copy(
              title = if (title.isBlank()) note.title else title.trim(),
              transcript = if (transcript.isBlank()) note.transcript else transcript.trim(),
              summary = if (summary.isBlank()) note.summary else summary.trim(),
              tagIds = (note.tagIds + resolvedIds).distinct(),
              reminderAt = if (reminderApplies) reminderAt else note.reminderAt,
              reminderFromAi = if (reminderApplies) true else note.reminderFromAi,
              reminderDone = if (reminderApplies) false else note.reminderDone,
              aiAppliedAt = clock(),
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
  // ---- done notes (S8 activities feature) -----------------------------------

  /** Marking a note as finished keeps it on the phone; the home screen shows the badge. */
  fun setNoteDone(noteId: String, done: Boolean) {
    commit(
      _state.value.copy(
        notes = _state.value.notes.map {
          if (it.id == noteId) {
            it.copy(done = done, doneAt = if (done) clock() else null, updatedAt = clock())
          } else {
            it
          }
        },
      ),
    )
  }

  // ---- activities (S2, S3, S4) ---------------------------------------------

  fun setWorkConfig(config: WorkConfig?) {
    commit(_state.value.copy(workConfig = config))
  }

  fun createActivity(
    title: String,
    dayOfWeek: Int = 0,
    startMinutes: Int,
    noteId: String? = null,
    durationMinutes: Int = ScheduleRules.DEFAULT_DURATION,
    repeatDays: List<Int> = emptyList(),
    /** S11 — ISO date; blank falls back to the next [dayOfWeek]. */
    date: String = "",
    repeat: String = Activity.REPEAT_NONE,
    repeatUntil: String? = null,
    remind: Boolean = false,
  ): Activity = createActivities(
    listOf(
      NewActivity(
        title = title,
        dayOfWeek = if (dayOfWeek == 0) 1 else dayOfWeek,
        startMinutes = startMinutes,
        date = date,
        durationMinutes = durationMinutes,
        repeat = repeat,
        repeatUntil = repeatUntil,
        remind = remind,
        repeatDays = repeatDays,
        noteId = noteId,
      ),
    ),
  ).first()

  /**
   * S1 (agenda) — one note can hold several tasks, and they belong to the same save: N activities
   * in a single write, so the file is not rewritten N times and the UI updates once.
   */
  fun createActivities(specs: List<NewActivity>): List<Activity> {
    if (specs.isEmpty()) return emptyList()
    val created = specs.map { spec ->
      val date = ActivityDates.parse(spec.date) ?: ActivityDates.nextDateFor(spec.dayOfWeek, todayDate())
      Activity(
        id = newId("act"),
        title = spec.title.trim(),
        date = ActivityDates.iso(date),
        startMinutes = spec.startMinutes.coerceIn(0, 1439),
        source = Activity.SOURCE_MANUAL,
        noteId = spec.noteId,
        durationMinutes = spec.durationMinutes.coerceIn(ScheduleRules.MIN_DURATION, ScheduleRules.MAX_DURATION),
        repeat = spec.repeat,
        repeatUntil = spec.repeatUntil,
        remind = spec.remind,
        dayOfWeek = date.dayOfWeek.value,
        repeatDays = spec.repeatDays.filter { it in 1..7 }.distinct().sorted(),
      )
    }
    commit(_state.value.copy(activities = _state.value.activities + created))
    return created
  }

  /** S2 (agenda) — the same slot with a new day, hour, length or repeat set. */
  fun updateActivity(
    id: String,
    title: String,
    dayOfWeek: Int = 0,
    startMinutes: Int,
    durationMinutes: Int,
    repeatDays: List<Int> = emptyList(),
    /** S11 — ISO date; blank keeps the stored date (or the next [dayOfWeek]). */
    date: String = "",
    repeat: String = Activity.REPEAT_NONE,
    repeatUntil: String? = null,
    remind: Boolean = false,
  ) {
    val previous = _state.value.activities.firstOrNull { it.id == id } ?: return
    val resolved = ActivityDates.parse(date)
      ?: (if (dayOfWeek in 1..7) ActivityDates.nextDateFor(dayOfWeek, todayDate()) else null)
      ?: ActivityDates.startOf(previous)
      ?: return
    commit(
      _state.value.copy(
        activities = _state.value.activities.map {
          if (it.id == id) {
            it.copy(
              title = title.trim(),
              date = ActivityDates.iso(resolved),
              dayOfWeek = resolved.dayOfWeek.value,
              startMinutes = startMinutes.coerceIn(0, 1439),
              durationMinutes = durationMinutes.coerceIn(ScheduleRules.MIN_DURATION, ScheduleRules.MAX_DURATION),
              repeat = repeat,
              repeatUntil = repeatUntil,
              remind = remind,
              repeatDays = repeatDays.filter { day -> day in 1..7 }.distinct().sorted(),
            )
          } else {
            it
          }
        },
      ),
    )
  }

  /** S5 (agenda) — moves the leftovers to the next working day, keeping their hour. */
  fun moveActivities(ids: Collection<String>, dayOfWeek: Int): Int {
    if (ids.isEmpty()) return 0
    var moved = 0
    val target = ActivityDates.nextDateFor(dayOfWeek, todayDate())
    val updated = _state.value.activities.map {
      if (it.id in ids) {
        moved++
        it.copy(date = ActivityDates.iso(target), dayOfWeek = target.dayOfWeek.value)
      } else {
        it
      }
    }
    commit(_state.value.copy(activities = updated))
    return moved
  }

  fun deleteActivity(id: String) {
    commit(_state.value.copy(activities = _state.value.activities.filterNot { it.id == id }))
  }

  fun setActivityDone(id: String, done: Boolean) {
    commit(
      _state.value.copy(
        activities = _state.value.activities.map {
          if (it.id == id) it.copy(done = done, doneAt = if (done) clock() else null) else it
        },
      ),
    )
  }

  /**
   * S5 — the schedule generator writes here. Removes every activity it created before and keeps
   * the manual ones untouched. Each planned entry becomes an owned, id-bearing activity.
   */
  fun replaceAiActivities(planned: List<PlannedActivity>) {
    val kept = _state.value.activities.filter { it.source != Activity.SOURCE_AI }
    val created = planned.map {
      // S11 — the generator still thinks in weekdays; each plan lands on its next real date.
      val date = ActivityDates.nextDateFor(it.dayOfWeek, todayDate())
      Activity(
        id = newId("act"),
        title = it.title.trim(),
        date = ActivityDates.iso(date),
        startMinutes = it.startMinutes.coerceIn(0, 1439),
        source = Activity.SOURCE_AI,
        noteId = it.noteId,
        dayOfWeek = date.dayOfWeek.value,
      )
    }
    commit(_state.value.copy(activities = kept + created))
  }

  // ---- checklists (S7) ------------------------------------------------------

  fun createChecklist(title: String): Checklist {
    val checklist = Checklist(id = newId("list"), title = title.trim(), createdAt = clock())
    commit(_state.value.copy(checklists = _state.value.checklists + checklist))
    return checklist
  }

  fun renameChecklist(id: String, title: String) {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return
    commit(_state.value.copy(checklists = _state.value.checklists.map {
      if (it.id == id) it.copy(title = trimmed) else it
    }))
  }

  fun deleteChecklist(id: String) {
    commit(_state.value.copy(checklists = _state.value.checklists.filterNot { it.id == id }))
  }

  fun addChecklistItem(checklistId: String, text: String): ChecklistItem? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val item = ChecklistItem(id = newId("item"), text = trimmed)
    commit(_state.value.copy(checklists = _state.value.checklists.map {
      if (it.id == checklistId) it.copy(items = it.items + item) else it
    }))
    return item
  }

  fun setChecklistItemDone(checklistId: String, itemId: String, done: Boolean) {
    commit(_state.value.copy(checklists = _state.value.checklists.map {
      if (it.id != checklistId) {
        it
      } else {
        it.copy(
          items = it.items.map { item ->
            if (item.id == itemId) item.copy(done = done) else item
          },
        )
      }
    }))
  }

  fun deleteChecklistItem(checklistId: String, itemId: String) {
    commit(_state.value.copy(checklists = _state.value.checklists.map {
      if (it.id == checklistId) it.copy(items = it.items.filterNot { item -> item.id == itemId }) else it
    }))
  }
}