package com.trackcats.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.trackcats.app.core.EventInfo
import com.trackcats.app.core.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.time.LocalDate

/**
 * Cat icons and banner art. Nothing is bundled with the app: pictures are downloaded from the
 * community-run Battle Cats Wiki the first time they are needed and then kept on the phone, so
 * they keep working offline. When a picture is missing the app falls back to names and colours.
 */
object Art {
    private const val FILE_URL = "https://battlecats.miraheze.org/wiki/Special:FilePath/"
    private const val USER_AGENT = "TrackCats/1 (Android fan app; cached image viewer)"

    /** Icon of a cat's first form. Wiki files are numbered from 0, the game data from 1. */
    fun catUrl(id: Int): String = FILE_URL + "Uni%03d_f00.png".format(id - 1)

    private var bannerFiles: Map<Int, String>? = null

    private fun bannerMap(context: Context): Map<Int, String> = bannerFiles ?: runCatching {
        val text = context.assets.open("banner_art.json").use { it.readBytes().toString(Charsets.UTF_8) }
        Json.decodeFromString(MapSerializer(Int.serializer(), String.serializer()), text)
    }.getOrDefault(emptyMap()).also { bannerFiles = it }

    private val nearest = HashMap<Int, String?>()

    /**
     * Banner picture for an event. Banners added after this app was built are not in the bundled
     * list, so those borrow the art of the known banner whose uber line-up is most alike.
     */
    fun bannerUrl(context: Context, game: Game, event: EventInfo): String? {
        val map = bannerMap(context)
        map[event.id]?.let { return FILE_URL + it }
        val file = synchronized(nearest) {
            nearest.getOrPut(event.id) {
                val mine = ubers(game, event.id)
                if (mine.isEmpty()) null
                else map.entries.map { (id, f) ->
                    val other = ubers(game, id)
                    val union = (mine + other).size
                    (if (union == 0) 0f else mine.intersect(other).size.toFloat() / union) to f
                }.filter { it.first >= 0.5f }.maxByOrNull { it.first }?.second
            }
        }
        return file?.let { FILE_URL + it }
    }

    private fun ubers(game: Game, gachaId: Int): Set<Int> =
        game.data.gacha[gachaId].orEmpty().filter { (game.data.cats[it]?.r ?: 0) >= 4 }.toSet()

    /** Image loader with a permanent on-phone store, so each picture is downloaded only once. */
    fun newLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient {
            OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }.build()
        }
        .diskCache { DiskCache.Builder().directory(File(context.filesDir, "art")).maxSizeBytes(256L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(180)
        .build()

    /**
     * Fetches every cat icon and the current banners in the background, a few at a time, so lists
     * are already filled in when the player opens them and keep working without internet.
     */
    suspend fun prefetch(context: Context, loader: ImageLoader, game: Game, today: LocalDate) {
        if (Build.FINGERPRINT == "robolectric") return
        val marker = File(context.filesDir, "art-fetched")
        val want = "${game.gachaCats.size}-${game.data.fetched}"
        if (runCatching { marker.readText() }.getOrNull() == want) return
        val events = game.ticketBanners(today) + game.upcomingBanners(today) +
            listOfNotNull(game.specialTicketEvent("platinum", today), game.specialTicketEvent("legend", today))
        val urls = events.mapNotNull { bannerUrl(context, game, it) }.distinct() + game.gachaCats.map { catUrl(it) }
        var failed = 0
        withContext(Dispatchers.IO) {
            for (url in urls) {
                val request = ImageRequest.Builder(context).data(url).size(Size.ORIGINAL).memoryCachePolicy(CachePolicy.DISABLED).build()
                if (loader.execute(request).drawable == null) failed++
                if (failed > 25) return@withContext
                delay(40)
            }
        }
        if (failed <= 25) runCatching { marker.writeText(want) }
    }
}

/** A cat's picture in a rarity-coloured frame. Shows the first letter of its name until the picture arrives. */
@Composable
fun CatIcon(id: Int, game: Game, modifier: Modifier = Modifier, width: Dp = 54.dp) {
    val context = LocalContext.current
    val rarity = game.rarityIdx(id)
    val shape = RoundedCornerShape(8.dp)
    // The wiki icon is a 128 px square with the 110 x 85 picture centred in it.
    Box(
        modifier
            .size(width, width * 85 / 110)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(rarityColor(rarity).copy(alpha = 0.55f), rarityColor(rarity))))
            .border(2.dp, rarityColor(rarity), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(game.name(id).take(1), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
        if (id > 0) AsyncImage(
            model = remember(id) { ImageRequest.Builder(context).data(Art.catUrl(id)).build() },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // Laid out larger than the frame so the transparent margin falls outside the clip.
            modifier = Modifier.requiredSize(width * 128 / 110),
        )
    }
}

/** A banner's artwork (860 x 240). Falls back to a coloured panel with the banner's name. */
@Composable
fun BannerArt(event: EventInfo, game: Game, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(14.dp)) {
    val context = LocalContext.current
    val url = remember(event.id, game) { Art.bannerUrl(context, game, event) }
    Box(
        modifier.fillMaxWidth().aspectRatio(860f / 240f).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF3B5BDB), Color(0xFF7048E8), Color(0xFFE64980)))),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedText(
            bannerTitle(event), MaterialTheme.typography.titleMedium, Modifier.padding(horizontal = 12.dp),
            maxLines = 2, textAlign = TextAlign.Center, outlineWidth = 3.dp,
        )
        if (url != null) AsyncImage(
            model = remember(url) { ImageRequest.Builder(context).data(url).build() },
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
    }
}
