package com.smnexstudio.memora.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import com.smnexstudio.memora.R

/** The look of a card's front. */
enum class CardDesign(val id: String, val label: String) {
    MARIGOLD("marigold", "Marigold"),
    AIRMAIL("airmail", "Airmail"),
    RANGOLI("rangoli", "Rangoli"),
    BIG_NUMBER("number", "Big number"),
    CONFETTI("confetti", "Confetti");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: MARIGOLD
    }
}

/** What the picture is shaped for. */
enum class CardShape(val id: String, val label: String) {
    SQUARE("square", "Square chat"),   // 1:1, fits a chat bubble
    POSTCARD("postcard", "Postcard"),  // front and back, one under the other
    STATUS("status", "Status");        // 9:16, for WhatsApp Status

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: SQUARE
    }
}

/** Everything written on a card. */
data class CardContent(
    val headline: String,       // "Happy Birthday"
    val toLine: String,         // "dear Ananya"
    val number: String?,        // age / years, for the stamp and the Big number design
    val message: String,        // the wish, handwritten on the back
    val signature: String?,     // "— SM"
    val name: String,           // address line
    val address: String?,       // email or phone, second address line
    val postmark: String,       // "9 OCT\n2026"
    val stampColor: Int,
)

/**
 * Draws postcards as pictures, so the preview in the app is exactly what gets sent.
 * Sizes are in pixels and big enough to look sharp in WhatsApp and email.
 */
object Cards {
    private const val PAPER = 0xFFFFFDF8.toInt()
    private const val GROUND = 0xFFE6DFD2.toInt()
    private const val NAVY = 0xFF23395B.toInt()
    private const val RED = 0xFFB23A2E.toInt()
    private const val LINE = 0xFFD8CFBF.toInt()

    private class Fonts(val display: Typeface, val hand: Typeface, val handBold: Typeface, val sans: Typeface)

    @Volatile private var fonts: Fonts? = null

    private fun fonts(context: Context) = fonts ?: Fonts(
        ResourcesCompat.getFont(context, R.font.fraunces_extrabold_italic) ?: Typeface.SERIF,
        ResourcesCompat.getFont(context, R.font.caveat_medium) ?: Typeface.DEFAULT,
        ResourcesCompat.getFont(context, R.font.caveat_bold) ?: Typeface.DEFAULT_BOLD,
        ResourcesCompat.getFont(context, R.font.work_sans_semibold) ?: Typeface.DEFAULT,
    ).also { fonts = it }

    fun render(context: Context, design: CardDesign, shape: CardShape, c: CardContent, showNumber: Boolean = true): Bitmap {
        val f = fonts(context)
        return when (shape) {
            CardShape.SQUARE -> createBitmap(1080, 1080).also { bmp ->
                val cv = Canvas(bmp)
                cv.drawColor(PAPER)
                front(cv, RectF(0f, 0f, 1080f, 620f), design, c, f, showNumber)
                airmail(cv, RectF(0f, 620f, 1080f, 634f))
                back(cv, RectF(0f, 634f, 1080f, 1080f), c, f, compact = true)
            }
            CardShape.POSTCARD -> createBitmap(1500, 2060).also { bmp ->
                val cv = Canvas(bmp)
                cv.drawColor(GROUND)
                cv.withRotation(-0.8f, 750f, 520f) { front(this, RectF(30f, 30f, 1470f, 1000f), design, c, f, showNumber) }
                cv.withRotation(0.6f, 750f, 1540f) {
                    drawRect(RectF(30f, 1060f, 1470f, 2030f), fill(PAPER))
                    back(this, RectF(30f, 1060f, 1470f, 2030f), c, f, compact = false)
                }
            }
            CardShape.STATUS -> createBitmap(1080, 1920).also { bmp ->
                val cv = Canvas(bmp)
                front(cv, RectF(0f, 0f, 1080f, 1920f), design, c, f, showNumber, tall = true)
                // A small handwritten note pinned near the bottom.
                val note = RectF(70f, 1420f, 1010f, 1800f)
                cv.withRotation(-2f, note.centerX(), note.centerY()) {
                    drawRect(RectF(note.left, note.top + 10, note.right, note.bottom + 10), fill(0x33000000))
                    drawRect(note, fill(PAPER))
                    stamp(this, RectF(note.right - 190, note.top + 40, note.right - 40, note.top + 220), c, f)
                    text(this, c.message, f.hand, NAVY, RectF(note.left + 40, note.top + 36, note.right - 230, note.bottom - 30), 74f, 36f)
                }
            }
        }
    }

    // ---- Front ----

    private fun front(cv: Canvas, r: RectF, design: CardDesign, c: CardContent, f: Fonts, showNumber: Boolean, tall: Boolean = false) {
        val s = r.width() / 1000f
        cv.save()
        cv.clipRect(r)
        val ink: Int
        val toColor: Int
        when (design) {
            CardDesign.MARIGOLD -> {
                cv.drawRect(r, fill(0xFFF2A93B.toInt()))
                flower(cv, r.left + 20 * s, r.top + 20 * s, 150 * s)
                flower(cv, r.left + 230 * s, r.top + 10 * s, 70 * s, dark = true)
                flower(cv, r.right - 30 * s, r.bottom - 40 * s, 170 * s)
                flower(cv, r.right - 290 * s, r.bottom - 10 * s, 60 * s, dark = true)
                if (tall) flower(cv, r.right - 20 * s, r.top + 700 * s, 160 * s)
                ink = 0xFF5A1A0E.toInt(); toColor = ink
            }
            CardDesign.AIRMAIL -> {
                cv.drawRect(r, fill(PAPER))
                val band = 46 * s
                cv.save()
                cv.clipRect(r)
                cv.clipOutRect(RectF(r.left + band, r.top + band, r.right - band, r.bottom - band))
                cv.withRotation(45f, r.centerX(), r.centerY()) {
                    var y = -2000f
                    var red = true
                    while (y < 4000f) {
                        drawRect(-2000f, y, 4000f, y + 30 * s, fill(if (red) RED else NAVY))
                        y += 60 * s
                        red = !red
                    }
                }
                cv.restore()
                ink = NAVY; toColor = RED
            }
            CardDesign.RANGOLI -> {
                cv.drawRect(r, fill(0xFF7A1E2C.toInt()))
                val gold = 0xFFE8B23A.toInt()
                val stroke = stroke(gold, 6 * s)
                listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom).forEach { (cx, cy) ->
                    for (k in 1..3) diamond(cv, cx, cy, 70f * k * s, stroke)
                }
                for (i in 0 until 9) {
                    val cx = r.left + (i + 0.5f) * r.width() / 9
                    cv.drawCircle(cx, r.bottom - 40 * s, 7 * s, fill(gold))
                    cv.drawCircle(cx, r.top + 40 * s, 7 * s, fill(gold))
                }
                ink = gold; toColor = 0xFFF6E7C1.toInt()
            }
            CardDesign.BIG_NUMBER -> {
                cv.drawRect(r, fill(NAVY))
                val n = c.number?.takeIf { showNumber }
                if (n != null) {
                    val p = paint(f.display, 0x33FFFDF8, r.height() * (if (tall) 0.45f else 0.95f))
                    p.textAlign = Paint.Align.CENTER
                    cv.drawText(n, r.centerX() + 60 * s, r.centerY() + p.textSize * 0.35f, p)
                }
                ink = PAPER; toColor = 0xFFF2A93B.toInt()
            }
            CardDesign.CONFETTI -> {
                cv.drawRect(r, fill(PAPER))
                val colors = intArrayOf(RED, NAVY, 0xFFF2A93B.toInt(), 0xFF2E7060.toInt(), 0xFF5B4E9A.toInt())
                val rnd = java.util.Random(7)
                repeat(if (tall) 90 else 55) { i ->
                    val x = r.left + rnd.nextFloat() * r.width()
                    val y = r.top + rnd.nextFloat() * r.height()
                    val keepClear = y > r.top + r.height() * 0.28f && y < r.top + r.height() * 0.72f && x > r.left + r.width() * 0.08f && x < r.right - r.width() * 0.08f
                    if (keepClear) return@repeat
                    cv.withRotation(rnd.nextFloat() * 180, x, y) { drawRect(x - 9 * s, y - 22 * s, x + 9 * s, y + 22 * s, fill(colors[i % colors.size])) }
                }
                ink = NAVY; toColor = RED
            }
        }
        // Greeting, centred.
        val head = paint(f.display, ink, 96 * s)
        head.textAlign = Paint.Align.CENTER
        val to = paint(f.handBold, toColor, 92 * s)
        to.textAlign = Paint.Align.CENTER
        val maxW = r.width() - 120 * s
        shrinkToFit(head, c.headline, maxW)
        shrinkToFit(to, c.toLine, maxW)
        val cy = if (tall) r.top + r.height() * 0.32f else r.centerY()
        cv.drawText(c.headline, r.centerX(), cy - 6 * s, head)
        cv.drawText(c.toLine, r.centerX(), cy + to.textSize * 0.95f, to)
        cv.restore()
    }

    private fun flower(cv: Canvas, cx: Float, cy: Float, radius: Float, dark: Boolean = false) {
        cv.drawCircle(cx, cy, radius, fill(0xFFF6C453.toInt()))
        cv.drawCircle(cx, cy, radius * 0.64f, fill(if (dark) 0xFFC8551A.toInt() else 0xFFE07A1F.toInt()))
        cv.drawCircle(cx, cy, radius * 0.22f, fill(0xFFC8551A.toInt()))
    }

    private fun diamond(cv: Canvas, cx: Float, cy: Float, half: Float, p: Paint) {
        val path = Path().apply { moveTo(cx, cy - half); lineTo(cx + half, cy); lineTo(cx, cy + half); lineTo(cx - half, cy); close() }
        cv.drawPath(path, p)
    }

    private fun airmail(cv: Canvas, r: RectF) {
        var x = r.left
        val u = r.height() * 0.9f
        while (x < r.right) {
            cv.drawRect(x, r.top, x + 2.5f * u, r.bottom, fill(RED))
            cv.drawRect(x + 3.3f * u, r.top, x + 5.8f * u, r.bottom, fill(NAVY))
            x += 6.6f * u
        }
    }

    // ---- Back ----

    private fun back(cv: Canvas, r: RectF, c: CardContent, f: Fonts, compact: Boolean) {
        val pad = if (compact) 40f else 60f
        val right = if (compact) 200f else 380f
        val msgRect = RectF(r.left + pad, r.top + pad * 0.8f, r.right - right - pad, r.bottom - pad * 0.8f)
        val words = c.message + (c.signature?.let { "\n— $it" } ?: "")
        text(cv, words, f.hand, NAVY, msgRect, if (compact) 64f else 84f, 34f)
        if (!compact) {
            cv.drawLine(r.right - right - pad / 2, r.top + pad, r.right - right - pad / 2, r.bottom - pad, Paint().apply { color = LINE; strokeWidth = 3f })
        }
        val stampW = if (compact) 130f else 170f
        val stampRect = RectF(r.right - pad - stampW, r.top + pad, r.right - pad, r.top + pad + stampW * 1.2f)
        stamp(cv, stampRect, c, f)
        if (!compact) {
            // Postmark over the stamp's corner.
            val pm = stroke(0x7323395B, 5f)
            val cx = stampRect.left - 20f
            val cy = stampRect.bottom - 10f
            cv.drawCircle(cx, cy, 70f, pm)
            val pt = paint(f.sans, 0x9923395B.toInt(), 22f).apply { textAlign = Paint.Align.CENTER }
            cv.withRotation(-14f, cx, cy) {
                c.postmark.split("\n").forEachIndexed { i, line -> drawText(line, cx, cy - 4f + i * 28f, pt) }
            }
            // Address lines.
            val ax = r.right - right
            val line = Paint().apply { color = NAVY; strokeWidth = 3f }
            val name = paint(f.sans, NAVY, 34f)
            shrinkToFit(name, c.name, right - pad)
            var y = stampRect.bottom + 120f
            cv.drawText(c.name, ax, y, name); cv.drawLine(ax, y + 14f, r.right - pad, y + 14f, line)
            c.address?.let {
                val ap = paint(f.sans, 0xFF4A5B78.toInt(), 26f)
                shrinkToFit(ap, it, right - pad)
                y += 70f
                cv.drawText(it, ax, y, ap); cv.drawLine(ax, y + 14f, r.right - pad, y + 14f, line)
            }
        }
    }

    private fun stamp(cv: Canvas, r: RectF, c: CardContent, f: Fonts) {
        cv.drawRect(r, stroke(c.stampColor, 5f).apply { pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f) })
        val inner = RectF(r.left + 12, r.top + 12, r.right - 12, r.bottom - 12)
        cv.drawRect(inner, fill(c.stampColor))
        val label = c.number ?: "M"
        val p = paint(f.display, PAPER, inner.height() * 0.42f).apply { textAlign = Paint.Align.CENTER }
        shrinkToFit(p, label, inner.width() * 0.8f)
        cv.drawText(label, inner.centerX(), inner.centerY() + p.textSize * 0.35f, p)
    }

    // ---- Text helpers ----

    /** Wraps [s] into [r], shrinking from [max] towards [min] until it fits. */
    private fun text(cv: Canvas, s: String, face: Typeface, color: Int, r: RectF, max: Float, min: Float) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; this.color = color }
        var size = max
        var layout: StaticLayout
        while (true) {
            tp.textSize = size
            layout = StaticLayout.Builder.obtain(s, 0, s.length, tp, r.width().toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.0f).build()
            if (layout.height <= r.height() || size <= min) break
            size -= 2f
        }
        cv.withTranslation(r.left, r.top) {
            clipRect(0f, 0f, r.width(), r.height())
            layout.draw(this)
        }
    }

    private fun shrinkToFit(p: Paint, s: String, maxW: Float) {
        while (p.measureText(s) > maxW && p.textSize > 12f) p.textSize -= 2f
    }

    private fun paint(face: Typeface, color: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; this.color = color; textSize = size }
    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = w }
}
