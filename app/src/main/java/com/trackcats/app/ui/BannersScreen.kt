package com.trackcats.app.ui

import com.trackcats.app.core.Rarity
import com.trackcats.app.data.UserState
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackcats.app.core.BannerScore
import com.trackcats.app.core.EventInfo
import com.trackcats.app.core.Game
import com.trackcats.app.core.Planner
import java.time.LocalDate

/** A one-line description of what a banner's next rolls hold for this player. */
fun scoreSummary(score: BannerScore, game: Game): String {
    if (score.good.isEmpty()) return "Nothing special in these rolls."
    return score.good.joinToString(", ") { roll ->
        "${game.name(roll.catId)} (roll ${score.rolls.indexOf(roll) + 1})"
    }
}

@Composable
fun BannersScreen(vm: AppVm, onOpenBanner: (EventInfo) -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    val today = vm.today
    val context = LocalContext.current
    val seed = user.seedInt
    var view by rememberSaveable { mutableStateOf("compare") }
    var count by rememberSaveable { mutableIntStateOf(10) }
    val valuer = remember(user.owned, user.wishlist) { vm.valuer(user) }
    val active = remember(game, today) { game.ticketBanners(today) }
    val upcoming = remember(game, today) { game.upcomingBanners(today) }

    fun scores(events: List<EventInfo>): List<BannerScore> =
        if (seed == null) emptyList()
        else events.map { Planner.score(game.pool(it), seed, user.lastCat, count, valuer) }.sortedByDescending { it.value }

    val activeScores = remember(active, seed, user.lastCat, count, valuer) { scores(active) }
    val upcomingScores = remember(upcoming, seed, user.lastCat, count, valuer) { scores(upcoming) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            if (view == "calendar") ScreenTitle("Banner calendar", subtitle = "Open and upcoming banners, week by week. Tap one to see your rolls on it.")
            else ScreenTitle("Compare banners", subtitle = "The same seed gives different cats on each banner. This shows what your next rolls would be on every one.")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = view == "compare", onClick = { view = "compare" }, label = { Text("Compare") })
                FilterChip(selected = view == "calendar", onClick = { view = "calendar" }, label = { Text("Calendar") })
            }
        }
        if (view == "calendar") {
            if (active.isNotEmpty()) item(key = "c-open") { GroupLabel("Open now") }
            items(active, key = { "c-${it.k}" }) { e -> CalendarCard(e, Sets.name(context, game, e), game, user, today, onOpenBanner) }
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            for ((week, events) in upcoming.groupBy { it.start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSortedMap()) {
                val label = when (week) {
                    monday -> "Starting later this week"
                    monday.plusWeeks(1) -> "Starting next week"
                    else -> "Week of ${prettyDate(week.toString())}"
                }
                item(key = "c-w-$week") { GroupLabel(label) }
                items(events, key = { "c-${it.k}" }) { e -> CalendarCard(e, Sets.name(context, game, e), game, user, today, onOpenBanner) }
            }
            if (active.isEmpty() && upcoming.isEmpty()) item { SectionCard { Text("No banners in the saved data. Open More, then Game data, and tap Update now.") } }
            return@LazyColumn
        }
        if (seed == null) {
            item { SectionCard(highlight = true) { Text("Find your seed first (Rolls tab) to see forecasts here.") } }
            items(active, key = { "plain-${it.k}" }) { e -> PlainBannerCard(e, game, today) }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (n in listOf(5, 10, 20, 50)) FilterChip(selected = count == n, onClick = { count = n }, label = { Text("Next $n") })
            }
        }
        item {
            val best = activeScores.firstOrNull()
            SectionCard(title = "Verdict", highlight = true) {
                when {
                    best == null -> Text("No banner is open today.")
                    best.good.isEmpty() -> Text("None of today's banners gives you a new uber, legend or wishlist cat in your next $count rolls. Holding your tickets is the better move.")
                    else -> {
                        Text("Best use of your next $count rolls: ${bannerTitle(best.event)}", fontWeight = FontWeight.Bold)
                        BannerArt(best.event, game)
                        GoodCats(best, game)
                    }
                }
                val later = upcomingScores.firstOrNull()
                if (later != null && later.good.isNotEmpty() && (best == null || later.value > best.value)) {
                    Text("Worth waiting for: ${bannerTitle(later.event)}, ${bannerWhen(later.event, today).lowercase()}. ${scoreSummary(later, game)}")
                }
            }
        }
        if (activeScores.isNotEmpty()) item(key = "h-open") { GroupLabel("Open now", Modifier.animateItem()) }
        items(activeScores, key = { "a-${it.event.k}" }) { s -> ScoredBannerCard(s, game, today, onOpenBanner, Modifier.animateItem()) }
        if (upcomingScores.isNotEmpty()) item(key = "h-up") { GroupLabel("Coming up", Modifier.animateItem()) }
        items(upcomingScores, key = { "u-${it.event.k}" }) { s -> ScoredBannerCard(s, game, today, onOpenBanner, Modifier.animateItem()) }
    }
}

@Composable
private fun ScoredBannerCard(score: BannerScore, game: Game, today: LocalDate, onOpen: (EventInfo) -> Unit, modifier: Modifier = Modifier) {
    SectionCard(modifier.clip(RoundedCornerShape(20.dp)).clickable { onOpen(score.event) }) {
        BannerArt(score.event, game)
        BannerLines(score.event, today)
        GoodCats(score, game)
        Text("See the rolls", Modifier.align(Alignment.End), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

/** The notable cats in a banner's next rolls, as pictures with the roll number under each. */
@Composable
private fun GoodCats(score: BannerScore, game: Game) {
    if (score.good.isEmpty()) Text("Nothing special in these rolls.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    else CatStrip(score.good.map { it.catId }, game, captions = score.good.map { "Roll ${score.rolls.indexOf(it) + 1}" })
}

@Composable
private fun PlainBannerCard(event: EventInfo, game: Game, today: LocalDate) {
    SectionCard {
        BannerArt(event, game)
        BannerLines(event, today)
    }
}

/** One banner on the calendar: when it runs, what is special about it and what it holds for this player. */
@Composable
private fun CalendarCard(event: EventInfo, setName: String, game: Game, user: UserState, today: LocalDate, onOpen: (EventInfo) -> Unit) {
    val pool = game.pool(event)
    val ubers = remember(event.k) { (pool.slots[Rarity.UBER] + pool.slots[Rarity.LEGEND]).distinct() }
    val wanted = remember(event.k, user.wishlist) { user.wishlist.filter { pool.contains(it) }.sortedBy { game.name(it) } }
    val missing = remember(event.k, user.owned) { ubers.count { it !in user.owned } }
    SectionCard(Modifier.clip(RoundedCornerShape(20.dp)).clickable { onOpen(event) }) {
        BannerArt(event, game)
        Text(setName, style = MaterialTheme.typography.titleMedium)
        Text(
            "${prettyDate(event.s)} – ${if (event.end.year >= 2030) "no end date" else prettyDate(event.e)} · ${bannerWhen(event, today)}",
            fontWeight = FontWeight.SemiBold,
        )
        Text(bannerTitle(event), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (event.g) Tag("GUARANTEED 11-DRAW", rarityColor(Rarity.UBER))
            if (event.st) Tag("STEP-UP", rarityColor(Rarity.SUPER))
            if (!event.g && !event.st) Tag("NO GUARANTEE", MaterialTheme.colorScheme.outline)
        }
        Text(
            if (ubers.isEmpty()) "No ubers on this banner."
            else if (missing == 0) "You own all ${ubers.size} ubers and legends here."
            else "$missing of ${ubers.size} ubers and legends here are new for you.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (wanted.isNotEmpty()) {
            Text("Wishlist cats on this banner:", fontWeight = FontWeight.Bold)
            CatStrip(wanted.take(8), game)
        }
    }
}
