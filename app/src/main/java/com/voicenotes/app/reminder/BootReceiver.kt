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
    val notes = LibraryStore.get(context).state.value.notes
    ReminderScheduler(context).rescheduleAll(notes)
  }
}
