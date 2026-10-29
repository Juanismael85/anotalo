package com.voicenotes.app.data

import android.content.Context
import java.io.File

/**
 * One repository per process (S23). The reminder receiver can run while the UI is open, and two
 * independent copies of the file-backed repository would overwrite each other's notes; sharing the
 * instance keeps the `StateFlow` every screen collects in sync with what the receiver wrote.
 */
object LibraryStore {
  @Volatile
  private var instance: NotesRepository? = null

  fun get(context: Context): NotesRepository {
    instance?.let { return it }
    synchronized(this) {
      instance?.let { return it }
      val repository = NotesRepository(
        FileStorage(File(context.applicationContext.filesDir, "notes.json")),
      )
      repository.load()
      instance = repository
      return repository
    }
  }
}
