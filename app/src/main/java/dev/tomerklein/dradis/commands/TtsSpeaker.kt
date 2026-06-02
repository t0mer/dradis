package dev.tomerklein.dradis.commands

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

private const val TAG = "dradis.tts"

/**
 * Thin wrapper over Android [TextToSpeech] for the `say` command (CLAUDE.md §6.1).
 * Initialisation is asynchronous; text requested before the engine is ready is
 * queued and flushed once initialised.
 */
class TtsSpeaker(context: Context) {

    private var tts: TextToSpeech? = null
    @Volatile
    private var ready = false
    private val pending = ArrayList<String>()
    private var counter = 0

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault()
                synchronized(pending) {
                    pending.forEach { speakNow(it) }
                    pending.clear()
                }
            } else {
                Log.e(TAG, "TextToSpeech init failed: $status")
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (ready) {
            speakNow(text)
        } else {
            synchronized(pending) { pending.add(text) }
        }
    }

    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "dradis-say-${counter++}")
    }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ready = false
    }
}
