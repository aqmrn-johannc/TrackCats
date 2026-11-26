package com.trackcats.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackcats.app.core.EventInfo
import com.trackcats.app.core.Game
import com.trackcats.app.core.Pool
import com.trackcats.app.core.Rarity
import com.trackcats.app.core.Roll
import com.trackcats.app.core.Roller
import com.trackcats.app.core.TrackCell
import com.trackcats.app.core.TrackTable
import com.trackcats.app.core.Valuer
import com.trackcats.app.data.UserState
import java.time.LocalDate

/** Every banner the player can look at, grouped for the picker. */
fun bannerGroups(game: Game, today: LocalDate): List<Pair<String, List<EventInfo>>> = listOf(
    "Open now" to game.ticketBanners(today),
    "Special tickets" to listOfNotNull(game.specialTicketEvent("platinum", today), game.specialTicketEvent("legend", today)),
    "Coming up" to game.upcomingBanners(today),
)

fun selectedBanner(game: Game, user: UserState, today: LocalDate): EventInfo? {
    val all = bannerGroups(game, today).flatMap { it.second }
    return all.firstOrNull { it.k == user.selectedEvent } ?: all.firstOrNull()
}

@Composable
fun HomeScreen(vm: AppVm, onFindSeed: () -> Unit, onMismatch: (EventInfo) -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    val today = vm.today
    val seed = user.seedInt
    if (seed == null) {
        NoSeedYet(onFindSeed)
        return
    }
    val event = selectedBanner(game, user, today)
    if (event == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            SectionCard(title = "No banners available") {
                Text("The saved banner data has no banner for today. Open More, then Game data, and tap Update now.")
            }
        }
        return
    }
    val pool = game.pool(event)
    val valuer = remember(user.owned, user.wishlist) { vm.valuer(user) }
    var count by rememberSaveable { mutableIntStateOf(20) }
    var picking by remember { mutableStateOf(false) }
    var rolling by remember { mutableStateOf(false) }
    val rolls = remember(pool, seed, user.lastCat, count) { Roller.simulate(pool, seed, user.lastCat, count) }
    val draw = remember(pool, seed, user.lastCat) { Roller.guaranteedDraw(pool, seed, user.lastCat) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { BannerHeader(event, today) { picking = true } }
        if (!user.seedConfirmed) item {
            SectionCard(highlight = true) {
                Text("Seed not confirmed yet", fontWeight = FontWeight.Bold)
                Text("Roll once on this banner. If you get roll 1 below, tap \"I rolled\" and confirm; from then on the list is trustworthy.")
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your next rolls", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = { rolling = true }) { Text("I rolled") }
            }
        }
        itemsIndexed(rolls, key = { i, _ -> "roll-$i" }) { i, roll ->
            RollRow(i + 1, roll, game, valuer, user.advanced)
        }
        item {
            if (count < 200) OutlinedButton(onClick = { count = (count + 30).coerceAtMost(200) }, modifier = Modifier.fillMaxWidth()) {
                Text("Show more rolls")
            }
        }
        if (draw != null) item { GuaranteedPreview(pool, draw, game, valuer, user) }
        if (user.advanced) item { TrackTableCard(pool, seed, user.lastCat, game, minOf(count, 60)) }
    }

    if (picking) {
        BannerPickerDialog("Choose a banner", bannerGroups(game, today), today, onPick = { e ->
            vm.update { it.copy(selectedEvent = e.k) }
            picking = false
        }, onDismiss = { picking = false })
    }
    if (rolling) {
        RolledDialog(event, pool, seed, user.lastCat, game, valuer, user.advanced,
            onConfirm = { applied, guaranteed ->
                vm.applyRolls(event, applied, guaranteed)
                rolling = false
            },
            onMismatch = {
                rolling = false
                onMismatch(event)
            },
            onDismiss = { rolling = false })
    }
}

@Composable
private fun NoSeedYet(onFindSeed: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(title = "Let's find your seed", highlight = true) {
            Text("Your account has a hidden number, the seed, that decides every rare gacha roll in advance. Once TrackCats knows it, it can show you exactly which cats you will get next.")
            Text("You need your last 10 rolls from one banner, in order. If you don't remember them, roll 10 times now and note each cat.")
            Button(onClick = onFindSeed, modifier = Modifier.fillMaxWidth()) { Text("Find my seed") }
        }
        SectionCard(title = "Before you start") {
            Text("• Use Rare Ticket or Cat Food rolls from the Rare Capsule, all on the same banner.")
            Text("• The rolls must be your most recent ones, with no other rare gacha rolls in between or after.")
            Text("• From a guaranteed 11-draw, leave out the last cat (the guaranteed uber).")
        }
    }
}

@Composable
private fun GuaranteedPreview(pool: Pool, draw: List<Roll>, game: Game, valuer: Valuer, user: UserState) {
    var open by rememberSaveable(pool.event.k) { mutableStateOf(false) }
    val uber = draw.last()
    val after = remember(draw) { Roller.rollOne(pool, uber.seedAfter, uber.catId) }
    SectionCard(title = if (pool.guaranteedRolls == 15) "If you finish the step-up now" else "If you do the guaranteed 11-draw now") {
        Text("Guaranteed uber: ${game.name(uber.catId)}" + if (valuer.isOwned(uber.catId)) " (you own it)" else " (new)", fontWeight = FontWeight.Bold)
        val good = draw.dropLast(1).filter { valuer.isGood(it) }
        Text(
            if (good.isEmpty()) "The ${draw.size - 1} rolls before it are the same as rolls 1 to ${draw.size - 1} above, with nothing special among them."
            else "The ${draw.size - 1} rolls before it are rolls 1 to ${draw.size - 1} above, including ${good.joinToString(", ") { game.name(it.catId) }}."
        )
        Text("Afterwards your next single roll here would be ${game.name(after.catId)}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide the full draw" else "Show the full draw") }
        if (open) draw.forEachIndexed { i, roll -> RollRow(i + 1, roll, game, valuer, user.advanced) }
    }
}

@Composable
private fun RolledDialog(
    event: EventInfo, pool: Pool, seed: Int, last: Int, game: Game, valuer: Valuer, showTrack: Boolean,
    onConfirm: (List<Roll>, Boolean) -> Unit, onMismatch: () -> Unit, onDismiss: () -> Unit,
) {
    val canGuarantee = pool.guaranteedRolls > 0 && pool.slots[Rarity.UBER].isNotEmpty()
    var guaranteed by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(1) }
    val rolls = remember(guaranteed, count, seed, last) {
        if (guaranteed) Roller.guaranteedDraw(pool, seed, last).orEmpty() else Roller.simulate(pool, seed, last, count)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What did you roll?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (n in listOf(1, 2, 3, 5, 10, 11)) {
                        FilterChip(selected = !guaranteed && count == n, onClick = { guaranteed = false; count = n }, label = { Text(if (n == 1) "1 roll" else "$n rolls") })
                    }
                    if (canGuarantee) FilterChip(
                        selected = guaranteed, onClick = { guaranteed = true },
                        label = { Text(if (pool.guaranteedRolls == 15) "Full step-up" else "Guaranteed 11-draw") },
                    )
                }
                Text("You should have received, in this order:", style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    rolls.forEachIndexed { i, roll -> RollRow(i + 1, roll, game, valuer, showTrack) }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(rolls, guaranteed) }) { Text("Yes, that's what I got") } },
        dismissButton = {
            Row {
                TextButton(onClick = onMismatch) { Text("I got something else") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun TrackTableCard(pool: Pool, seed: Int, last: Int, game: Game, rows: Int) {
    val table = remember(pool, seed, last, rows) { TrackTable.build(pool, seed, last, rows) }
    SectionCard(title = "Track table (advanced)") {
        Text(
            "The classic two-track view used by other seed trackers. Highlighted cells are the path your rolls follow. \"or\" shows what you get if the same rare cat came just before it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (row in table) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${row.index}", Modifier.width(26.dp), style = MaterialTheme.typography.labelMedium)
                TrackCellView(row.a, game, Modifier.weight(1f))
                TrackCellView(row.b, game, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun TrackCellView(cell: TrackCell, game: Game, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (cell.onPath) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            "${cell.label}  ${game.name(cell.catId)}",
            style = MaterialTheme.typography.bodySmall, color = rarityColor(cell.rarity),
            fontWeight = if (cell.rarity >= Rarity.UBER) FontWeight.Bold else FontWeight.Normal,
        )
        if (cell.altCatId > 0) Text("or ${game.name(cell.altCatId)} → ${cell.altLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
