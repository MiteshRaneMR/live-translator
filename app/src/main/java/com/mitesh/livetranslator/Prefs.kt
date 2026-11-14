package com.mitesh.livetranslator

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun key(c: Context) = p(c).getString("key", "")!!.trim()
    fun region(c: Context) = p(c).getString("region", "")!!.trim().lowercase()
    fun isSet(c: Context) = key(c).isNotEmpty() && region(c).isNotEmpty()

    /** English locale used for recognition: en-IN, en-US or en-GB. */
    fun english(c: Context) = p(c).getString("english", "en-IN")!!

    /** Names and terms Bayan should expect to hear, one per line. */
    fun vocabulary(c: Context) = p(c).getString("vocab", DEFAULT_VOCAB)!!

    /** 0 = compact, 1 = standard, 2 = large. */
    fun textSize(c: Context) = p(c).getInt("size", 1)

    fun save(c: Context, key: String, region: String, english: String, vocab: String, size: Int) =
        p(c).edit()
            .putString("key", key.trim())
            .putString("region", region.trim().lowercase())
            .putString("english", english)
            .putString("vocab", vocab.trim())
            .putInt("size", size)
            .apply()

    // ---- Monthly listening meter (Azure's free tier is per calendar month) ----

    const val FREE_HOURS = 5.0
    private fun month() = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    fun usedSeconds(c: Context): Long =
        if (p(c).getString("usageMonth", "") == month()) p(c).getLong("usageSec", 0L) else 0L

    fun addUsage(c: Context, seconds: Long) {
        if (seconds <= 0) return
        p(c).edit().putString("usageMonth", month()).putLong("usageSec", usedSeconds(c) + seconds).apply()
    }

    /** e.g. "3.8 of 5 free hours left this month". */
    fun usageLine(c: Context): String {
        val left = FREE_HOURS - usedSeconds(c) / 3600.0
        return if (left > 0.05) String.format(Locale.US, "%.1f of 5 free hours left this month", left)
        else "Free hours used this month. Listening is billed per hour."
    }

    fun phrases(c: Context): List<String> =
        vocabulary(c).lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(500)

    private val DEFAULT_VOCAB = listOf(
        "IRR", "JV", "NOI", "GDV", "LTV", "DSCR", "cap rate", "sqm", "SAR",
        "Riyadh", "Jeddah", "Makkah", "Madinah", "Dammam", "KAFD", "PIF", "Wafi",
        "King Fahd Road", "Iqama", "Absher", "Muqeem", "Ejar",
        "term sheet", "investment committee", "masterplan", "off-plan", "mixed-use"
    ).joinToString("\n")
}
