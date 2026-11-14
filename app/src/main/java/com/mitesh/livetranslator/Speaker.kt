package com.mitesh.livetranslator

import android.content.Context
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import java.util.concurrent.Executors

/**
 * Reads a translation aloud through the same Azure Speech resource,
 * in a natural Saudi Arabic or English voice.
 */
object Speaker {
    private val worker = Executors.newSingleThreadExecutor()

    fun speak(c: Context, text: String, onDone: (error: String?) -> Unit) {
        val key = Prefs.key(c)
        val region = Prefs.region(c)
        val arabic = LineViews.isArabic(text)
        worker.execute {
            var error: String? = null
            try {
                val config = SpeechConfig.fromSubscription(key, region)
                config.speechSynthesisVoiceName = if (arabic) "ar-SA-ZariyahNeural" else "en-US-JennyNeural"
                SpeechSynthesizer(config).use { synth ->
                    val result = synth.SpeakText(text)
                    if (result.reason != ResultReason.SynthesizingAudioCompleted) {
                        error = "Couldn't play this line. Check the connection and try again."
                    }
                    result.close()
                }
                config.close()
            } catch (t: Throwable) {
                error = "Couldn't play this line. ${t.message ?: ""}".trim()
            }
            onDone(error)
        }
    }
}
