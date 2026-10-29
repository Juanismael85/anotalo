package com.voicenotes.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.voicenotes.app.data.LibraryStore

/**
 * Receives the reminder alarm and the notification actions (S23). It is the only place outside the
 * UI that writes notes, and it writes through the shared [LibraryStore] instance so an open screen
 * sees the change immediately.
 */
class ReminderReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
    val repository = LibraryStore.get(context)
    val scheduler = ReminderScheduler(context)

    when (intent.action) {
      ACTION_DONE -> {
        repository.markReminderDone(noteId, done = true)
        scheduler.cancel(noteId)
        ReminderNotifications.cancel(context, noteId)
      }

      ACTION_SNOOZE -> {
        repository.snoozeReminder(noteId, System.currentTimeMillis() + SNOOZE_MS)
        repository.state.value.notes.firstOrNull { it.id == noteId }?.let(scheduler::schedule)
        ReminderNotifications.cancel(context, noteId)
      }

      ACTION_FIRE -> {
        val note = repository.state.value.notes.firstOrNull { it.id == noteId } ?: return
        if (note.reminderAt == null || note.reminderDone) {
          ReminderNotifications.cancel(context, noteId)
          return
        }
        ReminderNotifications.show(context, note, repository.displayTitle(note))
      }
    }
  }

  companion object {
    const val ACTION_FIRE = "com.voicenotes.app.action.REMINDER_FIRE"
    const val ACTION_DONE = "com.voicenotes.app.action.REMINDER_DONE"
    const val ACTION_SNOOZE = "com.voicenotes.app.action.REMINDER_SNOOZE"
    const val EXTRA_NOTE_ID = "note_id"
    const val SNOOZE_MS = 10 * 60_000L
  }
}
