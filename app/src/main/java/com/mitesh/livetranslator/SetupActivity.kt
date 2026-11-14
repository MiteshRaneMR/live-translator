package com.mitesh.livetranslator

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.mitesh.livetranslator.databinding.ActivitySetupBinding

/** First-run connection screen, and the full settings screen afterwards. */
class SetupActivity : AppCompatActivity() {

    private lateinit var b: ActivitySetupBinding
    private var revealed = false
    private var syncing = false

    private val regions = listOf("UAE North" to "uaenorth", "West Europe" to "westeurope", "East US" to "eastus")
    private val accents = listOf("Indian" to "en-IN", "American" to "en-US", "British" to "en-GB")
    private val sizes = listOf("Compact" to 0, "Standard" to 1, "Large" to 2)

    private lateinit var regionChips: List<TextView>
    private lateinit var accentChips: List<TextView>
    private lateinit var sizeChips: List<TextView>
    private var accent = "en-IN"
    private var size = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(b.root)

        val firstRun = !Prefs.isSet(this)
        b.btnBack.visibility = if (firstRun) View.INVISIBLE else View.VISIBLE
        b.btnBack.setOnClickListener { finish() }
        if (!firstRun) {
            b.title.text = "Settings"
            b.mark.visibility = View.GONE
            b.intro.text = Prefs.usageLine(this) + "."
            b.btnSave.text = "Save"
        }
        // Keep the first run short; everything else has sensible defaults.
        b.hearing.visibility = if (firstRun) View.GONE else View.VISIBLE

        b.inputKey.setText(Prefs.key(this))
        b.inputRegion.setText(Prefs.region(this).ifEmpty { "uaenorth" })
        b.inputVocab.setText(Prefs.vocabulary(this))
        accent = Prefs.english(this)
        size = Prefs.textSize(this)

        regionChips = chips(b.regionChips, regions.map { it.first }) { i ->
            syncing = true; b.inputRegion.setText(regions[i].second); syncing = false
        }
        accentChips = chips(b.accentChips, accents.map { it.first }) { i -> accent = accents[i].second }
        sizeChips = chips(b.sizeChips, sizes.map { it.first }) { i -> size = sizes[i].second }
        sync()

        b.inputRegion.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun afterTextChanged(s: Editable?) { if (!syncing) sync() }
        })

        b.btnReveal.setOnClickListener {
            revealed = !revealed
            b.inputKey.inputType = InputType.TYPE_CLASS_TEXT or
                (if (revealed) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
            b.inputKey.typeface = ResourcesCompat.getFont(this, R.font.plex_regular)
            b.inputKey.setSelection(b.inputKey.text.length)
            b.btnReveal.text = if (revealed) "Hide" else "Show"
        }

        b.btnSave.setOnClickListener {
            val key = b.inputKey.text.toString().trim()
            val region = b.inputRegion.text.toString().trim().replace(" ", "")
            when {
                key.length < 20 -> b.inputKey.error = "Paste the full key from Azure"
                region.isEmpty() -> b.inputRegion.error = "Choose a region"
                else -> {
                    Prefs.save(this, key, region, accent, b.inputVocab.text.toString(), size)
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    private fun dp(v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun chips(host: LinearLayout, labels: List<String>, onPick: (Int) -> Unit): List<TextView> =
        labels.mapIndexed { i, label ->
            TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = ResourcesCompat.getFont(context, R.font.plex_medium)
                setTextColor(ContextCompat.getColorStateList(context, R.color.chip_text))
                background = ContextCompat.getDrawable(context, R.drawable.chip_bg)
                setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onPick(i)
                    sync()
                }
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40))
                if (i > 0) lp.marginStart = dp(8)
                host.addView(this, lp)
            }
        }

    private fun sync() {
        val region = b.inputRegion.text.toString().trim().lowercase()
        regions.forEachIndexed { i, (_, code) -> regionChips[i].isSelected = code == region }
        accents.forEachIndexed { i, (_, code) -> accentChips[i].isSelected = code == accent }
        sizes.forEachIndexed { i, (_, v) -> sizeChips[i].isSelected = v == size }
    }
}
