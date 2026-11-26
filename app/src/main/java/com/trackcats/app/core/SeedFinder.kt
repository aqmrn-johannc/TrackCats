package com.trackcats.app.core

import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Searches all 2^32 seeds for the ones that produce the cats you rolled, in order, on one banner.
 * Only the seed *after* your last roll matters, so the search may start from a later roll when
 * that roll is rarer (fewer candidates to test).
 */
class SeedFinder(private val pool: Pool, private val ids: IntArray) {
    class Result(val seeds: List<Int>, val startIndex: Int)

    @Volatile
    var cancelled = false
    private val done = AtomicLong(0)
    private var totalBlocks = BLOCKS

    /** 0.0 to 1.0 */
    val progress: Float get() = (done.get().toFloat() / totalBlocks).coerceIn(0f, 1f)

    private val rar = IntArray(ids.size) { pool.rarityOfCat(ids[it]) }

    /** A message describing why the input cannot be searched, or null when it is fine. */
    fun problem(): String? = when {
        !pool.valid -> "This banner's cat list is not available."
        ids.size < MIN_ROLLS -> "Add at least $MIN_ROLLS rolls."
        rar.any { it < 0 } -> "One of the cats is not on this banner."
        else -> null
    }

    fun find(threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)): Result {
        val n = ids.size
        var start = 0
        var best = Long.MAX_VALUE
        for (k in 0..(n - MIN_ROLLS).coerceAtLeast(0)) {
            val (lo, hi) = pool.scoreRange(rar[k])
            val cost = (hi - lo).toLong()
            if (cost < best) { best = cost; start = k }
        }
        val tries = ArrayList<Int>()
        tries.add(start)
        // The very first roll may itself have been a duplicate reroll we cannot see; retry without it.
        if (start == 0 && rar[0] == Rarity.RARE && n - 1 >= MIN_ROLLS - 1) tries.add(1)
        totalBlocks = BLOCKS * tries.size
        for (k in tries) {
            val found = search(k, threads)
            if (cancelled) return Result(emptyList(), k)
            if (found.isNotEmpty()) return Result(found, k)
        }
        return Result(emptyList(), start)
    }

    private fun search(k: Int, threads: Int): List<Int> {
        val found = LinkedHashSet<Int>()
        val (low, high) = pool.scoreRange(rar[k])
        val slots0 = pool.slots[rar[k]]
        val size0 = slots0.size
        val want0 = ids[k]
        val prev = if (k > 0) ids[k - 1] else -1
        val rare0 = rar[k] == Rarity.RARE
        val workers = (0 until threads).map { t ->
            thread(name = "seed-finder-$t", priority = Thread.NORM_PRIORITY) {
                val from = BLOCKS * t / threads
                val to = BLOCKS * (t + 1) / threads
                var block = from
                var sinceReport = 0
                while (block < to && !cancelled) {
                    val base = block * 10000L
                    var off = low
                    while (off < high) {
                        val v = base + off
                        if (v > 0xFFFFFFFFL) break
                        val s1 = v.toInt()
                        off++
                        if (s1 == 0) continue
                        val s2 = Rng.adv(s1)
                        val id0 = slots0[Rng.umod(s2, size0)]
                        if (id0 != want0 && !(rare0 && id0 == prev)) continue
                        val end = verify(s1, k, prev)
                        if (end >= 0) synchronized(found) { if (found.size < MAX_RESULTS) found.add(end.toInt()) }
                    }
                    block++
                    if (++sinceReport == 256) { done.addAndGet(256); sinceReport = 0 }
                }
                done.addAndGet(sinceReport.toLong())
            }
        }
        workers.forEach { it.join() }
        return found.toList()
    }

    /** Simulates rolls k.. starting with [s1] as the first random number. Returns the final seed, or -1. */
    private fun verify(s1: Int, k: Int, prev: Int): Long {
        var cur = 0
        var last = prev
        for (j in k until ids.size) {
            val r = if (j == k) s1 else Rng.adv(cur)
            val rarity = pool.rarityOf(Rng.umod(r, 10000))
            if (rarity != rar[j]) return -1
            var s = Rng.adv(r)
            val slots = pool.slots[rarity]
            var slot = Rng.umod(s, slots.size)
            var id = slots[slot]
            if (rarity == Rarity.RARE && id == last && id > 0) {
                val list = slots.toMutableList()
                val copies = slots.count { it == id }
                var newId = id
                for (i in 1..copies) {
                    s = Rng.adv(s)
                    list.removeAt(slot)
                    if (list.isEmpty()) break
                    slot = Rng.umod(s, list.size)
                    newId = list[slot]
                    if (newId != id) break
                }
                id = newId
            }
            if (id != ids[j]) return -1
            cur = s
            last = id
        }
        return Rng.toLong(cur)
    }

    companion object {
        const val MIN_ROLLS = 7
        const val RECOMMENDED_ROLLS = 10
        const val MAX_RESULTS = 5
        private const val BLOCKS = 429497L // ceil(2^32 / 10000)
    }
}
