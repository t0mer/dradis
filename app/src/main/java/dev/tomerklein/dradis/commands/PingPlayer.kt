package dev.tomerklein.dradis.commands

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "dradis.ping"

/**
 * "Find my phone": plays a loud looping alarm tone that bypasses silent / DND
 * (CLAUDE.md §9.4). Uses USAGE_ALARM audio attributes (allowed through DND once
 * notification-policy access is granted) and maxes the alarm stream, restoring
 * the previous volume afterwards.
 *
 * Decision (changelog §16): rather than bundling a raw tone we play the system
 * default alarm ringtone with alarm usage — same audible/DND-bypass behaviour,
 * no binary asset to ship.
 */
object PingPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var stopJob: Job? = null
    private var savedAlarmVolume: Int? = null
    private var appContext: Context? = null

    val isActive: Boolean get() = player != null

    fun start(context: Context, seconds: Int, overrideDnd: Boolean, log: (String) -> Unit) {
        appContext = context.applicationContext
        stopInternal()

        if (overrideDnd) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm != null && !nm.isNotificationPolicyAccessGranted) {
                log("DND/notification-policy access not granted — alarm may be silenced by Do-Not-Disturb")
            }
            // Max the alarm stream so it cuts through silent/DND.
            val audio = context.getSystemService(AudioManager::class.java)
            if (audio != null) {
                savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                runCatching {
                    audio.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                        0,
                    )
                }
            }
        }

        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
        if (uri == null) {
            log("No alarm/ringtone available to play")
            return
        }

        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            runCatching {
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }.onFailure { Log.e(TAG, "alarm playback failed", it) }
        }

        startVibration(context)

        val duration = seconds.coerceIn(1, 600)
        stopJob = scope.launch {
            delay(duration * 1000L)
            stopInternal()
            log("Ping finished")
        }
        log("Ping started for ${duration}s")
    }

    fun stop(log: (String) -> Unit = {}) {
        stopInternal()
        log("Ping stopped")
    }

    private fun startVibration(context: Context) {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator = vib
        val pattern = longArrayOf(0, 600, 400)
        runCatching {
            vib?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun stopInternal() {
        stopJob?.cancel()
        stopJob = null
        player?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            runCatching { p.release() }
        }
        player = null
        vibrator?.let { runCatching { it.cancel() } }
        vibrator = null
        restoreVolume()
    }

    private fun restoreVolume() {
        val audio = appContext?.getSystemService(AudioManager::class.java) ?: return
        savedAlarmVolume?.let {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
        }
        savedAlarmVolume = null
    }
}
