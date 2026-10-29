package com.voicenotes.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.voicenotes.app.MainActivity
import com.voicenotes.app.R
import com.voicenotes.app.data.Note

/** The notification a reminder posts when its alarm fires (S23). */
object ReminderNotifications {
  const val CHANNEL_ID = "reminders_v2"

  /** The channel the app shipped before the sound was built as an alarm. */
  private const val LEGACY_CHANNEL_ID = "reminders"

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    // A channel's sound and importance are frozen once created, and recreating a deleted channel
    // only brings back the old settings, so a corrected channel needs a new id. The old one is
    // deleted so it does not linger as an extra entry in the system settings.
    if (manager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
      manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        "Recordatorios",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Avisos de notas con fecha y hora."
        enableVibration(true)
        // A reminder is an alarm, not small talk: playing the sound with USAGE_ALARM is what lets
        // it through Do Not Disturb when the user allows alarms there.
        setSound(
          RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build(),
        )
      },
    )
  }

  fun show(context: Context, note: Note, title: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    ensureChannel(context)

    val open = PendingIntent.getActivity(
      context,
      note.id.hashCode(),
      Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_NOTE)
        .putExtra(MainActivity.EXTRA_NOTE_ID, note.id)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_mic)
      .setContentTitle(title)
      .setContentText(preview(note))
      .setStyle(NotificationCompat.BigTextStyle().bigText(preview(note)))
      .setContentIntent(open)
      .setAutoCancel(true)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .addAction(0, "Listo", action(context, note.id, ReminderReceiver.ACTION_DONE))
      .addAction(0, "Posponer 10 min", action(context, note.id, ReminderReceiver.ACTION_SNOOZE))
      .build()

    NotificationManagerCompat.from(context).notify(note.id.hashCode(), notification)
  }

  fun cancel(context: Context, noteId: String) {
    NotificationManagerCompat.from(context).cancel(noteId.hashCode())
  }

  private fun preview(note: Note): String {
    val text = note.summary.ifBlank { note.transcript.ifBlank { note.body } }.trim()
    return if (text.length > 180) text.take(177) + "…" else text
  }

  private fun action(context: Context, noteId: String, action: String): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      noteId.hashCode() + action.hashCode(),
      Intent(context, ReminderReceiver::class.java)
        .setAction(action)
        .setData(Uri.parse("voicenotes://reminder/$noteId/$action"))
        .putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
