package com.trackcats.app.core

import kotlin.math.abs

/**
 * Reads the game's Cat Guide from a screenshot: finds the framed icon tiles and tells which cat
 * each one shows by comparing it with reference icons. Works on plain pixel arrays so it can be
 * tested without a phone.
 */
object GuideReader {
    /** Size of the small grey picture ("signature") two icons are compared by. */
    const val SW = 44
    const val SH = 34

    /** Width the screenshot is scaled to before looking for tiles. */
    const val SCAN_WIDTH = 1280

    /** A real match scores about 6 to 16; the closest wrong cat is usually far above 20. */
    const val MAX_DISTANCE = 17f
    const val MIN_MARGIN = 3f

    class Tile(val x: Int, val y: Int, val w: Int, val h: Int)

    /** One reference icon. [catId] is 0 for decoys: common cats that are not tracked but must not be mistaken for tracked ones. */
    class Ref(val catId: Int, val form: Int, val sig: ByteArray)

    class Match(val catId: Int, val distance: Float)

    private fun isDark(c: Int): Boolean = (c shr 16 and 0xFF) < 70 && (c shr 8 and 0xFF) < 70 && (c and 0xFF) < 70

    /**
     * Finds icon tiles: rectangles with a black frame, about 1.3 times wider than tall, each
     * between 5% and 20% of the picture's width. [px] is ARGB, row by row.
     */
    fun findTiles(px: IntArray, w: Int, h: Int): List<Tile> {
        val dark = BooleanArray(w * h) { isDark(px[it]) }
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val out = ArrayList<Tile>()
        for (start in 0 until w * h) {
            if (!dark[start] || seen[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            var x0 = start % w
            var x1 = x0
            var y0 = start / w
            var y1 = y0
            while (head < tail) {
                val p = queue[head++]
                val x = p % w
                val y = p / w
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
                if (x + 1 < w && dark[p + 1] && !seen[p + 1]) { seen[p + 1] = true; queue[tail++] = p + 1 }
                if (x > 0 && dark[p - 1] && !seen[p - 1]) { seen[p - 1] = true; queue[tail++] = p - 1 }
                if (y + 1 < h && dark[p + w] && !seen[p + w]) { seen[p + w] = true; queue[tail++] = p + w }
                if (y > 0 && dark[p - w] && !seen[p - w]) { seen[p - w] = true; queue[tail++] = p - w }
            }
            val bw = x1 - x0 + 1
            val bh = y1 - y0 + 1
            if (bw < 0.05f * w || bw > 0.2f * w || bh < 8) continue
            val aspect = bw.toFloat() / bh
            if (aspect < 1.15f || aspect > 1.45f) continue
            // Each side must be an almost solid dark line. The outermost line can be blurred, so
            // the best of the three outer lines counts.
            fun row(y: Int): Float { var n = 0; for (x in x0..x1) if (dark[y * w + x]) n++; return n.toFloat() / bw }
            fun col(x: Int): Float { var n = 0; for (y in y0..y1) if (dark[y * w + x]) n++; return n.toFloat() / bh }
            val top = maxOf(row(y0), row(y0 + 1), row(y0 + 2))
            val bottom = maxOf(row(y1), row(y1 - 1), row(y1 - 2))
            val left = maxOf(col(x0), col(x0 + 1), col(x0 + 2))
            val right = maxOf(col(x1), col(x1 - 1), col(x1 - 2))
            if (minOf(top, bottom, left, right) < 0.85f) continue
            out.add(Tile(x0, y0, bw, bh))
        }
        return out.sortedWith(compareBy({ it.y / (it.h / 2 + 1) }, { it.x }))
    }

    /**
     * Snaps a roughly placed tile to its black frame in the full-size picture. Tiles are found on
     * a scaled-down copy, so their mapped position can be a few pixels off, and line drawings
     * compare badly when shifted even slightly. Each side moves, within [slack] pixels, to the
     * outermost line that is mostly dark.
     */
    fun refine(px: IntArray, w: Int, h: Int, tile: Tile, slack: Int): Tile {
        val x0 = tile.x
        val y0 = tile.y
        val x1 = tile.x + tile.w - 1
        val y1 = tile.y + tile.h - 1
        fun row(y: Int, xa: Int, xb: Int): Float {
            if (y < 0 || y >= h) return 0f
            var n = 0
            for (x in xa..xb) if (isDark(px[y * w + x])) n++
            return n.toFloat() / (xb - xa + 1)
        }
        fun col(x: Int, ya: Int, yb: Int): Float {
            if (x < 0 || x >= w) return 0f
            var n = 0
            for (y in ya..yb) if (isDark(px[y * w + x])) n++
            return n.toFloat() / (yb - ya + 1)
        }
        // Measure along the middle two thirds of each side so corners and neighbours do not matter.
        val xa = (x0 + tile.w / 6).coerceIn(0, w - 1)
        val xb = (x1 - tile.w / 6).coerceIn(xa, w - 1)
        val ya = (y0 + tile.h / 6).coerceIn(0, h - 1)
        val yb = (y1 - tile.h / 6).coerceIn(ya, h - 1)
        val top = (y0 - slack..y0 + slack).firstOrNull { row(it, xa, xb) >= 0.7f } ?: y0
        val bottom = (y1 + slack downTo y1 - slack).firstOrNull { row(it, xa, xb) >= 0.7f } ?: y1
        val left = (x0 - slack..x0 + slack).firstOrNull { col(it, ya, yb) >= 0.7f } ?: x0
        val right = (x1 + slack downTo x1 - slack).firstOrNull { col(it, ya, yb) >= 0.7f } ?: x1
        val nx = left.coerceIn(0, w - 2)
        val ny = top.coerceIn(0, h - 2)
        return Tile(nx, ny, (right - nx + 1).coerceIn(1, w - nx), (bottom - ny + 1).coerceIn(1, h - ny))
    }

    /**
     * Shrinks the rectangle at ([x0], [y0]) of size [bw] x [bh] to a [SW] x [SH] grey picture by
     * averaging. Transparent pixels count as white.
     */
    fun signature(px: IntArray, w: Int, x0: Int, y0: Int, bw: Int, bh: Int): ByteArray {
        val out = ByteArray(SW * SH)
        for (sy in 0 until SH) {
            val ya = y0 + sy * bh / SH
            val yb = maxOf(ya + 1, y0 + (sy + 1) * bh / SH)
            for (sx in 0 until SW) {
                val xa = x0 + sx * bw / SW
                val xb = maxOf(xa + 1, x0 + (sx + 1) * bw / SW)
                var sum = 0L
                var n = 0
                for (y in ya until yb) for (x in xa until xb) {
                    val c = px[y * w + x]
                    val a = c ushr 24
                    val grey = ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
                    sum += (grey * a + 255 * (255 - a)) / 255
                    n++
                }
                out[sy * SW + sx] = (sum / n).toInt().toByte()
            }
        }
        return out
    }

    /** Mean difference per pixel between two signatures, 0 (identical) to 255. */
    fun distance(a: ByteArray, b: ByteArray): Float {
        var sum = 0
        for (i in a.indices) sum += abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
        return sum.toFloat() / a.size
    }

    /**
     * The tracked cat a tile shows, or null when nothing is close enough, the closest icon is a
     * decoy, or two different cats are too close to call.
     */
    fun match(sig: ByteArray, refs: List<Ref>): Match? {
        var best: Ref? = null
        var bestD = Float.MAX_VALUE
        for (r in refs) {
            val d = distance(sig, r.sig)
            if (d < bestD) { bestD = d; best = r }
        }
        val winner = best ?: return null
        if (bestD > MAX_DISTANCE || winner.catId <= 0) return null
        var rival = Float.MAX_VALUE
        for (r in refs) if (r.catId != winner.catId) rival = minOf(rival, distance(sig, r.sig))
        return if (rival - bestD >= MIN_MARGIN) Match(winner.catId, bestD) else null
    }
}
