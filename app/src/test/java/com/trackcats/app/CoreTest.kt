package com.trackcats.app

import com.trackcats.app.core.Game
import com.trackcats.app.core.GameData
import com.trackcats.app.core.Planner
import com.trackcats.app.core.Rarity
import com.trackcats.app.core.Resync
import com.trackcats.app.core.Rng
import com.trackcats.app.core.Roller
import com.trackcats.app.core.SeedFinder
import com.trackcats.app.core.TrackTable
import com.trackcats.app.core.Valuer
import com.trackcats.app.core.YamlLite
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CoreTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val data: GameData = json.decodeFromString(File("src/main/assets/gamedata.json").readText())
    private val game = Game(data)
    private val today = LocalDate.parse("2026-11-26")

    private fun event(key: String) = game.event(key)!!

    @Test
    fun xorshiftMatchesReference() {
        // Worked by hand: 1 -> 0x2001 (<<13) -> 0x2001 (>>>17) -> 0x1000A001 (<<15)
        assertEquals(0x1000A001, Rng.adv(1))
        assertEquals(1_000_000_007 % 10000, Rng.umod(1_000_000_007, 10000))
        assertEquals(((-5).toLong() and 0xFFFFFFFFL).rem(10000).toInt(), Rng.umod(-5, 10000))
    }

    @Test
    fun bundledDataIsUsable() {
        val banners = game.ticketBanners(today)
        assertTrue("expected active banners", banners.isNotEmpty())
        assertNotNull(game.specialTicketEvent("platinum", today))
        assertNotNull(game.specialTicketEvent("legend", today))
        for (e in banners) {
            val pool = game.pool(e)
            assertTrue(pool.valid)
            assertEquals(10000, pool.scoreRange(Rarity.LEGEND).second)
        }
    }

    @Test
    fun rollsAreSequentialAndConsistent() {
        val pool = game.pool(game.ticketBanners(today).first())
        val rolls = Roller.simulate(pool, 123456789, -1, 200)
        assertEquals(200, rolls.size)
        for (i in 1 until rolls.size) {
            assertEquals(rolls[i - 1].seedAfter, rolls[i].seedBefore)
            assertEquals(rolls[i - 1].offset + rolls[i - 1].consumed, rolls[i].offset)
            // A rare cat is never the same as the roll before it.
            if (rolls[i].rarity == Rarity.RARE) assertTrue(rolls[i].catId != rolls[i - 1].catId)
        }
        assertEquals("1A", rolls[0].label)
    }

    @Test
    fun duplicateRareIsRerolledAndSwitchesTrack() {
        val pool = game.pool(game.ticketBanners(today).first())
        // Find a seed whose first roll is a rare, then roll again pretending that same cat was just obtained.
        var seed = 1
        var plain = Roller.rollOne(pool, seed, -1)
        while (plain.rarity != Rarity.RARE) { seed++; plain = Roller.rollOne(pool, seed, -1) }
        val rerolled = Roller.rollOne(pool, seed, plain.catId)
        assertTrue(rerolled.rerolled)
        assertTrue(rerolled.catId != plain.catId)
        assertEquals(3, rerolled.consumed)
        val next = Roller.simulate(pool, seed, plain.catId, 2)[1]
        assertEquals("2B", next.label)
    }

    @Test
    fun guaranteedDrawEndsWithUberAndSwitchesTrack() {
        val e = game.ticketBanners(today).firstOrNull { it.g } ?: data.events.first { it.g && game.pool(it).valid }
        val pool = game.pool(e)
        val draw = Roller.guaranteedDraw(pool, 987654321, -1)!!
        assertEquals(11, draw.size)
        assertEquals(Rarity.UBER, draw.last().rarity)
        assertTrue(draw.last().guaranteed)
        assertEquals(1, draw.last().consumed)
        assertEquals(Rng.adv(draw[9].seedAfter), draw.last().seedAfter)
    }

    @Test
    fun trackTableAgreesWithSequentialRolls() {
        val pool = game.pool(game.ticketBanners(today).first())
        val seed = 555555555
        val table = TrackTable.build(pool, seed, -1, 30)
        val cells = table.flatMap { listOf(it.a, it.b) }.associateBy { it.label }
        for (roll in Roller.simulate(pool, seed, -1, 10)) {
            val cell = cells[roll.label]!!
            assertTrue(cell.onPath)
            if (!roll.rerolled) assertEquals(roll.catId, cell.catId)
        }
    }

    @Test
    fun seedFinderRecoversSeed() {
        for ((idx, seed) in listOf(2_000_000_011.toInt(), 0xDEADBEEF.toInt(), 42).withIndex()) {
            val pool = game.pool(game.ticketBanners(today)[idx % game.ticketBanners(today).size])
            val rolls = Roller.simulate(pool, seed, -1, 10)
            val finder = SeedFinder(pool, rolls.map { it.catId }.toIntArray())
            assertNull(finder.problem())
            val started = System.currentTimeMillis()
            val result = finder.find()
            println("seed search ${idx + 1}: ${System.currentTimeMillis() - started} ms, ${result.seeds.size} match(es), start roll ${result.startIndex + 1}")
            assertTrue("expected the true end seed among results", rolls.last().seedAfter in result.seeds)
        }
    }

    @Test
    fun seedFinderHandlesHiddenFirstReroll() {
        val pool = game.pool(game.ticketBanners(today).first())
        var seed = 77
        var first = Roller.rollOne(pool, seed, -1)
        while (first.rarity != Rarity.RARE) { seed++; first = Roller.rollOne(pool, seed, -1) }
        // The player rolled `first.catId` just before starting to write rolls down, so roll 1 was rerolled.
        val rolls = Roller.simulate(pool, seed, first.catId, 10)
        assertTrue(rolls[0].rerolled)
        val result = SeedFinder(pool, rolls.map { it.catId }.toIntArray()).find()
        assertTrue(rolls.last().seedAfter in result.seeds)
    }

    @Test
    fun resyncFindsPositionAfterMissedRolls() {
        val pool = game.pool(game.ticketBanners(today).first())
        val seed = 31337
        val rolls = Roller.simulate(pool, seed, -1, 12)
        // The app missed 7 rolls; the player enters the last 4 cats they got.
        val observed = rolls.subList(7, 11).map { it.catId }
        val match = Resync.find(pool, seed, observed)
        assertNotNull(match)
        assertEquals(rolls[10].seedAfter, match!!.seedAfter)
    }

    @Test
    fun plannerProducesSensiblePlans() {
        val pools = game.ticketBanners(today).map { game.pool(it) }
        val v = Valuer(emptySet(), emptySet())
        val seed = 20261126
        val plan = Planner.ticketPlan(pools, seed, -1, 30, v)
        if (plan != null) {
            assertTrue(plan.tickets in 1..30)
            assertEquals(plan.tickets, plan.steps.sumOf { it.rolls.size })
            assertTrue(v.isGood(plan.steps.last().rolls.last()))
            // Steps chain on one seed.
            val flat = plan.steps.flatMap { it.rolls }
            for (i in 1 until flat.size) assertEquals(flat[i - 1].seedAfter, flat[i].seedBefore)
        }
        val target = pools.first().slots[Rarity.UBER].first()
        val routes = Planner.findTarget(pools, emptyList(), seed, -1, target)
        for (r in routes) {
            if (!r.viaGuaranteed) {
                val rolls = Roller.simulate(game.pool(r.event), seed, -1, r.tickets)
                assertEquals(target, rolls.last().catId)
            }
        }
        assertTrue(Planner.lookahead(pools.first(), seed, -1, 100, v).all { it.index in 1..100 })
        val plat = game.pool(game.specialTicketEvent("platinum", today)!!)
        val timing = Planner.ticketTiming(plat, pools, seed, -1, 10, v)
        assertEquals(Rarity.UBER, timing.roll.rarity)
    }

    @Test
    fun yamlParserMatchesBundledSnapshot() {
        val yaml = File("../refdata/bc-en.yaml")
        assumeTrue("reference yaml not present", yaml.exists())
        val parsed = YamlLite.parse(yaml.readText(), today)
        assertEquals(data.events, parsed.events)
        assertEquals(data.gacha, parsed.gacha)
        assertEquals(data.cats, parsed.cats)
    }

    /**
     * Checked against the reference tracker's live site (bc.godfat.org) on 2026-11-26 for these seeds:
     * first cat on track A, and where the banner's legend rare first appears.
     */
    @Test
    fun matchesReferenceSite() {
        fun firstLegend(key: String, seed: Int): String {
            val table = TrackTable.build(game.pool(event(key)), seed, -1, 400)
            return table.flatMap { listOf(it.a, it.b) }.first { it.rarity == Rarity.LEGEND }.let { "${game.name(it.catId)} ${it.label}" }
        }
        for ((key, seed) in listOf("2026-11-23_991" to 123456789, "2026-11-23_1059" to 3000000000L.toInt())) {
            val table = TrackTable.build(game.pool(event(key)), seed, -1, 12)
            println("reference check $key seed ${Rng.toLong(seed)}: " + table.joinToString(" | ") { "${it.index}A ${game.name(it.a.catId)} / ${it.index}B ${game.name(it.b.catId)}" })
            println("first legend: " + firstLegend(key, seed))
        }
        val t1 = TrackTable.build(game.pool(event("2026-11-23_991")), 123456789, -1, 12)
        assertEquals("Pogo Cat", game.name(t1[0].a.catId))
        assertEquals("Gaia the Creator 329B", firstLegend("2026-11-23_991", 123456789))
        val t2 = TrackTable.build(game.pool(event("2026-11-23_1059")), 3000000000L.toInt(), -1, 12)
        assertEquals("Wushu Cat", game.name(t2[0].a.catId))
        assertEquals("High Lord Babel 106A", firstLegend("2026-11-23_1059", 3000000000L.toInt()))
    }

    @Test
    fun flowListHandlesQuotes() {
        assertEquals(listOf("Kai", "Kai, type μ", "Divine Kai μ"), YamlLite.flowList("[Kai, 'Kai, type μ', Divine Kai μ]"))
        assertEquals(listOf("Li'l Gau", "a \"b\""), YamlLite.flowList("[Li'l Gau, \"a \\\"b\\\"\"]"))
        assertEquals(listOf("1", "22", "333"), YamlLite.flowList("[1, 22, 333]"))
        assertEquals("it's", YamlLite.unquote("'it''s'"))
    }
}
