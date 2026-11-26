package com.trackcats.app.core

/*
 * Roll simulation for The Battle Cats rare gacha (game version 8.6 and later).
 *
 * The algorithm is a Kotlin port of the logic in "battle-cats-rolls" by godfat
 * (https://gitlab.com/godfat/battle-cats-rolls), licensed under Apache-2.0. See NOTICE.
 */

object Rng {
    /** xorshift32, the game's generator. */
    fun adv(s: Int): Int {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 15)
        return x
    }

    fun umod(x: Int, m: Int): Int = ((x.toLong() and 0xFFFFFFFFL) % m).toInt()

    fun toLong(seed: Int): Long = seed.toLong() and 0xFFFFFFFFL
}

/** The cats and drop rates of one banner, split by rarity in the game's slot order. */
class Pool(val event: EventInfo, data: GameData) {
    val slots: Array<IntArray>
    val valid: Boolean
    private val t1 = event.ra
    private val t2 = event.ra + event.su
    private val t3 = event.ra + event.su + event.ub

    init {
        val lists = Array(4) { ArrayList<Int>() }
        var ok = true
        val ids = data.gacha[event.id]
        if (ids == null) ok = false else for (id in ids) {
            val cat = data.cats[id]
            if (cat == null) { ok = false; break }
            if (cat.r in 2..5) lists[cat.r - 2].add(id)
        }
        slots = if (ok) Array(4) { lists[it].toIntArray() } else Array(4) { IntArray(0) }
        valid = ok && slots.any { it.isNotEmpty() }
    }

    fun rarityOf(score: Int): Int = when {
        score < t1 -> Rarity.RARE
        score < t2 -> Rarity.SUPER
        score < t3 -> Rarity.UBER
        else -> Rarity.LEGEND
    }

    /** Scores (0 until 10000) that give this rarity: first inclusive, second exclusive. */
    fun scoreRange(rarity: Int): Pair<Int, Int> = when (rarity) {
        Rarity.RARE -> 0 to t1
        Rarity.SUPER -> t1 to t2
        Rarity.UBER -> t2 to t3
        else -> t3 to 10000
    }

    /** 11 for a guaranteed-uber 11-draw, 15 for a step-up banner, 0 when there is no guarantee. */
    val guaranteedRolls: Int get() = if (event.g) 11 else if (event.st) 15 else 0

    fun contains(id: Int): Boolean = slots.any { s -> s.any { it == id } }

    fun rarityOfCat(id: Int): Int = slots.indexOfFirst { s -> s.any { it == id } }
}

/**
 * One predicted roll.
 * [offset] counts how many random numbers were used before this roll, starting from the current seed;
 * it is what the classic "1A / 1B" track position is derived from.
 */
data class Roll(
    val catId: Int,
    val rarity: Int,
    val rerolled: Boolean,
    val seedBefore: Int,
    val seedAfter: Int,
    val offset: Int,
    val consumed: Int,
    val guaranteed: Boolean = false,
) {
    val label: String get() = trackLabel(offset)
}

fun trackLabel(offset: Int): String = if (offset % 2 == 0) "${offset / 2 + 1}A" else "${(offset + 1) / 2}B"

object Roller {
    /**
     * Roll once. [seed] is the seed as the game holds it before the roll and [last] is the cat
     * obtained by the previous roll (-1 if unknown); a rare cat identical to [last] is rerolled.
     */
    fun rollOne(pool: Pool, seed: Int, last: Int, offset: Int = 0): Roll {
        val r = Rng.adv(seed)
        val rarity = pool.rarityOf(Rng.umod(r, 10000))
        var s = Rng.adv(r)
        val slots = pool.slots[rarity]
        if (slots.isEmpty()) return Roll(-1, rarity, false, seed, s, offset, 2)
        var slot = Rng.umod(s, slots.size)
        var id = slots[slot]
        var steps = 0
        if (rarity == Rarity.RARE && id == last && id > 0) {
            val list = slots.toMutableList()
            val copies = slots.count { it == id }
            var newId = id
            for (i in 1..copies) {
                s = Rng.adv(s)
                list.removeAt(slot)
                steps = i
                if (list.isEmpty()) break
                slot = Rng.umod(s, list.size)
                newId = list[slot]
                if (newId != id) break
            }
            id = newId
        }
        return Roll(id, rarity, steps > 0, seed, s, offset, 2 + steps)
    }

    fun simulate(pool: Pool, seed: Int, last: Int, count: Int, offset: Int = 0): List<Roll> {
        val out = ArrayList<Roll>(count)
        var s = seed
        var l = last
        var o = offset
        repeat(count) {
            val roll = rollOne(pool, s, l, o)
            out.add(roll)
            s = roll.seedAfter
            l = roll.catId
            o += roll.consumed
        }
        return out
    }

    /**
     * A guaranteed draw: (total - 1) normal rolls, then one uber picked with the next random number.
     * Returns null when the banner has no guarantee or no ubers.
     */
    fun guaranteedDraw(pool: Pool, seed: Int, last: Int, offset: Int = 0, total: Int = pool.guaranteedRolls): List<Roll>? {
        val ubers = pool.slots[Rarity.UBER]
        if (total < 2 || ubers.isEmpty()) return null
        val rolls = ArrayList(simulate(pool, seed, last, total - 1, offset))
        val before = rolls.last().seedAfter
        val g = Rng.adv(before)
        val off = rolls.last().offset + rolls.last().consumed
        rolls.add(Roll(ubers[Rng.umod(g, ubers.size)], Rarity.UBER, false, before, g, off, 1, guaranteed = true))
        return rolls
    }
}

/** A cell of the classic two-track table. [altCatId] is what you get instead if the cell above was the same rare cat. */
class TrackCell(val label: String, val catId: Int, val rarity: Int, val altCatId: Int, val altLabel: String?, val onPath: Boolean)

class TrackRow(val index: Int, val a: TrackCell, val b: TrackCell)

object TrackTable {
    fun build(pool: Pool, seed: Int, last: Int, rows: Int): List<TrackRow> {
        val seeds = IntArray(rows * 2 + 2)
        seeds[0] = seed
        for (i in 1 until seeds.size) seeds[i] = Rng.adv(seeds[i - 1])
        val path = Roller.simulate(pool, seed, last, rows).map { it.offset }.toHashSet()
        val out = ArrayList<TrackRow>(rows)
        val prev = intArrayOf(last, -1)
        for (n in 0 until rows) {
            val cells = Array(2) { track ->
                val offset = n * 2 + track
                val plain = Roller.rollOne(pool, seeds[offset], -1, offset)
                var alt = -1
                var altLabel: String? = null
                if (plain.rarity == Rarity.RARE && plain.catId == prev[track] && plain.catId > 0) {
                    val rr = Roller.rollOne(pool, seeds[offset], prev[track], offset)
                    alt = rr.catId
                    altLabel = trackLabel(offset + rr.consumed)
                }
                prev[track] = plain.catId
                TrackCell(trackLabel(offset), plain.catId, plain.rarity, alt, altLabel, offset in path)
            }
            out.add(TrackRow(n + 1, cells[0], cells[1]))
        }
        return out
    }
}

/** Finds where you are when your rolls stopped matching: looks ahead from the stored seed for the cats you actually got. */
object Resync {
    class Match(val seedAfter: Int, val skippedNumbers: Int)

    fun find(pool: Pool, seed: Int, observed: List<Int>, maxOffset: Int = 8000): Match? {
        if (observed.isEmpty()) return null
        for (skipFirst in 0..1) {
            if (skipFirst == 1 && observed.size < 3) break
            var s = seed
            for (offset in 0..maxOffset) {
                val end = tryFrom(pool, s, observed, skipFirst)
                if (end != null) return Match(end, offset)
                s = Rng.adv(s)
            }
        }
        return null
    }

    private fun tryFrom(pool: Pool, seed: Int, observed: List<Int>, skipFirst: Int): Int? {
        var s = seed
        var last = if (skipFirst == 1) observed[0] else -1
        for (j in skipFirst until observed.size) {
            val roll = Roller.rollOne(pool, s, last)
            if (roll.catId != observed[j]) return null
            s = roll.seedAfter
            last = roll.catId
        }
        return s
    }
}
