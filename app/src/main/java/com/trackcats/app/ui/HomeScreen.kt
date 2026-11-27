package com.trackcats.app.ui

import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetScaffold
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
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

@OptIn(ExperimentalMaterial3Api::class)
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

    val sheet = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    val sheetOpen = sheet.bottomSheetState.targetValue == SheetValue.Expanded
    BackHandler(enabled = sheetOpen) { scope.launch { sheet.bottomSheetState.partialExpand() } }

    // The track table lives in a tab at the bottom edge: swipe it up (or tap it) to open.
    BottomSheetScaffold(
        scaffoldState = sheet,
        sheetPeekHeight = 54.dp,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        sheetShadowElevation = 12.dp,
        sheetDragHandle = {
            TrackTableHandle(sheetOpen) {
                scope.launch { if (sheetOpen) sheet.bottomSheetState.partialExpand() else sheet.bottomSheetState.expand() }
            }
        },
        sheetContent = { TrackTableSheet(pool, seed, user.lastCat, game) },
        containerColor = Color.Transparent,
    ) { inner ->
    LazyColumn(
        Modifier.fillMaxSize().padding(inner),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { BannerHeader(event, game, today) { picking = true } }
        item {
            if (draw != null) GuaranteedPreview(pool, draw, game, valuer, user)
            else SectionCard(title = "Guaranteed uber") { Text("This banner doesn't have a guaranteed uber.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (!user.seedConfirmed) item {
            SectionCard(highlight = true) {
                Text("Seed not confirmed yet", fontWeight = FontWeight.Bold)
                Text("Roll once on this banner. If you get roll 1 below, tap \"I rolled\" and confirm; from then on the list is trustworthy.")
            }
        }
        item {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Your next rolls", Modifier.weight(1f))
                Button(onClick = { rolling = true }) { Text("I rolled") }
            }
        }
        itemsIndexed(rolls, key = { i, _ -> "roll-$i" }) { i, roll ->
            RollCard(Modifier.animateItem()) { RollRow(i + 1, roll, game, valuer, user.advanced) }
        }
        item {
            if (count < 200) OutlinedButton(onClick = { count = (count + 30).coerceAtMost(200) }, modifier = Modifier.fillMaxWidth()) {
                Text("Show more rolls")
            }
        }
    }
    }

    if (picking) {
        BannerPickerDialog("Choose a banner", game, bannerGroups(game, today), today, onPick = { e ->
            vm.update { it.copy(selectedEvent = e.k) }
            picking = false
        }, onDismiss = { picking = false })
    }
    if (rolling) {
        RolledDialog(event, pool, seed, user.lastCat, game, valuer, user.advanced,
            onConfirm = { applied, guaranteed, markOwned ->
                vm.applyRolls(event, applied, guaranteed, markOwned)
                rolling = false
            },
            onMismatch = {
                rolling = false
                onMismatch(event)
            },
            onDismiss = { rolling = false })
    }
}

/** One predicted roll in its own slim card. */
@Composable
fun RollCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 2.dp,
    ) { Box(Modifier.padding(4.dp)) { content() } }
}

@Composable
private fun NoSeedYet(onFindSeed: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Welcome!")
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CatIcon(uber.catId, game, width = 84.dp)
            Column {
                Text("Guaranteed uber", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(game.name(uber.catId), style = MaterialTheme.typography.titleMedium, color = rarityColor(Rarity.UBER))
                Text(if (valuer.isOwned(uber.catId)) "You own it" else "New for you", fontWeight = FontWeight.Bold)
            }
        }
        val good = draw.dropLast(1).filter { valuer.isGood(it) }
        Text(
            if (good.isEmpty()) "The ${draw.size - 1} rolls before it are the same as rolls 1 to ${draw.size - 1} above, with nothing special among them."
            else "The ${draw.size - 1} rolls before it are rolls 1 to ${draw.size - 1} above, including ${good.joinToString(", ") { game.name(it.catId) }}."
        )
        Text("Afterwards your next single roll here would be ${game.name(after.catId)}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide the full draw" else "Show the full draw") }
        Expandable(open) { draw.forEachIndexed { i, roll -> RollRow(i + 1, roll, game, valuer, user.advanced) } }
    }
}

@Composable
private fun RolledDialog(
    event: EventInfo, pool: Pool, seed: Int, last: Int, game: Game, valuer: Valuer, showTrack: Boolean,
    onConfirm: (List<Roll>, Boolean, Boolean) -> Unit, onMismatch: () -> Unit, onDismiss: () -> Unit,
) {
    val canGuarantee = pool.guaranteedRolls > 0 && pool.slots[Rarity.UBER].isNotEmpty()
    var guaranteed by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(1) }
    var markOwned by remember { mutableStateOf(true) }
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
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { markOwned = !markOwned }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = markOwned, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Mark these cats as owned")
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(rolls, guaranteed, markOwned) }) { Text("Yes, that's what I got") } },
        dismissButton = {
            Row {
                TextButton(onClick = onMismatch) { Text("I got something else") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** The always-visible edge of the pull-up tab. */
@Composable
private fun TrackTableHandle(open: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Close track table" else "Open track table", onClick = onToggle).padding(top = 8.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 44.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outline))
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (open) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Track table", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(if (open) "  swipe down to close" else "  swipe up", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The full two-track table, shown when the tab is pulled up. */
@Composable
private fun TrackTableSheet(pool: Pool, seed: Int, last: Int, game: Game) {
    val table = remember(pool, seed, last) { TrackTable.build(pool, seed, last, 100) }
    LazyColumn(
        Modifier.fillMaxWidth().fillMaxHeight(0.88f),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                "The classic two-track view used by other seed trackers, for your next 100 positions. Highlighted cells are the path your rolls follow. \"or\" shows what you get if the same rare cat came just before it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.width(26.dp))
                Text("Track A", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Track B", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            }
        }
        items(table, key = { it.index }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${row.index}", Modifier.width(26.dp), style = MaterialTheme.typography.labelMedium)
                TrackCellView(row.a, game, Modifier.weight(1f))
                TrackCellView(row.b, game, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrackCellView(cell: TrackCell, game: Game, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (cell.onPath) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
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
