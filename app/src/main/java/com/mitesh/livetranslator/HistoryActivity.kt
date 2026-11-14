package com.mitesh.livetranslator

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mitesh.livetranslator.databinding.ActivityHistoryBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Lists saved transcripts; opened with an "id" extra it shows one transcript. */
class HistoryActivity : AppCompatActivity() {

    private lateinit var b: ActivityHistoryBinding
    private val time = SimpleDateFormat("HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun afterTextChanged(s: Editable?) { showAll() }
        })
    }

    override fun onResume() {
        super.onResume()
        val id = intent.getLongExtra("id", -1L)
        if (id > 0) showOne(id) else showAll()
    }

    private fun showAll() {
        b.list.removeAllViews()
        val everything = Sessions.all(this)
        val query = b.search.text.toString()
        val all = everything.filter { Sessions.matches(it, query) }
        b.search.visibility = if (everything.isEmpty()) View.GONE else View.VISIBLE
        b.empty.text = if (everything.isEmpty())
            "Transcripts appear here after you listen.\nEach one is saved as it happens."
        else "Nothing matches \u201C${query.trim()}\u201D."
        b.empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        all.forEach { s ->
            val v = layoutInflater.inflate(R.layout.item_session, b.list, false)
            v.findViewById<TextView>(R.id.whenLabel).text = s.title.ifBlank { label(s.started) }
            v.findViewById<TextView>(R.id.stats).text =
                if (s.title.isBlank()) stats(s) else "${label(s.started)}   ${stats(s)}"
            v.findViewById<TextView>(R.id.preview).apply {
                val first = s.lines.first()
                text = if (first.fromArabic) first.target else first.source
            }
            v.setOnClickListener {
                startActivity(Intent(this, HistoryActivity::class.java).putExtra("id", s.id))
            }
            b.list.addView(v)
        }
    }

    private fun showOne(id: Long) {
        val s = Sessions.load(this, id) ?: run { finish(); return }
        b.search.visibility = View.GONE
        b.title.text = s.title.ifBlank { label(s.started) }
        b.subtitle.text = (if (s.title.isBlank()) "" else label(s.started) + "   ") + stats(s) + "   Tap the title to rename"
        b.title.setOnClickListener { rename(s) }
        b.subtitle.visibility = View.VISIBLE
        b.btnShare.visibility = View.VISIBLE
        b.btnDelete.visibility = View.VISIBLE
        b.list.removeAllViews()

        val size = Prefs.textSize(this)
        s.lines.forEach { line ->
            val v = layoutInflater.inflate(R.layout.item_line, b.list, false)
            LineViews.bind(v, line, time.format(Date(line.time)), size)
            LineViews.copyOnLongPress(v, line)
            b.list.addView(v)
        }

        b.btnShare.setOnClickListener {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, s.asText())
            }, "Share transcript"))
        }
        b.btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Delete this transcript? This can't be undone.")
                .setPositiveButton("Delete") { _, _ -> Sessions.delete(this, id); finish() }
                .setNegativeButton("Keep", null)
                .show()
        }
    }

    private fun rename(s: Session) {
        val input = EditText(this).apply {
            setText(s.title)
            hint = "e.g. NHC meeting"
            setSingleLine()
            setSelection(text.length)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val frame = android.widget.FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(input) }
        AlertDialog.Builder(this)
            .setTitle("Name this transcript")
            .setView(frame)
            .setPositiveButton("Save") { _, _ ->
                s.title = input.text.toString().trim()
                Sessions.save(this, s)
                showOne(s.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun label(ms: Long): String {
        val then = Calendar.getInstance().apply { timeInMillis = ms }
        val now = Calendar.getInstance()
        val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        now.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        val t = time.format(Date(ms))
        return when {
            sameDay -> "Today, $t"
            yesterday -> "Yesterday, $t"
            else -> SimpleDateFormat("EEE d MMM, HH:mm", Locale.US).format(Date(ms))
        }
    }

    private fun stats(s: Session): String {
        val mins = ((s.ended - s.started) / 60000).coerceAtLeast(1)
        val n = s.lines.size
        return "$n ${if (n == 1) "line" else "lines"}, $mins min"
    }
}
