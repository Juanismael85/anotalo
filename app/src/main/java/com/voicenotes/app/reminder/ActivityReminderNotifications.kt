package com.voicenotes.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.voicenotes.app.MainActivity
import com.voicenotes.app.R
import com.voicenotes.app.data.Activity

/**
 * S14 — the notification that asks "¿ya está lista?" when an activity's occurrence comes due. Two
 * answers: mark it done (the series ends) or open the editor and reprogram it.
 */
object ActivityReminderNotifications {
  const val CHANNEL_ID = "activities_v1"

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "Actividades", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Avisos de las actividades de la agenda."
        enableVibration(true)
      },
    )
  }

  fun show(context: Context, activity: Activity) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    ensureChannel(context)

    val open = PendingIntent.getActivity(
      context,
      activity.id.hashCode(),
      Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_ACTIVITY)
        .putExtra(MainActivity.EXTRA_ACTIVITY_ID, activity.id)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val text = "Hoy ${clock(activity.startMinutes)} · ¿ya está lista?"
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_mic)
      .setContentTitle(activity.title)
      .setContentText(text)
      .setContentIntent(open)
      .setAutoCancel(true)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .addAction(0, "Ya está lista", action(context, activity.id, ActivityReminderReceiver.ACTION_DONE))
      .addAction(0, "Reprogramar", action(context, activity.id, ActivityReminderReceiver.ACTION_RESCHEDULE))
      .build()

    NotificationManagerCompat.from(context).notify(activity.id.hashCode(), notification)
  }

  fun cancel(context: Context, activityId: String) {
    NotificationManagerCompat.from(context).cancel(activityId.hashCode())
  }

  private fun clock(minutes: Int): String = "%02d:%02d".format(minutes / 60 % 24, minutes % 60)

  private fun action(context: Context, activityId: String, action: String): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      activityId.hashCode() + action.hashCode(),
      Intent(context, ActivityReminderReceiver::class.java)
        .setAction(action)
        .setData(Uri.parse("voicenotes://activity-reminder/$activityId/$action"))
        .putExtra(ActivityReminderReceiver.EXTRA_ACTIVITY_ID, activityId),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
