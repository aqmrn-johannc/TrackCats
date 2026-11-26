package com.trackcats.app.core

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** One cat: all form names (first form first) and the game's rarity number (2 rare .. 5 legend). */
@Serializable
data class CatInfo(val n: List<String>, val r: Int)

/** One gacha event (banner). Rates are out of 10000; legend is whatever is left over. */
@Serializable
data class EventInfo(
    val k: String,
    val s: String,
    val e: String,
    val n: String,
    val id: Int,
    val ra: Int,
    val su: Int,
    val ub: Int,
    val le: Int = 0,
    val g: Boolean = false,
    val st: Boolean = false,
    val p: String? = null,
) {
    val start: LocalDate get() = LocalDate.parse(s)
    val end: LocalDate get() = LocalDate.parse(e)
    fun isActive(today: LocalDate) = !today.isBefore(start) && !today.isAfter(end)
    fun isUpcoming(today: LocalDate) = start.isAfter(today)
}

@Serializable
data class GameData(
    val fetched: String,
    val cats: Map<Int, CatInfo>,
    val gacha: Map<Int, List<Int>>,
    val events: List<EventInfo>,
)

object Rarity {
    const val RARE = 0
    const val SUPER = 1
    const val UBER = 2
    const val LEGEND = 3
    val names = listOf("Rare", "Super Rare", "Uber Rare", "Legend Rare")
    val short = listOf("Rare", "Super", "Uber", "Legend")
}

/** Game data plus the lookups the app needs. */
class Game(val data: GameData) {
    private val pools = HashMap<String, Pool>()

    fun pool(event: EventInfo): Pool = synchronized(pools) { pools.getOrPut(event.k) { Pool(event, data) } }

    fun event(key: String?): EventInfo? = key?.let { k -> data.events.firstOrNull { it.k == k } }

    fun name(id: Int): String = data.cats[id]?.n?.firstOrNull() ?: if (id < 0) "(unknown)" else "Cat #$id"

    fun rarityIdx(id: Int): Int = ((data.cats[id]?.r ?: 2) - 2).coerceIn(0, 3)

    /** Banners you can spend Rare Tickets or Cat Food on today. */
    fun ticketBanners(today: LocalDate): List<EventInfo> =
        data.events.filter { it.p == null && it.isActive(today) && pool(it).valid }
            .sortedWith(compareBy({ it.e }, { it.n }))

    fun upcomingBanners(today: LocalDate): List<EventInfo> =
        data.events.filter { it.p == null && it.isUpcoming(today) && pool(it).valid }
            .sortedWith(compareBy({ it.s }, { it.n }))

    /** Banners that ended recently; needed when finding a seed from rolls made a while ago. */
    fun recentBanners(today: LocalDate, days: Long = 60): List<EventInfo> =
        data.events.filter {
            it.p == null && it.end.isBefore(today) && !it.end.isBefore(today.minusDays(days)) && pool(it).valid
        }.sortedByDescending { it.e }

    /** The current Platinum ("platinum") or Legend ("legend") ticket capsule. */
    fun specialTicketEvent(kind: String, today: LocalDate): EventInfo? =
        data.events.filter { it.p == kind && it.isActive(today) && pool(it).valid }.maxByOrNull { it.s }

    /** Every cat that appears in a banner we know about, rarest first then by name. */
    val gachaCats: List<Int> by lazy {
        data.cats.keys.filter { (data.cats[it]?.r ?: 0) in 2..5 }
            .sortedWith(compareBy({ -(data.cats[it]?.r ?: 0) }, { name(it) }))
    }
}
