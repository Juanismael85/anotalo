package com.voicenotes.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.voicenotes.app.MainActivity
import com.voicenotes.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * S14 — recording runs here so leaving the app does not cut it. The service owns the
 * recorder and the notification (with "Detener y guardar") and never touches notes.json.
 */
class RecordingService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var recorder: VoiceRecorder? = null
  private var ticker: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> saveAndStop()
      ACTION_CANCEL -> discard()
      else -> begin()
    }
    return START_NOT_STICKY
  }

  private fun begin() {
    createChannel()
    startForegroundWithNotification(RecordingSession.elapsed.value)
    if (recorder?.isRecording == true) return
    val created = VoiceRecorder(this)
    val started = runCatching { created.start() }
    if (started.isFailure) {
      RecordingSession.setActive(false)
      RecordingSession.setError("No pude acceder al micrófono.")
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return
    }
    recorder = created
    RecordingSession.setError(null)
    RecordingSession.setElapsed(0L)
    RecordingSession.setActive(true)
    ticker?.cancel()
    ticker = scope.launch {
      while (isActive) {
        val elapsed = created.elapsedMillis()
        RecordingSession.setElapsed(elapsed)
        notify(buildNotification(elapsed))
        delay(500)
      }
    }
  }

  private fun saveAndStop() {
    val current = recorder
    recorder = null
    ticker?.cancel()
    ticker = null
    val finished = current?.stop()
    RecordingSession.setActive(false)
    RecordingSession.setElapsed(0L)
    // A tap that starts and stops almost instantly must not leave a 0:00 note behind; the
    // audio it produced has no content, so the file goes with it.
    if (finished != null && finished.second >= MIN_DURATION_MS) {
      RecordingSession.publish(RecordedAudio(finished.first.absolutePath, finished.second))
    } else {
      runCatching { finished?.first?.delete() }
      if (finished == null && current != null) {
        RecordingSession.setError("La grabación quedó vacía.")
      }
    }
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun discard() {
    recorder?.cancel()
    recorder = null
    ticker?.cancel()
    ticker = null
    RecordingSession.setActive(false)
    RecordingSession.setElapsed(0L)
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    if (recorder?.isRecording == true) saveAndStop()
    ticker?.cancel()
    scope.cancel()
    super.onDestroy()
  }

  private fun createChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "Grabación", NotificationManager.IMPORTANCE_LOW).apply {
        description = "Muestra la grabación en curso"
        setShowBadge(false)
      },
    )
  }

  private fun startForegroundWithNotification(elapsedMs: Long) {
    val notification = buildNotification(elapsedMs)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun notify(notification: Notification) {
    runCatching {
      NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }
  }

  private fun buildNotification(elapsedMs: Long): Notification {
    val open = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val stop = PendingIntent.getService(
      this,
      1,
      Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_mic)
      .setContentTitle("Grabando…")
      .setContentText(formatElapsed(elapsedMs))
      .setContentIntent(open)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .addAction(0, "Detener y guardar", stop)
      .build()
  }

  private fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%02d:%02d".format(minutes, seconds)
  }

  companion object {
    private const val CHANNEL_ID = "recording"
    /** Anything shorter than this is an accidental double tap, not a note. */
    private const val MIN_DURATION_MS = 800L
    private const val NOTIFICATION_ID = 41
    private const val ACTION_START = "com.voicenotes.app.action.START"
    private const val ACTION_STOP = "com.voicenotes.app.action.STOP"
    private const val ACTION_CANCEL = "com.voicenotes.app.action.CANCEL"

    fun start(context: Context) {
      ContextCompat.startForegroundService(
        context,
        Intent(context, RecordingService::class.java).setAction(ACTION_START),
      )
    }

    fun stop(context: Context) {
      context.startService(
        Intent(context, RecordingService::class.java).setAction(ACTION_STOP),
      )
    }

    fun cancel(context: Context) {
      context.startService(
        Intent(context, RecordingService::class.java).setAction(ACTION_CANCEL),
      )
    }
  }
}
