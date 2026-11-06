package com.voicenotes.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.voicenotes.app.data.Activity

/**
 * S14 — one alarm per activity occurrence. The series is never copied: each time an alarm fires the
 * receiver arms the following occurrence, so a daily activity keeps asking until it is done.
 */
class ActivityReminderScheduler(private val context: Context) {

  fun schedule(activity: Activity) {
    val at = ActivityReminders.nextAtIfAsked(activity, System.currentTimeMillis()) ?: run {
      cancel(activity.id)
      return
    }
    val manager = context.getSystemService(AlarmManager::class.java) ?: return
    val pending = alarmIntent(activity.id)
    if (canScheduleExact()) {
      manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    } else {
      manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
  }

  fun cancel(activityId: String) {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return
    manager.cancel(alarmIntent(activityId))
  }

  /** Re-arms every activity that asks for a reminder; used after a reboot and on every change. */
  fun rescheduleAll(activities: List<Activity>) {
    activities.forEach { activity ->
      if (activity.remind) schedule(activity) else cancel(activity.id)
    }
  }

  fun canScheduleExact(): Boolean {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return false
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
  }

  private fun alarmIntent(activityId: String): PendingIntent {
    val intent = Intent(context, ActivityReminderReceiver::class.java)
      .setAction(ActivityReminderReceiver.ACTION_FIRE)
      .setData(Uri.parse("voicenotes://activity-reminder/$activityId"))
      .putExtra(ActivityReminderReceiver.EXTRA_ACTIVITY_ID, activityId)
    return PendingIntent.getBroadcast(
      context,
      activityId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}
