package com.trackcats.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackcats.app.core.Planner
import com.trackcats.app.core.Rarity
import com.trackcats.app.core.Valuer

/** Everything the app knows about one cat, opened by tapping the cat anywhere in the app. */
@Composable
fun CatDetailDialog(vm: AppVm, id: Int, onDismiss: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val today = vm.today
    val info = game.data.cats[id]
    val rarity = game.rarityIdx(id)
    val owned = id in user.owned
    val wanted = id in user.wishlist
    val active = remember(game, today) { game.ticketBanners(today) }
    val upcoming = remember(game, today) { game.upcomingBanners(today) }
    val carriers = remember(id, active, upcoming) { (active + upcoming).filter { game.pool(it).contains(id) } }
    val seed = user.seedInt
    val routes = remember(id, seed, user.lastCat, active, upcoming) {
        if (seed == null) emptyList()
        else Planner.findTarget(active.map { game.pool(it) }, upcoming.map { game.pool(it) }, seed, user.lastCat, id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(game.name(id), Modifier.weight(1f, fill = false))
                RarityTag(rarity)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Forms, first to last.
                val forms = info?.n.orEmpty().take(3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    forms.forEachIndexed { i, name ->
                        Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CatIcon(id, game, width = 80.dp, form = i)
                            Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { vm.update { u -> u.copy(owned = if (owned) u.owned - id else u.owned + id) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = owned, onCheckedChange = null, modifier = Modifier.padding(end = 10.dp))
                    Text(if (owned) "You own this cat" else "I own this cat", fontWeight = FontWeight.SemiBold)
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { vm.update { u -> u.copy(wishlist = if (wanted) u.wishlist - id else u.wishlist + id) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (wanted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null,
                        tint = if (wanted) rarityColor(Rarity.UBER) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(if (wanted) "On your wishlist" else "Add to wishlist", fontWeight = FontWeight.SemiBold)
                }
                val np = Valuer.DUPE_NP[rarity]
                if (np > 0) Text("A duplicate is worth $np NP.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                HorizontalDivider()
                Text("Where to get it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (carriers.isEmpty()) Text("Not on any open or upcoming banner right now.")
                for (e in carriers.take(8)) {
                    Column {
                        Text(Sets.name(context, game, e), fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(bannerWhen(e, today), bannerPerk(e)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (carriers.size > 8) Text("…and ${carriers.size - 8} more banners.", color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (seed != null && carriers.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Your cheapest way to it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    if (routes.isEmpty()) Text("Not within your next 300 rolls on any of those banners.")
                    for (r in routes.take(2)) RouteLine(r, user.tickets, user.catFood, today)
                }

                OutlinedButton(
                    onClick = {
                        val url = "https://battlecats.miraheze.org/w/index.php?search=" + Uri.encode(game.name(id))
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Look it up on the wiki") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
