package com.trackcats.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trackcats.app.core.EventInfo
import com.trackcats.app.core.Game
import com.trackcats.app.core.Pool
import com.trackcats.app.core.Rarity
import com.trackcats.app.core.Roll
import com.trackcats.app.core.Valuer
import com.trackcats.app.work.Alerts
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("MMM d")

fun prettyDate(iso: String): String = runCatching { LocalDate.parse(iso).format(dayFormat) }.getOrDefault(iso)

fun bannerTitle(event: EventInfo): String = when (event.p) {
    "platinum" -> "Platinum Ticket capsule"
    "legend" -> "Legend Ticket capsule"
    else -> Alerts.shortName(event.n)
}

fun bannerWhen(event: EventInfo, today: LocalDate): String = when {
    event.p != null -> "Always available"
    event.isUpcoming(today) -> "Starts ${prettyDate(event.s)}"
    event.isActive(today) -> if (event.end.year >= 2030) "No end date" else "Ends ${prettyDate(event.e)}"
    else -> "Ended ${prettyDate(event.e)}"
}

fun bannerPerk(event: EventInfo): String? = when {
    event.g -> "Guaranteed uber on 11-draw"
    event.st -> "Step-up: guaranteed uber on the 15th roll"
    else -> null
}

fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) "1 $one" else "$n $many"

@Composable
fun SectionCard(modifier: Modifier = Modifier, title: String? = null, highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun RarityTag(rarity: Int, modifier: Modifier = Modifier) {
    Text(
        Rarity.short[rarity],
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(rarityColor(rarity))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}

/** One predicted roll: number, cat, rarity and what it means for this player. */
@Composable
fun RollRow(index: Int, roll: Roll, game: Game, valuer: Valuer, showTrack: Boolean, modifier: Modifier = Modifier) {
    val good = valuer.isGood(roll)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (good) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(rarityColor(roll.rarity)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (roll.guaranteed) "★" else "$index", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                game.name(roll.catId),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (good || roll.rarity >= Rarity.UBER) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RarityTag(roll.rarity)
                when {
                    valuer.isWanted(roll.catId) -> Tag("WISHLIST", MaterialTheme.colorScheme.tertiary)
                    !valuer.isOwned(roll.catId) -> Tag("NEW", MaterialTheme.colorScheme.tertiary)
                    else -> Text(dupeText(roll.rarity), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (roll.guaranteed) Tag("GUARANTEED", rarityColor(Rarity.UBER))
                if (showTrack) Text(roll.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (roll.rerolled) {
                Text(
                    "Would have been the same rare cat twice in a row, so the game swaps it. Your path shifts here; the list already accounts for it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun dupeText(rarity: Int): String {
    val np = Valuer.DUPE_NP[rarity]
    return if (np > 0) "Duplicate · worth $np NP" else "Duplicate"
}

/** A compact, tappable summary of the chosen banner. */
@Composable
fun BannerHeader(event: EventInfo, today: LocalDate, onChange: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onChange)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Banner", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(bannerTitle(event), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(bannerWhen(event, today), bannerPerk(event)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Change", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BannerPickerDialog(title: String, groups: List<Pair<String, List<EventInfo>>>, today: LocalDate, onPick: (EventInfo) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                for ((label, events) in groups) {
                    if (events.isEmpty()) continue
                    item(key = "h-$label") {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                    items(events, key = { "$label-${it.k}" }) { e ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(e) }.padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            Text(bannerTitle(e), fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(bannerWhen(e, today), bannerPerk(e)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") } },
        shape = RoundedCornerShape(14.dp),
    )
}

fun matchesQuery(game: Game, id: Int, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return game.data.cats[id]?.n?.any { it.lowercase().contains(q) } == true
}

/** Pick one cat from a list, with search. */
@Composable
fun CatPickerDialog(title: String, game: Game, ids: List<Int>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, ids) { ids.filter { matchesQuery(game, it, query) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SearchField(query, { query = it }, "Search cats")
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it }) { id ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(id) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(game.name(id), Modifier.weight(1f))
                            RarityTag(game.rarityIdx(id))
                        }
                    }
                }
                if (shown.isEmpty()) Text("No cats match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Lets the player build an ordered list of cats they rolled by tapping names, grouped by rarity.
 * Used by the seed wizard and by "my rolls stopped matching".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RollSequenceEditor(game: Game, pool: Pool, ids: List<Int>, onChange: (List<Int>) -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionCard(title = if (ids.isEmpty()) "Your rolls (oldest first)" else "Your rolls, oldest first: ${ids.size}") {
            if (ids.isEmpty()) {
                Text("Tap cats below in the order you got them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ids.forEachIndexed { i, id ->
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(rarityColor(game.rarityIdx(id))).padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}. ${game.name(id)}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { onChange(ids.filterIndexed { j, _ -> j != i }) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Clear, contentDescription = "Remove roll ${i + 1}", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        SearchField(query, { query = it }, "Search this banner's cats")
        for (rarity in 0..3) {
            val cats = remember(pool, query, rarity) {
                pool.slots[rarity].distinct().filter { matchesQuery(game, it, query) }.sortedBy { game.name(it) }
            }
            if (cats.isEmpty()) continue
            Text(Rarity.names[rarity], style = MaterialTheme.typography.labelLarge, color = rarityColor(rarity), fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (id in cats) {
                    Text(
                        game.name(id),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, rarityColor(rarity), RoundedCornerShape(50))
                            .clickable { onChange(ids + id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
