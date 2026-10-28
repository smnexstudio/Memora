package com.smnexstudio.memora.reminders

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Rings an alarm: the alarm tone on a loop and a vibration, for [ALARM_RING_MINUTES] or until stopped.
 * The sound is played here rather than by the notification, because Android rate-limits and
 * quietens notification sounds (a reminder and an alarm arriving together lost the alarm's).
 * The alarm notification is this service's own, so it stays up for as long as it rings.
 */
class AlarmSound : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stop(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = intent?.getParcelableExtra(EXTRA_NOTIFICATION, Notification::class.java) ?: run { stopSelf(); return START_NOT_STICKY }
        startForeground(ALARM_NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        // A second reminder joining a ringing alarm only updates the notification and the timeout.
        if (player == null) ring()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, ALARM_RING_MINUTES * 60_000L)
        return START_NOT_STICKY
    }

    private fun ring() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmSound, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        // The vibration is extra; never let it take the sound down with it.
        runCatching {
            getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 400), 0),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        player?.run { runCatching { stop() }; release() }
        player = null
        runCatching { getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel() }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_NOTIFICATION = "memora.notification"

        fun start(context: Context, n: Notification) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, AlarmSound::class.java).putExtra(EXTRA_NOTIFICATION, n)) }
                // If Android won't let it start, the notification still shows, just without the ringing.
                .onFailure {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        runCatching { NotificationManagerCompat.from(context).notify(ALARM_NOTIFICATION_ID, n) }
                    }
                }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmSound::class.java))
            NotificationManagerCompat.from(context).cancel(ALARM_NOTIFICATION_ID)
        }
    }
}
