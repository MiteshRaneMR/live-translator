package com.mitesh.livetranslator

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.microsoft.cognitiveservices.speech.AutoDetectSourceLanguageConfig
import com.microsoft.cognitiveservices.speech.CancellationErrorCode
import com.microsoft.cognitiveservices.speech.PhraseListGrammar
import com.microsoft.cognitiveservices.speech.ProfanityOption
import com.microsoft.cognitiveservices.speech.PropertyId
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.microsoft.cognitiveservices.speech.translation.SpeechTranslationConfig
import com.microsoft.cognitiveservices.speech.translation.TranslationRecognitionResult
import com.microsoft.cognitiveservices.speech.translation.TranslationRecognizer
import java.net.URI
import java.util.concurrent.Executors

/**
 * Owns the live recognition and the current transcript, independent of any screen,
 * so listening carries on with the phone locked or another app open.
 * [ListenService] keeps the process in the foreground; screens just observe.
 * All listener callbacks arrive on the main thread.
 */
object Engine {

    enum class State { IDLE, CONNECTING, LISTENING, RECONNECTING }

    interface Listener {
        fun onState(state: State) {}
        fun onPartial(line: Line) {}
        fun onFinal(line: Line) {}
        fun onError(message: String) {}
    }

    var state = State.IDLE
        private set
    var session = Session(System.currentTimeMillis())
        private set
    /** When the current listening run began, for the on-screen timer. */
    var startedAt = 0L
        private set

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val listeners = linkedSetOf<Listener>()

    @Volatile private var recognizer: TranslationRecognizer? = null
    @Volatile private var audioConfig: AudioConfig? = null
    private var meterStart = 0L
    private var retries = 0
    private lateinit var app: Context

    private val TRANSIENT = setOf(
        CancellationErrorCode.ConnectionFailure,
        CancellationErrorCode.ServiceTimeout,
        CancellationErrorCode.ServiceUnavailable,
        CancellationErrorCode.ServiceError
    )

    val isActive get() = state != State.IDLE

    fun addListener(l: Listener) { listeners += l }
    fun removeListener(l: Listener) { listeners -= l }

    private fun setState(s: State) {
        state = s
        listeners.toList().forEach { it.onState(s) }
    }

    // ---------- Control ----------

    fun start(context: Context) {
        app = context.applicationContext
        if (state != State.IDLE) return
        if (retries == 0) startedAt = System.currentTimeMillis()
        setState(if (retries > 0) State.RECONNECTING else State.CONNECTING)

        val key = Prefs.key(app)
        val region = Prefs.region(app)
        val english = Prefs.english(app)
        val phrases = Prefs.phrases(app)

        worker.execute {
            try {
                // The v2 endpoint enables continuous language detection, so speakers
                // can switch between Arabic and English mid-conversation.
                val endpoint = URI("wss://$region.stt.speech.microsoft.com/speech/universal/v2")
                val config = SpeechTranslationConfig.fromEndpoint(endpoint, key)
                config.setProperty(PropertyId.SpeechServiceConnection_LanguageIdMode, "Continuous")
                config.addTargetLanguage("en")
                config.addTargetLanguage("ar")

                // Accuracy tuning:
                // Show words exactly as heard rather than masking them.
                config.setProfanity(ProfanityOption.Raw)
                // Wait a little longer before closing a sentence, so translations get
                // whole thoughts instead of fragments cut at a natural pause.
                config.setProperty(PropertyId.Speech_SegmentationSilenceTimeoutMs, "900")
                // Only translate words the recogniser is confident about while
                // someone is still speaking, so the live line flickers less.
                config.setProperty(PropertyId.SpeechServiceResponse_TranslationRequestStablePartialResult, "true")
                config.setProperty(PropertyId.SpeechServiceResponse_StablePartialResultThreshold, "3")

                val autoDetect = AutoDetectSourceLanguageConfig.fromLanguages(listOf("ar-SA", english))
                val audio = AudioConfig.fromDefaultMicrophoneInput()
                val rec = TranslationRecognizer(config, autoDetect, audio)

                // Names and jargon the user expects to hear get priority spellings.
                if (phrases.isNotEmpty()) {
                    val grammar = PhraseListGrammar.fromRecognizer(rec)
                    phrases.forEach { grammar.addPhrase(it) }
                }

                rec.recognizing.addEventListener { _, e ->
                    val line = split(e.result)
                    main.post { listeners.toList().forEach { it.onPartial(line) } }
                }
                rec.recognized.addEventListener { _, e ->
                    if (e.result.reason == ResultReason.TranslatedSpeech && e.result.text.isNotBlank()) {
                        val line = split(e.result).copy(time = System.currentTimeMillis())
                        main.post { commit(line) }
                    }
                }
                rec.canceled.addEventListener { _, e ->
                    val transient = e.errorCode in TRANSIENT
                    val msg = friendlyError(e.errorCode, e.errorDetails)
                    main.post {
                        if (transient && isActive && retries < 3) reconnect()
                        else {
                            stop(app)
                            if (msg != null) listeners.toList().forEach { it.onError(msg) }
                        }
                    }
                }
                rec.sessionStarted.addEventListener { _, _ -> main.post { onSessionStarted() } }

                rec.startContinuousRecognitionAsync().get()
                recognizer = rec
                audioConfig = audio
                main.post { if (!isActive) release() }
            } catch (t: Throwable) {
                main.post {
                    stop(app)
                    val msg = "Couldn't start listening. ${t.message ?: ""}".trim()
                    listeners.toList().forEach { it.onError(msg) }
                }
            }
        }
    }

    fun stop(context: Context) {
        app = context.applicationContext
        if (state == State.IDLE) return
        retries = 0
        release()
        Sessions.save(app, session)
        setState(State.IDLE)
    }

    fun newSession(context: Context) {
        Sessions.save(context.applicationContext, session)
        session = Session(System.currentTimeMillis())
    }

    // ---------- Internals ----------

    private fun onSessionStarted() {
        if (!isActive) return
        retries = 0
        meterStart = System.currentTimeMillis()
        setState(State.LISTENING)
    }

    /** Network blips are common on mobile data: quietly reconnect a few times. */
    private fun reconnect() {
        retries++
        release()
        state = State.IDLE  // allow start() to run again
        setState(State.RECONNECTING)
        main.postDelayed({
            if (state == State.RECONNECTING) {
                state = State.IDLE
                start(app)
            }
        }, 1500L * retries)
    }

    private fun commit(line: Line) {
        session.lines += line
        Sessions.save(app, session)
        listeners.toList().forEach { it.onFinal(line) }
    }

    private fun release() {
        if (meterStart > 0) Prefs.addUsage(app, (System.currentTimeMillis() - meterStart) / 1000)
        meterStart = 0
        val rec = recognizer
        val audio = audioConfig
        recognizer = null
        audioConfig = null
        if (rec != null) worker.execute {
            try { rec.stopContinuousRecognitionAsync().get() } catch (_: Throwable) {}
            rec.close()
            audio?.close()
        }
    }

    private fun split(r: TranslationRecognitionResult): Line {
        val text = r.text ?: ""
        val detected = r.properties.getProperty(
            PropertyId.SpeechServiceConnection_AutoDetectSourceLanguageResult
        ) ?: ""
        val fromArabic = when {
            detected.startsWith("ar") -> true
            detected.startsWith("en") -> false
            else -> LineViews.isArabic(text)
        }
        return Line(text, r.translations[if (fromArabic) "en" else "ar"] ?: "", fromArabic)
    }

    private fun friendlyError(code: CancellationErrorCode?, details: String?): String? = when (code) {
        null, CancellationErrorCode.NoError -> null
        CancellationErrorCode.AuthenticationFailure, CancellationErrorCode.Forbidden ->
            "Azure didn't accept the key. Check the key and region in settings."
        CancellationErrorCode.ConnectionFailure, CancellationErrorCode.ServiceTimeout ->
            "Lost the connection to Azure. Check the internet and tap the mic again."
        CancellationErrorCode.TooManyRequests ->
            "This month's free Azure hours are used up. Switch the resource to Standard in Azure to keep going."
        else -> "Listening stopped. ${details ?: ""}".trim()
    }
}
