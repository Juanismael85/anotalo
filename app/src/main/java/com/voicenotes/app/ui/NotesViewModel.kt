package com.voicenotes.app.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicenotes.app.ai.AiClient
import com.voicenotes.app.ai.ScheduleSource
import java.time.LocalDate
import com.voicenotes.app.data.ScheduleRules
import com.voicenotes.app.ai.TaskExtractor
import com.voicenotes.app.ai.LocalSchedulePlanner
import com.voicenotes.app.ai.AiException
import com.voicenotes.app.ai.GeminiClient
import com.voicenotes.app.audio.RecordingService
import com.voicenotes.app.audio.RecordingSession
import com.voicenotes.app.data.BackupArchive
import com.voicenotes.app.data.Folder
import com.voicenotes.app.data.Note
import com.voicenotes.app.data.LibraryStore
import com.voicenotes.app.data.Tag
import com.voicenotes.app.reminder.DailyAgendaScheduler
import com.voicenotes.app.reminder.ReminderScheduler
import com.voicenotes.app.reminder.ReminderTime
import com.voicenotes.app.reminder.Reminders
import com.voicenotes.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class NotesViewModel(application: Application) : AndroidViewModel(application) {

  /** Swappable so a fake client can replace it without Android in the constructor. */
  internal var aiClient: AiClient = GeminiClient()

  // Shared with the reminder receiver so both write the same in-memory library (S23).
  private val repository = LibraryStore.get(application)

  private val scheduler = ReminderScheduler(application)

  private val _query = MutableStateFlow("")
  val query: StateFlow<String> = _query.asStateFlow()

  private val _folderFilter = MutableStateFlow<String?>(null)
  val folderFilter: StateFlow<String?> = _folderFilter.asStateFlow()

  private val _tagFilter = MutableStateFlow<String?>(null)
  val tagFilter: StateFlow<String?> = _tagFilter.asStateFlow()

  private val _recording = MutableStateFlow(false)
  val recording: StateFlow<Boolean> = _recording.asStateFlow()

  private val preferences =
    application.getSharedPreferences("voice_notes_settings", android.content.Context.MODE_PRIVATE)

  private val _themeMode = MutableStateFlow(
    ThemeMode.fromKey(preferences.getString(KEY_THEME, null)),
  )
  val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

  private val _apiKey = MutableStateFlow(preferences.getString(KEY_API, "").orEmpty())
  val apiKey: StateFlow<String> = _apiKey.asStateFlow()

  private val _aiModel = MutableStateFlow(
    preferences.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL,
  )
  val aiModel: StateFlow<String> = _aiModel.asStateFlow()

  private val _aiRunning = MutableStateFlow(false)
  val aiRunning: StateFlow<Boolean> = _aiRunning.asStateFlow()

  private val _aiStatus = MutableStateFlow<String?>(null)
  val aiStatus: StateFlow<String?> = _aiStatus.asStateFlow()

  private val _aiError = MutableStateFlow<String?>(null)
  val aiError: StateFlow<String?> = _aiError.asStateFlow()

  val aiConfigured: StateFlow<Boolean> = _apiKey
    .map { it.isNotBlank() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  private var recordingFolder: String? = null

  private val _aiModels = MutableStateFlow<List<String>>(emptyList())
  val aiModels: StateFlow<List<String>> = _aiModels.asStateFlow()

  private val _modelsLoading = MutableStateFlow(false)
  val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

  private val _modelsError = MutableStateFlow<String?>(null)
  val modelsError: StateFlow<String?> = _modelsError.asStateFlow()

  init {
    importOrphanRecordings()
    // Alarms are lost on reboot, update or battery cleanup; opening the app re-arms them.
    scheduler.rescheduleAll(repository.state.value.notes)
    val agendaScheduler = DailyAgendaScheduler(application)
    agendaScheduler.schedule(repository.state.value.workConfig)
    viewModelScope.launch {
      repository.state.map { it.workConfig }.distinctUntilChanged().collect { config ->
        agendaScheduler.schedule(config)
      }
    }
    viewModelScope.launch {
      RecordingSession.result.collect { finished ->
        if (finished != null) {
          repository.createAudioNote(finished.path, finished.durationMs, recordingFolder)
          RecordingSession.consumeResult()
        }
      }
    }
    viewModelScope.launch {
      RecordingSession.active.collect { _recording.value = it }
    }
  }

  // ---- background recording (S14) ---------------------------------------

  fun startRecording() {
    recordingFolder = _folderFilter.value
    RecordingSession.setError(null)
    RecordingSession.consumeResult()
    RecordingService.start(getApplication())
  }

  fun stopRecording() {
    RecordingService.stop(getApplication())
  }

  fun cancelRecording() {
    RecordingService.cancel(getApplication())
  }

  fun setThemeMode(mode: ThemeMode) {
    _themeMode.value = mode
    preferences.edit().putString(KEY_THEME, mode.key).apply()
  }

  fun setApiKey(value: String) {
    _apiKey.value = value
    preferences.edit().putString(KEY_API, value.trim()).apply()
  }

  fun setAiModel(value: String) {
    val clean = value.trim().ifBlank { DEFAULT_MODEL }
    _aiModel.value = clean
    preferences.edit().putString(KEY_MODEL, clean).apply()
  }

  fun clearAiFeedback() {
    _aiStatus.value = null
    _aiError.value = null
  }

  /** Model list for the drawer picker; empty means "not loaded yet". */
  fun loadAiModels() {
    val key = _apiKey.value.trim()
    if (key.isEmpty() || _modelsLoading.value) return
    _modelsLoading.value = true
    _modelsError.value = null
    viewModelScope.launch {
      runCatching { aiClient.listModels(key) }
        .onSuccess { models ->
          _aiModels.value = models
          // A stored model the key cannot use would fail every request; fall back to the default.
          if (models.isNotEmpty() && _aiModel.value !in models) setAiModel(DEFAULT_MODEL)
        }
        .onFailure { _modelsError.value = it.message ?: "No pude traer la lista de modelos." }
      _modelsLoading.value = false
    }
  }

  /** Cheap key check used by the settings drawer (S18). */
  fun checkAiConnection() {
    val key = _apiKey.value.trim()
    if (key.isEmpty()) {
      _aiError.value = "Escribí una clave de API."
      return
    }
    _aiRunning.value = true
    _aiStatus.value = null
    _aiError.value = null
    viewModelScope.launch {
      val result = aiClient.check(key, _aiModel.value)
      _aiRunning.value = false
      result
        .onSuccess {
          _aiStatus.value = "Conexión verificada con ${_aiModel.value}."
          loadAiModels()
        }
        .onFailure { _aiError.value = it.message ?: "No pude conectar." }
    }
  }

  /**
   * S19 — transcribes the audio (when present and not yet transcribed), cleans the text,
   * summarizes it and proposes a title and tags.
   */
  fun runAi(noteId: String) {
    if (_aiRunning.value) return
    val note = repository.state.value.notes.firstOrNull { it.id == noteId }
    if (note == null) {
      _aiError.value = "La nota ya no existe."
      return
    }
    val key = _apiKey.value.trim()
    if (key.isEmpty()) {
      _aiError.value = "Configurá tu clave de API en Ajustes para usar la IA."
      return
    }

    _aiRunning.value = true
    _aiStatus.value = null
    _aiError.value = null
    viewModelScope.launch {
      val audio = note.audioPath?.let { File(it) }?.takeIf { it.exists() }
      val hasTranscript = note.transcript.isNotBlank()
      val audioToSend = if (audio != null && !hasTranscript) audio else null
      val outcome = runCatching {
        withContext(Dispatchers.IO) {
          aiClient.process(
            apiKey = key,
            model = _aiModel.value,
            audio = audioToSend,
            transcript = note.transcript,
          )
        }
      }
      _aiRunning.value = false
      outcome
        .onSuccess { result ->
          val reminderAt = ReminderTime.parse(result.reminder, System.currentTimeMillis())
          repository.applyAiResult(
            noteId = note.id,
            title = if (note.title.isBlank()) result.title else "",
            transcript = result.transcript,
            summary = result.summary,
            tagNames = result.tags,
            reminderAt = reminderAt,
          )
          repository.state.value.notes.firstOrNull { it.id == note.id }?.let(scheduler::schedule)
          _aiStatus.value = if (reminderAt != null) {
            "La IA actualizó la nota y agregó el recordatorio."
          } else {
            "La IA actualizó la nota."
          }
        }
        .onFailure { error ->
          _aiError.value = when (error) {
            is AiException -> error.message ?: "No pude procesar la nota."
            else -> error.message?.let { "No pude procesar la nota: $it" }
              ?: "No pude procesar la nota."
          }
        }
    }
  }

  val notes: StateFlow<List<Note>> =
    combine(repository.state, _query, _folderFilter, _tagFilter) { library, query, folder, tag ->
      val needle = query.trim().lowercase()
      library.notes.filter { note ->
        val folderOk = folder == null || note.folderId == folder
        val tagOk = tag == null || note.tagIds.contains(tag)
        val queryOk = needle.isEmpty() ||
          note.title.lowercase().contains(needle) ||
          note.body.lowercase().contains(needle) ||
          note.transcript.lowercase().contains(needle) ||
          note.summary.lowercase().contains(needle)
        folderOk && tagOk && queryOk
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val folders: StateFlow<List<Folder>> = repository.state
    .map { it.folders }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val tags: StateFlow<List<Tag>> = repository.state
    .map { it.tags }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val totalNotes: StateFlow<Int> = repository.state
    .map { it.notes.size }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

  val allNotes: StateFlow<List<Note>> = repository.state
    .map { it.notes }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val library: StateFlow<com.voicenotes.app.data.Library> = repository.state

  fun noteById(id: String): Note? = repository.state.value.notes.firstOrNull { it.id == id }

  fun displayTitle(note: Note): String = repository.displayTitle(note)

  fun setQuery(value: String) {
    _query.value = value
  }

  fun setFolderFilter(folderId: String?) {
    _folderFilter.value = folderId
  }

  fun setTagFilter(tagId: String?) {
    _tagFilter.value = tagId
  }

  fun setRecording(value: Boolean) {
    _recording.value = value
  }

  fun createTextNote(title: String, body: String): Note =
    repository.createTextNote(title, body, _folderFilter.value)

  fun createAudioNote(path: String, durationMs: Long): Note =
    repository.createAudioNote(path, durationMs, _folderFilter.value)

  fun updateNote(id: String, title: String, body: String) = repository.updateNote(id, title, body)

  fun appendTranscript(id: String, text: String) = repository.appendTranscript(id, text)

  /** The audio belongs to the note, so deleting the note removes the file too (S14). */
  fun deleteNote(id: String) {
    val audioPath = repository.state.value.notes.firstOrNull { it.id == id }?.audioPath
    repository.deleteNote(id)
    scheduler.cancel(id)
    if (audioPath != null) {
      runCatching { File(audioPath).delete() }
    }
  }

  fun assignFolder(noteId: String, folderId: String?) = repository.assignFolder(noteId, folderId)

  fun toggleTag(noteId: String, tagId: String) = repository.toggleTag(noteId, tagId)

  fun createFolder(name: String): Folder = repository.createFolder(name)

  fun createTag(name: String): Tag = repository.createTag(name)

  fun renameFolder(id: String, name: String) = repository.renameFolder(id, name)

  fun deleteFolder(id: String) = repository.deleteFolder(id)

  fun renameTag(id: String, name: String) = repository.renameTag(id, name)

  fun deleteTag(id: String) = repository.deleteTag(id)

  // ---- reminders (S20, S22, S23) -----------------------------------------

  val pendingReminders: StateFlow<List<Note>> = repository.state
    .map { Reminders.pending(it.notes) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val reminderCount: StateFlow<Int> = pendingReminders
    .map { it.size }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

  private val _reminderStatus = MutableStateFlow<String?>(null)
  val reminderStatus: StateFlow<String?> = _reminderStatus.asStateFlow()

  // ---- activities section ---------------------------------------------------

  val activities: StateFlow<List<com.voicenotes.app.data.Activity>> = repository.state
    .map { state -> state.activities.sortedWith(compareBy({ it.dayOfWeek }, { it.startMinutes })) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val workConfig: StateFlow<com.voicenotes.app.data.WorkConfig?> = repository.state
    .map { it.workConfig }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val checklists: StateFlow<List<com.voicenotes.app.data.Checklist>> = repository.state
    .map { it.checklists }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun createChecklist(title: String): com.voicenotes.app.data.Checklist =
    repository.createChecklist(title)

  fun deleteChecklist(id: String) = repository.deleteChecklist(id)

  fun addChecklistItem(checklistId: String, text: String) {
    repository.addChecklistItem(checklistId, text)
  }

  fun toggleChecklistItem(checklistId: String, itemId: String, done: Boolean) {
    repository.setChecklistItemDone(checklistId, itemId, done)
  }

  fun deleteChecklistItem(checklistId: String, itemId: String) {
    repository.deleteChecklistItem(checklistId, itemId)
  }

  private val _scheduleStatus = MutableStateFlow<String?>(null)
  val scheduleStatus: StateFlow<String?> = _scheduleStatus.asStateFlow()

  private val _fallbackOffered = MutableStateFlow(false)

  /** S3 (agenda) — the AI failed, so the screen offers the local plan instead of a dead end. */
  val fallbackOffered: StateFlow<Boolean> = _fallbackOffered.asStateFlow()

  private val _generating = MutableStateFlow(false)
  val generating: StateFlow<Boolean> = _generating.asStateFlow()

  fun saveWorkConfig(config: com.voicenotes.app.data.WorkConfig?) {
    repository.setWorkConfig(config)
  }

  fun toggleActivityDone(id: String, done: Boolean) {
    repository.setActivityDone(id, done)
  }

  fun deleteActivity(id: String) {
    repository.deleteActivity(id)
  }

  fun clearScheduleStatus() {
    _scheduleStatus.value = null
    _fallbackOffered.value = false
  }

  /** S1 (agenda) — the draft the "Agendar" button shows for one note; nothing is saved yet. */
  fun suggestActivity(noteId: String): com.voicenotes.app.ui.components.ActivityDraft? {
    val note = repository.state.value.notes.firstOrNull { it.id == noteId } ?: return null
    val suggestion = TaskExtractor.suggest(
      title = displayTitle(note),
      summary = note.summary,
      transcript = note.transcript.ifBlank { note.body },
      config = repository.state.value.workConfig,
      today = LocalDate.now().dayOfWeek.value,
    )
    return com.voicenotes.app.ui.components.ActivityDraft(
      title = suggestion.title,
      dayOfWeek = suggestion.dayOfWeek,
      startMinutes = suggestion.startMinutes,
      durationMinutes = suggestion.durationMinutes,
      noteId = note.id,
    )
  }

  fun saveActivity(draft: com.voicenotes.app.ui.components.ActivityDraft) {
    val id = draft.id
    if (id == null) {
      repository.createActivity(
        title = draft.title,
        dayOfWeek = draft.dayOfWeek,
        startMinutes = draft.startMinutes,
        noteId = draft.noteId,
        durationMinutes = draft.durationMinutes,
        repeatDays = draft.repeatDays,
      )
      _scheduleStatus.value = "Actividad agregada al cronograma."
    } else {
      repository.updateActivity(
        id = id,
        title = draft.title,
        dayOfWeek = draft.dayOfWeek,
        startMinutes = draft.startMinutes,
        durationMinutes = draft.durationMinutes,
        repeatDays = draft.repeatDays,
      )
      _scheduleStatus.value = "Actividad actualizada."
    }
  }

  /** S3 (agenda) — the schedule without the AI, offered only after a failed generation. */
  fun generateScheduleLocally() {
    val config = repository.state.value.workConfig
    if (config == null) {
      _scheduleStatus.value = "Configurá tus días y horas de trabajo primero."
      return
    }
    val plan = LocalSchedulePlanner.plan(config, scheduleSources())
    if (plan.isEmpty()) {
      _fallbackOffered.value = false
      _scheduleStatus.value = "No encontré tareas para agendar. Cargá una nota con algo para hacer."
      return
    }
    repository.replaceAiActivities(plan)
    _fallbackOffered.value = false
    _scheduleStatus.value =
      "Cronograma simple creado con ${plan.size} actividad${if (plan.size == 1) "" else "es"}, sin IA."
  }

  /** S5 (agenda) — moves what was left over to the next working day and says how many. */
  fun rescheduleLeftovers(ids: List<String>) {
    val today = LocalDate.now().dayOfWeek.value
    val target = ScheduleRules.nextWorkday(repository.state.value.workConfig, today)
    val moved = repository.moveActivities(ids, target)
    _scheduleStatus.value = when {
      moved == 0 -> "No había nada para reprogramar."
      else -> "$moved actividad${if (moved == 1) "" else "es"} pasó al ${dayNameOf(target)}."
    }
  }

  private fun dayNameOf(day: Int): String =
    listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
      .getOrElse(day - 1) { "día $day" }

  private fun scheduleSources() = repository.state.value.notes.map { note ->
    ScheduleSource(
      title = note.title.trim().ifBlank { "Nota sin título" },
      summary = note.summary,
      transcript = note.transcript.take(400),
      noteId = note.id,
    )
  }

  /** S5 — reads the notes with the configured workdays and hours and builds the weekly schedule. */
  fun generateWeeklySchedule() {
    val config = repository.state.value.workConfig
    if (config == null) {
      _scheduleStatus.value = "Configurá tus días y horas de trabajo primero."
      return
    }
    if (_generating.value) return
    viewModelScope.launch {
      _generating.value = true
      val sources = scheduleSources()
      val outcome = runCatching {
        aiClient.generateSchedule(_apiKey.value, _aiModel.value, config, sources)
      }
      outcome.fold(
        onSuccess = { plan ->
          repository.replaceAiActivities(plan)
          _fallbackOffered.value = false
          _scheduleStatus.value =
            "Cronograma creado con ${plan.size} actividad${if (plan.size == 1) "" else "es"}."
        },
        onFailure = { error ->
          _fallbackOffered.value = true
          _scheduleStatus.value =
            (error.message ?: "No pude generar el cronograma.") +
            " Podés generar una versión simple, sin IA."
        },
      )
      _generating.value = false
    }
  }

  fun clearReminderFeedback() {
    _reminderStatus.value = null
  }

  /** Re-arms every pending reminder, e.g. after the exact-alarm permission is granted. */
  fun rescheduleReminders() {
    scheduler.rescheduleAll(repository.state.value.notes)
  }

  /** Saves the reminder and arms its alarm; null removes both. */
  fun setReminder(noteId: String, at: Long?) {
    repository.setReminder(noteId, at)
    val updated = repository.state.value.notes.firstOrNull { it.id == noteId }
    if (updated != null) scheduler.schedule(updated)
    _reminderStatus.value = if (at == null) "Recordatorio eliminado." else "Recordatorio guardado."
  }

  /** S8 — the home screen can mark a note as finished without opening it. */
  fun setNoteDone(noteId: String, done: Boolean) {
    repository.setNoteDone(noteId, done)
  }

  fun markReminderDone(noteId: String, done: Boolean = true) {
    repository.markReminderDone(noteId, done)
    repository.state.value.notes.firstOrNull { it.id == noteId }?.let(scheduler::schedule)
  }

  fun snoozeReminder(noteId: String, minutes: Int) {
    val until = System.currentTimeMillis() + minutes * 60_000L
    repository.snoozeReminder(noteId, until)
    repository.state.value.notes.firstOrNull { it.id == noteId }?.let(scheduler::schedule)
    _reminderStatus.value = "Pospuesto $minutes minutos."
  }

  /** Radar shortcut: push the reminder to tomorrow at 09:00 local time. */
  fun snoozeUntilTomorrow(noteId: String) {
    val zone = java.time.ZoneId.systemDefault()
    val until = java.time.LocalDate.now(zone)
      .plusDays(1)
      .atTime(9, 0)
      .atZone(zone)
      .toInstant()
      .toEpochMilli()
    repository.snoozeReminder(noteId, until)
    repository.state.value.notes.firstOrNull { it.id == noteId }?.let(scheduler::schedule)
    _reminderStatus.value = "Pospuesto para mañana a las 9:00."
  }

  // ---- backup and restore (S16) -----------------------------------------

  private val _backupStatus = MutableStateFlow<String?>(null)
  val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

  private val _backupError = MutableStateFlow<String?>(null)
  val backupError: StateFlow<String?> = _backupError.asStateFlow()

  fun clearBackupFeedback() {
    _backupStatus.value = null
    _backupError.value = null
  }

  fun exportBackup(uri: Uri) {
    val app = getApplication<Application>()
    _backupStatus.value = null
    _backupError.value = null
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val audio = audioDir().listFiles { file -> file.isFile }?.toList().orEmpty()
          val stream = app.contentResolver.openOutputStream(uri)
            ?: error("No pude abrir el archivo de destino.")
          stream.use { BackupArchive.write(it, repository.snapshotJson(), audio) }
          audio.size
        }
      }
      result
        .onSuccess { count ->
          _backupStatus.value = "Respaldo guardado con $count audio(s)."
        }
        .onFailure { _backupError.value = it.message ?: "No pude guardar el respaldo." }
    }
  }

  fun restoreBackup(uri: Uri) {
    val app = getApplication<Application>()
    _backupStatus.value = null
    _backupError.value = null
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val staging = File(app.cacheDir, "restore-${System.currentTimeMillis()}")
          staging.mkdirs()
          try {
            val input = app.contentResolver.openInputStream(uri)
              ?: error("No pude leer el archivo elegido.")
            val extracted = input.use { BackupArchive.extract(it, staging) }
              ?: error("El archivo no es un respaldo de VoiceNotes.")
            if (!repository.replaceWithJson(extracted.notesJson)) {
              error("El respaldo está dañado y no se aplicó nada.")
            }
            val target = audioDir()
            target.mkdirs()
            extracted.audioFiles.forEach { file ->
              val destination = File(target, file.name)
              if (!destination.exists() || destination.length() != file.length()) {
                file.copyTo(destination, overwrite = true)
              }
            }
            repository.rebaseAudioPaths(target.absolutePath)
            extracted.audioFiles.size
          } finally {
            staging.deleteRecursively()
          }
        }
      }
      result
        .onSuccess { count ->
          importOrphanRecordings()
          scheduler.rescheduleAll(repository.state.value.notes)
          _backupStatus.value = "Respaldo restaurado con $count audio(s)."
        }
        .onFailure { _backupError.value = it.message ?: "No pude restaurar el respaldo." }
    }
  }

  private fun audioDir(): File = File(getApplication<Application>().filesDir, "audio")


  /**
   * S14 — the recording service only writes the audio file. If the app died before turning it
   * into a note, the file is adopted here instead of being lost.
   */  fun importOrphanRecordings() {
    val dir = audioDir()
    val files = runCatching { dir.listFiles { file -> file.isFile && file.extension == "m4a" } }
      .getOrNull()
      ?.toList()
      .orEmpty()
    if (files.isEmpty()) return
    val known = repository.state.value.notes.mapNotNull { it.audioPath }.toSet()
    files
      .filter { it.absolutePath !in known && it.length() > 0 }
      .sortedBy { it.lastModified() }
      .forEach { file ->
        // A file the system killed mid-write has no usable duration: adopting it would only
        // create a note that plays nothing.
        val duration = durationOf(file)
        if (duration >= MIN_ORPHAN_DURATION_MS) {
          repository.createAudioNote(file.absolutePath, duration)
        } else {
          runCatching { file.delete() }
        }
      }
  }

  private fun durationOf(file: File): Long {
    val retriever = MediaMetadataRetriever()
    return runCatching {
      retriever.setDataSource(file.absolutePath)
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    }.getOrDefault(0L).also { runCatching { retriever.release() } }
  }

  companion object {
    const val DEFAULT_MODEL = "gemini-2.5-flash"
    private const val MIN_ORPHAN_DURATION_MS = 800L
    private const val KEY_THEME = "theme_mode"
    private const val KEY_API = "ai_api_key"
    private const val KEY_MODEL = "ai_model"
  }
}
