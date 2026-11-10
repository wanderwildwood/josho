package com.wanderwildwood.josho.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/** How many pages [pdf] has; 0 if it cannot be read. */
fun pageCount(pdf: File): Int = runCatching {
    PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
}.getOrDefault(0)

/**
 * One page of [pdf], [width] pixels wide, every pixel pushed to pure black or white: the panel
 * then draws crisp edges instead of sixteen greys of anti-aliasing.
 *
 * The page's empty margins are cut away first (a first, rough drawing finds where the ink is;
 * the second draws just that, scaled to the width), so an A4 page on a 480-pixel screen uses
 * the whole width for music rather than for paper.
 */
fun renderPage(pdf: File, page: Int, width: Int): Bitmap? = runCatching {
    PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
        r.openPage(page.coerceIn(0, r.pageCount - 1)).use { p ->
            val w = width.coerceAtLeast(1)
            val s1 = w.toFloat() / p.width
            val rough = draw(p, w, (p.height * s1).toInt().coerceAtLeast(1), Matrix().apply { setScale(s1, s1) })
            val box = inkBox(rough) ?: return@use rough
            val pad = (w * 0.02f).toInt()
            // The same width comes off both sides, the narrower of the two blanks: LilyPond
            // centers a title on the page, and a first line indented (its default) leaves more
            // paper on the left, so cutting each side to its own ink pushed the title off center.
            val side = (minOf(box[0], rough.width - box[2]) - pad).coerceAtLeast(0)
            val x0 = side; val y0 = (box[1] - pad).coerceAtLeast(0)
            val x1 = rough.width - side; val y1 = (box[3] + pad).coerceAtMost(rough.height)
            rough.recycle()
            val k = w.toFloat() / (x1 - x0)
            val h = ((y1 - y0) * k).toInt().coerceAtLeast(1)
            draw(p, w, h, Matrix().apply { setScale(s1 * k, s1 * k); postTranslate(-x0 * k, -y0 * k) })
        }
    }
}.getOrNull()

private fun draw(p: PdfRenderer.Page, w: Int, h: Int, m: Matrix): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.WHITE)
    p.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    for (i in px.indices) {
        val c = px[i]
        val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        px[i] = if (lum < THRESHOLD) Color.BLACK else Color.WHITE
    }
    bmp.setPixels(px, 0, w, 0, 0, w, h)
    return bmp
}

/** The smallest box around the black pixels, as left, top, right, bottom; null for a blank page. */
private fun inkBox(b: Bitmap): IntArray? {
    val w = b.width; val h = b.height
    val px = IntArray(w * h)
    b.getPixels(px, 0, w, 0, 0, w, h)
    var l = w; var t = h; var r = -1; var bo = -1
    for (y in 0 until h) for (x in 0 until w) if (px[y * w + x] == Color.BLACK) {
        if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > bo) bo = y
    }
    return if (r < 0) null else intArrayOf(l, t, r + 1, bo + 1)
}

/** Grey darker than this becomes black. Staff lines are thin; a high threshold keeps them whole. */
private const val THRESHOLD = 170
