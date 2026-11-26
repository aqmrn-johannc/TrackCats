package com.trackcats.app.ui

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
    val seed = user.seedInt
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
            Text("Compare banners", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "The same seed gives different cats on each banner. This shows what your next rolls would be on every one.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (seed == null) {
            item { SectionCard(highlight = true) { Text("Find your seed first (Rolls tab) to see forecasts here.") } }
            items(active, key = { "plain-${it.k}" }) { e -> PlainBannerCard(e, today) }
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
                        Text(scoreSummary(best, game))
                    }
                }
                val later = upcomingScores.firstOrNull()
                if (later != null && later.good.isNotEmpty() && (best == null || later.value > best.value)) {
                    Text("Worth waiting for: ${bannerTitle(later.event)}, ${bannerWhen(later.event, today).lowercase()}. ${scoreSummary(later, game)}")
                }
            }
        }
        if (activeScores.isNotEmpty()) item { Text("Open now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(activeScores, key = { "a-${it.event.k}" }) { s -> ScoredBannerCard(s, game, today, onOpenBanner) }
        if (upcomingScores.isNotEmpty()) item { Text("Coming up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(upcomingScores, key = { "u-${it.event.k}" }) { s -> ScoredBannerCard(s, game, today, onOpenBanner) }
    }
}

@Composable
private fun ScoredBannerCard(score: BannerScore, game: Game, today: LocalDate, onOpen: (EventInfo) -> Unit) {
    SectionCard(highlight = false) {
        Text(bannerTitle(score.event), fontWeight = FontWeight.Bold)
        Text(
            listOfNotNull(bannerWhen(score.event, today), bannerPerk(score.event)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(scoreSummary(score, game), color = if (score.good.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = { onOpen(score.event) }) { Text("See the rolls") }
    }
}

@Composable
private fun PlainBannerCard(event: EventInfo, today: LocalDate) {
    SectionCard {
        Text(bannerTitle(event), fontWeight = FontWeight.Bold)
        Text(
            listOfNotNull(bannerWhen(event, today), bannerPerk(event)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
