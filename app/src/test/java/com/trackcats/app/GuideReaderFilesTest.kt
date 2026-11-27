package com.trackcats.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.trackcats.app.core.GuideReader
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Runs the Cat Guide reader on real files for tuning, decoding and scaling them the way the app
 * does. Skipped unless TRACKCATS_GUIDE is set to "screenshot;folder of wiki icons (UniNNN_x00.png)".
 * It prints the three closest icons per tile.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class GuideReaderFilesTest {
    private fun pixels(b: Bitmap): IntArray = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    @Test
    fun printMatches() {
        val arg = System.getenv("TRACKCATS_GUIDE")
        assumeTrue(!arg.isNullOrBlank())
        val (shotPath, refDir) = arg!!.split(";")
        val names = ArrayList<String>()
        val refs = ArrayList<GuideReader.Ref>()
        for (f in File(refDir).listFiles()!!.sortedBy { it.name }) {
            if (f.length() == 0L || !f.name.matches(Regex("Uni\\d+_[fcsu]00\\.png"))) continue
            val im = BitmapFactory.decodeFile(f.path) ?: continue
            if (im.width != 128 || im.height != 128) continue
            val unit = f.name.substring(3, 6).toInt() + 1
            names.add(f.name)
            refs.add(GuideReader.Ref(if (unit <= 9) 0 else unit, "fcsu".indexOf(f.name[7]), GuideReader.signature(pixels(im), 128, 9, 21, 110, 85)))
        }
        val full = BitmapFactory.decodeFile(shotPath)
        val scanW = GuideReader.SCAN_WIDTH
        val scanH = full.height * scanW / full.width
        val scan = Bitmap.createScaledBitmap(full, scanW, scanH, true)
        val tiles = GuideReader.findTiles(pixels(scan), scanW, scanH)
        val px = pixels(full)
        val scale = full.width.toFloat() / scanW
        println("GUIDE>> refs ${refs.size} tiles ${tiles.size} full ${full.width}x${full.height}")
        for (t in tiles) {
            val x = (t.x * scale).toInt()
            val y = (t.y * scale).toInt()
            val w = (t.w * scale).toInt().coerceAtMost(full.width - x)
            val h = (t.h * scale).toInt().coerceAtMost(full.height - y)
            val e = GuideReader.refine(px, full.width, full.height, GuideReader.Tile(x, y, w, h), (scale * 3).toInt() + 3)
            val sig = GuideReader.signature(px, full.width, e.x, e.y, e.w, e.h)
            val ranked = refs.indices.map { GuideReader.distance(sig, refs[it].sig) to names[it] }.sortedBy { it.first }.take(3)
            val m = GuideReader.match(sig, refs)
            println("GUIDE>> tile ${e.x},${e.y} ${e.w}x${e.h} -> ${m?.catId} ${ranked.joinToString { "%.1f %s".format(it.first, it.second) }}")
        }
    }
}
