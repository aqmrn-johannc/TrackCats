package com.trackcats.app

import com.trackcats.app.core.GuideReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Random

/** The Cat Guide reader on drawn test pictures: no real artwork is needed to check the logic. */
class GuideReaderTest {
    private val teal = 0xFF59AABE.toInt()
    private val black = 0xFF000000.toInt()

    /** A made-up icon: white field with a pattern of dark blocks decided by [seed]. */
    private fun icon(seed: Long, w: Int, h: Int): IntArray {
        val r = Random(seed)
        val cells = BooleanArray(48) { r.nextInt(3) == 0 }
        return IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val border = x < w * 4 / 100 + 2 || y < h * 5 / 100 + 2 || x >= w - w * 4 / 100 - 2 || y >= h - h * 5 / 100 - 2
            if (border || cells[(y * 6 / h) * 8 + x * 8 / w]) black else 0xFFFFFFFF.toInt()
        }
    }

    private fun page(width: Int, height: Int, tiles: List<Triple<Int, Int, Long>>, tw: Int, th: Int): IntArray {
        val px = IntArray(width * height) { teal }
        for ((x0, y0, seed) in tiles) {
            val art = icon(seed, tw, th)
            for (y in 0 until th) for (x in 0 until tw) px[(y0 + y) * width + x0 + x] = art[y * tw + x]
        }
        // Things that are dark but not tiles: a wide bar and a small square.
        for (y in 20 until 50) for (x in 700 until 1000) px[y * width + x] = black
        for (y in 500 until 530) for (x in 100 until 130) px[y * width + x] = black
        return px
    }

    @Test
    fun findsFramedTilesAndIgnoresOtherDarkShapes() {
        val tiles = listOf(Triple(310, 120, 1L), Triple(422, 120, 2L), Triple(534, 120, 3L), Triple(310, 210, 4L))
        val found = GuideReader.findTiles(page(1280, 576, tiles, 98, 76), 1280, 576)
        assertEquals(listOf(310 to 120, 422 to 120, 534 to 120, 310 to 210), found.map { it.x to it.y })
        assertEquals(98, found[0].w)
        assertEquals(76, found[0].h)
    }

    @Test
    fun snapsARoughlyPlacedTileToItsFrame() {
        val px = page(1280, 576, listOf(Triple(310, 120, 1L), Triple(422, 120, 2L)), 98, 76)
        val exact = GuideReader.refine(px, 1280, 576, GuideReader.Tile(307, 123, 101, 71), 6)
        assertEquals(listOf(310, 120, 98, 76), listOf(exact.x, exact.y, exact.w, exact.h))
    }

    @Test
    fun matchesTheRightCatAtAnotherSizeAndRejectsStrangers() {
        // References at the wiki's size, tiles at a phone screenshot's size.
        val refs = (1L..40L).map { GuideReader.Ref(it.toInt(), 0, GuideReader.signature(icon(it, 110, 85), 110, 0, 0, 110, 85)) } +
            GuideReader.Ref(0, 0, GuideReader.signature(icon(99, 110, 85), 110, 0, 0, 110, 85))
        for (seed in listOf(3L, 17L, 40L)) {
            val tile = GuideReader.signature(icon(seed, 207, 161), 207, 0, 0, 207, 161)
            assertEquals(seed.toInt(), GuideReader.match(tile, refs)?.catId)
        }
        // An icon nobody knows, and a decoy, give no match.
        assertNull(GuideReader.match(GuideReader.signature(icon(1234, 207, 161), 207, 0, 0, 207, 161), refs))
        assertNull(GuideReader.match(GuideReader.signature(icon(99, 207, 161), 207, 0, 0, 207, 161), refs))
    }
}
