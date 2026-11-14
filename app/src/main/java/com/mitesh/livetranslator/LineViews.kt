package com.mitesh.livetranslator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView
import android.widget.Toast

/** Shared rendering for transcript lines, live and saved. */
object LineViews {
    private val ARABIC = Regex("[\\u0600-\\u06FF]")

    fun isArabic(text: String) = ARABIC.containsMatchIn(text)

    /** (source sp, translation sp) for each text-size setting. */
    private fun sizes(setting: Int) = when (setting) {
        0 -> 14f to 18f
        2 -> 18f to 27f
        else -> 15f to 21f
    }

    fun bind(v: View, line: Line, stamp: String, sizeSetting: Int) {
        val (srcSp, tgtSp) = sizes(sizeSetting)
        v.findViewById<TextView>(R.id.meta).text = "$stamp   ${line.direction}"
        apply(v.findViewById(R.id.source), line.source, srcSp)
        apply(v.findViewById(R.id.target), line.target, tgtSp)
    }

    fun direct(tv: TextView, text: String) {
        // Arabic reads right-to-left and sits on the right edge.
        tv.textDirection = if (isArabic(text)) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        tv.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
    }

    private fun apply(tv: TextView, text: String, sp: Float) {
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        tv.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        direct(tv, text)
    }

    /** Long-press a line to copy both languages. */
    fun copyOnLongPress(v: View, line: Line) {
        v.setOnLongClickListener {
            val cm = it.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Bayan", "${line.source}\n${line.target}"))
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            Toast.makeText(it.context, "Copied", Toast.LENGTH_SHORT).show()
            true
        }
    }
}
