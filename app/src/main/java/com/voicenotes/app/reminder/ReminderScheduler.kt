package com.voicenotes.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.voicenotes.app.data.Note

/**
 * Programs one exact alarm per pending reminder (S23). Without the exact-alarm grant the system
 * batches it, so the fallback fires late instead of never.
 */
class ReminderScheduler(private val context: Context) {

  fun schedule(note: Note) {
    val at = note.reminderAt
    if (at == null || note.reminderDone || at <= System.currentTimeMillis()) {
      cancel(note.id)
      return
    }
    val manager = context.getSystemService(AlarmManager::class.java) ?: return
    val pending = alarmIntent(note.id)
    if (canScheduleExact()) {
      manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    } else {
      manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
  }

  fun cancel(noteId: String) {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return
    manager.cancel(alarmIntent(noteId))
  }

  /** Re-arms every pending reminder, used after a reboot, a restore or a fresh app start. */
  fun rescheduleAll(notes: List<Note>) {
    notes.forEach { note ->
      if (note.reminderAt != null) schedule(note)
    }
  }

  fun canScheduleExact(): Boolean {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return false
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
  }

  private fun alarmIntent(noteId: String): PendingIntent {
    val intent = Intent(context, ReminderReceiver::class.java)
      .setAction(ReminderReceiver.ACTION_FIRE)
      .setData(Uri.parse("voicenotes://reminder/$noteId"))
      .putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId)
    return PendingIntent.getBroadcast(
      context,
      noteId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}
