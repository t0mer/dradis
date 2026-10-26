package dev.tomerklein.dradis.commands

import android.content.Context
import dev.tomerklein.dradis.R
import dev.tomerklein.dradis.settings.DradisSettings
import dev.tomerklein.dradis.settings.NotifyIcon

/** Real notification, speech, alarm and picture-storage effects for custom topics. */
class AndroidCustomTopicEffects(
    private val context: Context,
    private val tts: TtsSpeaker,
    private val settings: () -> DradisSettings,
    private val pictures: PictureStore,
    private val log: (String) -> Unit,
) : CustomTopicEffects {

    override fun notify(title: String, text: String, icon: NotifyIcon) {
        val res = when (icon) {
            NotifyIcon.DEFAULT -> R.drawable.ic_launcher_foreground
            NotifyIcon.ALARM -> android.R.drawable.ic_lock_idle_alarm
            NotifyIcon.CAMERA -> android.R.drawable.ic_menu_camera
            NotifyIcon.INFO -> android.R.drawable.ic_dialog_info
        }
        Notifier.post(context, title, text, icon = res)
    }

    override fun speak(text: String) = tts.speak(text)

    override fun alarm(seconds: Int) {
        val s = settings()
        PingPlayer.start(context, seconds, s.alarmOverrideDnd, s.alarmRingtoneUri, log)
    }

    override fun savePicture(image: ImagePayload.Image): Boolean = pictures.save(image)
}
