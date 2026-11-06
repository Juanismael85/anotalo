package com.voicenotes.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.voicenotes.app.data.LibraryStore

/**
 * S14 — the answer to "¿ya está lista?". "Ya está lista" marks the activity done, which ends the
 * series; "Reprogramar" opens the editor for that activity; ignoring the notification leaves the
 * series alive, so the next occurrence asks again.
 */
class ActivityReminderReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID) ?: return
    val repository = LibraryStore.get(context)
    val scheduler = ActivityReminderScheduler(context)
    val activity = repository.state.value.activities.firstOrNull { it.id == activityId }

    when (intent.action) {
      ACTION_DONE -> {
        repository.setActivityDone(activityId, true)
        scheduler.cancel(activityId)
        ActivityReminderNotifications.cancel(context, activityId)
      }

      ACTION_RESCHEDULE -> {
        ActivityReminderNotifications.cancel(context, activityId)
        openEditor(context, activityId)
      }

      else -> {
        if (activity == null) return
        ActivityReminderNotifications.show(context, activity)
        // The series keeps going: arm the following occurrence before this alarm is spent.
        scheduler.schedule(activity)
      }
    }
  }

  private fun openEditor(context: Context, activityId: String) {
    val intent = Intent(context, com.voicenotes.app.MainActivity::class.java)
      .setAction(com.voicenotes.app.MainActivity.ACTION_OPEN_ACTIVITY)
      .putExtra(com.voicenotes.app.MainActivity.EXTRA_ACTIVITY_ID, activityId)
      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    context.startActivity(intent)
  }

  companion object {
    const val ACTION_FIRE = "com.voicenotes.app.action.ACTIVITY_REMINDER"
    const val ACTION_DONE = "com.voicenotes.app.action.ACTIVITY_DONE"
    const val ACTION_RESCHEDULE = "com.voicenotes.app.action.ACTIVITY_RESCHEDULE"
    const val EXTRA_ACTIVITY_ID = "activity_reminder_id"
  }
}
