package com.voicenotes.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import com.voicenotes.app.data.LibraryStore
import com.voicenotes.app.data.WorkConfig
import java.time.LocalDate

/**
 * S4 of the agenda block — one notification a day, at the beginning of the working hours, with the
 * activities of that day. It is a digest, not an alarm: notification importance and no sound.
 */
class DailyAgendaReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_FIRE) return
    val repository = LibraryStore.get(context)
    val items = DailyAgenda.today(
      repository.state.value.activities,
      LocalDate.now().dayOfWeek.value,
    )
    if (items.isNotEmpty()) {
      DailyAgendaNotifications.show(context, items)
    }
    // Re-arms itself for the next day: a one-shot alarm plus one re-arm is cheaper to reason about
    // than a repeating one that drifts and does not survive a reboot.
    DailyAgendaScheduler(context).schedule(repository.state.value.workConfig)
  }

  companion object {
    const val ACTION_FIRE = "com.voicenotes.app.action.DAILY_AGENDA"
  }
}

/** Programs the daily digest, re-armed on boot and every time the working hours change. */
class DailyAgendaScheduler(private val context: Context) {

  fun schedule(config: WorkConfig?) {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return
    if (config == null) {
      manager.cancel(pendingIntent())
      return
    }
    val at = DailyAgenda.nextOccurrence(config.startMinutes, System.currentTimeMillis())
    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent())
  }

  fun cancel() {
    context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent())
  }

  private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
    context,
    REQUEST_CODE,
    Intent(context, DailyAgendaReceiver::class.java)
      .setAction(DailyAgendaReceiver.ACTION_FIRE)
      .setData(Uri.parse("voicenotes://agenda/daily")),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )

  private companion object {
    const val REQUEST_CODE = 4_112
  }
}

object DailyAgendaNotifications {
  private const val CHANNEL_ID = "agenda_v1"
  private const val NOTIFICATION_ID = 4_112

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "Agenda del día", NotificationManager.IMPORTANCE_DEFAULT)
        .apply { description = "Resumen de las actividades de hoy." },
    )
  }

  fun show(context: Context, items: List<Activity>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    ensureChannel(context)

    val open = PendingIntent.getActivity(
      context,
      NOTIFICATION_ID,
      Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val summary = DailyAgenda.summary(items)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_mic)
      .setContentTitle(DailyAgenda.title(items.size))
      .setContentText(summary)
      .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
      .setContentIntent(open)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
  }
}
