package com.trackcats.app.ui

import com.trackcats.app.core.Game
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackcats.app.core.Rarity
import com.trackcats.app.core.Valuer

@Composable
fun CatsScreen(vm: AppVm) {
    val user by vm.user.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val details = LocalCatDetails.current
    var view by rememberSaveable { mutableStateOf("cats") }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var rarity by rememberSaveable { mutableIntStateOf(-1) }
    var setName by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingSet by remember { mutableStateOf(false) }
    /** Pending bulk change waiting for confirmation: true marks owned, false unmarks. */
    var bulk by remember { mutableStateOf<Boolean?>(null) }
    val all = game.gachaCats
    val sets = remember(game) { Sets.all(context, game) }
    val chosenSet = sets.firstOrNull { it.name == setName }
    val shown = remember(all, query, filter, rarity, chosenSet, user.owned, user.wishlist) {
        all.filter { id ->
            matchesQuery(game, id, query) &&
                (rarity < 0 || game.rarityIdx(id) == rarity) &&
                (chosenSet == null || id in chosenSet.cats) &&
                when (filter) {
                    "Owned" -> id in user.owned
                    "Missing" -> id !in user.owned
                    "Wishlist" -> id in user.wishlist
                    else -> true
                }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            ScreenTitle("My cats", subtitle = "Tick the cats you own so TrackCats knows which rolls are new. Tap the heart to put a cat on your wishlist, or tap a cat for details. You own ${user.owned.count { it in game.data.cats }} of ${all.size}.")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = view == "cats", onClick = { view = "cats" }, label = { Text("Cats") })
                FilterChip(selected = view == "sets", onClick = { view = "sets" }, label = { Text("Sets") })
            }
        }
        if (view == "sets") {
            items(sets, key = { "set-${it.name}" }) { set ->
                SetCard(set, game, user.owned, Modifier.animateItem()) { setName = set.name; rarity = -1; filter = "All"; query = ""; view = "cats" }
            }
            return@LazyColumn
        }
        item { SearchField(query, { query = it }, "Search cats", Modifier.padding(top = 8.dp)) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (f in listOf("All", "Missing", "Owned", "Wishlist")) FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = rarity < 0, onClick = { rarity = -1 }, label = { Text("Any rarity") })
                for (r in 3 downTo 0) FilterChip(selected = rarity == r, onClick = { rarity = r }, label = { Text(Rarity.short[r]) })
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = chosenSet != null, onClick = { pickingSet = true }, label = { Text(chosenSet?.let { "Set: ${it.name}" } ?: "Any set") })
                if (chosenSet != null) FilterChip(selected = false, onClick = { setName = null }, label = { Text("Clear set") })
            }
        }
        // Bulk marking acts on exactly the cats listed below.
        if (shown.isNotEmpty()) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { bulk = true }, enabled = shown.any { it !in user.owned }) { Text("Mark all ${shown.size} owned") }
                OutlinedButton(onClick = { bulk = false }, enabled = shown.any { it in user.owned }) { Text("Unmark all") }
            }
        }
        if (shown.isEmpty()) item { Text("No cats match.", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(shown, key = { it }) { id ->
            val owned = id in user.owned
            val wanted = id in user.wishlist
            RollCard(Modifier.animateItem()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = owned, onCheckedChange = { on -> vm.update { u -> u.copy(owned = if (on) u.owned + id else u.owned - id) } })
                Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Cat details") { details(id) }, verticalAlignment = Alignment.CenterVertically) {
                CatIcon(id, game)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(game.name(id), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val r = game.rarityIdx(id)
                        RarityTag(r)
                        val np = Valuer.DUPE_NP[r]
                        if (owned && np > 0) Text("A duplicate is worth $np NP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                }
                IconButton(onClick = { vm.update { u -> u.copy(wishlist = if (wanted) u.wishlist - id else u.wishlist + id) } }) {
                    Icon(
                        if (wanted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (wanted) "Remove ${game.name(id)} from wishlist" else "Add ${game.name(id)} to wishlist",
                        tint = if (wanted) rarityColor(Rarity.UBER) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
        }
    }

    if (pickingSet) AlertDialog(
        onDismissRequest = { pickingSet = false },
        title = { Text("Show cats from which set?") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(sets, key = { it.name }) { set ->
                    val have = set.ubers.count { it in user.owned }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { setName = set.name; pickingSet = false }.padding(vertical = 10.dp, horizontal = 4.dp)) {
                        Text(set.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("$have/${set.ubers.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { pickingSet = false }) { Text("Cancel") } },
    )
    bulk?.let { mark ->
        val targets = if (mark) shown.filter { it !in user.owned } else shown.filter { it in user.owned }
        AlertDialog(
            onDismissRequest = { bulk = null },
            title = { Text(if (mark) "Mark ${plural(targets.size, "cat")} as owned?" else "Unmark ${plural(targets.size, "cat")}?") },
            text = { Text("This changes every cat in the current list" + (chosenSet?.let { " (${it.name})" } ?: "") + ". Use the search and filters first to narrow the list.") },
            confirmButton = {
                Button(onClick = {
                    vm.update { u -> u.copy(owned = if (mark) u.owned + targets else u.owned - targets.toSet()) }
                    bulk = null
                }) { Text(if (mark) "Mark as owned" else "Unmark") }
            },
            dismissButton = { TextButton(onClick = { bulk = null }) { Text("Cancel") } },
        )
    }
}

/** Collection progress for one banner set. */
@Composable
private fun SetCard(set: CatSet, game: Game, owned: Set<Int>, modifier: Modifier, onOpen: () -> Unit) {
    val have = set.ubers.count { it in owned }
    val missing = set.ubers.filter { it !in owned }
    SectionCard(modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpen)) {
        BannerArt(set.event, game)
        Text(set.name, style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { if (set.ubers.isEmpty()) 0f else have.toFloat() / set.ubers.size }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)))
        Text(
            "Ubers $have of ${set.ubers.size}" + if (set.legends.isNotEmpty()) " · Legend ${set.legends.count { it in owned }} of ${set.legends.size}" else "",
            fontWeight = FontWeight.SemiBold,
        )
        if (missing.isEmpty() && set.ubers.isNotEmpty()) Text("Complete!", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
        else if (missing.size <= 12) CatStrip(missing, game)
        else Text("${missing.size} ubers still missing. Tap to see them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
