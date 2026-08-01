package com.nmrf.remote.hat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/** Spielt Bruces tft_logger-Draw-Ops (0xAA-Pakete) auf ein Bitmap = CYD-Screen-Mirror. */
class TftReplay {
    var bmp: Bitmap? = null
        private set
    private var cv: Canvas? = null
    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val stroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = false }
    private val textP = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE }

    init { ensure(240, 320) }

    private fun ensure(w: Int, h: Int) {
        val cw = w.coerceIn(1, 480); val ch = h.coerceIn(1, 480)
        if (bmp == null || bmp!!.width != cw || bmp!!.height != ch) {
            val b = Bitmap.createBitmap(cw, ch, Bitmap.Config.RGB_565)
            b.eraseColor(0xFF000000.toInt())
            bmp = b; cv = Canvas(b)
        }
    }

    private fun c565(v: Int): Int {
        val r = ((v shr 11) and 0x1F) * 255 / 31
        val g = ((v shr 5) and 0x3F) * 255 / 63
        val b = (v and 0x1F) * 255 / 31
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun apply(pkt: ByteArray) {
        if (pkt.size < 3) return
        val fn = pkt[2].toInt() and 0xFF
        var p = 3
        fun u16(): Int {
            if (p + 1 >= pkt.size) return 0
            val hi = pkt[p].toInt() and 0xFF; val lo = pkt[p + 1].toInt() and 0xFF; p += 2
            return (hi shl 8) or lo
        }
        fun rest(): String { val s = String(pkt, p, (pkt.size - p).coerceAtLeast(0), Charsets.UTF_8); p = pkt.size; return s }
        val c = cv ?: return
        when (fn) {
            99 -> { val w = u16(); val h = u16(); ensure(w, h); bmp!!.eraseColor(0xFF000000.toInt()) }
            0 -> { val fg = u16(); bmp!!.eraseColor(c565(fg)) }
            2 -> { val x = u16(); val y = u16(); val w = u16(); val h = u16(); val fg = u16(); fill.color = c565(fg); c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), fill) }
            1 -> { val x = u16(); val y = u16(); val w = u16(); val h = u16(); val fg = u16(); stroke.color = c565(fg); c.drawRect(x + 0.5f, y + 0.5f, x + w - 0.5f, y + h - 0.5f, stroke) }
            4 -> { val x = u16(); val y = u16(); val w = u16(); val h = u16(); val r = u16(); val fg = u16(); fill.color = c565(fg); c.drawRoundRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), r.toFloat(), r.toFloat(), fill) }
            3 -> { val x = u16(); val y = u16(); val w = u16(); val h = u16(); val r = u16(); val fg = u16(); stroke.color = c565(fg); c.drawRoundRect(x + 0.5f, y + 0.5f, x + w - 0.5f, y + h - 0.5f, r.toFloat(), r.toFloat(), stroke) }
            6 -> { val x = u16(); val y = u16(); val r = u16(); val fg = u16(); fill.color = c565(fg); c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), fill) }
            5 -> { val x = u16(); val y = u16(); val r = u16(); val fg = u16(); stroke.color = c565(fg); c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), stroke) }
            11 -> { val x = u16(); val y = u16(); val x1 = u16(); val y1 = u16(); val fg = u16(); stroke.color = c565(fg); c.drawLine(x.toFloat(), y.toFloat(), x1.toFloat(), y1.toFloat(), stroke) }
            20 -> { val x = u16(); val y = u16(); val h = u16(); val fg = u16(); fill.color = c565(fg); c.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + h).toFloat(), fill) }
            21 -> { val x = u16(); val y = u16(); val w = u16(); val fg = u16(); fill.color = c565(fg); c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + 1).toFloat(), fill) }
            7, 8 -> {
                val x = u16(); val y = u16(); val x2 = u16(); val y2 = u16(); val x3 = u16(); val y3 = u16(); val fg = u16()
                val path = Path().apply { moveTo(x.toFloat(), y.toFloat()); lineTo(x2.toFloat(), y2.toFloat()); lineTo(x3.toFloat(), y3.toFloat()); close() }
                if (fn == 8) { fill.color = c565(fg); c.drawPath(path, fill) } else { stroke.color = c565(fg); c.drawPath(path, stroke) }
            }
            14, 15, 16, 17 -> {
                val x = u16(); val y = u16(); val size = u16(); val fg = u16(); var bg = u16(); val txt = rest().replace("\n", "")
                if (bg == fg) bg = 0
                val ts = (size * 8).toFloat().coerceAtLeast(6f)
                val fw = size * 4.5f
                textP.textSize = ts
                val w = txt.length * fw
                val ox = when (fn) { 14 -> w / 2f; 15 -> w; else -> 0f }
                fill.color = c565(bg); c.drawRect(x - ox, y.toFloat(), x - ox + w, y + ts, fill)
                textP.color = c565(fg)
                textP.textAlign = when (fn) { 14 -> Paint.Align.CENTER; 15 -> Paint.Align.RIGHT; else -> Paint.Align.LEFT }
                c.drawText(txt, x.toFloat(), y + ts * 0.82f, textP)
            }
            // 9/10 ellipse, 12 arc, 13 wideline, 18 image, 19 pixel: ausgelassen (in Menüs selten)
        }
    }
}
