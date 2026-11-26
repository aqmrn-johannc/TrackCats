package com.trackcats.app.core

/** Scores cats from the player's point of view: new and wanted cats are worth far more than duplicates. */
class Valuer(private val owned: Set<Int>, private val wishlist: Set<Int>) {
    fun isOwned(id: Int) = id in owned
    fun isWanted(id: Int) = id in wishlist

    /** A roll worth spending for: anything on the wishlist, or an uber/legend you do not own. */
    fun isGood(roll: Roll): Boolean =
        roll.catId > 0 && (isWanted(roll.catId) || (roll.rarity >= Rarity.UBER && !isOwned(roll.catId)))

    fun value(roll: Roll): Double {
        if (roll.catId <= 0) return 0.0
        val base = if (isOwned(roll.catId)) DUPE[roll.rarity] else NEW[roll.rarity]
        return base + if (isWanted(roll.catId)) 300.0 else 0.0
    }

    companion object {
        private val NEW = doubleArrayOf(3.0, 10.0, 100.0, 400.0)
        private val DUPE = doubleArrayOf(0.5, 2.0, 12.0, 30.0)

        /** NP received for trading in a duplicate, by rarity (legend rares are not listed in the app). */
        val DUPE_NP = intArrayOf(5, 15, 50, 0)
    }
}

class BannerScore(val event: EventInfo, val rolls: List<Roll>, val value: Double, val good: List<Roll>)

enum class StepKind { SINGLES, GUARANTEED, PLATINUM, LEGEND }

class PlanStep(val event: EventInfo, val kind: StepKind, val rolls: List<Roll>)

class Plan(val steps: List<PlanStep>, val tickets: Int, val catFood: Int, val good: List<Roll>, val value: Double)

class TicketTiming(val event: EventInfo, val rollsFirst: Int, val roll: Roll, val nowRoll: Roll)

class TargetRoute(val event: EventInfo, val upcoming: Boolean, val tickets: Int, val catFood: Int, val viaGuaranteed: Boolean) {
    /** Rough single-number cost so routes can be ranked: one ticket is worth one 150 cat food roll. */
    val cost: Int get() = tickets * 150 + catFood
}

class Notable(val index: Int, val roll: Roll)

object Planner {
    const val CF_SINGLE = 150
    const val CF_ELEVEN = 1500

    fun score(pool: Pool, seed: Int, last: Int, count: Int, v: Valuer): BannerScore {
        val rolls = Roller.simulate(pool, seed, last, count)
        return BannerScore(pool.event, rolls, rolls.sumOf { v.value(it) }, rolls.filter { v.isGood(it) })
    }

    /**
     * Best use of Rare Tickets on the banners open today. For every roll it picks the banner whose cat at
     * that point is worth most (all banners share one seed), then drops trailing rolls that gain nothing.
     */
    fun ticketPlan(pools: List<Pool>, seed: Int, last: Int, tickets: Int, v: Valuer): Plan? {
        if (pools.isEmpty() || tickets <= 0) return null
        val picks = mixedPath(pools, seed, last, tickets.coerceAtMost(MAX_PLAN_ROLLS), v)
        val cut = picks.indexOfLast { v.isGood(it.second) }
        if (cut < 0) return null
        val kept = picks.subList(0, cut + 1)
        return Plan(group(kept), kept.size, 0, kept.map { it.second }.filter { v.isGood(it) }, kept.sumOf { v.value(it.second) })
    }

    /** Best guaranteed draw paid with Cat Food, optionally after a few ticket rolls to line it up. */
    fun catFoodPlan(pools: List<Pool>, seed: Int, last: Int, tickets: Int, catFood: Int, v: Valuer): Plan? {
        val guaranteed = pools.filter { it.event.g }
        if (guaranteed.isEmpty() || catFood < CF_ELEVEN) return null
        val lead = mixedPath(pools, seed, last, tickets.coerceAtMost(30), v)
        var best: Plan? = null
        for (k in 0..lead.size) {
            val prefix = lead.subList(0, k)
            val s = if (k == 0) seed else prefix.last().second.seedAfter
            val l = if (k == 0) last else prefix.last().second.catId
            for (pool in guaranteed) {
                val draw = Roller.guaranteedDraw(pool, s, l) ?: continue
                if (!v.isGood(draw.last())) continue
                val all = prefix.map { it.second } + draw
                // Each ticket spent only to line the draw up costs a little, so the earliest good draw wins ties.
                val value = all.sumOf { v.value(it) } - k * 5.0
                if (best == null || value > best.value) {
                    best = Plan(group(prefix) + PlanStep(pool.event, StepKind.GUARANTEED, draw), k, CF_ELEVEN, all.filter { v.isGood(it) }, value)
                }
            }
        }
        return best
    }

    /** When to use a Platinum or Legend ticket: now, or after a few more ticket rolls. */
    fun ticketTiming(special: Pool, pools: List<Pool>, seed: Int, last: Int, tickets: Int, v: Valuer): TicketTiming {
        val now = Roller.rollOne(special, seed, last)
        if (v.isGood(now)) return TicketTiming(special.event, 0, now, now)
        val lead = mixedPath(pools, seed, last, tickets.coerceAtMost(MAX_PLAN_ROLLS), v)
        var bestK = 0
        var best = now
        for (k in 1..lead.size) {
            val r = lead[k - 1].second
            val roll = Roller.rollOne(special, r.seedAfter, r.catId)
            if (v.value(roll) > v.value(best)) { best = roll; bestK = k }
            if (v.isGood(roll) && v.isWanted(roll.catId)) break
        }
        return TicketTiming(special.event, bestK, best, now)
    }

    /** Cheapest ways to reach one specific cat, on banners open now and banners coming up. */
    fun findTarget(active: List<Pool>, upcoming: List<Pool>, seed: Int, last: Int, catId: Int, maxRolls: Int = 300): List<TargetRoute> {
        val routes = ArrayList<TargetRoute>()
        for ((pools, isUpcoming) in listOf(active to false, upcoming to true)) {
            for (pool in pools) {
                if (!pool.contains(catId)) continue
                val rolls = Roller.simulate(pool, seed, last, maxRolls)
                val at = rolls.indexOfFirst { it.catId == catId }
                if (at >= 0) routes.add(TargetRoute(pool.event, isUpcoming, at + 1, 0, false))
                if (pool.event.g && pool.rarityOfCat(catId) == Rarity.UBER) {
                    for (k in 0..minOf(100, rolls.size)) {
                        val s = if (k == 0) seed else rolls[k - 1].seedAfter
                        val l = if (k == 0) last else rolls[k - 1].catId
                        val draw = Roller.guaranteedDraw(pool, s, l) ?: break
                        if (draw.any { it.catId == catId }) {
                            routes.add(TargetRoute(pool.event, isUpcoming, k, CF_ELEVEN, true))
                            break
                        }
                    }
                }
            }
        }
        return routes.sortedWith(compareBy({ it.cost }, { it.upcoming }))
    }

    /** Legend rares, wishlist cats and ubers you do not own within the next [count] rolls on one banner. */
    fun lookahead(pool: Pool, seed: Int, last: Int, count: Int, v: Valuer): List<Notable> =
        Roller.simulate(pool, seed, last, count).mapIndexedNotNull { i, roll ->
            if (roll.rarity == Rarity.LEGEND || v.isGood(roll)) Notable(i + 1, roll) else null
        }

    private fun mixedPath(pools: List<Pool>, seed: Int, last: Int, count: Int, v: Valuer): List<Pair<Pool, Roll>> {
        val out = ArrayList<Pair<Pool, Roll>>(count)
        var s = seed
        var l = last
        var o = 0
        var current: Pool? = null
        repeat(count) {
            var bestPool = pools[0]
            var bestRoll = Roller.rollOne(bestPool, s, l, o)
            var bestValue = v.value(bestRoll) + if (bestPool === current) STAY_BONUS else 0.0
            for (i in 1 until pools.size) {
                val roll = Roller.rollOne(pools[i], s, l, o)
                val value = v.value(roll) + if (pools[i] === current) STAY_BONUS else 0.0
                if (value > bestValue) { bestPool = pools[i]; bestRoll = roll; bestValue = value }
            }
            out.add(bestPool to bestRoll)
            current = bestPool
            s = bestRoll.seedAfter
            l = bestRoll.catId
            o += bestRoll.consumed
        }
        return out
    }

    private fun group(picks: List<Pair<Pool, Roll>>): List<PlanStep> {
        val steps = ArrayList<PlanStep>()
        var i = 0
        while (i < picks.size) {
            var j = i
            while (j < picks.size && picks[j].first === picks[i].first) j++
            steps.add(PlanStep(picks[i].first.event, StepKind.SINGLES, picks.subList(i, j).map { it.second }))
            i = j
        }
        return steps
    }

    private const val MAX_PLAN_ROLLS = 60

    /** Small preference for staying on the same banner so plans do not hop around for nothing. */
    private const val STAY_BONUS = 1.5
}
