package app.openeq

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * Equalizer icon drawn on a 512 x 512 grid.
 * rounded = true  : rounded square, good for a preview
 * rounded = false : full square, use as the launcher background
 * value           : knob position, 0f..1f
 *
 * Preview: setContentView(EqualizerIcon(this))
 */
class EqualizerIcon(
    context: Context,
    private val rounded: Boolean = true,
    private val value: Float = 0.7f
) : View(context) {

    private val accent = Color.rgb(169, 139, 255)
    private val deep = Color.rgb(107, 79, 216)
    private val light = Color.rgb(220, 208, 255)
    private val track = Color.rgb(43, 39, 64)
    private val edge = Color.rgb(58, 53, 82)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = RectF()

    private val heights = floatArrayOf(50f, 84f, 120f, 150f, 110f, 78f, 48f)
    private val barW = 30f
    private val gap = 15f
    private val baseline = 270f
    private val startX = (512f - (heights.size * barW + (heights.size - 1) * gap)) / 2f

    private val bgShader = LinearGradient(
        0f, 0f, 512f, 512f,
        intArrayOf(Color.rgb(28, 26, 41), Color.rgb(11, 10, 18), Color.rgb(20, 16, 42)),
        null, Shader.TileMode.CLAMP
    )
    private val glowShader = RadialGradient(
        256f, 230f, 230f,
        intArrayOf(Color.argb(51, 169, 139, 255), Color.argb(0, 169, 139, 255)),
        null, Shader.TileMode.CLAMP
    )
    private val barShaders = Array(heights.size) { i ->
        LinearGradient(0f, baseline - heights[i] + 13f, 0f, baseline, accent, deep, Shader.TileMode.CLAMP)
    }
    private val ringShader = LinearGradient(200f, 400f, 320f, 300f, deep, accent, Shader.TileMode.CLAMP)
    private val discShader = RadialGradient(
        246f, 331f, 54f,
        Color.rgb(49, 44, 71), Color.rgb(21, 19, 29),
        Shader.TileMode.CLAMP
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = minOf(width, height) / 512f
        val save = canvas.save()
        canvas.translate((width - 512f * s) / 2f, (height - 512f * s) / 2f)
        canvas.scale(s, s)
        drawBackground(canvas)
        drawBars(canvas)
        drawKnob(canvas)
        canvas.restoreToCount(save)
    }

    private fun shape(c: Canvas, p: Paint) {
        if (rounded) c.drawRoundRect(0f, 0f, 512f, 512f, 112f, 112f, p)
        else c.drawRect(0f, 0f, 512f, 512f, p)
    }

    private fun drawBackground(c: Canvas) {
        fill.shader = bgShader
        shape(c, fill)
        fill.shader = glowShader
        shape(c, fill)
        fill.shader = null
        if (rounded) {
            stroke.shader = null
            stroke.strokeWidth = 3f
            stroke.color = edge
            c.drawRoundRect(2f, 2f, 510f, 510f, 110f, 110f, stroke)
        }
    }

    private fun drawBars(c: Canvas) {
        for (i in heights.indices) {
            val x = startX + i * (barW + gap)
            val top = baseline - heights[i]
            val bodyTop = top + 13f

            fill.shader = null
            for (k in 2 downTo 1) {
                val e = k * 4f
                fill.color = Color.argb(24 * (3 - k), 169, 139, 255)
                c.drawRoundRect(x - e, bodyTop - e, x + barW + e, baseline + e, 10f + e, 10f + e, fill)
            }

            fill.shader = barShaders[i]
            c.drawRoundRect(x, bodyTop, x + barW, baseline, 10f, 10f, fill)

            fill.shader = null
            fill.color = Color.argb(48, 169, 139, 255)
            c.drawRoundRect(x - 7f, top - 3f, x + barW + 7f, top + 10f, 6.5f, 6.5f, fill)
            fill.color = light
            c.drawRoundRect(x - 4f, top, x + barW + 4f, top + 7f, 3.5f, 3.5f, fill)
        }
    }

    private fun drawKnob(c: Canvas) {
        val cx = 256f
        val cy = 345f
        val r = 50f
        val sweep = 270f * value.coerceIn(0f, 1f)
        arc.set(cx - r, cy - r, cx + r, cy + r)

        stroke.shader = null
        stroke.strokeWidth = 12f
        stroke.color = track
        c.drawArc(arc, 135f, 270f, false, stroke)

        for (k in 3 downTo 1) {
            stroke.strokeWidth = 12f + k * 5f
            stroke.color = Color.argb(18 * (4 - k), 169, 139, 255)
            c.drawArc(arc, 135f, sweep, false, stroke)
        }
        stroke.strokeWidth = 12f
        stroke.shader = ringShader
        c.drawArc(arc, 135f, sweep, false, stroke)
        stroke.shader = null

        fill.shader = discShader
        c.drawCircle(cx, cy, 36f, fill)
        fill.shader = null
        stroke.strokeWidth = 2f
        stroke.color = edge
        c.drawCircle(cx, cy, 36f, stroke)

        val a = Math.toRadians((135f + sweep).toDouble())
        val dx = Math.cos(a).toFloat()
        val dy = Math.sin(a).toFloat()
        stroke.strokeWidth = 6f
        stroke.color = accent
        c.drawLine(cx + dx * 12f, cy + dy * 12f, cx + dx * 26f, cy + dy * 26f, stroke)

        val tx = cx + dx * r
        val ty = cy + dy * r
        fill.color = Color.argb(64, 169, 139, 255)
        c.drawCircle(tx, ty, 12f, fill)
        fill.color = light
        c.drawCircle(tx, ty, 5f, fill)
    }
}
