package com.mitesh.livetranslator

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * The one bold element on screen.
 * Idle: a sand disc with a mic glyph.
 * Listening: the disc turns to ink with a sand stop glyph, and rings ripple
 * outward; they swell while speech is actually being heard.
 */
class MicButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val sand = ContextCompat.getColor(context, R.color.sand)
    private val ink = ContextCompat.getColor(context, R.color.ink)
    private val dusk = ContextCompat.getColor(context, R.color.dusk)
    private val density = resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.6f * density
    }
    private val rect = RectF()

    var listening = false
        set(value) {
            field = value
            if (value) ripple.start() else { ripple.cancel(); phase = 0f; energy = 0f }
            invalidate()
        }

    var connecting = false
        set(value) { field = value; invalidate() }

    private var phase = 0f
    private var energy = 0f          // 0..1, raised by speech, decays over time
    private var lastSpeech = 0L

    private val ripple = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            val since = System.currentTimeMillis() - lastSpeech
            val target = if (since < 600) 1f else 0f
            energy += (target - energy) * 0.08f
            invalidate()
        }
    }

    /** Call whenever partial speech arrives. */
    fun pulse() { lastSpeech = System.currentTimeMillis() }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val max = min(width, height) / 2f
        val core = 36f * density

        if (listening) {
            // Three staggered ripples.
            for (i in 0 until 3) {
                val p = (phase + i / 3f) % 1f
                val reach = core + (max - core) * p * (0.55f + 0.45f * energy)
                ring.strokeWidth = (1.5f + 1.5f * energy) * density
                ring.color = sand
                ring.alpha = ((1f - p) * (90 + 120 * energy)).toInt().coerceIn(0, 255)
                canvas.drawCircle(cx, cy, reach, ring)
            }
            fill.color = dusk
            canvas.drawCircle(cx, cy, core, fill)
            ring.strokeWidth = 2f * density
            ring.color = sand
            ring.alpha = 255
            canvas.drawCircle(cx, cy, core, ring)
            // Stop glyph
            fill.color = sand
            val s = 9f * density
            rect.set(cx - s, cy - s, cx + s, cy + s)
            canvas.drawRoundRect(rect, 3f * density, 3f * density, fill)
        } else {
            fill.color = sand
            fill.alpha = if (connecting) 140 else 255
            canvas.drawCircle(cx, cy, core, fill)
            fill.alpha = 255
            drawMic(canvas, cx, cy)
        }
    }

    private fun drawMic(c: Canvas, cx: Float, cy: Float) {
        val d = density
        glyph.color = ink
        // Capsule
        rect.set(cx - 6 * d, cy - 14 * d, cx + 6 * d, cy + 4 * d)
        fill.color = ink
        c.drawRoundRect(rect, 6 * d, 6 * d, fill)
        // Cradle
        rect.set(cx - 11 * d, cy - 8 * d, cx + 11 * d, cy + 10 * d)
        c.drawArc(rect, 0f, 180f, false, glyph)
        // Stem and base
        c.drawLine(cx, cy + 10 * d, cx, cy + 15 * d, glyph)
        c.drawLine(cx - 6 * d, cy + 15 * d, cx + 6 * d, cy + 15 * d, glyph)
    }

    override fun onDetachedFromWindow() {
        ripple.cancel()
        super.onDetachedFromWindow()
    }
}
