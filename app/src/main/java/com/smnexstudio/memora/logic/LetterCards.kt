package com.smnexstudio.memora.logic

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Faces the letter cards write with. */
class LetterFonts(val script: Typeface, val hand: Typeface, val mono: Typeface, val serif: Typeface)

/**
 * The "letter" designs: one sheet with the greeting in script, the wish written out and the decoration
 * around it, instead of a picture front and a written back. Everything is drawn here, nothing is a picture file.
 */
object LetterCards {

    /** Size of a letter card for [shape]: square for chats, portrait for postcards and Status. */
    fun size(shape: CardShape): Pair<Int, Int> = when (shape) {
        CardShape.SQUARE -> 1080 to 1080
        CardShape.POSTCARD -> 1240 to 1748
        CardShape.STATUS -> 1080 to 1920
    }

    fun draw(cv: Canvas, w: Float, h: Float, design: CardDesign, c: CardContent, f: LetterFonts) {
        val s = w / 1000f
        val square = h / w < 1.2f
        when (design) {
            CardDesign.LOVE_LETTER -> loveLetter(cv, w, h, s, square, c, f)
            CardDesign.BLUSH -> blush(cv, w, h, s, square, c, f)
            CardDesign.RIBBONS -> ribbons(cv, w, h, s, square, c, f)
            CardDesign.PARTY -> party(cv, w, h, s, square, c, f)
            CardDesign.WILDFLOWER -> wildflower(cv, w, h, s, square, c, f)
            CardDesign.GOLDEN -> golden(cv, w, h, s, square, c, f)
            CardDesign.PLAIN -> plain(cv, w, h, s, square, c, f)
            CardDesign.CLASSIC -> classic(cv, w, h, s, square, c, f)
            CardDesign.MEMORY -> memory(cv, w, h, s, square, c, f)
            else -> Unit
        }
    }

    // ---- Designs ----

    private fun loveLetter(cv: Canvas, w: Float, h: Float, s: Float, square: Boolean, c: CardContent, f: LetterFonts) {
        val ink = 0xFF2E2828.toInt()
        val rose = 0xFFB0566A.toInt()
        cv.drawColor(0xFFF1E6DF.toInt())
        // A rose card behind the paper, slightly turned.
        cv.withRotation(-4f, w * 0.4f, h * 0.5f) { drawRect(RectF(w * 0.02f, h * 0.06f, w * 0.78f, h * 0.97f), fill(0xFFC99A98.toInt())) }
        // Deckled paper.
        val paper = RectF(w * 0.07f, h * 0.035f, w * 0.86f, h * 0.965f)
        cv.drawPath(deckled(paper, 9f * s, 3), fill(0x22000000).also { it.setShadowLayer(18f * s, 0f, 6f * s, 0x33000000) })
        cv.drawPath(deckled(paper, 9f * s, 3), fill(0xFFFBF7F0.toInt()))
        // Gold wire heart clip and dried flowers on the right edge.
        cv.withRotation(18f, paper.right - 30 * s, paper.top + 40 * s) {
            drawPath(heart(paper.right - 30 * s, paper.top + 40 * s, 90 * s), stroke(0xFFC6A055.toInt(), 6 * s))
        }
        sprig(cv, paper.right + 40 * s, h * 0.93f, h * (if (square) 0.55f else 0.5f), s)

        val pad = 70 * s
        val head = RectF(paper.left + pad, paper.top + h * 0.05f, paper.right - pad * 0.6f, paper.top + h * (if (square) 0.22f else 0.17f))
        val headPaint = paint(f.script, ink, 150 * s)
        shrink(headPaint, c.headline, head.width() * 0.85f)
        cv.drawText(c.headline, head.left, head.bottom - 20 * s, headPaint)
        val endX = head.left + headPaint.measureText(c.headline)
        cv.drawPath(heart(endX + 45 * s, head.bottom - 70 * s, 48 * s), fill(0xFFB3263A.toInt()))
        // Brush underline.
        val brush = Path().apply {
            moveTo(head.left, head.bottom + 4 * s)
            quadTo(head.centerX(), head.bottom - 10 * s, head.right - 40 * s, head.bottom + 2 * s)
        }
        cv.drawPath(brush, stroke(0x99C77F86.toInt(), 9 * s).apply { strokeCap = Paint.Cap.ROUND })
        val to = paint(f.script, rose, 74 * s)
        shrink(to, c.toLine, head.width())
        cv.drawText(c.toLine, head.left, head.bottom + to.textSize * 1.05f, to)

        val body = RectF(head.left, head.bottom + to.textSize * 1.6f, paper.right - pad * 1.4f, paper.bottom - pad * 0.8f)
        text(cv, c.message + (c.signature?.let { "\n\n— $it" } ?: ""), f.mono, ink, body, (if (square) 40f else 46f) * s, 22f * s, Layout.Alignment.ALIGN_NORMAL, 1.35f)
    }

    private fun blush(cv: Canvas, w: Float, h: Float, s: Float, square: Boolean, c: CardContent, f: LetterFonts) {
        val brown = 0xFF3F2A26.toInt()
        val rose = 0xFFC66F7E.toInt()
        cv.drawColor(0xFFFCF6F2.toInt())
        val top = if (square) 60 * s else 120 * s
        cv.drawPath(heart(w / 2, top + 40 * s, 60 * s), fill(0xFFEBA7AE.toInt()))
        rays(cv, w / 2, top + 40 * s, 60 * s, 0xFF3F2A26.toInt(), s)

        val headTop = top + 110 * s
        val head = paint(f.script, brown, (if (square) 150 else 190) * s).center()
        shrink(head, c.headline, w * 0.84f)
        cv.drawText(c.headline, w / 2, headTop + head.textSize * 0.8f, head)
        val to = paint(f.script, rose, 84 * s).center()
        shrink(to, c.toLine, w * 0.8f)
        val toY = headTop + head.textSize * 0.8f + to.textSize * 1.05f
        cv.drawText(c.toLine, w / 2, toY, to)

        val bowY = h - (if (square) 120 else 220) * s
        bow(cv, w / 2, bowY, 120 * s, 0xFFF0BFC3.toInt(), 0xFFD98F99.toInt(), s, tails = true, w = w)
        val body = RectF(w * 0.13f, toY + 50 * s, w * 0.87f, bowY - 110 * s)
        text(cv, c.message + (c.signature?.let { "\n— $it" } ?: ""), f.hand, brown, body, (if (square) 58f else 66f) * s, 30f * s, Layout.Alignment.ALIGN_CENTER, 1.1f)
    }

    private fun ribbons(cv: Canvas, w: Float, h: Float, s: Float, square: Boolean, c: CardContent, f: LetterFonts) {
        val navy = 0xFF1E2A46.toInt()
        val blue = 0xFF8EAEE6.toInt()
        val pale = 0xFFC9D9F6.toInt()
        cv.drawColor(0xFFFDFEFF.toInt())
        val inset = 70 * s
        // A wavy ribbon round all four sides.
        val frame = RectF(inset, inset, w - inset, h - inset)
        cv.drawPath(wavyFrame(frame, 24 * s, 110 * s), stroke(blue, 15 * s).apply { strokeCap = Paint.Cap.ROUND })
        cv.drawPath(wavyFrame(frame, 24 * s, 110 * s), stroke(pale, 5 * s).apply { strokeCap = Paint.Cap.ROUND })
        bow(cv, w / 2, inset, 85 * s, pale, blue, s)
        bow(cv, w / 2, h - inset, 85 * s, pale, blue, s)
        listOf(frame.left to frame.top, frame.right to frame.top, frame.left to frame.bottom, frame.right to frame.bottom).forEach { (x, y) -> blossom(cv, x, y, 26 * s, blue) }
        val rnd = java.util.Random(11)
        repeat(if (square) 6 else 10) {
            val left = it % 2 == 0
            val x = if (left) frame.left + 40 * s else frame.right - 40 * s
            val y = frame.top + 150 * s + rnd.nextFloat() * (frame.height() - 300 * s)
            cv.drawPath(heart(x, y, 26 * s), fill(blue))
        }

        val headY = frame.top + (if (square) 0.27f else 0.22f) * frame.height()
        val head = paint(f.script, navy, (if (square) 128 else 150) * s).center()
        shrink(head, c.headline, frame.width() * 0.82f)
        cv.drawText(c.headline, w / 2, headY, head)
        val to = paint(f.script, blue, 72 * s).center()
        shrink(to, c.toLine, frame.width() * 0.8f)
        cv.drawText(c.toLine, w / 2, headY + to.textSize * 1.05f, to)
        val k = if (square) 0.6f else 0.8f
        val cakeY = headY + to.textSize * 1.05f + 70 * s + 170 * k * s
        cake(cv, w / 2, cakeY, k * s, 0xFFFFFFFF.toInt(), blue, navy)
        val body = RectF(frame.left + 110 * s, cakeY + 120 * k * s, frame.right - 110 * s, frame.bottom - 120 * s)
        text(cv, c.message + (c.signature?.let { "\n— $it" } ?: ""), f.hand, navy, body, (if (square) 56f else 64f) * s, 28f * s, Layout.Alignment.ALIGN_CENTER, 1.1f)
    }

    private fun party(cv: Canvas, w: Float, h: Float, s: Float, square: Boolean, c: CardContent, f: LetterFonts) {
        val ink = 0xFF2B2B2B.toInt()
        val pink = 0xFFE88AA6.toInt()
        cv.drawColor(0xFFF7F2EA.toInt())
        val flags = intArrayOf(0xFFFBE08A.toInt(), 0xFFF6B8CB.toInt(), 0xFFA9D2F2.toInt(), 0xFFCDB8F0.toInt(), 0xFFBFE2B3.toInt())
        bunting(cv, 0f, 40 * s, w * 0.42f, 70 * s, 4, flags, s)
        bunting(cv, w * 0.58f, 70 * s, w, 40 * s, 4, flags.reversedArray(), s)
        val rnd = java.util.Random(5)
        repeat(if (square) 10 else 16) {
            val x = rnd.nextFloat() * w
            val y = 160 * s + rnd.nextFloat() * (h - 320 * s)
            if (x > w * 0.16f && x < w * 0.84f) return@repeat
            if (it % 2 == 0) sparkle(cv, x, y, 22 * s, 0xFFE0B63A.toInt()) else cv.drawPath(heart(x, y, 24 * s), fill(pink))
        }

        val headY = if (square) 300 * s else 420 * s
        val head = paint(f.script, ink, (if (square) 140 else 170) * s).center()
        shrink(head, c.headline, w * 0.84f)
        cv.drawText(c.headline, w / 2, headY, head)
        val to = paint(f.script, pink, 76 * s).center()
        shrink(to, c.toLine, w * 0.8f)
        cv.drawText(c.toLine, w / 2, headY + to.textSize * 1.05f, to)

        // Balloons bottom left, a cake bottom right.
        val base = h - (if (square) 90 else 150) * s
        val b = if (square) 0.75f else 1f
        balloon(cv, 95 * s, base - 260 * b * s, 66 * b * s, 0xFFF4A7BC.toInt(), base, s)
        balloon(cv, 170 * s, base - 200 * b * s, 58 * b * s, 0xFF9CC8EE.toInt(), base, s)
        cake(cv, w - 150 * s, base - 70 * b * s, b * s, 0xFFFFF4DE.toInt(), 0xFFF29BB4.toInt(), ink)
        val body = RectF(w * 0.26f, headY + to.textSize * 1.6f, w * 0.74f, base - (if (square) 60 else 260) * s)
        text(cv, c.message + (c.signature?.let { "\n— $it" } ?: ""), f.hand, ink, body, (if (square) 56f else 64f) * s, 28f * s, Layout.Alignment.ALIGN_CENTER, 1.1f)
    }

    private fun wildflower(cv: Canvas, w: Float, h: Float, s: Float, square: Boolean, c: CardContent, f: LetterFonts) {
        val brown = 0xFF6E5444.toInt()
        val rose = 0xFFCF8A88.toInt()
        cv.drawColor(0xFFFBF3EF.toInt())
        val top = if (square) 70 * s else 140 * s
        cv.drawPath(heart(w / 2, top, 42 * s), stroke(rose, 5 * s))

        val headY = top + (if (square) 190 else 250) * s
        val head = paint(f.script, brown, (if (square) 140 else 170) * s).center()
        shrink(head, c.headline, w * 0.84f)
        cv.drawText(c.headline, w / 2, headY, head)
        val to = paint(f.script, rose, 76 * s).center()
        shrink(to, c.toLine, w * 0.8f)
        cv.drawText(c.toLine, w / 2, headY + to.textSize * 1.05f, to)
        cv.drawPath(heart(w / 2, headY + to.textSize * 1.05f + 70 * s, 34 * s), stroke(brown, 4 * s))

        val ground = h - (if (square) 50 else 90) * s
        val meadowH = h * (if (square) 0.3f else 0.26f)
        meadow(cv, 0f, ground, w * 0.2f, meadowH, s, rose)
        meadow(cv, w * 0.8f, ground, w, meadowH, s, rose)
        val body = RectF(w * 0.23f, headY + to.textSize * 1.05f + 120 * s, w * 0.77f, ground - (if (square) 20 else 160) * s)
        text(cv, c.message + (c.signature?.let { "\n— $it" } ?: ""), f.serif, brown, body, (if (square) 42f else 50f) * s, 22f * s, Layout.Alignment.ALIGN_CENTER, 1.3f)
    }

    private fun golden(cv: Canvas, w: Float, h: Float, s: Float, square: Boolean, c: CardContent, f: LetterFonts) {
        val ink = 0xFF3A2E22.toInt()
        val gold = 0xFFC29B4A.toInt()
        cv.drawColor(0xFFFBF6EC.toInt())
        frame(cv, RectF(50 * s, 50 * s, w - 50 * s, h - 50 * s), gold, s, double = true)
        // Two linked rings.
        val ry = if (square) 175 * s else 260 * s
        val ring = stroke(gold, 11 * s)
        cv.drawCircle(w / 2 - 42 * s, ry, 62 * s, ring)
        cv.drawCircle(w / 2 + 42 * s, ry, 62 * s, ring)
        cv.drawCircle(w / 2 - 42 * s, ry, 62 * s, stroke(0x55FFFFFF, 3 * s))
        sparkle(cv, w / 2 + 112 * s, ry - 70 * s, 18 * s, gold)

        val headY = ry + (if (square) 210 else 260) * s
        val head = paint(f.script, ink, (if (square) 130 else 160) * s).center()
        shrink(head, c.headline, w * 0.8f)
        cv.drawText(c.headline, w / 2, headY, head)
        val to = paint(f.script, gold, 72 * s).center()
        shrink(to, c.toLine, w * 0.78f)
        cv.drawText(c.toLine, w / 2, headY + to.textSize * 1.05f, to)
        val body = RectF(w * 0.16f, headY + to.textSize * 1.7f, w * 0.84f, h - 120 * s)
        text(cv, c.message + (c.signature?.let { "\n— $it" } ?: ""), f.hand, ink, body, (if (square) 56f else 64f) * s, 28f * s, Layout.Alignment.ALIGN_CENTER, 1.1f)
    }

    /** Quiet and neutral: thin rules and serif type, for any occasion. */
    private fun plain(cv: Canvas, w: Float, h: Float, s: Float, square: Boolean, c: CardContent, f: LetterFonts) {
        val rule = 0xFFB5B0A6.toInt()
        cv.drawColor(0xFFFBFAF7.toInt())
        frame(cv, RectF(60 * s, 60 * s, w - 60 * s, h - 60 * s), rule, s, double = true)
        serifBlock(cv, w, h, s, square, c, f, 0xFF2F2F2F.toInt(), 0xFF6B6760.toInt(), rule, if (square) 0.2f else 0.24f)
    }

    /** Navy with a gold frame: graduations, farewells, achievements. */
    private fun classic(cv: Canvas, w: Float, h: Float, s: Float, square: Boolean, c: CardContent, f: LetterFonts) {
        val gold = 0xFFCFAE68.toInt()
        cv.drawColor(0xFF1F2C44.toInt())
        val r = RectF(55 * s, 55 * s, w - 55 * s, h - 55 * s)
        frame(cv, r, gold, s, double = true)
        listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom).forEach { (x, y) ->
            val half = 16 * s
            cv.drawPath(Path().apply { moveTo(x, y - half); lineTo(x + half, y); lineTo(x, y + half); lineTo(x - half, y); close() }, fill(gold))
        }
        serifBlock(cv, w, h, s, square, c, f, 0xFFF4EBD8.toInt(), gold, gold, if (square) 0.22f else 0.26f)
    }

    /** For remembrance: soft and muted, an olive sprig and a small lit lamp, nothing festive. */
    private fun memory(cv: Canvas, w: Float, h: Float, s: Float, square: Boolean, c: CardContent, f: LetterFonts) {
        cv.drawColor(0xFFF2F0EB.toInt())
        frame(cv, RectF(60 * s, 60 * s, w - 60 * s, h - 60 * s), 0xFFC9C5BC.toInt(), s, double = false)
        olive(cv, w / 2, if (square) 150 * s else 230 * s, 210 * s, 0xFF8E9A86.toInt(), s)
        diya(cv, w / 2, h - (if (square) 130 else 200) * s, s)
        serifBlock(cv, w, h - (if (square) 130 else 190) * s, s, square, c, f, 0xFF3B4048.toInt(), 0xFF6F7680.toInt(), 0xFFC9C5BC.toInt(), 0.27f)
    }

    /** Serif heading, a short rule, and the message: shared by the quiet designs. */
    private fun serifBlock(cv: Canvas, w: Float, h: Float, s: Float, square: Boolean, c: CardContent, f: LetterFonts, ink: Int, sub: Int, rule: Int, top: Float) {
        val headY = h * top + 60 * s
        val head = paint(f.serif, ink, (if (square) 84 else 100) * s).center()
        shrink(head, c.headline, w * 0.76f)
        cv.drawText(c.headline, w / 2, headY, head)
        val to = paint(f.serif, sub, 46 * s).center().apply { textSkewX = -0.18f }
        shrink(to, c.toLine, w * 0.74f)
        val toY = headY + to.textSize * 1.6f
        cv.drawText(c.toLine, w / 2, toY, to)
        cv.drawLine(w / 2 - 70 * s, toY + 50 * s, w / 2 + 70 * s, toY + 50 * s, stroke(rule, 3 * s))
        val body = RectF(w * 0.17f, toY + 100 * s, w * 0.83f, h - 110 * s)
        text(cv, c.message + (c.signature?.let { "\n\n— $it" } ?: ""), f.serif, ink, body, (if (square) 40f else 48f) * s, 22f * s, Layout.Alignment.ALIGN_CENTER, 1.35f)
    }

    // ---- Ornaments ----

    /** A heart centred on (cx, cy), [size] wide. */
    fun heart(cx: Float, cy: Float, size: Float): Path {
        val r = size / 2
        return Path().apply {
            moveTo(cx, cy + r * 0.95f)
            cubicTo(cx - r * 1.6f, cy - r * 0.1f, cx - r * 0.75f, cy - r * 1.25f, cx, cy - r * 0.45f)
            cubicTo(cx + r * 0.75f, cy - r * 1.25f, cx + r * 1.6f, cy - r * 0.1f, cx, cy + r * 0.95f)
            close()
        }
    }

    /** A thin rectangular frame; [double] adds a second line inside. */
    private fun frame(cv: Canvas, r: RectF, color: Int, s: Float, double: Boolean) {
        cv.drawRect(r, stroke(color, 3 * s))
        if (double) cv.drawRect(RectF(r.left + 14 * s, r.top + 14 * s, r.right - 14 * s, r.bottom - 14 * s), stroke(color, 1.5f * s))
    }

    /** A gently curved olive branch, centred on (cx, cy), [width] across, leaves alternating along it. */
    private fun olive(cv: Canvas, cx: Float, cy: Float, width: Float, color: Int, s: Float) {
        val half = width / 2
        val sag = 26 * s
        // The stem is a shallow arc: y = cy - sag * (1 - u²), u from -1 to 1.
        fun at(u: Float) = (cx + half * u) to (cy - sag * (1 - u * u))
        val stem = Path()
        for (i in 0..40) {
            val (x, y) = at(-1f + 2f * i / 40)
            if (i == 0) stem.moveTo(x, y) else stem.lineTo(x, y)
        }
        cv.drawPath(stem, stroke(color, 3.5f * s).apply { strokeCap = Paint.Cap.ROUND })
        val leaf = 30 * s
        for (i in 0 until 9) {
            val u = -0.9f + 1.8f * i / 8
            val (x, y) = at(u)
            // Angle of the stem here, then lean each leaf out to alternate sides and towards the tip.
            val slope = Math.toDegrees(kotlin.math.atan2((2 * sag * u).toDouble(), half.toDouble())).toFloat()
            val side = if (i % 2 == 0) -1f else 1f
            val outward = if (u < 0) 180f else 0f
            cv.withRotation(slope + outward + side * 38f, x, y) {
                drawOval(RectF(x, y - 6.5f * s, x + leaf, y + 6.5f * s), fill(color))
            }
        }
        // A leaf at each tip.
        listOf(-1f, 1f).forEach { u ->
            val (x, y) = at(u)
            cv.withRotation(if (u < 0) 160f else 20f, x, y) { drawOval(RectF(x, y - 6.5f * s, x + leaf, y + 6.5f * s), fill(color)) }
        }
    }

    /** A small clay lamp: a shallow bowl drawn out to a spout, the wick and its flame at the tip. */
    private fun diya(cv: Canvas, cx: Float, cy: Float, s: Float) {
        val clay = 0xFFB0805F.toInt()
        val rim = 0xFF8F6249.toInt()
        // The bowl, with its spout to the right.
        val bowl = Path().apply {
            moveTo(cx - 75 * s, cy - 18 * s)
            quadTo(cx - 70 * s, cy + 38 * s, cx, cy + 40 * s)
            quadTo(cx + 55 * s, cy + 38 * s, cx + 80 * s, cy + 5 * s)
            lineTo(cx + 108 * s, cy - 22 * s)
            quadTo(cx + 70 * s, cy - 14 * s, cx + 40 * s, cy - 18 * s)
            close()
        }
        cv.drawPath(bowl, fill(clay))
        // The oil's surface, seen from just above.
        cv.drawOval(RectF(cx - 75 * s, cy - 30 * s, cx + 52 * s, cy - 6 * s), fill(rim))
        cv.drawOval(RectF(cx - 66 * s, cy - 26 * s, cx + 44 * s, cy - 10 * s), fill(0xFFD9B26A.toInt()))
        // Wick and flame on the spout's tip, with a small soft glow.
        val tipX = cx + 104 * s
        val tipY = cy - 22 * s
        cv.drawCircle(tipX, tipY - 34 * s, 42 * s, fill(0x22F2B44B))
        cv.drawLine(tipX - 4 * s, tipY + 2 * s, tipX, tipY - 8 * s, stroke(0xFF3B3028.toInt(), 3 * s))
        val flame = Path().apply {
            moveTo(tipX, tipY - 70 * s)
            quadTo(tipX + 20 * s, tipY - 30 * s, tipX, tipY - 6 * s)
            quadTo(tipX - 20 * s, tipY - 30 * s, tipX, tipY - 70 * s)
            close()
        }
        cv.drawPath(flame, fill(0xFFF0A93A.toInt()))
        val core = Path().apply {
            moveTo(tipX, tipY - 44 * s)
            quadTo(tipX + 8 * s, tipY - 24 * s, tipX, tipY - 10 * s)
            quadTo(tipX - 8 * s, tipY - 24 * s, tipX, tipY - 44 * s)
            close()
        }
        cv.drawPath(core, fill(0xFFFFE6A6.toInt()))
    }

    /** Paper with a torn, uneven edge. */
    private fun deckled(r: RectF, jag: Float, seed: Long): Path {
        val rnd = java.util.Random(seed)
        val step = jag * 2.2f
        fun j() = (rnd.nextFloat() - 0.5f) * jag
        return Path().apply {
            moveTo(r.left, r.top)
            var x = r.left
            while (x < r.right) { lineTo(x, r.top + j()); x += step }
            var y = r.top
            while (y < r.bottom) { lineTo(r.right + j(), y); y += step }
            x = r.right
            while (x > r.left) { lineTo(x, r.bottom + j()); x -= step }
            y = r.bottom
            while (y > r.top) { lineTo(r.left + j(), y); y -= step }
            close()
        }
    }

    /** A rectangle drawn as a ribbon that waves in and out. */
    private fun wavyFrame(r: RectF, amp: Float, wave: Float): Path {
        val p = Path()
        fun side(x0: Float, y0: Float, x1: Float, y1: Float, first: Boolean) {
            val len = kotlin.math.hypot(x1 - x0, y1 - y0)
            val nx = -(y1 - y0) / len
            val ny = (x1 - x0) / len
            val n = (len / 6f).toInt()
            for (i in 0..n) {
                val t = i / n.toFloat()
                val off = amp * sin(2 * PI * t * len / wave).toFloat()
                val x = x0 + (x1 - x0) * t + nx * off
                val y = y0 + (y1 - y0) * t + ny * off
                if (first && i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
        }
        side(r.left, r.top, r.right, r.top, true)
        side(r.right, r.top, r.right, r.bottom, false)
        side(r.right, r.bottom, r.left, r.bottom, false)
        side(r.left, r.bottom, r.left, r.top, false)
        return p
    }

    /** A ribbon bow; with [tails], long ribbons that run out to the sides. */
    private fun bow(cv: Canvas, cx: Float, cy: Float, size: Float, light: Int, dark: Int, s: Float, tails: Boolean = false, w: Float = 0f) {
        if (tails) {
            val tail = stroke(light, 14 * s).apply { strokeCap = Paint.Cap.ROUND }
            cv.drawPath(Path().apply { moveTo(cx, cy); cubicTo(cx - size * 1.6f, cy + size * 0.9f, cx - w * 0.3f, cy - size * 0.2f, w * 0.06f, cy + size * 0.3f) }, tail)
            cv.drawPath(Path().apply { moveTo(cx, cy); cubicTo(cx + size * 1.6f, cy + size * 0.9f, cx + w * 0.3f, cy - size * 0.2f, w * 0.94f, cy + size * 0.3f) }, tail)
        }
        val loop = { dir: Float ->
            Path().apply {
                moveTo(cx, cy)
                cubicTo(cx + dir * size * 0.4f, cy - size * 0.8f, cx + dir * size * 1.2f, cy - size * 0.55f, cx + dir * size * 1.05f, cy)
                cubicTo(cx + dir * size * 1.2f, cy + size * 0.45f, cx + dir * size * 0.4f, cy + size * 0.35f, cx, cy)
            }
        }
        listOf(-1f, 1f).forEach { d ->
            cv.drawPath(loop(d), fill(light))
            cv.drawPath(loop(d), stroke(dark, 3.5f * s))
            cv.drawPath(Path().apply { moveTo(cx, cy); quadTo(cx + d * size * 0.3f, cy + size * 0.7f, cx + d * size * 0.55f, cy + size * 1.15f) }, stroke(dark, 9 * s).apply { strokeCap = Paint.Cap.ROUND })
        }
        cv.drawCircle(cx, cy, size * 0.17f, fill(dark))
    }

    /** Little lines around a heart, as if it were glowing. */
    private fun rays(cv: Canvas, cx: Float, cy: Float, size: Float, color: Int, s: Float) {
        val p = stroke(color, 3.5f * s).apply { strokeCap = Paint.Cap.ROUND }
        listOf(-150.0, -30.0, -110.0, -70.0).forEach { deg ->
            val a = deg * PI / 180
            val r0 = size * 0.95f
            val r1 = size * 1.25f
            cv.drawLine(cx + (cos(a) * r0).toFloat(), cy + (sin(a) * r0).toFloat(), cx + (cos(a) * r1).toFloat(), cy + (sin(a) * r1).toFloat(), p)
        }
    }

    /** A five-petal flower with two leaves. */
    private fun blossom(cv: Canvas, cx: Float, cy: Float, r: Float, edge: Int) {
        cv.drawOval(RectF(cx - r * 2.2f, cy + r * 0.2f, cx - r * 0.6f, cy + r * 0.9f), fill(0xFF8FB58A.toInt()))
        cv.drawOval(RectF(cx + r * 0.6f, cy - r * 0.9f, cx + r * 2.2f, cy - r * 0.2f), fill(0xFF8FB58A.toInt()))
        for (i in 0 until 5) {
            val a = i * 2 * PI / 5 - PI / 2
            val px = cx + (cos(a) * r * 0.75f).toFloat()
            val py = cy + (sin(a) * r * 0.75f).toFloat()
            cv.drawCircle(px, py, r * 0.55f, fill(0xFFFFFFFF.toInt()))
            cv.drawCircle(px, py, r * 0.55f, stroke(edge, r * 0.12f))
        }
        cv.drawCircle(cx, cy, r * 0.32f, fill(0xFFF2C14E.toInt()))
    }

    /** Dried flowers on long stems, taped together. */
    private fun sprig(cv: Canvas, x: Float, bottom: Float, height: Float, s: Float) {
        val stem = stroke(0xFFB3926A.toInt(), 4 * s).apply { strokeCap = Paint.Cap.ROUND }
        val rnd = java.util.Random(9)
        val tips = listOf(-150f, -95f, -40f, 10f)
        tips.forEachIndexed { i, dx ->
            val tipX = x + dx * s
            val tipY = bottom - height + i * 40 * s
            cv.drawPath(Path().apply { moveTo(x - 10 * s, bottom); quadTo(x + dx * 0.3f * s, bottom - height * 0.5f, tipX, tipY) }, stem)
            repeat(14) {
                val bx = tipX + (rnd.nextFloat() - 0.5f) * 90 * s
                val by = tipY + (rnd.nextFloat() - 0.3f) * 80 * s
                cv.drawLine(tipX, tipY + 20 * s, bx, by, stroke(0xFFC4A57E.toInt(), 1.8f * s))
                cv.drawCircle(bx, by, (5 + rnd.nextFloat() * 5) * s, fill(if (rnd.nextBoolean()) 0xFFE2A2A5.toInt() else 0xFFD48A92.toInt()))
            }
        }
        cv.withRotation(-14f, x - 10 * s, bottom - height * 0.25f) {
            drawRect(RectF(x - 90 * s, bottom - height * 0.25f - 28 * s, x + 70 * s, bottom - height * 0.25f + 28 * s), fill(0xCCE6D7BC.toInt()))
        }
    }

    /** A string of pennant flags hanging between two points. */
    private fun bunting(cv: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, count: Int, colors: IntArray, s: Float) {
        val sag = 60 * s
        val line = stroke(0xFF4A4A4A.toInt(), 3 * s)
        cv.drawPath(Path().apply { moveTo(x0, y0); quadTo((x0 + x1) / 2, (y0 + y1) / 2 + sag * 2, x1, y1) }, line)
        for (i in 0 until count) {
            val t0 = (i + 0.15f) / count
            val t1 = (i + 0.85f) / count
            fun at(t: Float): Pair<Float, Float> {
                val mx = (x0 + x1) / 2
                val my = (y0 + y1) / 2 + sag * 2
                val u = 1 - t
                return (u * u * x0 + 2 * u * t * mx + t * t * x1) to (u * u * y0 + 2 * u * t * my + t * t * y1)
            }
            val (ax, ay) = at(t0)
            val (bx, by) = at(t1)
            val tip = ((ax + bx) / 2) to ((ay + by) / 2 + 95 * s)
            val p = Path().apply { moveTo(ax, ay); lineTo(bx, by); lineTo(tip.first, tip.second); close() }
            cv.drawPath(p, fill(colors[i % colors.size]))
            cv.drawPath(p, stroke(0xFF4A4A4A.toInt(), 2.5f * s))
        }
    }

    private fun sparkle(cv: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val p = Path().apply {
            moveTo(cx, cy - r); quadTo(cx, cy, cx + r, cy); quadTo(cx, cy, cx, cy + r); quadTo(cx, cy, cx - r, cy); quadTo(cx, cy, cx, cy - r); close()
        }
        cv.drawPath(p, fill(color))
    }

    private fun balloon(cv: Canvas, cx: Float, cy: Float, r: Float, color: Int, groundY: Float, s: Float) {
        cv.drawPath(Path().apply { moveTo(cx, cy + r * 1.15f); quadTo(cx - 30 * s, (cy + groundY) / 2, cx + 20 * s, groundY) }, stroke(0xFF555555.toInt(), 2.5f * s))
        cv.drawOval(RectF(cx - r, cy - r * 1.15f, cx + r, cy + r * 1.15f), fill(color))
        cv.drawOval(RectF(cx - r, cy - r * 1.15f, cx + r, cy + r * 1.15f), stroke(0xFF4A4A4A.toInt(), 3 * s))
        cv.drawPath(heart(cx - r * 0.3f, cy - r * 0.4f, r * 0.45f), fill(0xCCFFFFFF.toInt()))
    }

    /** A two-tier cake with candles, [k] is the scale. */
    private fun cake(cv: Canvas, cx: Float, cy: Float, k: Float, body: Int, accent: Int, ink: Int) {
        val line = stroke(ink, 4 * k)
        val bottom = RectF(cx - 130 * k, cy, cx + 130 * k, cy + 90 * k)
        val top = RectF(cx - 90 * k, cy - 70 * k, cx + 90 * k, cy)
        listOf(bottom, top).forEach { r ->
            cv.drawRoundRect(r, 14 * k, 14 * k, fill(body)); cv.drawRoundRect(r, 14 * k, 14 * k, line)
            // Icing drips.
            val drip = Path().apply {
                moveTo(r.left, r.top + 10 * k)
                var x = r.left
                var down = true
                while (x < r.right) { quadTo(x + 15 * k, r.top + (if (down) 34 else 10) * k, x + 30 * k, r.top + 12 * k); x += 30 * k; down = !down }
                lineTo(r.right, r.top); lineTo(r.left, r.top); close()
            }
            cv.drawPath(drip, fill(accent))
        }
        cv.drawRoundRect(RectF(cx - 160 * k, cy + 88 * k, cx + 160 * k, cy + 104 * k), 8 * k, 8 * k, fill(accent))
        listOf(-50f, 0f, 50f).forEach { dx ->
            val x = cx + dx * k
            cv.drawRoundRect(RectF(x - 8 * k, cy - 130 * k, x + 8 * k, cy - 70 * k), 4 * k, 4 * k, fill(accent))
            cv.drawOval(RectF(x - 9 * k, cy - 165 * k, x + 9 * k, cy - 135 * k), fill(0xFFF5B342.toInt()))
        }
    }

    /** A clump of thin-stemmed wildflowers rising from [ground]. */
    private fun meadow(cv: Canvas, x0: Float, ground: Float, x1: Float, height: Float, s: Float, petal: Int) {
        val rnd = java.util.Random((x0 * 7).toLong() + 3)
        val stem = stroke(0xFF8C8270.toInt(), 3 * s)
        cv.drawLine(x0, ground, x1, ground, stroke(0x55A08F7C, 2 * s))
        repeat(9) {
            val bx = x0 + (x1 - x0) * (0.1f + rnd.nextFloat() * 0.8f)
            val tx = bx + (rnd.nextFloat() - 0.5f) * 80 * s
            val ty = ground - height * (0.45f + rnd.nextFloat() * 0.55f)
            cv.drawPath(Path().apply { moveTo(bx, ground); quadTo(bx + (tx - bx) * 0.2f, (ground + ty) / 2, tx, ty) }, stem)
            // Leaves along the stem.
            val ly = (ground + ty) / 2
            cv.drawOval(RectF(tx - 26 * s, ly - 6 * s, tx, ly + 6 * s), fill(0xFFA7B59A.toInt()))
            // The flower: a cup of petals.
            for (i in 0 until 5) {
                val a = -PI / 2 + (i - 2) * 0.45
                cv.drawCircle(tx + (cos(a) * 11 * s).toFloat(), ty + (sin(a) * 11 * s).toFloat(), 9 * s, fill(petal))
            }
            cv.drawCircle(tx, ty, 6 * s, fill(0xFFE9C46A.toInt()))
        }
    }

    // ---- Text ----

    private fun text(cv: Canvas, s: String, face: Typeface, color: Int, r: RectF, max: Float, min: Float, align: Layout.Alignment, spacing: Float) {
        if (r.height() <= 0 || r.width() <= 0) return
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; this.color = color }
        var size = max
        var layout: StaticLayout
        while (true) {
            tp.textSize = size
            layout = StaticLayout.Builder.obtain(s, 0, s.length, tp, r.width().toInt().coerceAtLeast(1))
                .setAlignment(align).setLineSpacing(0f, spacing).build()
            if (layout.height <= r.height() || size <= min) break
            size -= 2f
        }
        // Centre the block vertically in its space.
        val dy = ((r.height() - layout.height) / 2).coerceAtLeast(0f)
        cv.withTranslation(r.left, r.top + dy) {
            clipRect(0f, 0f, r.width(), r.height())
            layout.draw(this)
        }
    }

    private fun shrink(p: Paint, s: String, maxW: Float) {
        while (p.measureText(s) > maxW && p.textSize > 12f) p.textSize -= 2f
    }

    private fun Paint.center() = apply { textAlign = Paint.Align.CENTER }
    private fun paint(face: Typeface, color: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; this.color = color; textSize = size }
    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = w }
}
