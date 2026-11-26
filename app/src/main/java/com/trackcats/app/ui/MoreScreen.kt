package com.trackcats.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackcats.app.data.HistoryEntry
import com.trackcats.app.data.Store
import com.trackcats.app.work.Alerts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MoreScreen(vm: AppVm, onFindSeed: () -> Unit, onResync: () -> Unit, onHistory: () -> Unit, onGlossary: () -> Unit, onTutorial: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var manual by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notice = if (granted) "Notifications are on." else "Notifications are blocked. Allow them in the phone's settings for TrackCats to get alerts."
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(Store.exportUser().toByteArray()) } }.isSuccess
            notice = if (ok) "Backup saved." else "Couldn't write the backup file."
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            notice = if (text != null && Store.importUser(context, text)) "Backup restored." else "That file is not a TrackCats backup."
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("More", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        (notice ?: vm.message)?.let { SectionCard(highlight = true) { Text(it) } }

        SectionCard(title = "My seed") {
            val seed = user.seed
            if (seed == null) Text("No seed saved yet.")
            else {
                SelectionContainer { Text("Seed: $seed", fontWeight = FontWeight.Bold) }
                Text(if (user.seedConfirmed) "Confirmed by a matching roll." else "Not confirmed yet. Roll once and confirm on the Rolls tab.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onFindSeed, modifier = Modifier.fillMaxWidth()) { Text(if (seed == null) "Find my seed" else "Find my seed again") }
            if (seed != null) OutlinedButton(onClick = onResync, modifier = Modifier.fillMaxWidth()) { Text("My rolls stopped matching") }
            TextButton(onClick = { manual = true }) { Text("Enter a seed by hand") }
        }

        SectionCard(title = "Roll history") {
            Text("${plural(user.history.size, "entry", "entries")}. You can undo the latest one if you tapped by mistake.")
            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Open history") }
        }

        SectionCard(title = "Alerts") {
            SwitchRow("Banner and wishlist alerts", user.alerts) { on ->
                vm.update { it.copy(alerts = on) }
                if (on && Build.VERSION.SDK_INT >= 33 && !Alerts.canNotify(context)) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Text("About twice a day TrackCats downloads the banner schedule and tells you when a new banner appears or a wishlist cat is close on your path.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (user.alerts && !Alerts.canNotify(context)) {
                Text("Notifications are currently blocked for TrackCats.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Allow notifications") }
            }
            Text("On Xiaomi, POCO and Redmi phones the system stops background checks unless you turn on Autostart for TrackCats and set its battery saver to \"No restrictions\".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("App settings") }
                OutlinedButton(enabled = !vm.refreshing, onClick = { notice = null; vm.refreshData(thenCheckAlerts = true) }) { Text("Check now") }
            }
        }

        SectionCard(title = "Game data") {
            Text("Banner and cat data from ${prettyDate(game.data.fetched)} · ${game.data.events.size} banners, ${game.data.cats.size} cats.")
            Text("Forecasts are only as good as this data. It comes from the community project battle-cats-rolls; update it when a new banner is announced.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = !vm.refreshing, onClick = { notice = null; vm.refreshData() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.refreshing) "Updating…" else "Update now")
            }
        }

        SectionCard(title = "Backup") {
            Text("Your seed, history and cat list live only on this phone. Save a backup file before resetting or changing phones.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportFile.launch("trackcats-backup.json") }) { Text("Save backup") }
                OutlinedButton(onClick = { importFile.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Restore backup") }
            }
        }

        SectionCard(title = "Display") {
            SwitchRow("Advanced view", user.advanced) { on -> vm.update { it.copy(advanced = on) } }
            Text("Shows track positions (1A, 2B…) on every roll and the full two-track table, for comparing with other seed trackers.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(title = "Help") {
            OutlinedButton(onClick = onTutorial, modifier = Modifier.fillMaxWidth()) { Text("Show the introduction again") }
            OutlinedButton(onClick = onGlossary, modifier = Modifier.fillMaxWidth()) { Text("What do these words mean?") }
        }

        SectionCard(title = "About") {
            Text("TrackCats 1.0.0")
            Text("An unofficial fan tool. Not affiliated with or endorsed by PONOS. Seed tracking reads nothing from your game and changes nothing in it; it only does maths on rolls you enter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("The roll algorithm and game data come from battle-cats-rolls by godfat (Apache License 2.0).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (manual) ManualSeedDialog(user.seed, onSave = { s -> vm.setSeed(s, -1, null, "manual"); manual = false }, onDismiss = { manual = false })
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val timeFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

@Composable
fun HistoryScreen(vm: AppVm, onDone: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Roll history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = user.history.isNotEmpty(), onClick = { vm.undoLast() }) { Text("Undo latest entry") }
            OutlinedButton(onClick = onDone) { Text("Back") }
        }
        if (user.history.isEmpty()) Text("Nothing yet. Entries appear when you save a seed or confirm rolls.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (entry in user.history.asReversed().take(100)) HistoryRow(entry) { game.name(it) }
        if (user.history.size > 100) Text("Showing the latest 100 entries.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, name: (Int) -> String) {
    val title = when (entry.kind) {
        "setup" -> "Seed found"
        "manual" -> "Seed entered by hand"
        "resync" -> "Position found again"
        "guaranteed" -> "Guaranteed draw"
        else -> if (entry.cats.size == 1) "1 roll" else "${entry.cats.size} rolls"
    }
    SectionCard {
        Text("$title · ${timeFormat.format(Date(entry.time))}", fontWeight = FontWeight.Bold)
        if (entry.banner.isNotEmpty()) Text(Alerts.shortName(entry.banner), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entry.cats.isNotEmpty()) Text(entry.cats.joinToString(", ") { name(it) })
    }
}

private val glossary = listOf(
    "Seed" to "A hidden number on your account. The game uses it to decide every rare gacha roll, so knowing it means knowing your future rolls. It changes each time you roll.",
    "Banner" to "One Rare Capsule event with its own set of cats. The same seed gives different cats on different banners, which is why comparing banners matters.",
    "Roll" to "One capsule opened with a Rare Ticket or 150 Cat Food.",
    "Guaranteed 11-draw" to "On some banners, paying 1500 Cat Food for 11 rolls at once makes the 11th an uber for certain. TrackCats shows which uber it would be.",
    "Step-up" to "A banner where you buy rolls in three stages and the last one ends with a guaranteed uber.",
    "Duplicate swap (reroll)" to "The game never gives the same rare cat twice in a row. When that would happen it picks a different rare instead, which shifts every roll after it.",
    "Track" to "Seed trackers draw future rolls as two columns, A and B. A duplicate swap or a guaranteed draw moves you from one column to the other. TrackCats works this out for you; the columns only appear in Advanced view.",
    "Platinum / Legend Ticket" to "Special tickets that always give an uber (or sometimes a legend rare). They use the same seed, so there is a best moment to use them.",
    "Wishlist" to "Cats you marked with a heart. Plans and alerts treat them as the most valuable rolls.",
    "NP" to "Points you get for trading in a duplicate cat, spent on talents.",
)

@Composable
fun GlossaryScreen(onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("What do these words mean?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        for ((term, meaning) in glossary) SectionCard(title = term) { Text(meaning) }
        OutlinedButton(onClick = onDone) { Text("Back") }
    }
}

private val tutorial = listOf(
    "Your rolls are already decided" to "The Battle Cats picks every rare gacha result from a hidden number called your seed. TrackCats works that number out and shows you the cats you will get before you spend anything.",
    "Step 1: find your seed" to "Tell TrackCats your last 10 rolls on one banner, in order. It searches every possible seed for the one that fits. You only do this once.",
    "Step 2: read your list" to "The Rolls tab lists your next cats on the banner you choose. New cats and wishlist cats are highlighted. The Banners tab compares every banner, and the Plan tab tells you exactly what to do with your tickets and Cat Food.",
    "Step 3: keep it in sync" to "Every time you roll in the game, tap \"I rolled\" and confirm what you got. That keeps the list correct. If you forget, \"My rolls stopped matching\" in More finds your place again.",
)

@Composable
fun TutorialScreen(onDone: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Welcome to TrackCats", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${page + 1} of ${tutorial.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard(title = tutorial[page].first, highlight = true) { Text(tutorial[page].second, style = MaterialTheme.typography.bodyLarge) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (page > 0) OutlinedButton(onClick = { page-- }) { Text("Back") }
            Button(onClick = { if (page < tutorial.size - 1) page++ else onDone() }) { Text(if (page < tutorial.size - 1) "Next" else "Get started") }
            if (page < tutorial.size - 1) TextButton(onClick = onDone) { Text("Skip") }
        }
    }
}
