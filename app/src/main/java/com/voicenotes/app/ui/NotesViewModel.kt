package com.voicenotes.app.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicenotes.app.ai.AiClient
import com.voicenotes.app.ai.AiException
import com.voicenotes.app.ai.GeminiClient
import com.voicenotes.app.audio.RecordingService
import com.voicenotes.app.audio.RecordingSession
import com.voicenotes.app.data.BackupArchive
import com.voicenotes.app.data.FileStorage
import com.voicenotes.app.data.Folder
import com.voicenotes.app.data.Note
import com.voicenotes.app.data.NotesRepository
import com.voicenotes.app.data.Tag
import com.voicenotes.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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

  private val repository = NotesRepository(
    FileStorage(File(application.filesDir, "notes.json")),
  )

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
    repository.load()
    importOrphanRecordings()
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
          repository.applyAiResult(
            noteId = note.id,
            title = if (note.title.isBlank()) result.title else "",
            transcript = result.transcript,
            summary = result.summary,
            tagNames = result.tags,
          )
          _aiStatus.value = "La IA actualizó la nota."
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
