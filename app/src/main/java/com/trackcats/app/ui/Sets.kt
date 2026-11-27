package com.trackcats.app.ui

import android.content.Context
import com.trackcats.app.core.EventInfo
import com.trackcats.app.core.Game
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** One banner set (for example "THE DYNAMITES"): its latest banner and the cats only found by rolling. */
class CatSet(val name: String, val event: EventInfo, val cats: Set<Int>, val ubers: List<Int>, val legends: List<Int>)

/**
 * Names of banner sets. The game data only carries each banner's advertising line, so set names
 * come from a bundled list (assets/banner_names.json, built by tools/make_banner_map.py). Banners
 * newer than the list take the name of the known banner whose uber line-up is most alike.
 */
object Sets {
    private var names: Map<Int, String>? = null
    private val nearest = HashMap<Int, String?>()

    private fun nameMap(context: Context): Map<Int, String> = names ?: runCatching {
        val text = context.assets.open("banner_names.json").use { it.readBytes().toString(Charsets.UTF_8) }
        Json.decodeFromString(MapSerializer(Int.serializer(), String.serializer()), text)
    }.getOrDefault(emptyMap()).also { names = it }

    private fun ubers(game: Game, gachaId: Int): Set<Int> =
        game.data.gacha[gachaId].orEmpty().filter { (game.data.cats[it]?.r ?: 0) >= 4 }.toSet()

    fun name(context: Context, game: Game, event: EventInfo): String {
        if (event.p == "platinum") return "Platinum Capsules"
        if (event.p == "legend") return "Legend Capsules"
        val map = nameMap(context)
        map[event.id]?.let { return it }
        val found = synchronized(nearest) {
            nearest.getOrPut(event.id) {
                val mine = ubers(game, event.id)
                if (mine.isEmpty()) null
                else map.entries.map { (id, n) ->
                    val other = ubers(game, id)
                    val union = (mine + other).size
                    (if (union == 0) 0f else mine.intersect(other).size.toFloat() / union) to n
                }.filter { it.first >= 0.5f }.maxByOrNull { it.first }?.second
            }
        }
        return found ?: bannerTitle(event)
    }

    /** Every set that has a Rare Ticket banner in the saved data, newest version of each, by name. */
    fun all(context: Context, game: Game): List<CatSet> =
        game.data.events.filter { it.p == null && game.pool(it).valid }
            .groupBy { name(context, game, it) }
            .map { (name, events) ->
                val event = events.maxBy { it.s }
                val cats = game.data.gacha[event.id].orEmpty()
                CatSet(
                    name, event, cats.toSet(),
                    cats.filter { game.data.cats[it]?.r == 4 }.distinct().sortedBy { game.name(it) },
                    cats.filter { game.data.cats[it]?.r == 5 }.distinct().sortedBy { game.name(it) },
                )
            }
            .sortedBy { it.name.lowercase() }
}
