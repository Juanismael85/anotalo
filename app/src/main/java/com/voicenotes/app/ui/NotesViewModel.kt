package com.voicenotes.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicenotes.app.data.FileStorage
import com.voicenotes.app.data.Folder
import com.voicenotes.app.data.Note
import com.voicenotes.app.data.NotesRepository
import com.voicenotes.app.data.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

class NotesViewModel(application: Application) : AndroidViewModel(application) {

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

  init {
    repository.load()
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
          note.transcript.lowercase().contains(needle)
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

  fun createTextNote(title: String, body: String): Note = repository.createTextNote(title, body)

  fun createAudioNote(path: String, durationMs: Long): Note = repository.createAudioNote(path, durationMs)

  fun updateNote(id: String, title: String, body: String) = repository.updateNote(id, title, body)

  fun appendTranscript(id: String, text: String) = repository.appendTranscript(id, text)

  fun deleteNote(id: String) = repository.deleteNote(id)

  fun assignFolder(noteId: String, folderId: String?) = repository.assignFolder(noteId, folderId)

  fun toggleTag(noteId: String, tagId: String) = repository.toggleTag(noteId, tagId)

  fun createFolder(name: String): Folder = repository.createFolder(name)

  fun createTag(name: String): Tag = repository.createTag(name)
}
