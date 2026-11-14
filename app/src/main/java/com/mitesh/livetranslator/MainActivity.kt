package com.mitesh.livetranslator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mitesh.livetranslator.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The transcript screen. Listening itself runs in [Engine] under [ListenService],
 * so it keeps going when this screen is closed or the phone is locked.
 */
class MainActivity : AppCompatActivity(), Engine.Listener {

    private lateinit var b: ActivityMainBinding
    private val ui = Handler(Looper.getMainLooper())

    /** The in-progress line, shown at the end of the transcript. */
    private var liveView: View? = null
    private var followLatest = true
    private var stageLine: Line? = null

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
    private val session get() = Engine.session
    private val listening get() = Engine.isActive

    private val settings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!Prefs.isSet(this)) finish() else rerender()
    }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // The microphone is required; the notification is optional but recommended.
        if (result[Manifest.permission.RECORD_AUDIO] == true || hasMic()) ListenService.start(this)
        else showBanner("Bayan needs the microphone to listen. Allow it in the phone's app settings.")
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (Engine.state != Engine.State.LISTENING) return
            val s = (System.currentTimeMillis() - Engine.startedAt) / 1000
            b.status.text = String.format(Locale.US, "Listening  %02d:%02d", s / 60, s % 60)
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.mic.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (listening) Engine.stop(this) else beginListening()
        }
        b.btnSettings.setOnClickListener { settings.launch(Intent(this, SetupActivity::class.java)) }
        b.btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        b.btnShare.setOnClickListener { share() }
        b.btnNew.setOnClickListener { newSession() }
        b.banner.setOnClickListener { b.banner.visibility = View.GONE }
        b.jump.setOnClickListener { followLatest = true; scrollToEnd(); b.jump.visibility = View.GONE }
        b.stage.setOnClickListener { hideStage() }
        b.stagePlay.setOnClickListener { playStage() }

        // Follow new lines only while the reader is at the bottom.
        b.scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val atBottom = y + b.scroll.height - b.scroll.paddingBottom >= b.transcript.bottom - dp(48)
            followLatest = atBottom
            if (atBottom) b.jump.visibility = View.GONE
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (b.stage.visibility == View.VISIBLE) hideStage()
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })

        if (!Prefs.isSet(this)) settings.launch(Intent(this, SetupActivity::class.java))
    }

    override fun onStart() {
        super.onStart()
        Engine.addListener(this)
        rerender()                 // pick up anything heard while this screen was away
        onState(Engine.state)
    }

    override fun onStop() {
        Engine.removeListener(this)
        ui.removeCallbacks(ticker)
        super.onStop()
    }

    // ---------- Start ----------

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun beginListening() {
        b.banner.visibility = View.GONE
        if (!Prefs.isSet(this)) { settings.launch(Intent(this, SetupActivity::class.java)); return }
        val needed = mutableListOf<String>()
        if (!hasMic()) needed += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.POST_NOTIFICATIONS
        if (needed.isEmpty()) ListenService.start(this) else permissions.launch(needed.toTypedArray())
    }

    // ---------- Engine events ----------

    override fun onState(state: Engine.State) {
        ui.removeCallbacks(ticker)
        when (state) {
            Engine.State.IDLE -> {
                b.mic.connecting = false
                b.mic.listening = false
                b.status.text = if (session.lines.isEmpty()) "Tap to listen" else "Paused. Tap to continue"
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                liveView?.let { b.transcript.removeView(it) }
                liveView = null
            }
            Engine.State.CONNECTING, Engine.State.RECONNECTING -> {
                b.mic.listening = false
                b.mic.connecting = true
                b.status.text = if (state == Engine.State.RECONNECTING) "Reconnecting" else "Connecting"
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            Engine.State.LISTENING -> {
                b.mic.connecting = false
                b.mic.listening = true
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                ui.post(ticker)
            }
        }
        refreshChrome()
        refreshUsage()
    }

    override fun onPartial(line: Line) {
        b.mic.pulse()
        val v = liveView ?: layoutInflater.inflate(R.layout.item_line, b.transcript, false).also {
            it.alpha = 0.5f
            it.isClickable = false
            b.transcript.addView(it)
            liveView = it
        }
        LineViews.bind(v, line, "Hearing", Prefs.textSize(this))
        refreshChrome()
        onNewContent()
    }

    override fun onFinal(line: Line) {
        val v = liveView ?: layoutInflater.inflate(R.layout.item_line, b.transcript, false).also {
            b.transcript.addView(it)
        }
        liveView = null
        v.isClickable = true
        v.animate().alpha(1f).setDuration(240).start()
        LineViews.bind(v, line, timeFmt.format(Date(line.time)), Prefs.textSize(this))
        wire(v, line)
        refreshChrome()
        onNewContent()
    }

    override fun onError(message: String) = showBanner(message)

    // ---------- Transcript ----------

    private fun wire(v: View, line: Line) {
        v.setOnClickListener { showStage(line) }
        LineViews.copyOnLongPress(v, line)
    }

    /** Draw the whole transcript from the engine's session. */
    private fun rerender() {
        b.transcript.removeAllViews()
        liveView = null
        session.lines.forEach { line ->
            val v = layoutInflater.inflate(R.layout.item_line, b.transcript, false)
            LineViews.bind(v, line, timeFmt.format(Date(line.time)), Prefs.textSize(this))
            wire(v, line)
            b.transcript.addView(v)
        }
        refreshChrome()
        followLatest = true
        scrollToEnd()
    }

    private fun onNewContent() {
        if (followLatest) scrollToEnd() else b.jump.visibility = View.VISIBLE
    }

    private fun scrollToEnd() = b.scroll.post { b.scroll.smoothScrollTo(0, b.transcript.bottom) }

    // ---------- Stage: one line, full screen ----------

    private fun showStage(line: Line) {
        stageLine = line
        b.stagePlay.visibility = if (line.target.isBlank()) View.GONE else View.VISIBLE
        b.stageTarget.text = line.target
        b.stageSource.text = line.source
        LineViews.direct(b.stageTarget, line.target)
        LineViews.direct(b.stageSource, line.source)
        b.stage.alpha = 0f
        b.stage.visibility = View.VISIBLE
        b.stage.animate().alpha(1f).setDuration(180).start()
    }

    private fun playStage() {
        val line = stageLine ?: return
        val resume = listening
        if (resume) Engine.stop(this)   // don't let the mic hear its own playback
        b.stagePlay.text = "Playing"
        b.stagePlay.isEnabled = false
        Speaker.speak(this, line.target) { err ->
            runOnUiThread {
                b.stagePlay.text = "Play aloud"
                b.stagePlay.isEnabled = true
                if (err != null) showBanner(err)
                if (resume) beginListening()
            }
        }
    }

    private fun hideStage() {
        b.stage.animate().alpha(0f).setDuration(150).withEndAction { b.stage.visibility = View.GONE }.start()
    }

    // ---------- Chrome ----------

    private fun newSession() {
        Engine.newSession(this)
        b.transcript.removeAllViews()
        liveView = null
        b.status.text = "Tap to listen"
        refreshChrome()
    }

    private fun refreshChrome() {
        b.empty.visibility = if (b.transcript.childCount > 0) View.GONE else View.VISIBLE
        val tools = if (session.lines.isNotEmpty() && !listening) View.VISIBLE else View.INVISIBLE
        b.btnShare.visibility = tools
        b.btnNew.visibility = tools
    }

    private fun refreshUsage() {
        b.usage.visibility = if (listening) View.GONE else View.VISIBLE
        b.usage.text = Prefs.usageLine(this)
    }

    private fun showBanner(msg: String) {
        b.banner.text = msg
        b.banner.alpha = 0f
        b.banner.visibility = View.VISIBLE
        b.banner.animate().alpha(1f).setDuration(200).start()
    }

    private fun share() {
        if (session.lines.isEmpty()) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, session.asText())
        }, "Share transcript"))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
