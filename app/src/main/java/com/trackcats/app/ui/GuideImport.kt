package com.trackcats.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.network.HttpException
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.size.Size
import com.trackcats.app.core.Game
import com.trackcats.app.core.GuideReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Marks cats as owned from screenshots of the game's Cat Guide. Each icon in the screenshot is
 * compared with the icons of every form of every tracked cat.
 */
object GuideImport {
    /** The nine basic cats appear in every guide and are not tracked; knowing them avoids false matches. */
    private val decoys = (1..9).toList()

    private class Key(val catId: Int, val unit: Int, val form: Int)

    private fun cacheFile(context: Context) = File(context.filesDir, "guide-refs-2.bin")

    /** Reference signatures saved from earlier imports. An empty one means the wiki has no such picture. */
    private fun loadCache(context: Context): HashMap<Long, ByteArray> {
        val map = HashMap<Long, ByteArray>()
        runCatching {
            DataInputStream(cacheFile(context).inputStream().buffered()).use { s ->
                repeat(s.readInt()) {
                    val key = s.readLong()
                    val bytes = ByteArray(s.readInt())
                    s.readFully(bytes)
                    map[key] = bytes
                }
            }
        }
        return map
    }

    private fun saveCache(context: Context, map: Map<Long, ByteArray>) {
        runCatching {
            val tmp = File(context.filesDir, "guide-refs.tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { s ->
                s.writeInt(map.size)
                for ((k, v) in map) { s.writeLong(k); s.writeInt(v.size); s.write(v) }
            }
            tmp.renameTo(cacheFile(context))
        }
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }

    /** Reference signatures, and how many pictures could not be downloaded this time. */
    class RefSet(val refs: List<GuideReader.Ref>, val failed: Int)

    /**
     * Signatures of every form of every tracked cat. The first run downloads the pictures it does
     * not have yet (several hundred small files), a few at a time so the wiki is not flooded;
     * later runs are instant. Pictures that fail are tried again on the next import.
     */
    suspend fun references(context: Context, game: Game, onProgress: (Float) -> Unit): RefSet = withContext(Dispatchers.IO) {
        runCatching { File(context.filesDir, "guide-refs.bin").delete() }
        val keys = ArrayList<Key>()
        for (id in game.gachaCats) for (form in 0 until (game.data.cats[id]?.n?.size ?: 1).coerceIn(1, 4)) keys.add(Key(id, id, form))
        for (id in decoys) for (form in 0..2) keys.add(Key(0, id, form))
        fun slot(key: Key) = key.unit.toLong() shl 8 or key.form.toLong()
        val cache = loadCache(context)
        var missing = keys.filter { slot(it) !in cache }
        val total = missing.size
        val done = AtomicInteger(0)
        val loader = context.imageLoader

        /** Signature, or an empty array when the wiki says there is no such picture, or null when the download failed. */
        suspend fun fetch(key: Key): ByteArray? {
            val request = ImageRequest.Builder(context).data(Art.catUrl(key.unit, key.form))
                .size(Size.ORIGINAL).allowHardware(false).memoryCachePolicy(CachePolicy.DISABLED).build()
            val result = loader.execute(request)
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap == null) {
                val code = ((result as? ErrorResult)?.throwable as? HttpException)?.response?.code
                return if (code == 404) ByteArray(0) else null
            }
            val px = pixels(bitmap.copy(Bitmap.Config.ARGB_8888, false))
            // The wiki icon is a 128 px square with the 110 x 85 picture centred in it.
            return if (bitmap.width == 128 && bitmap.height == 128) GuideReader.signature(px, 128, 9, 21, 110, 85)
            else GuideReader.signature(px, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }

        // First pass four at a time, then two slower passes for whatever was refused.
        for ((round, parallel) in listOf(4, 2, 1).withIndex()) {
            if (missing.isEmpty()) break
            if (round > 0) delay(2500)
            val gate = Semaphore(parallel)
            val fetched = coroutineScope {
                missing.map { key ->
                    async {
                        gate.withPermit {
                            if (round > 0) delay(150)
                            val sig = runCatching { fetch(key) }.getOrNull()
                            if (sig != null) onProgress(done.incrementAndGet().toFloat() / total)
                            key to sig
                        }
                    }
                }.awaitAll()
            }
            for ((key, sig) in fetched) if (sig != null) cache[slot(key)] = sig
            missing = fetched.filter { it.second == null }.map { it.first }
        }
        if (total > 0) saveCache(context, cache)
        RefSet(keys.mapNotNull { key -> cache[slot(key)]?.takeIf { it.isNotEmpty() }?.let { GuideReader.Ref(key.catId, key.form, it) } }, missing.size)
    }

    class Page(val tiles: Int, val cats: List<Int>)

    /** Reads one screenshot: how many icon tiles it has and which tracked cats they show. */
    suspend fun read(context: Context, uri: Uri, refs: List<GuideReader.Ref>): Page = withContext(Dispatchers.Default) {
        val full = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext Page(0, emptyList())
        val scanW = GuideReader.SCAN_WIDTH
        val scanH = (full.height.toLong() * scanW / full.width).toInt().coerceAtLeast(1)
        val scan = Bitmap.createScaledBitmap(full, scanW, scanH, true)
        val tiles = GuideReader.findTiles(pixels(scan), scanW, scanH)
        val px = pixels(full.copy(Bitmap.Config.ARGB_8888, false))
        val scale = full.width.toFloat() / scanW
        val found = ArrayList<Int>()
        for (t in tiles) {
            val x = (t.x * scale).toInt().coerceIn(0, full.width - 1)
            val y = (t.y * scale).toInt().coerceIn(0, full.height - 1)
            val w = (t.w * scale).toInt().coerceIn(1, full.width - x)
            val h = (t.h * scale).toInt().coerceIn(1, full.height - y)
            val exact = GuideReader.refine(px, full.width, full.height, GuideReader.Tile(x, y, w, h), (scale * 3).toInt() + 3)
            GuideReader.match(GuideReader.signature(px, full.width, exact.x, exact.y, exact.w, exact.h), refs)?.let { found.add(it.catId) }
        }
        Page(tiles.size, found)
    }
}

/**
 * The whole import: pick screenshots, read them, review what was found, then mark as owned.
 * [onDone] receives the cats to mark, or null when cancelled.
 */
@Composable
fun GuideImportDialog(game: Game, owned: Set<Int>, onDone: (Set<Int>?) -> Unit) {
    val context = LocalContext.current
    var uris by remember { mutableStateOf<List<Uri>?>(null) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var found by remember { mutableStateOf<List<Int>?>(null) }
    var tiles by remember { mutableStateOf(0) }
    var chosen by remember { mutableStateOf(setOf<Int>()) }
    var failed by remember { mutableStateOf<String?>(null) }
    var skipped by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(40)) { picked ->
        if (picked.isNotEmpty()) uris = picked
    }

    LaunchedEffect(uris) {
        val list = uris ?: return@LaunchedEffect
        failed = null
        status = "Getting cat pictures to compare with (first time only)…"
        progress = 0f
        val set = runCatching { GuideImport.references(context.applicationContext, game) { progress = it } }.getOrNull()
        val refs = set?.refs.orEmpty()
        skipped = set?.failed ?: 0
        if (refs.size < game.gachaCats.size / 2) {
            failed = "Couldn't download the cat pictures needed for matching. Check your internet connection and try again."
            uris = null
            return@LaunchedEffect
        }
        val all = LinkedHashSet<Int>()
        var count = 0
        list.forEachIndexed { i, uri ->
            status = "Reading screenshot ${i + 1} of ${list.size}…"
            progress = i.toFloat() / list.size
            val page = runCatching { GuideImport.read(context.applicationContext, uri, refs) }.getOrNull()
            if (page != null) { all.addAll(page.cats); count += page.tiles }
        }
        tiles = count
        found = all.sortedWith(compareBy({ -game.rarityIdx(it) }, { game.name(it) }))
        chosen = all.filter { it !in owned }.toSet()
    }

    val result = found
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("Import from Cat Guide") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    result != null -> {
                        val fresh = result.count { it !in owned }
                        Text(
                            if (result.isEmpty()) "No tracked cats were recognised in ${plural(tiles, "icon")}. Only cats from the Rare Capsule are tracked, so pages of Normal or Special cats find nothing."
                            else "Recognised ${plural(result.size, "cat")} among ${plural(tiles, "icon")}; $fresh of them are not marked owned yet. Untick any that look wrong.",
                        )
                        if (skipped > 0) Text(
                            "${plural(skipped, "comparison picture")} couldn't be downloaded just now, so a few cats may have been missed. Importing again later picks them up.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                        LazyColumn(Modifier.heightIn(max = 340.dp)) {
                            items(result, key = { it }) { id ->
                                val has = id in owned
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !has) { chosen = if (id in chosen) chosen - id else chosen + id }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = has || id in chosen, onCheckedChange = null, enabled = !has, modifier = Modifier.padding(end = 8.dp))
                                    CatIcon(id, game, width = 48.dp)
                                    Column(Modifier.padding(start = 10.dp)) {
                                        Text(game.name(id), fontWeight = FontWeight.SemiBold)
                                        if (has) Text("Already owned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    uris != null -> {
                        Text(status)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        Text("In the game, open the Cat Guide and take a screenshot of each page of your Rare, Super Rare, Uber Rare and Legend Rare cats. Then pick those screenshots here.")
                        Text("TrackCats recognises the icons and ticks those cats for you. You can review the list before anything is changed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        failed?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(
                            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Choose screenshots") }
                    }
                }
            }
        },
        confirmButton = {
            if (result != null && chosen.isNotEmpty()) Button(onClick = { onDone(chosen) }) { Text("Mark ${chosen.size} as owned") }
            else if (result != null) TextButton(onClick = { onDone(null) }) { Text("Close") }
        },
        dismissButton = { if (result == null || chosen.isNotEmpty()) TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}
