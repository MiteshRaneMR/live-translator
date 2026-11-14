package com.mitesh.livetranslator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Line(val source: String, val target: String, val fromArabic: Boolean, val time: Long = 0L) {
    val direction get() = if (fromArabic) "Arabic to English" else "English to Arabic"
}

class Session(val id: Long, val lines: MutableList<Line> = mutableListOf(), var title: String = "") {
    val started get() = lines.firstOrNull()?.time ?: id
    val ended get() = lines.lastOrNull()?.time ?: id

    fun asText(): String {
        val head = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(Date(started))
        val t = SimpleDateFormat("HH:mm", Locale.US)
        return buildString {
            append(if (title.isBlank()) "Bayan transcript, $head\n\n" else "$title\n$head\n\n")
            lines.forEach { append("${t.format(Date(it.time))}  ${it.direction}\n${it.source}\n${it.target}\n\n") }
        }
    }
}

/** Every transcript is saved as it grows, so nothing is lost if the app closes. */
object Sessions {
    private fun dir(c: Context) = File(c.filesDir, "sessions").apply { mkdirs() }
    private fun file(c: Context, id: Long) = File(dir(c), "$id.json")

    fun save(c: Context, s: Session) {
        if (s.lines.isEmpty()) return
        val arr = JSONArray()
        s.lines.forEach {
            arr.put(JSONObject().put("s", it.source).put("t", it.target).put("ar", it.fromArabic).put("at", it.time))
        }
        file(c, s.id).writeText(JSONObject().put("id", s.id).put("title", s.title).put("lines", arr).toString())
    }

    fun load(c: Context, id: Long): Session? = try {
        val o = JSONObject(file(c, id).readText())
        val arr = o.getJSONArray("lines")
        Session(id, MutableList(arr.length()) { i ->
            val l = arr.getJSONObject(i)
            Line(l.getString("s"), l.getString("t"), l.getBoolean("ar"), l.getLong("at"))
        }, o.optString("title", ""))
    } catch (_: Exception) { null }

    fun all(c: Context): List<Session> =
        dir(c).listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { it.nameWithoutExtension.toLongOrNull() }
            .sortedDescending()
            .mapNotNull { load(c, it) }

    fun matches(s: Session, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return s.title.contains(q, true) || s.lines.any { it.source.contains(q, true) || it.target.contains(q, true) }
    }

    fun delete(c: Context, id: Long) { file(c, id).delete() }
}
