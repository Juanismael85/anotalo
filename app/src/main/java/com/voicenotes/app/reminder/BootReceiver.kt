package com.voicenotes.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.voicenotes.app.data.LibraryStore

/**
 * Alarms do not survive a reboot or an app update, so every pending reminder is armed again (S23).
 * The same happens when the app opens, which also covers alarms dropped by the system.
 */
class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
    val library = LibraryStore.get(context).state.value
    ReminderScheduler(context).rescheduleAll(library.notes)
    // S14 — the activity alarms are lost the same way, so they are armed again too.
    ActivityReminderScheduler(context).rescheduleAll(library.activities)
    DailyAgendaScheduler(context).schedule(library.workConfig)
  }
}
