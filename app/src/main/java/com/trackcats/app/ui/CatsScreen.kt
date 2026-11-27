package com.trackcats.app.ui

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
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var rarity by rememberSaveable { mutableIntStateOf(-1) }
    val all = game.gachaCats
    val shown = remember(all, query, filter, rarity, user.owned, user.wishlist) {
        all.filter { id ->
            matchesQuery(game, id, query) &&
                (rarity < 0 || game.rarityIdx(id) == rarity) &&
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
            ScreenTitle("My cats", subtitle = "Tick the cats you own so TrackCats knows which rolls are new. Tap the heart to put a cat on your wishlist. You own ${user.owned.count { it in game.data.cats }} of ${all.size}.")
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
        if (shown.isEmpty()) item { Text("No cats match.", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(shown, key = { it }) { id ->
            val owned = id in user.owned
            val wanted = id in user.wishlist
            RollCard(Modifier.animateItem()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = owned, onCheckedChange = { on -> vm.update { u -> u.copy(owned = if (on) u.owned + id else u.owned - id) } })
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
}
