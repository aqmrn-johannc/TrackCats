package com.trackcats.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackcats.app.core.Game
import com.trackcats.app.core.Plan
import com.trackcats.app.core.PlanStep
import com.trackcats.app.core.Planner
import com.trackcats.app.core.StepKind
import com.trackcats.app.core.TargetRoute
import com.trackcats.app.core.TicketTiming
import com.trackcats.app.core.Valuer
import java.time.LocalDate

@Composable
fun PlanScreen(vm: AppVm) {
    val user by vm.user.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    val today = vm.today
    val seed = user.seedInt
    val valuer = remember(user.owned, user.wishlist) { vm.valuer(user) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenTitle("Plan", subtitle = "Tell TrackCats what you have and it works out what to do with it.")

        SectionCard(title = "What you have") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Rare Tickets", user.tickets, Modifier.weight(1f)) { n -> vm.update { it.copy(tickets = n) } }
                NumberField("Cat Food", user.catFood, Modifier.weight(1f)) { n -> vm.update { it.copy(catFood = n) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Platinum Tickets", user.platTickets, Modifier.weight(1f)) { n -> vm.update { it.copy(platTickets = n) } }
                NumberField("Legend Tickets", user.legendTickets, Modifier.weight(1f)) { n -> vm.update { it.copy(legendTickets = n) } }
            }
        }

        if (seed == null) {
            SectionCard(highlight = true) { Text("Find your seed first (Rolls tab). Plans are built from your own upcoming rolls.") }
            return@Column
        }

        val pools = remember(game, today) { game.ticketBanners(today).map { game.pool(it) } }
        val upcoming = remember(game, today) { game.upcomingBanners(today).map { game.pool(it) } }

        // Rare Ticket plan
        val ticketPlan = remember(pools, seed, user.lastCat, user.tickets, valuer) { Planner.ticketPlan(pools, seed, user.lastCat, user.tickets, valuer) }
        SectionCard(title = "Rare Tickets", highlight = ticketPlan != null) {
            when {
                user.tickets <= 0 -> Text("Enter how many Rare Tickets you have to get a plan.")
                pools.isEmpty() -> Text("No banner is open today.")
                ticketPlan == null -> {
                    Text("Hold your tickets.", fontWeight = FontWeight.Bold)
                    Text("None of your next ${user.tickets.coerceAtMost(60)} rolls on today's banners is a new uber, a legend or a wishlist cat.")
                    val nearest = remember(pools, seed, user.lastCat, valuer) {
                        pools.mapNotNull { p -> Planner.lookahead(p, seed, user.lastCat, 150, valuer).firstOrNull { valuer.isGood(it.roll) }?.let { p to it } }
                            .minByOrNull { it.second.index }
                    }
                    if (nearest != null) Text("The nearest one is ${game.name(nearest.second.roll.catId)}, ${plural(nearest.second.index, "roll")} away on ${bannerTitle(nearest.first.event)}.")
                }
                else -> PlanView(ticketPlan, game, valuer, leftover = user.tickets - ticketPlan.tickets)
            }
        }

        // Cat Food plan
        val foodPlan = remember(pools, seed, user.lastCat, user.tickets, user.catFood, valuer) { Planner.catFoodPlan(pools, seed, user.lastCat, user.tickets, user.catFood, valuer) }
        SectionCard(title = "Cat Food (guaranteed 11-draw)", highlight = foodPlan != null) {
            when {
                pools.none { it.event.g } -> Text("No banner with a guaranteed 11-draw is open today. Guaranteed draws are the only good use of Cat Food on the gacha, so keep saving.")
                user.catFood < Planner.CF_ELEVEN -> Text("A guaranteed 11-draw costs ${Planner.CF_ELEVEN} Cat Food. Enter your Cat Food above once you have that much.")
                foodPlan == null -> Text("A guaranteed draw right now, or after any of your ticket rolls, would give you an uber you already own. Keep your Cat Food for a later banner.")
                else -> PlanView(foodPlan, game, valuer, leftover = -1)
            }
        }

        // Special tickets
        for ((kind, have, label) in listOf(Triple("platinum", user.platTickets, "Platinum Ticket"), Triple("legend", user.legendTickets, "Legend Ticket"))) {
            val event = game.specialTicketEvent(kind, today) ?: continue
            if (have <= 0) continue
            val timing = remember(event.k, pools, seed, user.lastCat, user.tickets, valuer) {
                Planner.ticketTiming(game.pool(event), pools, seed, user.lastCat, user.tickets, valuer)
            }
            SectionCard(title = label) { TimingView(timing, label, game, valuer) }
        }

        // Upcoming
        val reach = user.tickets.coerceIn(5, 30)
        val later = remember(upcoming, seed, user.lastCat, reach, valuer) {
            upcoming.map { Planner.score(it, seed, user.lastCat, reach, valuer) }.filter { it.good.isNotEmpty() }.maxByOrNull { it.value }
        }
        if (later != null) SectionCard(title = "Coming up") {
            BannerArt(later.event, game)
            Text("${bannerTitle(later.event)}, ${bannerWhen(later.event, today).lowercase()}", fontWeight = FontWeight.Bold)
            Text("If you keep your seed where it is, your first $reach rolls there include: ${scoreSummary(later, game)}")
            Text("Any roll you make before then changes this.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        WishlistCard(game, pools, upcoming, seed, user.lastCat, user.wishlist, user.tickets, user.catFood, today)

        TargetCard(game, pools.map { it }, upcoming, seed, user.lastCat, user.tickets, user.catFood, today, valuer)

        // Far ahead
        val banner = selectedBanner(game, user, today)
        if (banner != null) {
            val notable = remember(banner.k, seed, user.lastCat, valuer) { Planner.lookahead(game.pool(banner), seed, user.lastCat, 100, valuer) }
            SectionCard(title = "Far ahead: next 100 rolls") {
                Text("On ${bannerTitle(banner)} (the banner chosen on the Rolls tab).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (notable.isEmpty()) Text("No legend rare, wishlist cat or new uber in the next 100 rolls here.")
                val details = LocalCatDetails.current
                for (n in notable.take(25)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${n.index}", Modifier.width(34.dp).clickable { details(n.roll.catId) }, style = MaterialTheme.typography.titleMedium, color = rarityColor(n.roll.rarity), textAlign = TextAlign.Center)
                        CatIcon(n.roll.catId, game, width = 48.dp)
                        Text(game.name(n.roll.catId), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        RarityTag(n.roll.rarity)
                    }
                }
                if (notable.size > 25) Text("…and ${notable.size - 25} more.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by rememberSaveable(label) { mutableStateOf(if (value == 0) "" else value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(6)
            text = digits
            onChange(digits.toIntOrNull() ?: 0)
        },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

fun stepText(step: PlanStep): String = when (step.kind) {
    StepKind.SINGLES -> "Roll ${plural(step.rolls.size, "time")} on ${bannerTitle(step.event)}, one ticket at a time"
    StepKind.GUARANTEED -> "Do one guaranteed 11-draw on ${bannerTitle(step.event)}"
    StepKind.PLATINUM -> "Use a Platinum Ticket"
    StepKind.LEGEND -> "Use a Legend Ticket"
}

@Composable
private fun PlanView(plan: Plan, game: Game, valuer: Valuer, leftover: Int) {
    Text("You get:", fontWeight = FontWeight.Bold)
    CatStrip(plan.good.map { it.catId }, game)
    plan.steps.forEachIndexed { i, step ->
        var open by rememberSaveable(i, step.event.k, step.rolls.size) { mutableStateOf(false) }
        Column {
            Text("${i + 1}. ${stepText(step)}.")
            val good = step.rolls.filter { valuer.isGood(it) }
            Text(
                if (good.isEmpty()) "Nothing special here; these rolls only move you forward."
                else "Includes ${good.joinToString(", ") { game.name(it.catId) }}.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { open = !open }) { Text(if (open) "Hide cats" else "Show every cat in this step") }
            Expandable(open) { step.rolls.forEachIndexed { j, roll -> RollRow(j + 1, roll, game, valuer, false) } }
        }
    }
    val cost = listOfNotNull(
        if (plan.tickets > 0) plural(plan.tickets, "Rare Ticket") else null,
        if (plan.catFood > 0) "${plan.catFood} Cat Food" else null,
    ).joinToString(" and ")
    Text("Cost: $cost." + if (leftover > 0) " Then stop and keep your other ${plural(leftover, "ticket")}; the rolls after that are not worth it." else "")
    Text("Follow the steps in order and tap \"I rolled\" on the Rolls tab after each one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TimingView(timing: TicketTiming, label: String, game: Game, valuer: Valuer) {
    val nowName = game.name(timing.nowRoll.catId)
    if (timing.rollsFirst == 0) {
        CatStrip(listOf(timing.nowRoll.catId), game)
        Text("Use it now: you get $nowName" + if (valuer.isOwned(timing.nowRoll.catId)) " (a duplicate; nothing better is within reach of your tickets)." else " (new).", fontWeight = FontWeight.Bold)
    } else {
        CatStrip(listOf(timing.nowRoll.catId, timing.roll.catId), game, captions = listOf("Now", "If you wait"))
        Text("Wait: using it now gives $nowName" + if (valuer.isOwned(timing.nowRoll.catId)) ", which you own." else ".")
        Text("After ${plural(timing.rollsFirst, "more Rare Ticket roll")} (see the Rare Tickets plan), the $label gives ${game.name(timing.roll.catId)}.", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TargetCard(
    game: Game, active: List<com.trackcats.app.core.Pool>, upcoming: List<com.trackcats.app.core.Pool>,
    seed: Int, last: Int, tickets: Int, catFood: Int, today: LocalDate, valuer: Valuer,
) {
    var picking by remember { mutableStateOf(false) }
    var target by rememberSaveable { mutableStateOf(-1) }
    SectionCard(title = "I want a specific cat") {
        if (target <= 0) {
            Text("Pick a cat and TrackCats finds the cheapest way to reach it.")
        } else {
            val routes = remember(target, active, upcoming, seed, last) { Planner.findTarget(active, upcoming, seed, last, target) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CatIcon(target, game, width = 72.dp)
                Text(game.name(target), style = MaterialTheme.typography.titleMedium)
            }
            if (routes.isEmpty()) {
                val onAny = (active + upcoming).any { it.contains(target) }
                Text(
                    if (onAny) "Not within your next 300 rolls on any open or upcoming banner. Not worth chasing with your current seed."
                    else "This cat is not on any open or upcoming banner right now."
                )
            }
            for (r in routes.take(4)) RouteLine(r, tickets, catFood, today)
        }
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text(if (target <= 0) "Pick a cat" else "Pick another cat") }
    }
    if (picking) {
        CatPickerDialog("Which cat do you want?", game, game.gachaCats, onPick = { target = it; picking = false }, onDismiss = { picking = false })
    }
}

@Composable
fun RouteLine(route: TargetRoute, tickets: Int, catFood: Int, today: LocalDate) {
    val how = when {
        route.viaGuaranteed && route.tickets == 0 -> "one guaranteed 11-draw (${route.catFood} Cat Food)"
        route.viaGuaranteed -> "${plural(route.tickets, "ticket roll")}, then one guaranteed 11-draw (${route.catFood} Cat Food)"
        else -> "roll ${route.tickets} (${plural(route.tickets, "Rare Ticket")})"
    }
    val affordable = route.tickets <= tickets && route.catFood <= catFood
    Column {
        Text("• ${bannerTitle(route.event)}: $how")
        Text(
            bannerWhen(route.event, today) + " · " + if (affordable) "You can afford this." else "More than you have right now.",
            style = MaterialTheme.typography.bodySmall,
            color = if (affordable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** For every wishlist cat: the cheapest way to reach it from where the seed is now. */
@Composable
private fun WishlistCard(
    game: Game, active: List<com.trackcats.app.core.Pool>, upcoming: List<com.trackcats.app.core.Pool>,
    seed: Int, last: Int, wishlist: Set<Int>, tickets: Int, catFood: Int, today: LocalDate,
) {
    val details = LocalCatDetails.current
    val rows = remember(wishlist, active, upcoming, seed, last) {
        wishlist.filter { it in game.data.cats }
            .map { id -> id to Planner.findTarget(active, upcoming, seed, last, id).firstOrNull() }
            .sortedWith(compareBy({ it.second?.cost ?: Int.MAX_VALUE }, { game.name(it.first) }))
    }
    SectionCard(title = "Wishlist: when will I get it?") {
        if (rows.isEmpty()) {
            Text("Your wishlist is empty. Tap the heart next to a cat on the My cats tab, and its cheapest route shows up here.")
            return@SectionCard
        }
        Text("The cheapest way to each wishlist cat from where your seed is now. Any roll you make changes these.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((id, route) in rows) {
            Row(Modifier.fillMaxWidth().clickable { details(id) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CatIcon(id, game, width = 60.dp)
                Column(Modifier.weight(1f)) {
                    Text(game.name(id), fontWeight = FontWeight.Bold)
                    if (route != null) RouteLine(route, tickets, catFood, today)
                    else Text(
                        if ((active + upcoming).any { it.contains(id) }) "Not within your next 300 rolls on any open or upcoming banner."
                        else "Not on any open or upcoming banner right now.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
